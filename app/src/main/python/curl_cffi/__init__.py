"""Pure-Python curl_cffi shim backed by httpx for embedded SearXNG."""

from __future__ import annotations

import asyncio
import enum
import ssl
import typing as t
import certifi
import httpx

from . import requests
from .requests import Response
from .requests.exceptions import (
    CertificateVerifyError,
    ConnectTimeout,
    ConnectionError as CurlConnectionError,
    HTTPError,
    IncompleteRead,
    ProxyError,
    ReadTimeout,
    RequestException,
    SSLError,
    Timeout,
)


class CurlHttpVersion(enum.IntEnum):
    V1_0 = 1
    V1_1 = 2
    V2_0 = 3
    V2TLS = 4
    V2_PRIOR_KNOWLEDGE = 5
    V3 = 30
    V3ONLY = 31


class _CurlOptMeta(type):
    def __getattr__(cls, name: str) -> int:
        return abs(hash(name)) % 10000


class CurlOpt(metaclass=_CurlOptMeta):
    PROTOCOLS_STR = 1
    REDIR_PROTOCOLS_STR = 2
    CAINFO = 3
    CAPATH = 4
    FORBID_REUSE = 5


_DEFAULT_CHROME_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)
_DEFAULT_FIREFOX_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"
)
_BROWSER_CIPHERS = (
    "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:"
    "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:"
    "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:"
    "ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:"
    "AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA:AES256-SHA"
)


def _create_ssl_context(verify: bool | str) -> ssl.SSLContext | bool:
    if verify is False:
        return False
    cafile = verify if isinstance(verify, str) else certifi.where()
    ctx = ssl.create_default_context(cafile=cafile)
    try:
        ctx.set_ciphers(_BROWSER_CIPHERS)
    except ssl.SSLError:
        pass
    return ctx


def _translate_httpx_exception(exc: Exception) -> RequestException:
    if isinstance(exc, httpx.ConnectTimeout):
        return ConnectTimeout(str(exc))
    if isinstance(exc, httpx.ReadTimeout):
        return ReadTimeout(str(exc))
    if isinstance(exc, httpx.TimeoutException):
        return Timeout(str(exc))
    if isinstance(exc, httpx.ProxyError):
        return ProxyError(str(exc))
    if isinstance(exc, httpx.ConnectError):
        msg = str(exc)
        if "CERTIFICATE_VERIFY_FAILED" in msg or "SSL" in msg.upper():
            return CertificateVerifyError(msg)
        return CurlConnectionError(msg)
    if isinstance(exc, httpx.ReadError):
        return IncompleteRead(str(exc))
    if isinstance(exc, ssl.SSLError):
        return SSLError(str(exc))
    if isinstance(exc, httpx.HTTPStatusError):
        return HTTPError(str(exc))
    return RequestException(str(exc))


class _StreamContext:
    def __init__(
        self,
        session: "AsyncSession",
        method: str,
        url: str,
        kwargs: dict[str, t.Any],
    ) -> None:
        self._session = session
        self._method = method
        self._url = url
        self._kwargs = kwargs
        self._cm: t.Any = None

    async def __aenter__(self) -> Response:
        req_kwargs, _ = self._session._prepare_kwargs(self._kwargs)
        try:
            self._cm = self._session._client.stream(self._method, self._url, **req_kwargs)
            raw_resp = await self._cm.__aenter__()
            return self._session._wrap_response(raw_resp)
        except Exception as exc:
            raise _translate_httpx_exception(exc) from exc

    async def __aexit__(self, exc_type: t.Any, exc_val: t.Any, exc_tb: t.Any) -> None:
        if self._cm is not None:
            await self._cm.__aexit__(exc_type, exc_val, exc_tb)
        if self._session._discard_cookies:
            self._session._client.cookies.clear()


class AsyncSession:
    """curl_cffi.AsyncSession-compatible client backed by httpx.AsyncClient."""

    def __init__(
        self,
        *,
        verify: bool | str = True,
        max_redirects: int = 30,
        max_clients: int = 10,
        response_class: type[Response] = Response,
        discard_cookies: bool = True,
        proxy: str | None = None,
        proxies: dict[str, str] | None = None,
        impersonate: str | None = None,
        default_headers: bool = True,
        interface: str | None = None,
        http_version: int = CurlHttpVersion.V1_1,
        curl_options: dict[int, t.Any] | None = None,
        **extra: t.Any,
    ) -> None:
        del max_clients, interface, http_version, extra
        self._response_class = response_class
        self._discard_cookies = discard_cookies
        self._impersonate = impersonate or ""
        self._default_headers = default_headers
        self._forbid_reuse = bool((curl_options or {}).get(CurlOpt.FORBID_REUSE))

        resolved_proxy = proxy
        if not resolved_proxy and proxies:
            resolved_proxy = proxies.get("https") or proxies.get("http")

        limits = httpx.Limits(
            max_connections=30,
            max_keepalive_connections=10,
            keepalive_expiry=10.0,
        )
        client_kwargs: dict[str, t.Any] = {
            "verify": _create_ssl_context(verify),
            "max_redirects": max_redirects if max_redirects and max_redirects > 0 else 30,
            "http2": False,
            "limits": limits,
            "follow_redirects": True,
        }
        if resolved_proxy:
            client_kwargs["proxy"] = resolved_proxy

        self._client_kwargs = client_kwargs
        self._client = httpx.AsyncClient(**client_kwargs)

    async def _reset_client(self) -> None:
        old = self._client
        self._client = httpx.AsyncClient(**self._client_kwargs)
        try:
            await old.aclose()
        except Exception:
            pass

    def _wrap_response(self, raw_resp: httpx.Response) -> Response:
        if self._response_class is Response:
            return Response(raw_resp)
        return self._response_class(raw_resp)

    def _prepare_kwargs(self, raw_kwargs: dict[str, t.Any]) -> tuple[dict[str, t.Any], bool]:
        kwargs = dict(raw_kwargs)
        impersonate = kwargs.pop("impersonate", self._impersonate)
        default_headers = kwargs.pop("default_headers", self._default_headers)
        curl_options = kwargs.pop("curl_options", None) or {}
        forbid_reuse = self._forbid_reuse or bool(curl_options.get(CurlOpt.FORBID_REUSE))

        kwargs.pop("max_redirects", None)
        kwargs.pop("verify", None)
        kwargs.pop("enable_http3", None)
        kwargs.pop("accept_encoding", None)

        if "allow_redirects" in kwargs:
            kwargs["follow_redirects"] = bool(kwargs.pop("allow_redirects"))

        headers = dict(kwargs.get("headers") or {})
        lower_keys = {k.lower() for k in headers}

        if default_headers and impersonate not in ("", "none"):
            if "user-agent" not in lower_keys:
                headers["User-Agent"] = _DEFAULT_FIREFOX_UA
            if "accept" not in lower_keys:
                headers["Accept"] = (
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
            if headers.get("Accept") != "*/*":
                if "upgrade-insecure-requests" not in lower_keys:
                    headers["Upgrade-Insecure-Requests"] = "1"
                if "sec-fetch-dest" not in lower_keys:
                    headers["Sec-Fetch-Dest"] = "document"
                if "sec-fetch-mode" not in lower_keys:
                    headers["Sec-Fetch-Mode"] = "navigate"
                if "sec-fetch-site" not in lower_keys:
                    headers["Sec-Fetch-Site"] = "none"
                if "sec-fetch-user" not in lower_keys:
                    headers["Sec-Fetch-User"] = "?1"

        cookies = kwargs.pop("cookies", None)
        if isinstance(cookies, dict) and cookies:
            cookie_str = "; ".join(f"{k}={v}" for k, v in cookies.items())
            existing_cookie_key = next((k for k in headers if k.lower() == "cookie"), None)
            if existing_cookie_key:
                headers[existing_cookie_key] = f"{headers[existing_cookie_key]}; {cookie_str}"
            else:
                headers["Cookie"] = cookie_str

        if headers:
            kwargs["headers"] = headers
        else:
            kwargs.pop("headers", None)

        for empty_body_key in ("data", "json", "content"):
            if empty_body_key in kwargs and not kwargs[empty_body_key]:
                kwargs.pop(empty_body_key)

        if kwargs.get("auth") is None:
            kwargs.pop("auth", None)

        if "timeout" in kwargs and isinstance(kwargs["timeout"], (int, float)):
            kwargs["timeout"] = float(kwargs["timeout"])

        return kwargs, forbid_reuse

    async def request(self, method: str, url: str, **kwargs: t.Any) -> Response:
        req_kwargs, forbid_reuse = self._prepare_kwargs(kwargs)
        reset_needed = forbid_reuse
        try:
            raw_resp = await self._client.request(method, url, **req_kwargs)
            return self._wrap_response(raw_resp)
        except asyncio.CancelledError:
            reset_needed = True
            raise
        except Exception as exc:
            reset_needed = True
            raise _translate_httpx_exception(exc) from exc
        finally:
            if reset_needed:
                await self._reset_client()
            elif self._discard_cookies:
                self._client.cookies.clear()

    def stream(self, method: str, url: str, **kwargs: t.Any) -> _StreamContext:
        return _StreamContext(self, method, url, kwargs)

    async def close(self) -> None:
        await self._client.aclose()


__all__ = [
    "AsyncSession",
    "CurlHttpVersion",
    "CurlOpt",
    "Response",
    "requests",
]
