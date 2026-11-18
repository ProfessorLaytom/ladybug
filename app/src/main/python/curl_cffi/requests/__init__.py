"""Pure-Python curl_cffi.requests shim backed by httpx."""

from __future__ import annotations

import typing as t
import httpx

from . import exceptions


class Response:
    """curl_cffi.requests.Response-compatible wrapper around httpx.Response."""

    _url: str = ""

    def __init__(self, raw_response: httpx.Response | None = None) -> None:
        self._raw = raw_response
        self.status_code: int = raw_response.status_code if raw_response is not None else 0
        self.reason: str = raw_response.reason_phrase if raw_response is not None else ""
        self.headers = raw_response.headers if raw_response is not None else httpx.Headers()
        self.history: list[t.Any] = list(raw_response.history) if raw_response is not None else []
        self.request = raw_response.request if raw_response is not None else None
        self.cookies = raw_response.cookies if raw_response is not None else httpx.Cookies()
        self.http_version: int = self._map_http_version(
            getattr(raw_response, "http_version", "HTTP/1.1") if raw_response is not None else "HTTP/1.1"
        )
        self._content_cache: bytes | None = None
        if raw_response is not None:
            self.url = str(raw_response.url)

    @staticmethod
    def _map_http_version(version_str: str) -> int:
        if version_str == "HTTP/1.0":
            return 1
        if version_str == "HTTP/2":
            return 3
        if version_str == "HTTP/3":
            return 30
        return 2

    @property
    def url(self) -> str:
        return self._url

    @url.setter
    def url(self, value: str) -> None:
        self._url = str(value or "")

    @property
    def ok(self) -> bool:
        return 200 <= (self.status_code or 0) < 400

    @property
    def content(self) -> bytes:
        if self._content_cache is not None:
            return self._content_cache
        if self._raw is None:
            return b""
        return self._raw.content

    @property
    def text(self) -> str:
        if self._raw is None:
            return ""
        return self._raw.text

    def json(self, **kwargs: t.Any) -> t.Any:
        if self._raw is None:
            raise exceptions.RequestException("Empty response")
        return self._raw.json(**kwargs)

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise exceptions.HTTPError(f"HTTP {self.status_code} {self.reason} for {self.url}")

    async def aiter_content(self, chunk_size: int | None = None) -> t.AsyncIterator[bytes]:
        if self._raw is None:
            return
        try:
            async for chunk in self._raw.aiter_bytes(chunk_size=chunk_size):
                yield chunk
        except httpx.StreamConsumed as exc:
            raise exceptions.StreamConsumedError(str(exc)) from exc
        except httpx.HTTPError as exc:
            raise exceptions.RequestException(str(exc)) from exc

    async def aclose(self) -> None:
        if self._raw is not None:
            await self._raw.aclose()


__all__ = ["Response", "exceptions"]
