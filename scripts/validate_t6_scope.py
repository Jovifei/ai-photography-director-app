#!/usr/bin/env python3
"""Require the final T6 candidate to be a clean, exact-base status-only delta."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


DEFAULT_BASE = "0b04abdc57a3fa332689cfe664f9bcb997160e5e"
ROOT = Path(__file__).resolve().parent.parent
ALLOWED_EXACT = {
    "docs/CURRENT_PROGRAM_STATUS.md",
    "docs/current_program_status.v1.json",
    "docs/NEXT_GATE_MATRIX.md",
    "reports/T6_P1B_AUTHORIZATION_READINESS_REPORT.md",
    "scripts/test_phase1_5_p1b_readiness.py",
    "scripts/validate_phase1_5_p1b_readiness.py",
    "scripts/validate_program_status.py",
    "scripts/validate_t6_scope.py",
    "tasks/plans/2026-09-27-t6-p1b-authorization-readiness.md",
    "tasks/todo.md",
}


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout.strip()


def allowed(path: str) -> bool:
    return path in ALLOWED_EXACT or path.startswith("docs/phase1_5/p1b/")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args(argv)
    try:
        head = git("rev-parse", "HEAD")
        status = git("status", "--porcelain")
        changed = [line for line in git("diff", "--name-only", f"{args.base}..HEAD").splitlines() if line]
        if head == args.base:
            raise RuntimeError("candidate head is still the T5 base")
        if status:
            raise RuntimeError("worktree is not clean")
        unexpected = [path for path in changed if not allowed(path)]
        if unexpected:
            raise RuntimeError("forbidden changed paths: " + ", ".join(unexpected))
        if "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json" not in changed:
            raise RuntimeError("P1B readiness manifest is not in the candidate")
        if len(changed) < 8:
            raise RuntimeError(f"unexpectedly small T6 scope: {len(changed)} paths")
    except (RuntimeError, OSError) as exc:
        print(f"T6_SCOPE_FAILED: {exc}")
        return 1
    print(f"T6_SCOPE_PASS: base={args.base} head={head} changed_paths={len(changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
