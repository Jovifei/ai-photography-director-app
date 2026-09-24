#!/usr/bin/env python3
"""Compile/run actual capture engine and file store; no Android SDK, model, or network.
Room/Compose/CameraX are NOT tested here. Source hashes are canonical local bytes.
"""
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
    production = 'android/app/src/main/java/com/jovi/photoai/data/capture/'
    sources = [production + name for name in ('CaptureModels.kt', 'CaptureFiles.kt', 'CaptureEngine.kt')]
    sources.append('android/app/src/test/java/com/jovi/photoai/data/capture/CaptureCoreCases.kt')
    compiler, java = shutil.which('kotlinc'), shutil.which('java')
    if not compiler or not java:
        print('NOT_RUN: existing kotlinc and java required; no install attempted.', file=sys.stderr)
        return 2
    try:
        with tempfile.TemporaryDirectory(prefix='photoai-p25u-core-') as temporary:
            jar = Path(temporary) / 'capture-tests.jar'
            subprocess.run([compiler, *(str(root / name) for name in sources), '-include-runtime', '-d', str(jar)], check=True, timeout=120)
            subprocess.run([java, '-cp', str(jar), 'com.jovi.photoai.data.capture.CaptureCoreCases'], check=True, timeout=30)
    except (OSError, subprocess.SubprocessError) as error:
        print('FAIL: ' + type(error).__name__, file=sys.stderr)
        return 1
    print(json.dumps({p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}, indent=2))
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
