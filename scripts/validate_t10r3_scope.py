#!/usr/bin/env python3
"""Enforce the exact approved T10-R3 changed-path boundary."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

BASE_SHA = "d332ba8ee6f1ea036be655de8b09f8c9f4180592"
OWNER_DECISION_PATH = "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
APPROVED_PATHS = frozenset(
    {
        "scripts/validate_t10_owner_decision.py",
        "scripts/test_t10_owner_decision.py",
        "scripts/test_phase1_5_p1b_readiness.py",
        "scripts/validate_program_status.py",
        "scripts/validate_t10r3_scope.py",
        "docs/current_program_status.v1.json",
        "docs/CURRENT_PROGRAM_STATUS.md",
        "reports/T10_OWNER_DECISION_CAPTURE_REPORT.md",
        "tasks/plans/2026-09-29-t10-portable-decision-identity-repair.md",
        "tasks/todo.md",
    }
)


class ScopeError(ValueError):
    pass


def git(root: Path, *args: str) -> bytes:
    result = subprocess.run(
        ["git", *args], cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise ScopeError(f"git {' '.join(args)} failed: {detail}")
    return result.stdout


def owner_decision_blob_oid(root: Path, revision: str) -> str:
    try:
        object_id = git(root, "rev-parse", "--verify", f"{revision}:{OWNER_DECISION_PATH}").decode("ascii").strip()
    except ScopeError as exc:
        raise ScopeError(f"Owner decision file missing at {revision}: {OWNER_DECISION_PATH}") from exc
    if not object_id:
        raise ScopeError(f"Owner decision file missing at {revision}: {OWNER_DECISION_PATH}")
    object_type = git(root, "cat-file", "-t", object_id).decode("ascii").strip()
    if object_type != "blob":
        raise ScopeError(f"Owner decision path is not a blob at {revision}: {OWNER_DECISION_PATH}")
    return object_id


def validate_owner_decision_blob_identity(root: Path, base: str, head: str) -> str:
    base_oid = owner_decision_blob_oid(root, base)
    head_oid = owner_decision_blob_oid(root, head)
    if base_oid != head_oid:
        raise ScopeError("Owner decision blob identity changed between base and HEAD")
    return base_oid


def validate(root: Path, base: str) -> list[str]:
    if base != BASE_SHA:
        raise ScopeError(f"scope base must be exactly {BASE_SHA}")
    resolved_base = git(root, "rev-parse", "--verify", f"{BASE_SHA}^{{commit}}").decode().strip()
    if resolved_base != BASE_SHA:
        raise ScopeError("scope base did not resolve to the exact expected commit")
    head = git(root, "rev-parse", "HEAD").decode().strip()
    ancestry = subprocess.run(
        ["git", "merge-base", "--is-ancestor", BASE_SHA, head],
        cwd=root,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if ancestry.returncode != 0:
        raise ScopeError("exact R3 base is not an ancestor of HEAD")
    validate_owner_decision_blob_identity(root, BASE_SHA, head)

    changed = set(git(root, "diff", "--name-only", "-z", BASE_SHA, head, "--").decode("utf-8").split("\0"))
    changed.update(
        git(root, "ls-files", "--others", "--exclude-standard", "-z").decode("utf-8").split("\0")
    )
    changed.discard("")
    if OWNER_DECISION_PATH in changed:
        raise ScopeError(f"Owner decision file is forbidden in the R3 diff: {OWNER_DECISION_PATH}")
    if git(root, "status", "--porcelain=v1", "-z"):
        raise ScopeError("git worktree must be clean for exact scope validation")
    outside = sorted(changed - APPROVED_PATHS)
    if outside:
        raise ScopeError(f"changed paths outside the approved list: {', '.join(outside)}")
    return sorted(changed)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="must be the exact T10-R3 scope base")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args(argv)
    try:
        changed = validate(args.root.resolve(), args.base)
    except (ScopeError, UnicodeDecodeError) as exc:
        print(f"FAIL T10_R3_SCOPE: {exc}")
        return 1
    print(f"PASS T10_R3_SCOPE: {len(changed)} approved paths from {BASE_SHA}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
