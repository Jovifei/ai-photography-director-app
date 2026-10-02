#!/usr/bin/env python3
"""Enforce the exact T11 change allowlist and preserve inherited authority blobs."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = "2ff77cadc8696cbd06a504458da4d58c74aa6133"
BRANCH = "codex/t11-artifact-acquisition-preflight-20260929"
ORIGIN = "https://github.com/Jovifei/ai-photography-director-app.git"
OWNER_DECISION_PATH = "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
P1B_MANIFEST_PATH = "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json"
OWNER_DECISION_BLOB = "74d980e7dccdc67f659836b39126557d91e5460b"
P1B_MANIFEST_BLOB = "274dae454864b41e19a46d0f1129fb57a1dee842"
T10_VALIDATOR_PATH = "scripts/validate_t10_owner_decision.py"
ALLOWED_PATHS = {
    "docs/phase1_5/t11/ARTIFACT_ACQUISITION_PREFLIGHT.md",
    "docs/phase1_5/t11/artifact_acquisition_preflight.v1.json",
    "docs/phase1_5/t11/artifact_acquisition_preflight.v1.schema.json",
    "docs/phase1_5/t11/legal_review_input.v1.schema.json",
    "docs/phase1_5/t11/transport_verification_input.v1.schema.json",
    "docs/phase1_5/t11/fresh_metadata_input.v1.schema.json",
    "docs/phase1_5/t11/quarantine_destination_input.v1.schema.json",
    "docs/phase1_5/t11/artifact_acquisition_owner_decision.v1.schema.json",
    "docs/CURRENT_PROGRAM_STATUS.md",
    "docs/current_program_status.v1.json",
    "docs/NEXT_GATE_MATRIX.md",
    "reports/T11_ARTIFACT_ACQUISITION_PREFLIGHT_REPORT.md",
    "scripts/validate_t11_artifact_acquisition_preflight.py",
    "scripts/test_t11_artifact_acquisition_preflight.py",
    "scripts/validate_program_status.py",
    "scripts/validate_t11_scope.py",
    "tasks/plans/2026-09-29-t11-artifact-acquisition-preflight.md",
    "tasks/todo.md",
}


class ScopeError(ValueError):
    """Expected T11 scope failure."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ScopeError(message)


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
        raise ScopeError(f"git {' '.join(args[:2])} failed: {detail}")
    return result.stdout.strip()


def validate(root: Path, base: str = BASE) -> tuple[str, ...]:
    require(base == BASE, "T11 scope base must be the accepted T10-R3 SHA")
    require(Path(git(root, "rev-parse", "--show-toplevel")).resolve() == root.resolve(), "scope validator root mismatch")
    require(git(root, "remote", "get-url", "origin") == ORIGIN, "origin remote changed")
    require(git(root, "branch", "--show-current") == BRANCH, "T11 branch name changed")
    head = git(root, "rev-parse", "HEAD")
    require(git(root, "merge-base", head, BASE) == BASE, "T11 branch does not descend from the exact accepted base")
    status = git(root, "status", "--porcelain=v1", "--untracked-files=all")
    require(not status, "T11 worktree must be clean before scope acceptance")

    paths_text = git(root, "diff", "--name-only", BASE, head, "--")
    paths = tuple(sorted(path for path in paths_text.splitlines() if path))
    require(set(paths) == ALLOWED_PATHS, f"T11 changed-path set mismatch: unexpected={sorted(set(paths) - ALLOWED_PATHS)} missing={sorted(ALLOWED_PATHS - set(paths))}")
    require(git(root, "diff", "--check", BASE, head, "--") == "", "T11 diff whitespace check failed")

    owner_base = git(root, "rev-parse", f"{BASE}:{OWNER_DECISION_PATH}")
    owner_head = git(root, "rev-parse", f"{head}:{OWNER_DECISION_PATH}")
    require(owner_base == OWNER_DECISION_BLOB and owner_head == owner_base, "T10 Owner decision blob changed")
    manifest_base = git(root, "rev-parse", f"{BASE}:{P1B_MANIFEST_PATH}")
    manifest_head = git(root, "rev-parse", f"{head}:{P1B_MANIFEST_PATH}")
    require(manifest_base == P1B_MANIFEST_BLOB and manifest_head == manifest_base, "frozen P1B manifest blob changed")
    t10_validator_base = git(root, "rev-parse", f"{BASE}:{T10_VALIDATOR_PATH}")
    t10_validator_head = git(root, "rev-parse", f"{head}:{T10_VALIDATOR_PATH}")
    require(t10_validator_head == t10_validator_base, "T10 validator must remain unchanged")
    return paths


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--base", default=BASE)
    args = parser.parse_args(argv)
    try:
        paths = validate(args.root.resolve(), args.base)
    except ScopeError as exc:
        print(f"FAIL T11_SCOPE: {exc}")
        return 1
    print(f"PASS T11_SCOPE: {len(paths)} approved paths; Owner and P1B blobs unchanged; T10 validator unchanged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
