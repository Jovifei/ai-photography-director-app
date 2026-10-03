"""Bounded, single-document synthetic intake integrity checks; no import authority."""
from __future__ import annotations
import hashlib
import json
import re

MAX_BYTES = 512 * 1024
MAX_MANIFEST_BYTES = 16 * 1024
MAX_CHECKSUM_BYTES = 4096
CONTRACT = "T14_PREFLIGHT_MANIFEST_V1"
MODE = "SYNTHETIC_CONTRACT_TEST_ONLY"
SHA = re.compile(r"[0-9a-fA-F]{64}")
REVISION = re.compile(r"[0-9a-f]{40}")
LEAF = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
RESERVED = {"CON", "PRN", "AUX", "NUL"} | {f"COM{i}" for i in range(1, 10)} | {f"LPT{i}" for i in range(1, 10)}

class IntakeError(ValueError):
    pass

def _result(error=None, **details):
    return {"status": "COMPATIBILITY_PREPARATION_ONLY", "compatible_preflight": error is None,
            "ready": False, "db_import": False, "t14_authority": False,
            "producer_origin_verified": False, "consumer_validation": "NOT_RUN",
            **({"error": error} if error else {}), **details}

def _strict_json(raw: bytes, limit: int):
    if not isinstance(raw, bytes):
        raise IntakeError("INPUT_TYPE")
    if len(raw) > limit:
        raise IntakeError("SIZE")
    if raw.startswith(b"\xef\xbb\xbf"):
        raise IntakeError("BOM")
    depth = 0
    quoted = escaped = False
    for char in raw:
        if quoted:
            if escaped:
                escaped = False
            elif char == 92:
                escaped = True
            elif char == 34:
                quoted = False
        elif char == 34:
            quoted = True
        elif char in (123, 91):
            depth += 1
            if depth > 64:
                raise IntakeError("JSON_DEPTH")
        elif char in (125, 93):
            depth -= 1
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result:
                raise IntakeError("DUPLICATE_KEY")
            result[key] = value
        return result
    def constant(_):
        raise IntakeError("NONFINITE_JSON")
    try:
        return json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=pairs, parse_constant=constant)
    except IntakeError:
        raise
    except RecursionError as exc:
        raise IntakeError("JSON_DEPTH") from exc
    except UnicodeError as exc:
        raise IntakeError("UTF8") from exc
    except ValueError as exc:
        raise IntakeError("JSON") from exc

def _safe_path(path):
    return (isinstance(path, str) and LEAF.fullmatch(path) is not None
            and not path.endswith(".") and path.split(".", 1)[0].upper() not in RESERVED)

def preflight(raw: bytes, manifest_raw: bytes, checksum_raw: bytes, expected_name: str | None = None):
    try:
        payload = _strict_json(raw, MAX_BYTES)
        if not isinstance(payload, dict):
            raise IntakeError("RAW_ROOT_TYPE")
        manifest = _strict_json(manifest_raw, MAX_MANIFEST_BYTES)
        required = {"contract_version", "mode", "producer_revision", "files"}
        if not isinstance(manifest, dict) or set(manifest) != required:
            raise IntakeError("MANIFEST")
        if manifest["contract_version"] != CONTRACT or manifest["mode"] != MODE:
            raise IntakeError("MANIFEST_MODE")
        revision = manifest["producer_revision"]
        if not isinstance(revision, str) or REVISION.fullmatch(revision) is None:
            raise IntakeError("REVISION")
        files = manifest["files"]
        if not isinstance(files, list) or len(files) != 1:
            raise IntakeError("SINGLE_DOCUMENT_REQUIRED")
        item = files[0]
        if not isinstance(item, dict) or set(item) != {"path", "bytes", "sha256"}:
            raise IntakeError("MANIFEST_ENTRY")
        name = item["path"]
        if not _safe_path(name):
            raise IntakeError("PATH")
        if expected_name is not None and name != expected_name:
            raise IntakeError("PATH_BINDING")
        if type(item["bytes"]) is not int or item["bytes"] != len(raw):
            raise IntakeError("BYTE_LENGTH")
        declared = item["sha256"]
        if not isinstance(declared, str) or SHA.fullmatch(declared) is None:
            raise IntakeError("DIGEST_FORMAT")
        if not isinstance(checksum_raw, bytes) or len(checksum_raw) > MAX_CHECKSUM_BYTES:
            raise IntakeError("CHECKSUM_SIZE")
        try:
            lines = checksum_raw.decode("ascii", errors="strict").splitlines()
        except UnicodeError as exc:
            raise IntakeError("CHECKSUM_FORMAT") from exc
        if len(lines) != 1:
            raise IntakeError("CHECKSUM_RECORDS")
        parts = lines[0].split()
        if len(parts) != 2 or SHA.fullmatch(parts[0]) is None or parts[1] != name:
            raise IntakeError("CHECKSUM_FORMAT")
        actual = hashlib.sha256(raw).hexdigest()
        if declared.lower() != actual or parts[0].lower() != actual:
            raise IntakeError("DIGEST_MISMATCH")
        return _result(raw_sha256=actual, raw_bytes=len(raw), mode=MODE)
    except IntakeError as exc:
        return _result(str(exc))
