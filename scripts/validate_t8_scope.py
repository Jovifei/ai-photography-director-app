#!/usr/bin/env python3
"""Require a clean, exact-base, synthetic-only T8 candidate."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BASE = "61cb6776b2b6f00d6965789e0589c5337536d258"
ALLOWED_EXACT = {
    "docs/CURRENT_PROGRAM_STATUS.md",
    "docs/current_program_status.v1.json",
    "docs/NEXT_GATE_MATRIX.md",
    "reports/T8_SYNTHETIC_EVALUATION_HARNESS_REPORT.md",
    "scripts/run_phase1_5_t8_synthetic_evaluation.py",
    "scripts/test_phase1_5_t8_synthetic_evaluation.py",
    "scripts/validate_program_status.py",
    "scripts/validate_t8_scope.py",
    "tasks/plans/2026-09-27-t8-synthetic-evaluation-harness.md",
    "tasks/todo.md",
}


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout.strip()


def allowed(path: str) -> bool:
    return path in ALLOWED_EXACT or path.startswith("docs/phase1_5/t8/")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args(argv)
    try:
        head = git("rev-parse", "HEAD")
        status = git("status", "--porcelain")
        changed = [line for line in git("diff", "--name-only", f"{args.base}..HEAD").splitlines() if line]
        if head == args.base:
            raise RuntimeError("candidate head is still the T7 base")
        if status:
            raise RuntimeError("worktree is not clean")
        unexpected = [path for path in changed if not allowed(path)]
        if unexpected:
            raise RuntimeError("forbidden changed paths: " + ", ".join(unexpected))
        if "docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json" not in changed:
            raise RuntimeError("T8 synthetic manifest is not in the candidate")
        if len(changed) < 12:
            raise RuntimeError(f"unexpectedly small T8 scope: {len(changed)} paths")
    except (RuntimeError, OSError) as exc:
        print(f"T8_SCOPE_FAILED: {exc}")
        return 1
    print(f"T8_SCOPE_PASS: base={args.base} head={head} changed_paths={len(changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
