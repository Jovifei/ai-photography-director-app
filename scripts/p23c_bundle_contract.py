"""Strict offline PKB1 producer-side check. No I/O, image access or model execution.

The Android consumer remains authoritative. This producer profile additionally
rejects Unicode line/paragraph separators; it never repairs a malformed bundle.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import re
from typing import Any

MAX_BUNDLE_BYTES = 512 * 1024
PHOTO_FIELDS = (
    "scene", "background_story", "lighting", "composition", "subject_intent",
    "emotion", "pose_template", "camera_position", "director_prompt",
)
TOKEN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z", re.ASCII)
SHA256 = re.compile(r"[a-fA-F0-9]{64}\Z", re.ASCII)
UNSAFE = re.compile(
    r"[a-z][a-z0-9+.-]*://|(?:content|file):|(?:^|\s)[a-z]:[\\/]|"
    r"/sdcard(?:/|$)|/storage(?:/|$)|\.\.[\\/]", re.I,
)


class PackError(ValueError):
    """Only a fixed code may cross the CLI boundary; never include input content."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise PackError(code)


def exact_keys(value: Any, keys: set[str]) -> None:
    require(type(value) is dict and set(value) == keys, "SCHEMA_INVALID")


def opaque(value: Any) -> None:
    require(type(value) is str and TOKEN.fullmatch(value) is not None, "IDENTIFIER_INVALID")


def _unique_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def _reject_number(_: str) -> Any:
    raise PackError("NUMBER_NOT_ALLOWED")


def strict_json(raw: bytes, limit: int = MAX_BUNDLE_BYTES) -> dict[str, Any]:
    require(bool(raw), "DOCUMENT_EMPTY")
    require(len(raw) <= limit, "DOCUMENT_TOO_LARGE")
    require(not raw.startswith(b"\xef\xbb\xbf"), "INVALID_UTF8")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeError as error:
        raise PackError("INVALID_UTF8") from error
    # Bound containers before CPython's recursive parser. Strings are skipped,
    # including escaped quotes. Full syntax is still checked by json.loads.
    depth, in_string, escaped = 0, False, False
    for char in text:
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char in "{[":
            depth += 1
            require(depth <= 32, "NESTING_TOO_DEEP")
        elif char in "}]":
            depth -= 1
    try:
        result = json.loads(text, object_pairs_hook=_unique_pairs,
                            parse_float=_reject_number, parse_constant=_reject_number)
    except PackError:
        raise
    except (ValueError, RecursionError) as error:
        raise PackError("MALFORMED_JSON") from error
    require(type(result) is dict, "SCHEMA_INVALID")
    def scalar_unicode(value: Any) -> None:
        if isinstance(value, str):
            require(not any(0xD800 <= ord(c) <= 0xDFFF for c in value), "INVALID_UNICODE")
        elif isinstance(value, list):
            for item in value:
                scalar_unicode(item)
        elif isinstance(value, dict):
            for key, item in value.items():
                scalar_unicode(key)
                scalar_unicode(item)
    scalar_unicode(result)
    return result


def encoded_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2,
                       allow_nan=False) + "\n").encode("utf-8")


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical_payload(bundle: dict[str, Any]) -> bytes:
    """PKB1 bytes, not RFC8785/JCS. Call after shape validation for external input."""
    result = bytearray(b"PKB1\n")
    def append(name: str, value: str) -> None:
        for token in (name, value):
            raw = token.encode("utf-8")
            result.extend(str(len(raw)).encode("ascii") + b":" + raw + b"\n")
    append("contract_version", bundle["contract_version"])
    append("bundle_id", bundle["bundle_id"])
    for name in ("origin", "producer_id", "release_id"):
        append("source." + name, bundle["source"][name])
    append("references.count", str(len(bundle["references"])))
    for index, reference in enumerate(bundle["references"]):
        prefix = f"references[{index}]"
        append(prefix + ".reference_id", reference["reference_id"])
        for name in PHOTO_FIELDS:
            append(prefix + ".photography." + name, reference["photography"][name])
    return bytes(result)


def validate_bundle(raw: bytes) -> dict[str, Any]:
    bundle = strict_json(raw)
    exact_keys(bundle, {"contract_version", "bundle_id", "source", "integrity", "references"})
    require(bundle["contract_version"] == "1.0", "VERSION_UNSUPPORTED")
    opaque(bundle["bundle_id"])
    source = bundle["source"]
    exact_keys(source, {"origin", "producer_id", "release_id"})
    require(source["origin"] in ("PIPELINE", "LOCAL_SERVICE"), "ORIGIN_INVALID")
    opaque(source["producer_id"])
    opaque(source["release_id"])
    integrity = bundle["integrity"]
    exact_keys(integrity, {"algorithm", "payload_sha256"})
    digest = integrity["payload_sha256"]
    require(integrity["algorithm"] == "SHA-256" and type(digest) is str
            and SHA256.fullmatch(digest) is not None, "DIGEST_INVALID")
    refs = bundle["references"]
    require(type(refs) is list and 1 <= len(refs) <= 20, "REFERENCE_COUNT_INVALID")
    seen: set[str] = set()
    for ref in refs:
        exact_keys(ref, {"reference_id", "photography"})
        opaque(ref["reference_id"])
        require(ref["reference_id"] not in seen, "DUPLICATE_REFERENCE_ID")
        seen.add(ref["reference_id"])
        exact_keys(ref["photography"], set(PHOTO_FIELDS))
        for name, value in ref["photography"].items():
            require(type(value) is str and bool(value.strip()), "TEXT_INVALID")
            require(len(value) <= (1200 if name == "director_prompt" else 800), "TEXT_TOO_LONG")
            require(not any(ord(c) < 32 or 127 <= ord(c) <= 159
                            or c in "\u2028\u2029" for c in value), "TEXT_CONTROL_CHARACTER")
            require(UNSAFE.search(value) is None, "TRANSPORT_TEXT_FORBIDDEN")
    require(hmac.compare_digest(digest.lower(), sha256(canonical_payload(bundle))), "DIGEST_MISMATCH")
    return bundle
