#!/usr/bin/env python3
"""Run the contract-only, JSON-only T8 synthetic evaluation harness."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_REL = Path("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json")
T7_GOV_REL = Path("docs/phase1_5/t7/evaluation_governance.v1.json")
T7_BASE = "61cb6776b2b6f00d6965789e0589c5337536d258"
sys.path.insert(0, str(ROOT / "scripts"))
from validate_phase1_5_t7_evaluation import T7ValidationError, aggregate_review_packets, read_json, validate_governance, validate_review_packet  # noqa: E402


class T8ValidationError(ValueError):
    """Expected fail-closed harness error."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise T8ValidationError(message)


def unsafe(value: Any) -> bool:
    if isinstance(value, str):
        return bool(re.search(r"(?:^[A-Za-z]:[\\/]|^\\\\|(?:^|/)(?:Users|home|mnt|private|secrets?)(?:/|$)|(?:content|file)://|Photo Picker|@)", value, re.IGNORECASE)) or ".." in Path(value).parts
    if isinstance(value, dict):
        return any(unsafe(key) or unsafe(child) for key, child in value.items())
    if isinstance(value, list):
        return any(unsafe(child) for child in value)
    return False


def load_manifest(root: Path) -> dict[str, Any]:
    manifest = read_json(root, MANIFEST_REL)
    require(manifest.get("schema_version") == "1.0.0", "synthetic manifest schema drift")
    require(manifest.get("status") == "CONTRACT_ONLY_SYNTHETIC_NO_PROVIDER_EXECUTED", "synthetic marker missing")
    require(manifest.get("t7_base") == T7_BASE, "T7 base drift")
    require(manifest.get("fixture_class") == "CONTRACT_ONLY_SYNTHETIC", "fixture class drift")
    authority = manifest.get("authority", {})
    for key in ("provider_runtime_authorized", "human_review_authorized", "pipeline_authorized", "private_media_authorized", "thresholds_approved"):
        require(authority.get(key) is False, f"authority promoted: {key}")
    require(not unsafe(manifest), "unsafe path or PII in synthetic manifest")
    return manifest


def packet(governance: dict[str, Any], simple: dict[str, Any], slot: str) -> dict[str, Any]:
    score = int(simple.get("scores", 0))
    categories = simple.get("categories", [])
    severe = bool(simple.get("severe", False))
    return {
        "packet_version": "1.0.0",
        "sample_id": simple["reference_id"],
        "blinded_candidate_id": simple["blinded_candidate_id"],
        "rubric_version": governance["governance_id"],
        "reviewer_slot": slot,
        "criterion_scores": {f"c{i:02d}": score for i in range(1, 18)},
        "automated_criterion_18": {"status": "PASS", "required_fields_valid": True, "bounds_valid": True, "source_version_consistent": True},
        "severe_hallucination": severe,
        "severe_hallucination_categories": categories,
        "adjudication_required": False,
        "review_status": "REVIEW_PACKET_VALID",
    }


def run_case(root: Path, case: dict[str, Any], governance: dict[str, Any]) -> dict[str, Any]:
    fixture = read_json(root, case["path"])
    require(fixture.get("fixture_class") == "CONTRACT_ONLY_SYNTHETIC_NO_PROVIDER_EXECUTED", f"fixture marker drift: {case['id']}")
    require(fixture.get("reference_id", "").startswith("synthetic-reference-"), f"reference identity drift: {case['id']}")
    require(fixture.get("blinded_candidate_id", "").startswith("candidate-synthetic-"), f"candidate identity drift: {case['id']}")
    metadata = fixture.get("provider_metadata", {})
    require(metadata.get("provider_id") == "synthetic-contract-provider", f"provider provenance is not synthetic: {case['id']}")
    require(metadata.get("model_id") == "synthetic-model" and metadata.get("model_revision") == "synthetic-revision" and metadata.get("runtime_id") == "synthetic-runtime", f"model/runtime provenance is not synthetic: {case['id']}")
    require(metadata.get("provider_type") in {"LOCAL_VLM", "PIPELINE", "CLOUD", "ON_DEVICE"}, f"provider type invalid: {case['id']}")
    require(not unsafe(fixture), f"unsafe fixture field: {case['id']}")
    outcome = fixture.get("provider_outcome")
    require(outcome in {"SUCCESS", "FAILED", "CANCELLED"}, f"unsupported outcome: {case['id']}")
    if outcome != "SUCCESS":
        require(not fixture.get("review_packets"), f"ineligible outcome has review packets: {case['id']}")
        return {"case_id": case["id"], "provider_outcome": outcome, "contract_valid": bool(fixture.get("contract_valid")), "quality_review_eligible": False, "adjudication_state": "NOT_APPLICABLE", "criterion_scores": {}, "raw_score": None, "normalized_quality": None, "severe_hallucination_count": 0, "hard_findings": [fixture.get("error_code", "PROVIDER_OUTCOME_NOT_SUCCESS")], "threshold_status": "PROPOSED_NOT_APPROVED", "qualification_state": "NOT_PROVIDER_QUALIFICATION"}
    require(fixture.get("contract_valid") is True, f"SUCCESS fixture is not contract-valid: {case['id']}")
    simple_packets = fixture.get("review_packets", [])
    require(len(simple_packets) == 2, f"SUCCESS fixture must have R1/R2: {case['id']}")
    packets = [packet(governance, dict(simple, reference_id=fixture["reference_id"], blinded_candidate_id=fixture["blinded_candidate_id"]), slot) for simple, slot in zip(simple_packets, ("R1", "R2"))]
    for candidate in packets:
        validate_review_packet(candidate, governance)
    try:
        result = aggregate_review_packets(packets, governance)
    except T7ValidationError as exc:
        if "required R3 adjudication is missing" not in str(exc):
            raise
        return {"case_id": case["id"], "provider_outcome": outcome, "contract_valid": True, "quality_review_eligible": True, "adjudication_state": "ADJUDICATION_REQUIRED", "criterion_scores": {}, "raw_score": None, "normalized_quality": None, "severe_hallucination_count": sum(1 for simple in simple_packets if simple.get("severe")), "hard_findings": ["SEVERE_HALLUCINATION_FLAGGED"] if any(simple.get("severe") for simple in simple_packets) else ["REVIEWER_DISAGREEMENT_REQUIRES_R3"], "threshold_status": "PROPOSED_NOT_APPROVED", "qualification_state": "NOT_PROVIDER_QUALIFICATION"}
    raw = sum(result["criterion_medians"].values()) if result["status"] == "REVIEW_PACKET_VALID" else None
    normalized = round(raw / 36 * 100, 2) if raw is not None else None
    return {"case_id": case["id"], "provider_outcome": outcome, "contract_valid": True, "quality_review_eligible": True, "adjudication_state": "METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED" if result["status"] == "REVIEW_PACKET_VALID" else result["status"], "criterion_scores": result["criterion_medians"], "raw_score": raw, "normalized_quality": normalized, "severe_hallucination_count": sum(1 for simple in simple_packets if simple.get("severe")), "hard_findings": ["SEVERE_HALLUCINATION_FLAGGED"] if any(simple.get("severe") for simple in simple_packets) else [], "threshold_status": "PROPOSED_NOT_APPROVED", "qualification_state": "NOT_PROVIDER_QUALIFICATION"}


def run(root: Path) -> dict[str, Any]:
    manifest = load_manifest(root)
    governance, _ = validate_governance(root)
    summaries = [run_case(root, case, governance) for case in manifest["cases"]]
    return {"summary_version": "1.0.0", "fixture_class": "CONTRACT_ONLY_SYNTHETIC", "qualification_state": "NOT_PROVIDER_QUALIFICATION", "threshold_status": "PROPOSED_NOT_APPROVED", "cases": summaries}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--require-real-provider", action="store_true")
    parser.add_argument("--require-human-review", action="store_true")
    parser.add_argument("--require-pipeline", action="store_true")
    args = parser.parse_args(argv)
    if args.require_real_provider:
        print("BLOCKED_PROVIDER_RUNTIME_AUTHORIZATION_REQUIRED")
        return 2
    if args.require_human_review:
        print("BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED")
        return 3
    if args.require_pipeline:
        print("BLOCKED_PIPELINE_COMPATIBILITY_AND_AUTHORIZATION_REQUIRED")
        return 4
    try:
        summary = run(args.root.resolve())
    except (T8ValidationError, T7ValidationError, OSError, KeyError, TypeError, ValueError) as exc:
        print(f"T8_HARNESS_FAILED: {exc}")
        return 1
    print("SYNTHETIC_EVALUATION_HARNESS_READY")
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
