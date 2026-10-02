"""Compatibility-only synthetic bundle preflight.
Never imports DB and never returns T14 READY authority.
"""
from __future__ import annotations
import hashlib
import json
import re

MAX_BYTES = 512 * 1024
HEX_SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")


def _reject(error):
    return {"ready": False, "db_import": False, "t14_authority": False, "error": error}


def _strict_json(raw: bytes):
    if raw.startswith(b"\xef\xbb\xbf"):
        raise ValueError("BOM")
    def dup(pairs):
        result = {}
        for k, v in pairs:
            if k in result:
                raise ValueError("DUPLICATE_KEY")
            result[k] = v
        return result
    return json.loads(raw.decode("utf-8"), object_pairs_hook=dup)


def _safe_path(path: str):
    if not isinstance(path, str) or not path:
        return False
    if "\\" in path or ":" in path or path.startswith("/"):
        return False
    if path.startswith("//") or ".." in path.split("/"):
        return False
    return True


def preflight(raw: bytes, manifest_raw: bytes, checksum_raw: bytes):
    try:
        if len(raw) > MAX_BYTES:
            return _reject("SIZE")
        payload = _strict_json(raw)
        manifest = _strict_json(manifest_raw)
        if not isinstance(manifest.get("files"), list) or not manifest["files"]:
            return _reject("MANIFEST_FILES")
        seen = set()
        checks = checksum_raw.decode("ascii")
        checksum_map = {}
        for line in checks.splitlines():
            sha, path = line.split(None, 1)
            if not HEX_SHA256.match(sha) or path in checksum_map:
                return _reject("CHECKSUM")
            checksum_map[path] = sha.lower()
        for item in manifest["files"]:
            path = item.get("path")
            if not _safe_path(path) or path in seen:
                return _reject("PATH")
            seen.add(path)
            if path not in checksum_map:
                return _reject("CHECKSUM_MISSING")
        return {"ready": False, "db_import": False, "t14_authority": False, "compatible_preflight": True}
    except Exception as exc:
        return _reject(str(exc))
