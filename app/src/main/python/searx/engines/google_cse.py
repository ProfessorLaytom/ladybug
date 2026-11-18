# SPDX-License-Identifier: AGPL-3.0-or-later
"""Google Custom Search Engine"""

import datetime
import threading
import time
import typing as t
from json import loads
from urllib.parse import urlencode

from searx.enginelib import EngineCache
from searx.exceptions import SearxEngineAPIException, SearxEngineTooManyRequestsException
from searx.network import get
from searx.result_types import EngineResults, Result, MainResult, Image

from searx.engines.google import fetch_traits  # pylint: disable=unused-import
from searx.engines.google import filter_mapping, get_google_info

if t.TYPE_CHECKING:
    from searx.extended_types import SXNG_Response
    from searx.search.processors import OnlineParams

about = {
    "website": "https://www.google.com",
    "wikidata_id": "Q2233943",
    "official_api_documentation": "https://developers.google.com/custom-search/docs/element",
    "use_official_api": False,
    "require_api_key": False,
    "results": "JSONP",
    "description": "Platform for creating custom search engines based on Google Search.",
}

categories = ["general", "web"]
paging = True
enable_http3 = True
max_page = 5
page_size = 20
time_range_support = True
language_support = True
safesearch = True
retries = 1

GoogleCategType = t.Literal["", "image"]
google_categ: GoogleCategType = ""
"""Google CSE category. Set to ``""`` for web search."""

CX = "partner-pub-8993703457585266:4862972284"  # blackle.com
_TOKEN_TTL_SECONDS = 3600
_TOKEN_LOCK = threading.Lock()
_MEM_CACHE: tuple[dict[str, str], float] | None = None

CACHE: EngineCache


def _warm_token_bg() -> None:
    try:
        from searx.network import set_context_network_name, set_timeout_for_thread

        set_timeout_for_thread(4.0)
        set_context_network_name("google cse")
        _cse_token()
    except Exception:  # pylint: disable=broad-except
        pass


def setup(engine_settings: dict[str, t.Any]) -> bool:
    global CACHE  # pylint: disable=global-statement

    if google_categ not in t.get_args(GoogleCategType):
        raise ValueError("invalid google cse category: %s" % google_categ)

    CACHE = EngineCache(engine_settings["name"])
    threading.Thread(target=_warm_token_bg, name="google_cse_warm", daemon=True).start()
    return True


def _cse_token() -> dict[str, str]:
    global _MEM_CACHE  # pylint: disable=global-statement

    now = time.monotonic()
    with _TOKEN_LOCK:
        if _MEM_CACHE is not None and _MEM_CACHE[1] > now:
            return _MEM_CACHE[0]

        token: dict[str, str] | None = CACHE.get(CX)
        if token:
            _MEM_CACHE = (token, time.monotonic() + _TOKEN_TTL_SECONDS)
            return token

        resp = get(
            f"https://cse.google.com/cse.js?cx={CX}",
            headers={"Accept": "*/*", "Referer": "https://cse.google.com/"},
            timeout=2.5,
        )
        if not resp.ok:
            raise SearxEngineAPIException("failed to obtain cse token")

        end = resp.text.rfind("});")
        start = resp.text.rfind("({")
        opts: dict[str, t.Any] = loads(resp.text[start + 1 : end + 1])

        cse_tok = opts.get("cse_token")
        if not cse_tok:
            raise SearxEngineAPIException("failed to obtain cse token")

        exp = opts.get("exp")
        token = {
            "cse_tok": str(cse_tok),
            "cselibv": str(opts.get("cselibVersion", "")),
            "exp": ",".join(exp) if exp else "",
        }
        CACHE.set(CX, token, expire=_TOKEN_TTL_SECONDS)
        _MEM_CACHE = (token, time.monotonic() + _TOKEN_TTL_SECONDS)
        return token


def _get_start_and_end_date_str(time_range: str) -> tuple[str, str]:
    time_range_map = {"day": 1, "week": 7, "month": 30, "year": 365}

    end_date = datetime.datetime.now()
    start_date = end_date - datetime.timedelta(days=time_range_map[time_range])

    return start_date.strftime("%Y%m%d"), end_date.strftime("%Y%m%d")


def request(query: str, params: "OnlineParams") -> None:
    token = _cse_token()

    google_info = get_google_info(params, traits)

    args = {
        "rsz": "filtered_cse",
        "num": str(page_size),
        "hl": google_info["params"]["hl"],
        "cselibv": token["cselibv"],
        "cx": CX,
        "q": query,
        "safe": filter_mapping[params["safesearch"]],
        "cse_tok": token["cse_tok"],
        "callback": "_",
        "rurl": "",
        "searchtype": google_categ,
    }
    if params["time_range"]:
        start_date, end_date = _get_start_and_end_date_str(params["time_range"])
        args["sort"] = f"date:r:{start_date}:{end_date}"

    if google_info["country"] not in (None, "ZZ"):
        args["gl"] = google_info["country"]
    if token["exp"]:
        args["exp"] = token["exp"]

    start = (params["pageno"] - 1) * page_size
    if start:
        args["start"] = str(start)

    params["url"] = "https://cse.google.com/cse/element/v1?" + urlencode(args)
    params["cookies"] = google_info["cookies"]
    params["headers"].update(google_info["headers"])
    params["headers"]["Referer"] = "https://cse.google.com/"


def response(resp: "SXNG_Response") -> EngineResults:
    json_resp = resp.text[resp.text.find("{") : resp.text.rfind("}") + 1]
    data = loads(json_resp)

    # not the real types, but a sufficient approximation
    item: dict[str, t.Any]
    error: dict[str, t.Any]

    if error := data.get("error"):
        message = error.get("message", "unknown error")
        if error.get("code") == 429:
            raise SearxEngineTooManyRequestsException(message=f"google cse: {message}")
        raise SearxEngineAPIException(f"google cse: {message}")

    results = EngineResults()

    for item in data.get("results", []):

        res: Result | None = None
        if google_categ == "":
            res = web_item(item)
        elif google_categ == "image":
            res = img_item(item)

        if res is not None:
            results.add(res)

    return results


def web_item(item: dict[str, t.Any]) -> MainResult | None:
    url = item.get("unescapedUrl")
    if not url:
        return None
    return MainResult(
        url=url,
        title=item.get("titleNoFormatting", ""),
        content=item.get("contentNoFormatting", ""),
        thumbnail=item.get("richSnippet", {}).get("cseThumbnail", {}).get("src", ""),
    )


def img_item(item: dict[str, t.Any]) -> Image | None:
    resolution = ""
    if item.get("height") and item.get("width"):
        resolution = f"{item['width']}x{item['height']}"
    return Image(
        url=item["originalContextUrl"],
        title=item.get("titleNoFormatting", ""),
        content=item.get("contentNoFormatting", ""),
        img_src=item["unescapedUrl"],
        thumbnail_src=item["tbUrl"],
        resolution=resolution,
        img_format=item["fileFormat"].split("/")[-1],
    )
