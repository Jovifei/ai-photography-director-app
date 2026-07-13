#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

FORBIDDEN_SEGMENTS = {
    "private-data", "runtime", "source-library", "user-library", "model-cache",
    "models", "review-output", "deriveddata", "xcuserdata", "outputs"
}
FORBIDDEN_PREFIXES = (
    "docs/references/repos/",
    "data/private/",
    "releases/private/",
)
FORBIDDEN_NAMES = {".env", "id_rsa", "id_ed25519"}
FORBIDDEN_SUFFIXES = {
    ".sqlite", ".sqlite3", ".db", ".p12", ".mobileprovision", ".pem", ".key",
    ".safetensors", ".ckpt", ".pth", ".pt", ".onnx", ".engine", ".tflite",
    ".gguf", ".mlmodel", ".mlpackage",
    ".heic", ".dng", ".arw", ".cr2", ".cr3", ".nef", ".raf"
}
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".tif", ".tiff"}
ALLOWED_IMAGE_PATH_PARTS = {
    "assets.xcassets",
    "tests/fixtures/public",
    "test/fixtures/public",
    "shared-contract/fixtures",
    "docs/public-assets",
}
SCANNER_SOURCE_ALLOWLIST = {"scripts/prepush_privacy_audit.py"}

SECRET_PATTERNS = [
    re.compile(r"gh[pousr]_[A-Za-z0-9_]{20,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"(?i)(api[_-]?key|access[_-]?token|secret[_-]?key)\s*[:=]\s*['\"][^'\"]{12,}['\"]"),
]


def git_files(root: Path) -> list[str]:
    proc = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=root,
        text=True,
        capture_output=True,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or "git ls-files failed")
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def is_allowed_image(norm_lower: str) -> bool:
    return any(part in norm_lower for part in ALLOWED_IMAGE_PATH_PARTS)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    errors: list[str] = []
    for rel in git_files(root):
        norm = rel.replace("\\", "/")
        low = norm.lower()
        posix = PurePosixPath(low)
        segments = set(posix.parts)
        if any(low.startswith(prefix) for prefix in FORBIDDEN_PREFIXES):
            errors.append(f"forbidden local/private path: {rel}")
        if segments.intersection(FORBIDDEN_SEGMENTS):
            errors.append(f"forbidden directory segment: {rel}")
        name = Path(norm).name.lower()
        if name in FORBIDDEN_NAMES or name.startswith(".env."):
            errors.append(f"forbidden secret/config file: {rel}")
        suffix = Path(norm).suffix.lower()
        if suffix in FORBIDDEN_SUFFIXES:
            errors.append(f"forbidden private/binary artifact: {rel}")
        if suffix in IMAGE_SUFFIXES and not is_allowed_image(low):
            errors.append(f"unapproved image asset; move to an allowlisted public fixture/assets path or keep private: {rel}")
        path = root / rel
        if norm in SCANNER_SOURCE_ALLOWLIST:
            continue
        if path.is_file() and path.stat().st_size <= 2_000_000:
            try:
                text = path.read_text(encoding="utf-8")
            except Exception:
                continue
            for pattern in SECRET_PATTERNS:
                if pattern.search(text):
                    errors.append(f"possible secret in: {rel}")
    if errors:
        print("PRE-PUSH PRIVACY AUDIT FAILED", file=sys.stderr)
        for err in sorted(set(errors)):
            print(f"- {err}", file=sys.stderr)
        return 1
    print("PASS: no forbidden private assets, unapproved images, model weights, databases, reference clones, or common secrets detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
