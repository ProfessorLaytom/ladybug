"""Lightweight pure-Python msgspec compatibility shim for embedded SearXNG."""

from __future__ import annotations

import builtins
import copy
import datetime
import json as _stdlib_json
import sys
import tomllib as _stdlib_tomllib
import types
import typing as t

__all__ = [
    "DecodeError",
    "Meta",
    "Struct",
    "UNSET",
    "ValidationError",
    "field",
    "json",
    "to_builtins",
    "toml",
]


class DecodeError(Exception):
    """Raised when decoding structured data fails."""


class ValidationError(DecodeError):
    """Raised when decoded data fails schema validation."""


class _UnsetType:
    def __repr__(self) -> str:
        return "UNSET"

    def __bool__(self) -> bool:
        return False


UNSET = _UnsetType()


class Meta:
    """Stub annotation metadata compatible with typing.Annotated[..., msgspec.Meta(...)]."""

    def __init__(self, **kwargs: t.Any) -> None:
        for k, v in kwargs.items():
            setattr(self, k, v)


class _FieldSpec:
    __slots__ = ("default", "default_factory", "name")

    def __init__(
        self,
        *,
        default: t.Any = UNSET,
        default_factory: t.Any = UNSET,
        name: str | None = None,
    ) -> None:
        self.default = default
        self.default_factory = default_factory
        self.name = name


def field(
    *,
    default: t.Any = UNSET,
    default_factory: t.Any = UNSET,
    name: str | None = None,
) -> t.Any:
    if default is not UNSET and default_factory is not UNSET:
        raise TypeError("cannot specify both default and default_factory")
    return _FieldSpec(default=default, default_factory=default_factory, name=name)


class _FieldInfo:
    __slots__ = ("name", "encode_name", "kw_only", "has_default", "default", "default_factory")

    def __init__(
        self,
        name: str,
        encode_name: str,
        kw_only: bool,
        has_default: bool,
        default: t.Any = UNSET,
        default_factory: t.Any = UNSET,
    ) -> None:
        self.name = name
        self.encode_name = encode_name
        self.kw_only = kw_only
        self.has_default = has_default
        self.default = default
        self.default_factory = default_factory

    def resolve_default(self) -> t.Any:
        if self.default_factory is not UNSET:
            return self.default_factory()
        if isinstance(self.default, (dict, list, set)):
            return self.default.copy()
        return copy.deepcopy(self.default) if hasattr(self.default, "__dict__") else self.default


class _StructConfig:
    __slots__ = ("kw_only", "forbid_unknown_fields", "frozen", "omit_defaults", "tag")

    def __init__(
        self,
        *,
        kw_only: bool = False,
        forbid_unknown_fields: bool = False,
        frozen: bool = False,
        omit_defaults: bool = False,
        tag: t.Any = None,
    ) -> None:
        self.kw_only = kw_only
        self.forbid_unknown_fields = forbid_unknown_fields
        self.frozen = frozen
        self.omit_defaults = omit_defaults
        self.tag = tag


class _StructMeta(type):
    def __new__(
        mcs,
        name: str,
        bases: tuple[type, ...],
        namespace: dict[str, t.Any],
        kw_only: bool = False,
        forbid_unknown_fields: bool = False,
        frozen: bool = False,
        omit_defaults: bool = False,
        tag: t.Any = None,
        **extra_kwargs: t.Any,
    ) -> type:
        # Remove _FieldSpec placeholders from class dict so class attributes don't leak specs
        cleaned_ns = dict(namespace)
        own_annotations = dict(namespace.get("__annotations__", {}))
        own_field_specs: dict[str, _FieldInfo] = {}

        for field_name in own_annotations:
            if field_name.startswith("__"):
                continue
            raw_val = namespace.get(field_name, UNSET)
            if isinstance(raw_val, _FieldSpec):
                has_def = raw_val.default is not UNSET or raw_val.default_factory is not UNSET
                own_field_specs[field_name] = _FieldInfo(
                    name=field_name,
                    encode_name=raw_val.name or field_name,
                    kw_only=kw_only,
                    has_default=has_def,
                    default=raw_val.default,
                    default_factory=raw_val.default_factory,
                )
                if raw_val.default is not UNSET:
                    cleaned_ns[field_name] = raw_val.default
                else:
                    cleaned_ns.pop(field_name, None)
            elif raw_val is UNSET:
                own_field_specs[field_name] = _FieldInfo(
                    name=field_name,
                    encode_name=field_name,
                    kw_only=kw_only,
                    has_default=False,
                )
            else:
                own_field_specs[field_name] = _FieldInfo(
                    name=field_name,
                    encode_name=field_name,
                    kw_only=kw_only,
                    has_default=True,
                    default=raw_val,
                )

        cleaned_ns["__own_struct_fields__"] = own_field_specs
        cleaned_ns["__struct_config__"] = _StructConfig(
            kw_only=kw_only,
            forbid_unknown_fields=forbid_unknown_fields,
            frozen=frozen,
            omit_defaults=omit_defaults,
            tag=tag,
        )

        cls = super().__new__(mcs, name, bases, cleaned_ns)

        # Merge fields across MRO (base classes first, subclasses override)
        merged: dict[str, _FieldInfo] = {}
        for base in reversed(cls.__mro__):
            base_fields = base.__dict__.get("__own_struct_fields__")
            if base_fields:
                for fname, finfo in base_fields.items():
                    merged[fname] = finfo

        cls.__struct_field_infos__ = merged
        cls.__struct_fields__ = tuple(merged.keys())
        cls.__struct_defaults__ = tuple(
            finfo.default if finfo.default is not UNSET else UNSET
            for finfo in merged.values()
        )
        return cls


class Struct(metaclass=_StructMeta):
    """Pure-Python drop-in for msgspec.Struct."""

    __own_struct_fields__: t.ClassVar[dict[str, _FieldInfo]] = {}
    __struct_field_infos__: t.ClassVar[dict[str, _FieldInfo]] = {}
    __struct_fields__: t.ClassVar[tuple[str, ...]] = ()
    __struct_defaults__: t.ClassVar[tuple[t.Any, ...]] = ()
    __struct_config__: t.ClassVar[_StructConfig] = _StructConfig()

    def __init__(self, *args: t.Any, **kwargs: t.Any) -> None:
        infos = type(self).__struct_field_infos__
        pos_fields = [f for f in infos.values() if not f.kw_only]
        if len(args) > len(pos_fields):
            raise TypeError(
                f"{type(self).__name__}() takes at most {len(pos_fields)} positional arguments but {len(args)} were given"
            )

        assigned: dict[str, t.Any] = {}
        for finfo, val in zip(pos_fields, args):
            assigned[finfo.name] = val

        for k, v in kwargs.items():
            if k not in infos:
                raise TypeError(f"{type(self).__name__}() got an unexpected keyword argument {k!r}")
            if k in assigned:
                raise TypeError(f"{type(self).__name__}() got multiple values for argument {k!r}")
            assigned[k] = v

        for fname, finfo in infos.items():
            if fname in assigned:
                object.__setattr__(self, fname, assigned[fname])
            elif finfo.has_default:
                object.__setattr__(self, fname, finfo.resolve_default())
            else:
                raise TypeError(f"{type(self).__name__}() missing required argument: {fname!r}")

        post_init = getattr(self, "__post_init__", None)
        if callable(post_init):
            post_init()

    def __repr__(self) -> str:
        parts = [f"{f}={getattr(self, f, UNSET)!r}" for f in type(self).__struct_fields__]
        return f"{type(self).__name__}({', '.join(parts)})"

    def __eq__(self, other: object) -> bool:
        if type(self) is not type(other):
            return NotImplemented
        return all(getattr(self, f, UNSET) == getattr(other, f, UNSET) for f in type(self).__struct_fields__)


def to_builtins(obj: t.Any) -> t.Any:
    if isinstance(obj, Struct):
        return {
            finfo.encode_name: to_builtins(getattr(obj, fname))
            for fname, finfo in type(obj).__struct_field_infos__.items()
            if getattr(obj, fname, UNSET) is not UNSET
        }
    if isinstance(obj, dict):
        return {to_builtins(k): to_builtins(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple, set, frozenset)):
        return [to_builtins(item) for item in obj]
    if isinstance(obj, (datetime.datetime, datetime.date, datetime.time)):
        return obj.isoformat()
    if isinstance(obj, datetime.timedelta):
        return obj.total_seconds()
    return obj


def _resolve_hints(cls: type) -> dict[str, t.Any]:
    hints: dict[str, t.Any] = {}
    for base in reversed(cls.__mro__):
        ann = base.__dict__.get("__annotations__")
        if not ann:
            continue
        mod = sys.modules.get(base.__module__)
        globalns = vars(mod) if mod is not None else {}
        for k, v in ann.items():
            if isinstance(v, str):
                try:
                    hints[k] = eval(v, globalns)  # noqa: S307
                except Exception:
                    hints[k] = t.Any
            else:
                hints[k] = v
    return hints


def _convert_value(value: t.Any, target_type: t.Any, path: str = "$") -> t.Any:
    if target_type is None or target_type is t.Any or target_type is object:
        return value

    origin = t.get_origin(target_type)
    args = t.get_args(target_type)

    if origin is t.Annotated and args:
        return _convert_value(value, args[0], path)

    if origin is t.Literal:
        if value not in args:
            raise ValidationError(f"Invalid value {value!r}; expected one of {args!r} - at `{path}`")
        return value

    if origin in (t.Union, types.UnionType):
        if value is None and type(None) in args:
            return None
        last_err: Exception | None = None
        for arm in args:
            if arm is type(None):
                continue
            try:
                return _convert_value(value, arm, path)
            except ValidationError as exc:
                last_err = exc
        if last_err is not None:
            raise last_err
        raise ValidationError(f"Expected {target_type!r}, got {type(value).__name__} - at `{path}`")

    if origin is list:
        if not isinstance(value, list):
            raise ValidationError(f"Expected `list`, got `{type(value).__name__}` - at `{path}`")
        item_type = args[0] if args else t.Any
        return [_convert_value(item, item_type, f"{path}[{idx}]") for idx, item in enumerate(value)]

    if origin is builtins.set:
        if not isinstance(value, (list, set, tuple)):
            raise ValidationError(f"Expected `set`, got `{type(value).__name__}` - at `{path}`")
        item_type = args[0] if args else t.Any
        return {_convert_value(item, item_type, f"{path}[{idx}]") for idx, item in enumerate(value)}

    if origin is dict:
        if not isinstance(value, dict):
            raise ValidationError(f"Expected `dict`, got `{type(value).__name__}` - at `{path}`")
        key_type = args[0] if len(args) >= 1 else t.Any
        val_type = args[1] if len(args) >= 2 else t.Any
        return {
            _convert_value(k, key_type, f"{path}.{k}"): _convert_value(v, val_type, f"{path}.{k}")
            for k, v in value.items()
        }

    if isinstance(target_type, type) and issubclass(target_type, Struct):
        if not isinstance(value, dict):
            raise ValidationError(
                f"Expected `{target_type.__name__}`, got `{type(value).__name__}` - at `{path}`"
            )
        infos = target_type.__struct_field_infos__
        hints = _resolve_hints(target_type)
        cfg = target_type.__struct_config__

        encode_to_field = {finfo.encode_name: fname for fname, finfo in infos.items()}
        if cfg.forbid_unknown_fields:
            for k in value:
                if k not in encode_to_field:
                    raise ValidationError(f"Object contains unknown field `{k}` - at `{path}`")

        kwargs: dict[str, t.Any] = {}
        for raw_key, raw_val in value.items():
            fname = encode_to_field.get(raw_key)
            if fname is None:
                continue
            ftype = hints.get(fname, t.Any)
            kwargs[fname] = _convert_value(raw_val, ftype, f"{path}.{raw_key}")

        try:
            return target_type(**kwargs)
        except TypeError as exc:
            raise ValidationError(f"{exc} - at `{path}`") from exc

    if target_type is bool:
        if not isinstance(value, bool):
            raise ValidationError(f"Expected `bool`, got `{type(value).__name__}` - at `{path}`")
        return value

    if target_type is int:
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValidationError(f"Expected `int`, got `{type(value).__name__}` - at `{path}`")
        return value

    if target_type is float:
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValidationError(f"Expected `float`, got `{type(value).__name__}` - at `{path}`")
        return float(value)

    if target_type is str:
        if not isinstance(value, str):
            raise ValidationError(f"Expected `str`, got `{type(value).__name__}` - at `{path}`")
        return value

    return value


class _JsonNamespace:
    @staticmethod
    def encode(obj: t.Any) -> bytes:
        return _stdlib_json.dumps(to_builtins(obj), ensure_ascii=False).encode("utf-8")

    @staticmethod
    def decode(buf: bytes | bytearray | memoryview | str, *, type: t.Any = None) -> t.Any:  # noqa: A002
        try:
            raw = buf.decode("utf-8") if isinstance(buf, (bytes, bytearray, memoryview)) else buf
            parsed = _stdlib_json.loads(raw)
        except Exception as exc:
            raise DecodeError(str(exc)) from exc
        if type is not None:
            return _convert_value(parsed, type, "$")
        return parsed


class _TomlNamespace:
    @staticmethod
    def decode(buf: bytes | bytearray | memoryview | str, *, type: t.Any = None) -> t.Any:  # noqa: A002
        try:
            raw = buf.decode("utf-8") if isinstance(buf, (bytes, bytearray, memoryview)) else buf
            parsed = _stdlib_tomllib.loads(raw)
        except Exception as exc:
            raise DecodeError(str(exc)) from exc
        if type is not None:
            return _convert_value(parsed, type, "$")
        return parsed


json = _JsonNamespace()
toml = _TomlNamespace()
