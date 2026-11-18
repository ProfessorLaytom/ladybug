"""Minimal valkey stub for embedded SearXNG when limiter/valkey is disabled."""


class ValkeyError(Exception):
    """Stub Valkey error."""


class exceptions:  # noqa: N801
    ValkeyError = ValkeyError


class Valkey:
    """Stub Valkey client."""

    @classmethod
    def from_url(cls, url: str, **kwargs):
        raise ValkeyError(f"Valkey is disabled in embedded Ladybug SearXNG ({url})")
