#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_SEGMENTS = {"private-data", "runtime", "source-library", "user-library", "model-cache", "models", "review-output", "deriveddata", "xcuserdata", "outputs"}
FORBIDDEN_NAMES = {".env", "id_rsa", "id_ed25519"}
SECRET_PATTERNS = [re.compile(r"gh[pousr]_[A-Za-z0-9_]{20,}"), re.compile(r"AKIA[0-9A-Z]{16}"), re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")]


def main() -> int:
    errors: list[str] = []
    if (ROOT / "docs/project_progress.json").exists():
        result = subprocess.run([sys.executable, str(ROOT / "scripts/validate_progress_contract.py")], cwd=ROOT, text=True, capture_output=True)
        if result.returncode:
            errors.append(result.stderr.strip())
        result = subprocess.run([sys.executable, str(ROOT / "scripts/render_project_progress.py"), "--check"], cwd=ROOT, text=True, capture_output=True)
        if result.returncode:
            errors.append("milestone progress is invalid or out of date")
    files = subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard"], cwd=ROOT, text=True, capture_output=True).stdout.splitlines()
    for rel in files:
        low = rel.lower().replace('\\', '/')
        if set(PurePosixPath(low).parts).intersection(FORBIDDEN_SEGMENTS):
            errors.append(f"forbidden directory: {rel}")
        if Path(low).name in FORBIDDEN_NAMES:
            errors.append(f"forbidden secret file: {rel}")
        path = ROOT / rel
        if path.is_file() and path.stat().st_size <= 2000000:
            try:
                text = path.read_text(encoding="utf-8")
            except Exception:
                continue
            for pattern in SECRET_PATTERNS:
                if pattern.search(text):
                    errors.append(f"possible secret: {rel}")
    if errors:
        print("PRE-PUSH PRIVACY AUDIT FAILED", file=sys.stderr)
        for item in sorted(set(errors)):
            print(f"- {item}", file=sys.stderr)
        return 1
    print("PASS: privacy audit and progress contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
