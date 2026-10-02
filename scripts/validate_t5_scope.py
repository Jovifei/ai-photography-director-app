#!/usr/bin/env python3
"""Fail closed if the T5 status stage touches product/runtime paths."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


ALLOWED_EXACT = {
    "README.md",
    "tasks/todo.md",
    "docs/CURRENT_PROGRAM_STATUS.md",
    "docs/current_program_status.v1.json",
    "docs/NEXT_GATE_MATRIX.md",
    "docs/PROGRAM_HANDOFF_REFERENCE.md",
    "docs/00_ANDROID_FIRST_EXECUTION_PLAN.md",
    "docs/01_CAMERAX_AND_POSE_SPIKE.md",
    "docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md",
    "docs/UI1_P1B_ENGINEERING_COMPLETION_PLAN.md",
    "docs/P25R_OWNER_FINAL_REVIEW_GUIDE.md",
    "docs/ANDROID_BETA_PHONE_SETUP.md",
    "docs/BETA_USER_GUIDE.md",
    "reports/T5_PROGRAM_STATUS_CONSOLIDATION_REPORT.md",
    "scripts/validate_program_status.py",
    "scripts/test_program_status.py",
    "scripts/validate_t5_scope.py",
    "tasks/plans/2026-09-27-t5-program-status-consolidation.md",
}
EXPECTED_BASE = "e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb"


def changed_paths(root: Path, base: str) -> list[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", f"{base}..HEAD"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    )
    return [line.replace("\\", "/") for line in result.stdout.splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    if args.base != EXPECTED_BASE:
        print(f"FAIL T5_SCOPE: expected base {EXPECTED_BASE}, got {args.base}")
        return 1
    status = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all"],
        cwd=args.root.resolve(),
        check=True,
        capture_output=True,
        text=True,
    )
    if status.stdout.strip():
        print("FAIL T5_SCOPE: worktree is not clean")
        return 1
    paths = changed_paths(args.root.resolve(), args.base)
    unexpected = sorted(set(paths) - ALLOWED_EXACT)
    if unexpected:
        print("FAIL T5_SCOPE: " + ", ".join(unexpected))
        return 1
    print(f"PASS T5_SCOPE: {len(paths)} approved documentation/status paths")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
