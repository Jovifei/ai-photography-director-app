#!/usr/bin/env python3
"""Validate the machine-readable current program status and forward-facing pointers."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


SHA_RE = re.compile(r"^[0-9a-f]{40}$")
REQUIRED_EXTERNAL_GATES = {
    "real_photos": "NOT_RUN",
    "physical_device": "NOT_RUN",
    "qwen_private_lan": "NOT_RUN",
    "pipeline": "NOT_RUN",
    "human_editorial_rights_privacy": "NOT_RUN",
    "signing": "NOT_RUN",
    "beta_public_release": "NOT_RUN",
    "main_merge": "NOT_RUN",
    "pose": "FROZEN",
}


class StatusError(AssertionError):
    pass


def read_status(root: Path) -> dict:
    path = root / "docs" / "current_program_status.v1.json"
    try:
        status = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise StatusError(f"cannot read status JSON: {exc}") from exc
    if not isinstance(status, dict):
        raise StatusError("status JSON must be an object")
    return status


def require(condition: bool, message: str) -> None:
    if not condition:
        raise StatusError(message)


def validate(root: Path) -> None:
    status = read_status(root)
    for key in ("delivery_head", "reviewed_product_source", "t3_base"):
        value = status.get(key)
        require(isinstance(value, str) and SHA_RE.fullmatch(value), f"invalid {key}")

    require(status["delivery_head"] == "e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb", "delivery head drift")
    require(status["reviewed_product_source"] == "a69ede68f3e54e5ab006dbfd7a65c33040590f93", "product source drift")
    require(status["t3_base"] == "14a56f49e1220eb8139bf7280c747124118fdb21", "T3 base drift")
    require(status.get("delivery_pr_number") == 8, "unexpected delivery PR")
    require(status.get("delivery_pr_state") == "DRAFT_UNMERGED", "delivery PR is not explicitly Draft/unmerged")
    require(status.get("qualification_state") == "T4_SYNTHETIC_API35_VALIDATED", "qualification state drift")
    require(status.get("local_independent_review_state") == "PASS_EXACT_SHA", "local review state drift")
    require(status.get("c2c_review_limit") == "C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE", "remote review limit missing")
    require(status.get("current_stage") == "T5_PROGRAM_STATUS_ROADMAP_AUTHORITY_CONSOLIDATION", "current stage drift")
    require(status.get("next_authorized_stage") == "T5_PROGRAM_STATUS_ROADMAP_AUTHORITY_CONSOLIDATION", "next stage drift")
    require(status.get("external_gates") == REQUIRED_EXTERNAL_GATES, "external gate state drift")

    current = (root / "docs" / "CURRENT_PROGRAM_STATUS.md").read_text(encoding="utf-8")
    matrix = (root / "docs" / "NEXT_GATE_MATRIX.md").read_text(encoding="utf-8")
    require("current_program_status.v1.json" in current, "current status does not point to JSON")
    require("NEXT_GATE_MATRIX.md" in current, "current status does not point to gate matrix")
    require("C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE" in current, "current status drops C2C review limit")
    require("e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb" in current, "current status drops delivery head")
    require("a69ede68f3e54e5ab006dbfd7a65c33040590f93" in current, "current status drops reviewed source")
    for marker in ("BLOCKED_PENDING_SEPARATE_GATE", "NOT_RUN", "FROZEN"):
        require(marker in matrix, f"gate matrix lacks {marker}")

    readme = (root / "README.md").read_text(encoding="utf-8")
    require("docs/CURRENT_PROGRAM_STATUS.md" in readme, "README lacks current status pointer")
    require("docs/NEXT_GATE_MATRIX.md" in readme, "README lacks gate matrix pointer")

    handoff = (root / "docs" / "PROGRAM_HANDOFF_REFERENCE.md").read_text(encoding="utf-8")
    require("CURRENT_PROGRAM_STATUS.md" in handoff and "Historical handoff" in handoff, "handoff is not marked historical")

    todo = (root / "tasks" / "todo.md").read_text(encoding="utf-8")
    require("CURRENT_PROGRAM_STATUS.md" in todo and "T5" in todo, "task ledger lacks current authority")

    historical_docs = (
        "docs/00_ANDROID_FIRST_EXECUTION_PLAN.md",
        "docs/01_CAMERAX_AND_POSE_SPIKE.md",
        "docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md",
        "docs/UI1_P1B_ENGINEERING_COMPLETION_PLAN.md",
        "docs/P25R_OWNER_FINAL_REVIEW_GUIDE.md",
    )
    for relative in historical_docs:
        text = (root / relative).read_text(encoding="utf-8")
        require("CURRENT_PROGRAM_STATUS.md" in text and "historical" in text.lower(), f"historical pointer missing: {relative}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        validate(args.root.resolve())
    except StatusError as exc:
        print(f"FAIL PROGRAM_STATUS: {exc}")
        return 1
    print("PASS PROGRAM_STATUS: canonical status, gate matrix and forward pointers agree")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
