#!/usr/bin/env python3
"""CLI wrapper for compatibility-only T14 preflight."""
from pathlib import Path
import json
from t14_bundle_intake_preflight_contract import preflight

if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument("raw_bundle", type=Path)
    p.add_argument("manifest", type=Path)
    p.add_argument("checksums", type=Path)
    args = p.parse_args()
    print(json.dumps(preflight(
        args.raw_bundle.read_bytes(),
        args.manifest.read_bytes(),
        args.checksums.read_bytes(),
    ), indent=2))
