#!/usr/bin/env python3
"""Require a clean, exact-base, non-runtime T9 candidate."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASE = "acf43a8d734675ec23da534472cade129b7b1068"
ALLOWED_EXACT = {
    "docs/CURRENT_PROGRAM_STATUS.md", "docs/current_program_status.v1.json", "docs/NEXT_GATE_MATRIX.md",
    "reports/T9_PRODUCT_READY_PROMOTION_GATE_REPORT.md", "scripts/validate_phase1_5_t9_ready_promotion.py",
    "scripts/test_phase1_5_t9_ready_promotion.py", "scripts/validate_program_status.py", "scripts/validate_t9_scope.py",
    "tasks/plans/2026-09-27-t9-product-ready-promotion-gate.md", "tasks/todo.md",
}


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout.strip()


def allowed(path: str) -> bool:
    return path in ALLOWED_EXACT or path.startswith("docs/phase1_5/t9/")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args(argv)
    try:
        head = git("rev-parse", "HEAD")
        require_clean = git("status", "--porcelain")
        if require_clean:
            raise RuntimeError("worktree is not clean")
        changed = [path for path in git("diff", "--name-only", f"{args.base}..HEAD").splitlines() if path]
        unexpected = [path for path in changed if not allowed(path)]
        if unexpected:
            raise RuntimeError("forbidden changed paths: " + ", ".join(unexpected))
        if not any(path.startswith("docs/phase1_5/t9/") for path in changed):
            raise RuntimeError("T9 contract directory is not in candidate")
        if len(changed) < 8:
            raise RuntimeError(f"unexpectedly small T9 scope: {len(changed)} paths")
    except (RuntimeError, OSError) as exc:
        print(f"T9_SCOPE_FAILED: {exc}")
        return 1
    print(f"T9_SCOPE_PASS: base={args.base} head={head} changed_paths={len(changed)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
