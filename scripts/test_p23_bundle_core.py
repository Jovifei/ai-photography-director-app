#!/usr/bin/env python3
"""Compile and run production Kotlin helpers without Android SDK or network access.

This is NOT an Android/Room/Compose qualification. It shares assertions with the
JUnit suite and prints source hashes so a local reviewer can reproduce the run.
No compiler artifacts or private input are written into the repository.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    production = "android/app/src/main/java/com/jovi/photoai/data/reference/"
    tests = "android/app/src/test/java/com/jovi/photoai/data/reference/"
    sources = [production + "StrictKnowledgeBundleJson.kt", production + "KnowledgeBundleImportSession.kt",
               production + "BoundedKnowledgeBundleReader.kt",
               tests + "KnowledgeBundleCoreRegressionCases.kt"]
    compiler, java = shutil.which("kotlinc"), shutil.which("java")
    if not compiler or not java:
        print("NOT_RUN: kotlinc and java must already be installed; no download attempted.", file=sys.stderr)
        return 2
    missing = [relative for relative in sources if not (root / relative).is_file()]
    if missing:
        print("FAIL: missing source: " + ", ".join(missing), file=sys.stderr)
        return 1
    try:
        with tempfile.TemporaryDirectory(prefix="photoai-p23-core-") as temporary:
            jar = Path(temporary) / "core-tests.jar"
            subprocess.run([compiler, *(str(root / relative) for relative in sources),
                            "-include-runtime", "-d", str(jar)], check=True, timeout=180)
            run = subprocess.run([java, "-cp", str(jar),
                                  "com.jovi.photoai.data.reference.KnowledgeBundleCoreRegressionCases"],
                                 check=True, capture_output=True, text=True, timeout=60)
            print(run.stdout.strip())
    except (OSError, subprocess.SubprocessError) as error:
        print(f"FAIL: host Kotlin qualification ({type(error).__name__}).", file=sys.stderr)
        return 1
    print(json.dumps({"scope": "HOST_KOTLIN_HELPERS_ONLY", "sources": {
        relative: hashlib.sha256((root / relative).read_bytes()).hexdigest() for relative in sources
    }}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
