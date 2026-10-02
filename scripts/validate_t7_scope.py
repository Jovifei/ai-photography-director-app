#!/usr/bin/env python3
"""Require a clean, exact-base, metadata-only T7 candidate."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BASE = "3233313af8be946cc43245daf15f24f853933d88"
ALLOWED_EXACT = {
    "docs/CURRENT_PROGRAM_STATUS.md",
    "docs/current_program_status.v1.json",
    "docs/NEXT_GATE_MATRIX.md",
    "reports/T7_EVALUATION_GOVERNANCE_READINESS_REPORT.md",
    "scripts/test_phase1_5_t7_evaluation.py",
    "scripts/validate_phase1_5_t7_evaluation.py",
    "scripts/validate_program_status.py",
    "scripts/validate_t7_scope.py",
    "tasks/plans/2026-09-27-t7-evaluation-governance-readiness.md",
    "tasks/todo.md",
}


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout.strip()


def allowed(path: str) -> bool:
    return path in ALLOWED_EXACT or path.startswith("docs/phase1_5/t7/")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE)
    args = parser.parse_args(argv)
    try:
        head = git("rev-parse", "HEAD")
        status = git("status", "--porcelain")
        changed = [line for line in git("diff", "--name-only", f"{args.base}..HEAD").splitlines() if line]
        if head == args.base:
            raise RuntimeError("candidate head is still the T6 base")
        if status:
            raise RuntimeError("worktree is not clean")
        unexpected = [path for path in changed if not allowed(path)]
        if unexpected:
            raise RuntimeError("forbidden changed paths: " + ", ".join(unexpected))
        if "docs/phase1_5/t7/evaluation_governance.v1.json" not in changed:
            raise RuntimeError("T7 governance manifest is not in the candidate")
        if len(changed) < 10:
            raise RuntimeError(f"unexpectedly small T7 scope: {len(changed)} paths")
    except (RuntimeError, OSError) as exc:
        print(f"T7_SCOPE_FAILED: {exc}")
        return 1
    print(f"T7_SCOPE_PASS: base={args.base} head={head} changed_paths={len(changed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
