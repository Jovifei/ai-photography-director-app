#!/usr/bin/env python3
"""Run the contract-only, JSON-only T8 synthetic evaluation harness."""

from __future__ import annotations

import argparse
import copy
import json
import re
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_REL = Path("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json")
ENVELOPE_SCHEMA_REL = Path("docs/reference/provider_analysis_envelope.v1.schema.json")
BUNDLE_SCHEMA_REL = Path("docs/reference/reference_bundle.v1.schema.json")
MANIFEST_SCHEMA_REL = Path("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.schema.json")
SUMMARY_SCHEMA_REL = Path("docs/phase1_5/t8/evaluation_run_summary.v1.schema.json")
ERROR_POLICY_REL = Path("docs/phase1_5/error_policy.v1.json")
T7_BASE = "61cb6776b2b6f00d6965789e0589c5337536d258"
LEGACY_SYNTHETIC_PROVIDER_TYPES = {"LOCAL_VLM", "PIPELINE", "CLOUD", "ON_DEVICE"}
PROVIDER_PROVENANCE_FIELDS = {
    "provider_id", "provider_type", "model_id", "model_revision",
    "model_artifact_sha256", "runtime_id", "producer_release", "provenance",
}

sys.path.insert(0, str(ROOT / "scripts"))
from phase1_5_contract_semantics import (  # noqa: E402
    load_error_policy,
    validate_error_policy,
    validate_provider_envelope_semantics,
    validate_reference_bundle_semantics,
)
from validate_phase1_5_t7_evaluation import (  # noqa: E402
    T7ValidationError,
    aggregate_review_packets,
    read_json,
    validate_governance,
    validate_review_packet,
)


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


def _schema_error(error: Any) -> str:
    path = ".".join(str(part) for part in error.absolute_path)
    return f"{path or '$'}: {error.message}"


def load_contract_validators(root: Path) -> tuple[Draft202012Validator, Draft202012Validator, Draft202012Validator, Draft202012Validator]:
    """Load the tracked frozen schemas without resolving over the network."""
    envelope_schema = read_json(root, ENVELOPE_SCHEMA_REL)
    bundle_schema = read_json(root, BUNDLE_SCHEMA_REL)
    manifest_schema = read_json(root, MANIFEST_SCHEMA_REL)
    summary_schema = read_json(root, SUMMARY_SCHEMA_REL)
    for schema in (envelope_schema, bundle_schema, manifest_schema, summary_schema):
        Draft202012Validator.check_schema(schema)
    registry = Registry().with_resource(envelope_schema["$id"], Resource.from_contents(envelope_schema)).with_resource(bundle_schema["$id"], Resource.from_contents(bundle_schema))
    envelope_validator = Draft202012Validator(
        envelope_schema,
        format_checker=FormatChecker(),
        registry=registry,
    )
    bundle_validator = Draft202012Validator(bundle_schema, format_checker=FormatChecker())
    manifest_validator = Draft202012Validator(manifest_schema, format_checker=FormatChecker())
    summary_validator = Draft202012Validator(summary_schema, format_checker=FormatChecker())
    return envelope_validator, bundle_validator, manifest_validator, summary_validator


def validate_frozen_envelope(
    envelope: dict[str, Any],
    envelope_validator: Draft202012Validator,
    bundle_validator: Draft202012Validator,
    policy: dict[str, Any],
) -> None:
    """Apply schema, cross-field semantics, and the single frozen error policy."""
    schema_errors = sorted(envelope_validator.iter_errors(envelope), key=lambda item: list(item.absolute_path))
    require(not schema_errors, "frozen ProviderAnalysisEnvelope schema invalid: " + "; ".join(_schema_error(error) for error in schema_errors))
    if envelope.get("status") == "SUCCESS":
        bundle = envelope.get("bundle")
        bundle_errors = sorted(bundle_validator.iter_errors(bundle), key=lambda item: list(item.absolute_path))
        require(not bundle_errors, "frozen ReferenceBundle schema invalid: " + "; ".join(_schema_error(error) for error in bundle_errors))
    semantic_errors = validate_provider_envelope_semantics(envelope)
    require(not semantic_errors, "frozen envelope semantics invalid: " + json.dumps(semantic_errors, ensure_ascii=False, sort_keys=True))
    policy_errors = validate_error_policy(envelope, policy)
    require(not policy_errors, "frozen error policy invalid: " + json.dumps(policy_errors, ensure_ascii=False, sort_keys=True))
    if envelope.get("status") == "SUCCESS":
        bundle_semantic_errors = validate_reference_bundle_semantics(envelope["bundle"])
        require(not bundle_semantic_errors, "frozen Bundle semantics invalid: " + json.dumps(bundle_semantic_errors, ensure_ascii=False, sort_keys=True))


def load_manifest(root: Path, manifest_validator: Draft202012Validator) -> dict[str, Any]:
    manifest = read_json(root, MANIFEST_REL)
    manifest_errors = sorted(manifest_validator.iter_errors(manifest), key=lambda item: list(item.absolute_path))
    require(not manifest_errors, "synthetic manifest schema invalid: " + "; ".join(_schema_error(error) for error in manifest_errors))
    require(manifest.get("schema_version") == "1.0.0", "synthetic manifest schema drift")
    require(manifest.get("status") == "CONTRACT_ONLY_SYNTHETIC_NO_PROVIDER_EXECUTED", "synthetic marker missing")
    require(manifest.get("t7_base") == T7_BASE, "T7 base drift")
    require(manifest.get("fixture_class") == "CONTRACT_ONLY_SYNTHETIC", "fixture class drift")
    authority = manifest.get("authority", {})
    for key in ("provider_runtime_authorized", "human_review_authorized", "pipeline_authorized", "private_media_authorized", "thresholds_approved"):
        require(authority.get(key) is False, f"authority promoted: {key}")
    require(not unsafe(manifest), "unsafe path or PII in synthetic manifest")
    return manifest


def build_blinded_review_payload(validated_success_envelope: dict[str, Any], blinded_candidate_id: str | None = None) -> dict[str, Any]:
    """Project only review-safe fields; provider provenance is never copied then deleted."""
    require(validated_success_envelope.get("status") == "SUCCESS", "blinded projection requires SUCCESS")
    require(isinstance(validated_success_envelope.get("bundle"), dict), "blinded projection requires a Bundle")
    candidate_id = blinded_candidate_id or f"candidate-{validated_success_envelope['reference_id']}"
    payload = {
        "blinded_candidate_id": candidate_id,
        "reference_id": validated_success_envelope["reference_id"],
        "bundle": copy.deepcopy(validated_success_envelope["bundle"]),
        "confidence_summary": copy.deepcopy(validated_success_envelope["confidence_summary"]),
        "uncertainty_flags": copy.deepcopy(validated_success_envelope["uncertainty_flags"]),
    }
    require(not any(field in payload for field in PROVIDER_PROVENANCE_FIELDS), "provider provenance leaked into blinded payload")
    require(not unsafe(payload), "unsafe value in blinded payload")
    return payload


def packet(governance: dict[str, Any], simple: dict[str, Any], payload: dict[str, Any], fallback_slot: str) -> dict[str, Any]:
    require("automated_criterion_18" not in simple, "reviewer cannot override automated criterion 18")
    score = int(simple.get("scores", 0))
    criterion_scores = simple.get("criterion_scores") or {f"c{i:02d}": score for i in range(1, 18)}
    slot = simple.get("reviewer_slot", fallback_slot)
    adjudication_required = bool(simple.get("adjudication_required", slot == "R3"))
    review_status = simple.get("review_status", "ADJUDICATION_COMPLETE" if slot == "R3" else "REVIEW_PACKET_VALID")
    return {
        "packet_version": "1.0.0",
        "sample_id": payload["reference_id"],
        "blinded_candidate_id": payload["blinded_candidate_id"],
        "rubric_version": governance["governance_id"],
        "reviewer_slot": slot,
        "criterion_scores": criterion_scores,
        "automated_criterion_18": {"status": "PASS", "required_fields_valid": True, "bounds_valid": True, "source_version_consistent": True},
        "severe_hallucination": bool(simple.get("severe", False)),
        "severe_hallucination_categories": simple.get("categories", []),
        "adjudication_required": adjudication_required,
        "review_status": review_status,
        **({"adjudication_note": simple["adjudication_note"]} if "adjudication_note" in simple else {}),
    }


def _base_summary(case_id: str, outcome: str, contract_valid: bool, eligible: bool) -> dict[str, Any]:
    return {
        "case_id": case_id,
        "provider_outcome": outcome,
        "contract_valid": contract_valid,
        "quality_review_eligible": eligible,
        "criterion_scores": {},
        "raw_score": None,
        "normalized_quality": None,
        "severe_hallucination_count": 0,
        "hard_findings": [],
        "threshold_status": "PROPOSED_NOT_APPROVED",
        "qualification_state": "NOT_PROVIDER_QUALIFICATION",
        "demo_fallback_used": False,
    }


def summary_contract_record(fixture: dict[str, Any], summary: dict[str, Any]) -> dict[str, Any]:
    """Return the strict per-case record required by evaluation_run_summary.v1."""
    return {
        "summary_version": "1.0.0",
        "fixture_class": "CONTRACT_ONLY_SYNTHETIC",
        "candidate_blind_id": fixture["blinded_candidate_id"],
        "provider_outcome": summary["provider_outcome"],
        "contract_valid": summary["contract_valid"],
        "quality_review_eligible": summary["quality_review_eligible"],
        "adjudication_state": summary["adjudication_state"],
        "criterion_scores": summary["criterion_scores"],
        "raw_score": summary["raw_score"],
        "normalized_quality": summary["normalized_quality"],
        "severe_hallucination_count": summary["severe_hallucination_count"],
        "hard_findings": summary["hard_findings"],
        "threshold_status": summary["threshold_status"],
        "qualification_state": summary["qualification_state"],
    }


def run_case(
    root: Path,
    case: dict[str, Any],
    governance: dict[str, Any],
    envelope_validator: Draft202012Validator,
    bundle_validator: Draft202012Validator,
    policy: dict[str, Any],
) -> dict[str, Any]:
    fixture = read_json(root, case["path"])
    require(fixture.get("fixture_class") == "CONTRACT_ONLY_SYNTHETIC_NO_PROVIDER_EXECUTED", f"fixture marker drift: {case['id']}")
    require(fixture.get("reference_id", "").startswith("synthetic-reference-"), f"reference identity drift: {case['id']}")
    require(fixture.get("blinded_candidate_id", "").startswith("candidate-synthetic-"), f"candidate identity drift: {case['id']}")
    metadata = fixture.get("provider_metadata", {})
    require(metadata.get("provider_id") == "synthetic-contract-provider", f"provider provenance is not synthetic: {case['id']}")
    require(metadata.get("model_id") == "synthetic-model" and metadata.get("model_revision") == "synthetic-revision" and metadata.get("runtime_id") == "synthetic-runtime", f"model/runtime provenance is not synthetic: {case['id']}")
    require(metadata.get("provider_type") in LEGACY_SYNTHETIC_PROVIDER_TYPES, f"provider type invalid: {case['id']}")
    require(not unsafe(fixture), f"unsafe fixture field: {case['id']}")
    require(fixture.get("threshold_status", "PROPOSED_NOT_APPROVED") == "PROPOSED_NOT_APPROVED", f"threshold promotion: {case['id']}")
    require(fixture.get("qualification_state", "NOT_PROVIDER_QUALIFICATION") == "NOT_PROVIDER_QUALIFICATION", f"provider qualification promotion: {case['id']}")
    outcome = fixture.get("provider_outcome")
    require(outcome in {"SUCCESS", "FAILED", "CANCELLED"}, f"unsupported outcome: {case['id']}")
    envelope = fixture.get("envelope")
    require(isinstance(envelope, dict), f"fixture envelope missing: {case['id']}")
    require(envelope.get("reference_id") == fixture["reference_id"], f"fixture/envelope reference mismatch: {case['id']}")
    require(envelope.get("status") == outcome, f"fixture/envelope outcome mismatch: {case['id']}")
    validate_frozen_envelope(envelope, envelope_validator, bundle_validator, policy)

    if outcome != "SUCCESS":
        require(not fixture.get("review_packets"), f"ineligible outcome has review packets: {case['id']}")
        require(envelope.get("error", {}).get("code") == fixture.get("error_code"), f"error identity drift: {case['id']}")
        summary = _base_summary(case["id"], outcome, bool(fixture.get("contract_valid")), False)
        summary.update({
            "adjudication_state": "NOT_APPLICABLE",
            "error_code": envelope["error"]["code"],
            "retryable": envelope["retryable"],
            "product_action": envelope["error"]["product_action"],
            "fallback_policy": policy["policies"][0]["demo_fallback_policy"],
            "hard_findings": [envelope["error"]["code"]],
        })
        return summary

    require(fixture.get("contract_valid") is True, f"SUCCESS fixture is not contract-valid: {case['id']}")
    blinded = build_blinded_review_payload(envelope, fixture["blinded_candidate_id"])
    simple_packets = fixture.get("review_packets", [])
    require(len(simple_packets) in {2, 3}, f"SUCCESS fixture must have R1/R2 or R1/R2/R3: {case['id']}")
    packets = [packet(governance, simple, blinded, slot) for simple, slot in zip(simple_packets, ("R1", "R2", "R3"))]
    for candidate in packets:
        validate_review_packet(candidate, governance)
    try:
        result = aggregate_review_packets(packets, governance)
    except T7ValidationError as exc:
        if "required R3 adjudication is missing" not in str(exc):
            raise
        summary = _base_summary(case["id"], outcome, True, True)
        summary.update({
            "adjudication_state": "ADJUDICATION_REQUIRED",
            "adjudication_result": "REQUIRED",
            "severe_hallucination_count": sum(1 for simple in simple_packets if simple.get("severe")),
            "hard_findings": ["SEVERE_HALLUCINATION_FLAGGED"] if any(simple.get("severe") for simple in simple_packets) else ["REVIEWER_DISAGREEMENT_REQUIRES_R3"],
        })
        return summary
    automated_points = 2 if all(candidate["automated_criterion_18"]["status"] == "PASS" for candidate in packets) else 0
    raw_total = sum(result["criterion_medians"].values()) + automated_points if result["status"] in {"REVIEW_PACKET_VALID", "ADJUDICATION_COMPLETE"} else None
    raw = int(raw_total) if raw_total is not None and float(raw_total).is_integer() else raw_total
    normalized = round(raw / 36 * 100, 2) if raw is not None else None
    summary = _base_summary(case["id"], outcome, True, True)
    summary.update({
        "adjudication_state": "METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED" if raw is not None else result["status"],
        "adjudication_result": "COMPLETE" if result["status"] == "ADJUDICATION_COMPLETE" else "NOT_TRIGGERED",
        "criterion_scores": {**result["criterion_medians"], "c18": automated_points},
        "raw_score": raw,
        "normalized_quality": normalized,
        "severe_hallucination_count": sum(1 for simple in simple_packets if simple.get("severe")),
        "hard_findings": ["SEVERE_HALLUCINATION_FLAGGED"] if any(simple.get("severe") for simple in simple_packets) else [],
    })
    return summary


def run(root: Path) -> dict[str, Any]:
    envelope_validator, bundle_validator, manifest_validator, summary_validator = load_contract_validators(root)
    manifest = load_manifest(root, manifest_validator)
    governance, _ = validate_governance(root)
    policy = load_error_policy(root / ERROR_POLICY_REL)
    summaries = []
    for case in manifest["cases"]:
        summary = run_case(root, case, governance, envelope_validator, bundle_validator, policy)
        fixture = read_json(root, case["path"])
        contract_record = summary_contract_record(fixture, summary)
        summary_errors = sorted(summary_validator.iter_errors(contract_record), key=lambda item: list(item.absolute_path))
        require(not summary_errors, "evaluation summary schema invalid: " + "; ".join(_schema_error(error) for error in summary_errors))
        summary["summary_contract"] = contract_record
        summaries.append(summary)
    for case, summary in zip(manifest["cases"], summaries):
        require(case.get("expected") == summary["adjudication_state"], f"case expectation drift: {case['id']}")
    return {
        "summary_version": "1.0.0",
        "fixture_class": "CONTRACT_ONLY_SYNTHETIC",
        "qualification_state": "NOT_PROVIDER_QUALIFICATION",
        "threshold_status": "PROPOSED_NOT_APPROVED",
        "frozen_contracts": {"provider_envelope_schema": "PASS", "reference_bundle_schema": "PASS", "manifest_schema": "PASS", "evaluation_summary_schema": "PASS", "semantic_validators": "PASS", "error_policy": "PASS"},
        "cases": summaries,
    }


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
