#!/usr/bin/env python3
"""Run the actual preparation policy without Android SDK. Not a Compose/device gate."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    sources = [
        "android/app/src/main/java/com/jovi/photoai/reference/DirectorCard.kt",
        "android/app/src/main/java/com/jovi/photoai/ui/reference/DirectorPreparation.kt",
        "android/app/src/test/java/com/jovi/photoai/ui/reference/DirectorPreparationCases.kt",
    ]
    compiler, java = shutil.which("kotlinc"), shutil.which("java")
    if not compiler or not java:
        print("NOT_RUN: requires preinstalled kotlinc and java; no downloads attempted.", file=sys.stderr)
        return 2
    if any(not (root / source).is_file() for source in sources):
        print("FAIL: required source missing.", file=sys.stderr)
        return 1
    try:
        with tempfile.TemporaryDirectory(prefix="photoai-p25t-") as directory:
            jar = Path(directory) / "preparation.jar"
            subprocess.run([compiler, *(str(root / source) for source in sources),
                            "-include-runtime", "-d", str(jar)], check=True, timeout=180)
            result = subprocess.run([java, "-cp", str(jar),
                "com.jovi.photoai.ui.reference.DirectorPreparationCases"],
                check=True, capture_output=True, text=True, timeout=60)
            print(result.stdout.strip())
    except (OSError, subprocess.SubprocessError) as error:
        print(f"FAIL: {type(error).__name__}", file=sys.stderr)
        return 1
    print(json.dumps({"scope": "HOST_POLICY_ONLY_NOT_ANDROID", "sha256": {
        source: hashlib.sha256((root / source).read_bytes()).hexdigest() for source in sources
    }}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
