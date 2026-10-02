#!/usr/bin/env python3
"""App-side T14 intake preflight.

Compatibility preparation only. This validates a supplied Bundle structure.
It does not import databases, create READY state, or approve producer output.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

STATUS = "COMPATIBILITY_PREPARATION_ONLY"


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def preflight(bundle_root: Path) -> dict:
    manifest = bundle_root / "bundle.json"
    checksum = bundle_root / "CHECKSUMS.sha256"
    if not manifest.exists() or not checksum.exists():
        return {"status": STATUS, "compatible": False, "reason": "missing_manifest_or_checksum"}

    data = json.loads(manifest.read_text(encoding="utf-8"))
    return {
        "status": STATUS,
        "schema_version": data.get("schema_version"),
        "manifest_present": True,
        "checksum_present": True,
        "db_import": False,
        "ready": False,
    }


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    print(json.dumps(preflight(parser.parse_args().bundle), indent=2))
