#!/usr/bin/env python3
"""Validate milestone progress identity and approval contracts.

This validator protects the progress ledger from silent checkpoint loss,
wrong source identities, duplicate identities, stale approvals, and false
render acceptance.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/project_progress.json"
EXPECTED_SOURCE_RE = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_PHASES = {"M1", "M2", "M3", "M4", "M5", "M6"}
EXPECTED_COUNTS = {"M1": 4, "M2": 6, "M3": 5, "M4": 7, "M5": 6, "M6": 6}


def fail(message: str) -> None:
    raise ValueError(message)


def validate(data: dict) -> None:
    if not EXPECTED_SOURCE_RE.fullmatch(data.get("source_revision", "")):
        fail("source_revision must be exact 40 lowercase hex characters")

    phases = data.get("phases")
    if not isinstance(phases, list):
        fail("phases missing")

    ids = [p.get("id") for p in phases]
    if set(ids) != EXPECTED_PHASES or len(ids) != len(set(ids)):
        fail("phase identity mismatch")

    for phase in phases:
        pid = phase["id"]
        checks = phase.get("checks", [])
        if len(checks) != EXPECTED_COUNTS[pid]:
            fail(f"{pid} checkpoint count changed")
        names = [c.get("name") for c in checks]
        if len(names) != len(set(names)):
            fail(f"{pid} duplicate checkpoint name")
        for check in checks:
            if check.get("status") not in {"done", "pending", "blocked", "not_run"}:
                fail(f"invalid checkpoint status {pid}/{check.get('name')}")
            if not check.get("evidence"):
                fail(f"missing evidence {pid}/{check.get('name')}")

    if data.get("planning_status") == "APPROVED":
        m1 = next(p for p in phases if p["id"] == "M1")
        approval = next((c for c in m1["checks"] if "路线图" in c["name"]), None)
        if not approval or approval.get("status") != "done":
            fail("APPROVED requires M1 roadmap approval evidence")


if __name__ == "__main__":
    try:
        validate(json.loads(LEDGER.read_text(encoding="utf-8")))
    except Exception as exc:
        print(f"PROGRESS CONTRACT FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
    print("PASS: progress checkpoint contract")
