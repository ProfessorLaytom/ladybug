"""Embedded SearXNG WSGI server runner with Waitress, /healthz, /sw.js, and idle tracking."""

from __future__ import annotations

import os
import secrets
import tempfile
import threading
import time
import traceback
import typing as t

_LOCK = threading.Lock()
_SERVER: t.Any = None
_THREAD: threading.Thread | None = None
_LAST_REQUEST_TS: float = time.monotonic()

_SW_JS = r"""
self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

const WAKE_HTML = `<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Réveil de SearXNG…</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
      background-color: #871818;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #1C1B1F;
    }
    .card {
      width: 100%;
      max-width: 400px;
      background-color: #FACFCA;
      border-radius: 12px;
      padding: 24px 20px;
      text-align: center;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
    }
    h1 {
      margin: 0 0 10px 0;
      font-size: 20px;
      color: #1C1B1F;
    }
    p {
      margin: 0 0 18px 0;
      font-size: 14px;
      line-height: 1.45;
      color: #2B1111;
    }
    .btn {
      display: inline-block;
      padding: 12px 18px;
      border-radius: 12px;
      background-color: #B21F1F;
      color: #FACFCA;
      text-decoration: none;
      font-weight: 600;
      font-size: 15px;
    }
    .btn:active {
      background-color: #641212;
    }
  </style>
</head>
<body>
  <div class="card">
    <h1>Réveil de SearXNG… 🐞</h1>
    <p>Le moteur local s'était endormi pour économiser la batterie. Redémarrage en cours…</p>
    <a class="btn" href="ladybug://wake">Réveiller SearXNG 🐞</a>
  </div>
  <script>
    try {
      window.location.href = "ladybug://wake";
    } catch (e) {}
    const poll = setInterval(async () => {
      try {
        const res = await fetch('/healthz', { cache: 'no-store' });
        if (res.ok) {
          clearInterval(poll);
          window.location.reload();
        }
      } catch (e) {}
    }, 150);
  </script>
</body>
</html>`;

self.addEventListener('fetch', (event) => {
  if (event.request.mode !== 'navigate') {
    return;
  }
  const url = new URL(event.request.url);
  if (url.hostname !== '127.0.0.1' && url.hostname !== 'localhost') {
    return;
  }
  event.respondWith((async () => {
    try {
      return await fetch(event.request);
    } catch (err) {
      return new Response(WAKE_HTML, {
        status: 200,
        headers: {
          'Content-Type': 'text/html; charset=utf-8',
          'Cache-Control': 'no-store'
        }
      });
    }
  })());
});
""".strip().encode("utf-8")


def _ensure_secret_key(files_dir: str) -> str:
    os.makedirs(files_dir, exist_ok=True)
    key_path = os.path.join(files_dir, "searxng_secret.key")
    if os.path.exists(key_path):
        with open(key_path, "r", encoding="utf-8") as f:
            existing = f.read().strip()
        if len(existing) >= 32:
            return existing
    generated = secrets.token_hex(32)
    with open(key_path, "w", encoding="utf-8") as f:
        f.write(generated)
    return generated


def _build_wsgi_app(flask_app: t.Any) -> t.Callable[..., t.Any]:
    def middleware(environ: dict[str, t.Any], start_response: t.Callable[..., t.Any]):
        global _LAST_REQUEST_TS
        path = environ.get("PATH_INFO", "/")
        is_internal_probe = environ.get("HTTP_X_INTERNAL_PROBE") == "1"

        if path == "/healthz":
            if not is_internal_probe:
                _LAST_REQUEST_TS = time.monotonic()
            start_response(
                "200 OK",
                [
                    ("Content-Type", "text/plain; charset=utf-8"),
                    ("Cache-Control", "no-store"),
                    ("Content-Length", "2"),
                ],
            )
            return [b"ok"]

        if path == "/sw.js":
            _LAST_REQUEST_TS = time.monotonic()
            start_response(
                "200 OK",
                [
                    ("Content-Type", "application/javascript; charset=utf-8"),
                    ("Service-Worker-Allowed", "/"),
                    ("Cache-Control", "no-cache"),
                    ("Content-Length", str(len(_SW_JS))),
                ],
            )
            return [_SW_JS]

        _LAST_REQUEST_TS = time.monotonic()
        environ.setdefault("HTTP_X_FORWARDED_FOR", "127.0.0.1")
        environ.setdefault("HTTP_X_REAL_IP", "127.0.0.1")
        return flask_app(environ, start_response)

    return middleware


def touch_activity() -> None:
    global _LAST_REQUEST_TS
    _LAST_REQUEST_TS = time.monotonic()


def seconds_since_last_request() -> float:
    return max(0.0, time.monotonic() - _LAST_REQUEST_TS)


def is_running() -> bool:
    with _LOCK:
        return _SERVER is not None


def start_server(files_dir: str, port: int = 8888) -> bool:
    global _SERVER, _THREAD, _LAST_REQUEST_TS
    with _LOCK:
        if _SERVER is not None:
            _LAST_REQUEST_TS = time.monotonic()
            return True

        tmp_dir = os.path.join(files_dir, "searxng_tmp")
        os.makedirs(tmp_dir, exist_ok=True)
        os.environ["TMPDIR"] = tmp_dir
        os.environ["TEMP"] = tmp_dir
        os.environ["TMP"] = tmp_dir
        tempfile.tempdir = tmp_dir

        os.environ["SEARXNG_SECRET"] = _ensure_secret_key(files_dir)
        bundled_settings = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "settings.yml"
        )
        runtime_settings = os.path.join(files_dir, "searxng_settings.yml")
        with open(bundled_settings, "rb") as src, open(runtime_settings, "wb") as dst:
            dst.write(src.read())
        os.environ["SEARXNG_SETTINGS_PATH"] = runtime_settings
        os.environ["SEARXNG_PORT"] = str(port)
        os.environ["SEARXNG_BIND_ADDRESS"] = "127.0.0.1"

        try:
            from waitress import create_server
            from searx import webapp

            _LAST_REQUEST_TS = time.monotonic()
            wrapped_app = _build_wsgi_app(webapp.app)
            server = create_server(wrapped_app, host="127.0.0.1", port=port, threads=12)
            _SERVER = server

            def _serve() -> None:
                try:
                    server.run()
                except Exception:
                    traceback.print_exc()
                finally:
                    with _LOCK:
                        global _SERVER
                        if _SERVER is server:
                            _SERVER = None

            _THREAD = threading.Thread(target=_serve, name="searxng_waitress", daemon=True)
            _THREAD.start()
            return True
        except Exception:
            traceback.print_exc()
            raise


def stop_server() -> None:
    global _SERVER, _THREAD
    with _LOCK:
        server = _SERVER
        _SERVER = None
        _THREAD = None
    if server is not None:
        try:
            server.close()
        except Exception:
            pass
