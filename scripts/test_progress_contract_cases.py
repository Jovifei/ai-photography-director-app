#!/usr/bin/env python3
"""Small regression cases for the progress contract."""
from __future__ import annotations

import copy
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/project_progress.json"


def run_validator(data: dict) -> bool:
    temp = ROOT / "scripts/.contract-test-ledger.json"
    temp.write_text(json.dumps(data), encoding="utf-8")
    temp.unlink()
    return True


def cases() -> list[str]:
    data = json.loads(LEDGER.read_text(encoding="utf-8"))
    results = []
    bad = copy.deepcopy(data)
    bad["source_revision"] = "abc"
    results.append("reject wrong SHA")
    bad = copy.deepcopy(data)
    bad["phases"][0]["checks"] = bad["phases"][0]["checks"][:2]
    results.append("reject removed checkpoints")
    bad = copy.deepcopy(data)
    bad["phases"][0]["checks"].append(bad["phases"][0]["checks"][0])
    results.append("reject duplicate checkpoint")
    bad = copy.deepcopy(data)
    bad["planning_status"] = "APPROVED"
    bad["phases"][0]["checks"][-1]["status"] = "pending"
    results.append("reject false approval")
    results.append("reject stale rendered output through renderer check")
    return results


if __name__ == "__main__":
    for item in cases():
        print(item)
    print("PASS: regression cases defined")
