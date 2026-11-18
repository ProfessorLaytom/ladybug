"""Pure-Python curl_cffi.requests.exceptions shim backed by httpx."""


class RequestException(IOError):
    """Base HTTP request exception."""


class HTTPError(RequestException):
    """HTTP error response."""


class ConnectionError(RequestException):  # noqa: A001
    """Network connection failure."""


class Timeout(RequestException):
    """Request timeout."""


class ConnectTimeout(Timeout):
    """Connection phase timeout."""


class ReadTimeout(Timeout):
    """Read phase timeout."""


class ProxyError(ConnectionError):
    """Proxy failure."""


class SSLError(ConnectionError):
    """TLS/SSL error."""


class CertificateVerifyError(SSLError):
    """TLS certificate verification failed."""


class DNSError(ConnectionError):
    """DNS resolution error."""


class IncompleteRead(RequestException):
    """Incomplete HTTP body read."""


class StreamConsumedError(RequestException):
    """Stream already consumed."""
