#!/usr/bin/env python3
"""Require a clean, exact-base, non-runtime T10 candidate."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASE = "dd5d297ffa26adc38470bfa5737ac82b94eaa2ec"
ALLOWED_EXACT = {
    "docs/CURRENT_PROGRAM_STATUS.md",
    "docs/current_program_status.v1.json",
    "docs/NEXT_GATE_MATRIX.md",
    "docs/phase1_5/p1b/p1b_owner_decision.v1.json",
    "reports/T10_OWNER_DECISION_CAPTURE_REPORT.md",
    "scripts/test_t10_owner_decision.py",
    "scripts/validate_program_status.py",
    "scripts/validate_t10_owner_decision.py",
    "scripts/validate_t10_scope.py",
    "tasks/plans/2026-09-28-t10-owner-decision-capture.md",
    "tasks/todo.md",
}


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout.strip()


def allowed(path: str) -> bool:
    return path in ALLOWED_EXACT


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args(argv)
    try:
        head = git("rev-parse", "HEAD")
        if git("status", "--porcelain"):
            raise RuntimeError("worktree is not clean")
        changed = [path for path in git("diff", "--name-only", f"{args.base}..HEAD").splitlines() if path]
        unexpected = [path for path in changed if not allowed(path)]
        if unexpected:
            raise RuntimeError("forbidden changed paths: " + ", ".join(unexpected))
        if "docs/phase1_5/p1b/p1b_owner_decision.v1.json" not in changed:
            raise RuntimeError("T10 decision record is not in candidate")
        if "scripts/validate_t10_owner_decision.py" not in changed:
            raise RuntimeError("T10 validator is not in candidate")
        if len(changed) < 7:
            raise RuntimeError(f"unexpectedly small T10 scope: {len(changed)} paths")
    except (RuntimeError, OSError) as exc:
        print(f"T10_SCOPE_FAILED: {exc}")
        return 1
    print(f"T10_SCOPE_PASS: base={args.base} head={head} changed_paths={len(changed)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
