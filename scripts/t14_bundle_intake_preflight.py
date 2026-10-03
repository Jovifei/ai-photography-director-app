"""Read only explicitly supplied synthetic inputs; reject failures with exit 1."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.t14_bundle_intake_preflight_contract import (
    MAX_BYTES, MAX_MANIFEST_BYTES, MAX_CHECKSUM_BYTES, IntakeError, _result, preflight,
)

def bounded_read(path: Path, limit: int) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise IntakeError("INPUT_FILE")
    with path.open("rb") as stream:
        value = stream.read(limit + 1)
    if len(value) > limit:
        raise IntakeError("SIZE")
    return value

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("raw_bundle", "manifest", "checksums"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args(argv)
    try:
        result = preflight(bounded_read(args.raw_bundle, MAX_BYTES),
                           bounded_read(args.manifest, MAX_MANIFEST_BYTES),
                           bounded_read(args.checksums, MAX_CHECKSUM_BYTES), args.raw_bundle.name)
    except IntakeError as exc:
        result = _result(str(exc))
    except OSError:
        result = _result("INPUT_IO")
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0 if result["compatible_preflight"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
