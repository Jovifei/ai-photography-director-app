#!/usr/bin/env python3
"""Validate the machine-readable current program status and forward-facing pointers."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


SHA_RE = re.compile(r"^[0-9a-f]{40}$")
T10_DECISION_HASH_MODE = "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1"
T10_DECISION_SHA256 = "45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9"
T11_BASE = "2ff77cadc8696cbd06a504458da4d58c74aa6133"
T11_MANIFEST_ID = "phase1-5-t11-artifact-acquisition-preflight-20260929"
T11_MANIFEST_SHA256 = "4aee65b8ae343ad57338f179cabfbfa798672aa704e635331aaedbaff9f49830"
T11_MANIFEST_HASH_MODE = "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1"
T11_STAGE_STATUS = "ARTIFACT_ACQUISITION_PREFLIGHT_READY_EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED"
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


def read_text(root: Path, relative: str) -> str:
    try:
        return (root / relative).read_text(encoding="utf-8")
    except OSError as exc:
        raise StatusError(f"cannot read {relative}: {exc}") from exc


def validate(root: Path) -> None:
    status = read_status(root)
    for key in ("delivery_head", "reviewed_product_source", "t3_base"):
        value = status.get(key)
        require(isinstance(value, str) and SHA_RE.fullmatch(value), f"invalid {key}")

    require(status["delivery_head"] == "e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb", "delivery head drift")
    require(status["reviewed_product_source"] == "a69ede68f3e54e5ab006dbfd7a65c33040590f93", "product source drift")
    require(status["t3_base"] == "14a56f49e1220eb8139bf7280c747124118fdb21", "T3 base drift")
    require(status.get("t5_content_candidate") == "b2565885382902d2f51ac2099436eddd69946f95", "T5 candidate drift")
    require(status.get("delivery_pr_number") == 8, "unexpected delivery PR")
    require(status.get("delivery_pr_state") == "DRAFT_UNMERGED", "delivery PR is not explicitly Draft/unmerged")
    require(status.get("qualification_state") == "T4_SYNTHETIC_API35_VALIDATED", "qualification state drift")
    require(status.get("local_independent_review_state") == "PASS_EXACT_SHA", "local review state drift")
    require(status.get("c2c_review_limit") == "C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE", "remote review limit missing")
    require(status.get("current_stage") in {
        "T5_PROGRAM_STATUS_ROADMAP_AUTHORITY_CONSOLIDATION_VALIDATED",
        "T6_P1B_AUTHORIZATION_PACKET_READY_EXECUTION_NOT_AUTHORIZED",
        "T7_EVALUATION_GOVERNANCE_PACKET_READY_HUMAN_REVIEW_NOT_AUTHORIZED",
        "SYNTHETIC_EVALUATION_HARNESS_READY_REAL_EVALUATION_NOT_AUTHORIZED",
        "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED",
    }, "current stage drift")
    require(status.get("next_authorized_stage") in {
        "OWNER_REVIEWED_NEXT_GATE_SELECTION",
        "OWNER_DECISION_OR_SEPARATE_NON_RUNTIME_GATE",
        "EXTERNAL_LEGAL_TRANSPORT_DESTINATION_EVIDENCE_OR_OWNER_DEFER",
    }, "next stage drift")
    if status.get("current_stage") == "T6_P1B_AUTHORIZATION_PACKET_READY_EXECUTION_NOT_AUTHORIZED":
        t6 = status.get("t6_readiness", {})
        require(t6.get("status") == "P1B_AUTHORIZATION_PACKET_READY_EXECUTION_NOT_AUTHORIZED", "T6 readiness status drift")
        require(t6.get("base") == "0b04abdc57a3fa332689cfe664f9bcb997160e5e", "T6 base drift")
        require(t6.get("readiness_id") == "phase1-5-p1b-qwen3-vl-2b-authorization-readiness-20260927", "T6 readiness identity drift")
        require(t6.get("execution_authorized") is False, "T6 execution authorization promoted")
        require(t6.get("artifact_download") == "NOT_RUN" and t6.get("qwen_runtime") == "NOT_RUN", "T6 external state promoted")
    if status.get("current_stage") == "T7_EVALUATION_GOVERNANCE_PACKET_READY_HUMAN_REVIEW_NOT_AUTHORIZED":
        t7 = status.get("t7_governance", {})
        require(t7.get("status") == "EVALUATION_GOVERNANCE_PACKET_READY_HUMAN_REVIEW_NOT_AUTHORIZED", "T7 governance status drift")
        require(t7.get("base") == "3233313af8be946cc43245daf15f24f853933d88", "T7 base drift")
        require(t7.get("criteria_count") == 18 and t7.get("owner_thresholds_approved") is False, "T7 rubric state drift")
        require(t7.get("human_review_authorized") is False and t7.get("provider_qualification_authorized") is False, "T7 authorization promoted")
        require(t7.get("pipeline_compatibility_claim_allowed") is False, "T7 Pipeline claim promoted")
    if status.get("current_stage") == "SYNTHETIC_EVALUATION_HARNESS_READY_REAL_EVALUATION_NOT_AUTHORIZED":
        t8 = status.get("t8_harness", {})
        require(t8.get("status") == "SYNTHETIC_EVALUATION_HARNESS_READY_REAL_EVALUATION_NOT_AUTHORIZED", "T8 harness status drift")
        require(t8.get("base") == "61cb6776b2b6f00d6965789e0589c5337536d258", "T8 base drift")
        require(t8.get("fixture_class") == "CONTRACT_ONLY_SYNTHETIC", "T8 fixture class drift")
        require(t8.get("real_provider_executed") is False and t8.get("human_review_executed") is False, "T8 external execution promoted")
        require(t8.get("pipeline_accessed") is False and t8.get("media_accessed") is False, "T8 external access promoted")
    if status.get("current_stage") == "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED":
        t9 = status.get("t9_promotion_gate", {})
        require(t9.get("status") == "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED", "T9 promotion status drift")
        require(t9.get("base") == "acf43a8d734675ec23da534472cade129b7b1068", "T9 base drift")
        require(t9.get("provider_ready_promotion_authorized") is False, "T9 Provider promotion authorized")
        require(t9.get("human_review_authorized") is False and t9.get("thresholds_approved") is False, "T9 authority promoted")
        require(t9.get("android_product_change_authorized") is False and t9.get("offline_bundle_path_preserved") is True, "T9 boundary drift")
        t10 = status.get("t10_owner_decision", {})
        require(t10.get("base") == "dd5d297ffa26adc38470bfa5737ac82b94eaa2ec", "T10 base drift")
        require(t10.get("decision") == "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY", "T10 decision drift")
        require(t10.get("decision_hash_mode") == T10_DECISION_HASH_MODE, "T10 decision hash mode drift")
        require(t10.get("decision_sha256") == T10_DECISION_SHA256, "T10 decision SHA drift")
        require(t10.get("status") == "P1B_ARTIFACT_QUARANTINE_SCOPE_APPROVED_ARTIFACT_ACQUISITION_NOT_YET_AUTHORIZED", "T10 authority status drift")
        require(t10.get("artifact_quarantine_only") is True, "T10 quarantine scope drift")
        require(all(t10.get(key) is False for key in ("download_authorized", "runtime_authorized", "inference_authorized", "app_integration_authorized", "pipeline_integration_authorized", "private_media_authorized")), "T10 forbidden authority promoted")
    require(status.get("current_stage") == "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED", "T11 nested preflight must remain layered under the T9 top-level stage")
    expected_t11 = {
        "stage_status": T11_STAGE_STATUS,
        "current_blocker": "BLOCKED_LEGAL_REVIEW",
        "base": T11_BASE,
        "manifest": "docs/phase1_5/t11/artifact_acquisition_preflight.v1.json",
        "manifest_id": T11_MANIFEST_ID,
        "manifest_hash_mode": T11_MANIFEST_HASH_MODE,
        "manifest_sha256": T11_MANIFEST_SHA256,
        "t10_decision_hash_mode": T10_DECISION_HASH_MODE,
        "t10_decision_sha256": T10_DECISION_SHA256,
        "legal_review_status": "NOT_LEGAL_APPROVED",
        "transport_status": "REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED",
        "fresh_metadata_status": "NOT_RUN",
        "quarantine_destination_status": "NOT_DECLARED",
        "owner_acquisition_decision": "NONE_TRACKED",
        "artifact_acquisition_authorized": False,
        "artifact_body_access_authorized": False,
        "network_access_authorized": False,
        "runtime_authorized": False,
        "inference_authorized": False,
        "app_integration_authorized": False,
        "pipeline_integration_authorized": False,
        "private_media_authorized": False,
    }
    require(status.get("t11_artifact_acquisition_preflight") == expected_t11, "T11 canonical status projection drift")
    require(status.get("next_authorized_stage") == "EXTERNAL_LEGAL_TRANSPORT_DESTINATION_EVIDENCE_OR_OWNER_DEFER", "T11 next stage drift")
    require(status.get("external_gates") == REQUIRED_EXTERNAL_GATES, "external gate state drift")
    require(status.get("t5_qualification", {}).get("scope_guard") == "PASS_18_APPROVED_PATHS", "T5 scope count drift")

    current = read_text(root, "docs/CURRENT_PROGRAM_STATUS.md")
    matrix = read_text(root, "docs/NEXT_GATE_MATRIX.md")
    require("current_program_status.v1.json" in current, "current status does not point to JSON")
    require("NEXT_GATE_MATRIX.md" in current, "current status does not point to gate matrix")
    require("C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE" in current, "current status drops C2C review limit")
    require("e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb" in current, "current status drops delivery head")
    require("a69ede68f3e54e5ab006dbfd7a65c33040590f93" in current, "current status drops reviewed source")
    delivery_in_markdown = re.search(r"delivery head is `([0-9a-f]{40})`", current)
    source_in_markdown = re.search(r"reviewed product source `([0-9a-f]{40})`", current)
    require(delivery_in_markdown and delivery_in_markdown.group(1) == status["delivery_head"], "JSON/Markdown delivery head mismatch")
    require(source_in_markdown and source_in_markdown.group(1) == status["reviewed_product_source"], "JSON/Markdown product source mismatch")
    for marker in ("P1B_AUTHORIZATION_PACKET_READY", "EVALUATION_GOVERNANCE_PACKET_READY", "SYNTHETIC_EVALUATION_HARNESS_READY", "PRODUCT_READY_PROMOTION_GATE_READY", "NOT_RUN", "FROZEN"):
        require(marker in matrix, f"gate matrix lacks {marker}")

    readme = read_text(root, "README.md")
    require("docs/CURRENT_PROGRAM_STATUS.md" in readme, "README lacks current status pointer")
    require("docs/NEXT_GATE_MATRIX.md" in readme, "README lacks gate matrix pointer")
    require("AH0 批准后创建" not in readme and "AH0 批准后在" not in readme, "README retains obsolete AH0 execution entry")
    require("历史试点" in readme and "历史 Beta" in readme, "README pilot links are not historical")

    handoff = read_text(root, "docs/PROGRAM_HANDOFF_REFERENCE.md")
    require("CURRENT_PROGRAM_STATUS.md" in handoff and "Historical handoff" in handoff, "handoff is not marked historical")

    todo = read_text(root, "tasks/todo.md")
    require("CURRENT_PROGRAM_STATUS.md" in todo and "T5" in todo, "task ledger lacks current authority")

    historical_docs = (
        "docs/00_ANDROID_FIRST_EXECUTION_PLAN.md",
        "docs/01_CAMERAX_AND_POSE_SPIKE.md",
        "docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md",
        "docs/UI1_P1B_ENGINEERING_COMPLETION_PLAN.md",
        "docs/P25R_OWNER_FINAL_REVIEW_GUIDE.md",
        "docs/ANDROID_BETA_PHONE_SETUP.md",
        "docs/BETA_USER_GUIDE.md",
    )
    for relative in historical_docs:
        text = read_text(root, relative)
        require("CURRENT_PROGRAM_STATUS.md" in text and "historical" in text.lower(), f"historical pointer missing: {relative}")

    p25r = read_text(root, "docs/P25R_OWNER_FINAL_REVIEW_GUIDE.md")
    require("当前唯一未完成 Gate" not in p25r, "P25R still claims to be the sole current gate")

    # Forward-facing status surfaces must not promote external capability claims.
    for relative in ("README.md", "docs/CURRENT_PROGRAM_STATUS.md", "docs/NEXT_GATE_MATRIX.md", "tasks/todo.md"):
        text = read_text(root, relative)
        require("Qwen 已连接" not in text and "Pipeline 已集成" not in text, f"promoted runtime claim in {relative}")
        require("实体设备 PASS" not in text and "主线已合并" not in text, f"promoted release claim in {relative}")


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
