#!/usr/bin/env python3
"""Validate provider-neutral evaluation governance without review or provider access."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
GOV_REL = Path("docs/phase1_5/t7/evaluation_governance.v1.json")
PACKET_SCHEMA_REL = Path("docs/phase1_5/t7/evaluation_review_packet.v1.schema.json")
T6_BASE = "3233313af8be946cc43245daf15f24f853933d88"
RUBRIC_REL = "docs/phase1_5/VLM_EVALUATION_RUBRIC.md"
RUBRIC_SHA = "e009955715dd001c3001fa37b8fcbd28707cc59d78fb5e892768ff7f993d088a"
RUBRIC_BYTES = 2771
UNCERTAINTY_REL = "docs/phase1_5/VLM_UNCERTAINTY_CONTRACT.md"
UNCERTAINTY_SHA = "4fccebf422630c49ca3b4010beaa9697a91677378f344188e4166dd50705aca0"
PIPELINE_REL = "docs/phase1_5/PIPELINE_APP_FIELD_MAPPING.md"
PIPELINE_SHA = "81f8768011a2805d722f74c3571bed1257117cb96841bd633a3fb0141db60675"
CRITERIA = [
    (1, "scene_correctness", "HUMAN"), (2, "background_story_plausibility", "HUMAN"), (3, "lighting_direction", "HUMAN"),
    (4, "lighting_quality", "HUMAN"), (5, "composition_description", "HUMAN"), (6, "subject_intent", "HUMAN"),
    (7, "emotion_interpretation", "HUMAN"), (8, "pose_advice", "HUMAN"), (9, "camera_position", "HUMAN"),
    (10, "director_prompt_executability", "HUMAN"), (11, "advice_specificity", "HUMAN"), (12, "advice_noncontradiction", "HUMAN"),
    (13, "severe_hallucination", "HUMAN"), (14, "honest_uncertainty", "HUMAN"), (15, "natural_chinese", "HUMAN"),
    (16, "photographic_professionalism", "HUMAN"), (17, "repetition_empty_language", "HUMAN"),
    (18, "schema_legality_required_fields_bounds_source_version", "AUTOMATED"),
]
ALLOWED_CATEGORIES = {"SUBJECT_COUNT", "SCENE_EVENT", "IDENTITY", "EXACT_LOCATION", "SAFETY", "LIVE_MEASUREMENT", "BIOGRAPHY", "SPECULATIVE_STORY_OR_EMOTION_AS_FACT", "UNPROVEN_PRIVACY_OR_LICENSE_CLAIM", "UNSAFE_RECOMMENDATION", "OTHER_CRITICAL"}
UNSAFE_RE = re.compile(r"(?:^[A-Za-z]:[\\/]|^\\\\|(?:^|/)(?:Users|home|mnt|private|secrets?)(?:/|$)|(?:content|file)://|Photo Picker|Bearer\s+[A-Za-z0-9._-]+|[\w.+-]+@[\w.-]+)", re.IGNORECASE)


class T7ValidationError(ValueError):
    """Expected fail-closed validation error."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise T7ValidationError(message)


def read_json(root: Path, relative: str | Path) -> Any:
    path = root / Path(relative)
    if not path.is_file():
        raise T7ValidationError(f"missing file: {Path(relative).as_posix()}")
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise T7ValidationError(f"invalid JSON: {Path(relative).as_posix()}") from exc


def digest(root: Path, relative: str) -> tuple[int, str]:
    path = root / Path(relative)
    if not path.is_file():
        raise T7ValidationError(f"missing bound source: {relative}")
    data = path.read_bytes()
    return len(data), hashlib.sha256(data).hexdigest()


def reject_unsafe(value: Any, location: str) -> None:
    if isinstance(value, str):
        if UNSAFE_RE.search(value) or ".." in Path(value).parts:
            raise T7ValidationError(f"unsafe/private value at {location}")
        return
    if isinstance(value, dict):
        for key, child in value.items():
            reject_unsafe(key, f"{location}.<key>")
            reject_unsafe(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_unsafe(child, f"{location}[{index}]")


def validate_governance(root: Path) -> tuple[dict[str, Any], str]:
    gov = read_json(root, GOV_REL)
    require(isinstance(gov, dict), "governance manifest must be an object")
    require(gov.get("schema_version") == "1.0.0", "governance schema version drift")
    require(gov.get("status") == "EVALUATION_GOVERNANCE_PACKET_READY_HUMAN_REVIEW_NOT_AUTHORIZED", "governance status is not fail-closed")
    require(gov.get("t6_base") == T6_BASE, "T6 base drift")
    expected_criteria = [{"id": i, "name": name, "verifier": verifier} for i, name, verifier in CRITERIA]
    require(gov.get("criteria") == expected_criteria, "18-criterion rubric binding drift")
    rubric = gov.get("rubric", {})
    require(rubric.get("path") == RUBRIC_REL and rubric.get("sha256") == RUBRIC_SHA and rubric.get("bytes") == RUBRIC_BYTES, "rubric source binding drift")
    require(rubric.get("status") == "PROPOSED_OWNER_THRESHOLD_NOT_MEASURED", "rubric threshold status promoted")
    require(rubric.get("criteria_count") == 18 and rubric.get("human_criteria") == 17 and rubric.get("automated_criteria") == [18], "rubric criterion counts drift")
    require(rubric.get("human_score_min") == 0 and rubric.get("human_score_max") == 2 and rubric.get("maximum_raw_score") == 36, "rubric score bounds drift")
    require(rubric.get("owner_thresholds_approved") is False, "owner threshold authorization promoted")
    actual_bytes, actual_sha = digest(root, RUBRIC_REL)
    require(actual_bytes == RUBRIC_BYTES and actual_sha == RUBRIC_SHA, "rubric file changed")
    uncertainty = gov.get("uncertainty", {})
    require(uncertainty.get("contract_path") == UNCERTAINTY_REL and uncertainty.get("contract_sha256") == UNCERTAINTY_SHA and uncertainty.get("field_count") == 9, "uncertainty contract binding drift")
    require(uncertainty.get("global_confidence_forbidden") is True, "global confidence boundary removed")
    uncertainty_bytes, uncertainty_sha = digest(root, UNCERTAINTY_REL)
    require(uncertainty_bytes > 0 and uncertainty_sha == UNCERTAINTY_SHA, "uncertainty contract changed")
    pipeline = gov.get("pipeline_boundary", {})
    require(pipeline.get("mapping_path") == PIPELINE_REL and pipeline.get("mapping_sha256") == PIPELINE_SHA, "Pipeline mapping identity drift")
    require(pipeline.get("compatibility_claim_allowed") is False and pipeline.get("missing_safe_mapping_remains_blocked") is True, "Pipeline readiness promoted")
    pipeline_bytes, pipeline_sha = digest(root, PIPELINE_REL)
    require(pipeline_bytes > 0 and pipeline_sha == PIPELINE_SHA, "Pipeline mapping changed")
    policy = gov.get("review_policy", {})
    require(policy.get("provider_model_blinded") is True and policy.get("provider_provenance_retained_in_envelope") is True, "blinding/provenance policy drift")
    require(policy.get("initial_reviewer_slots") == ["R1", "R2"] and policy.get("third_reviewer_slot") == "R3", "reviewer slots drift")
    require(policy.get("per_criterion_aggregate") == "MEDIAN" and policy.get("unresolved_adjudication_blocks_final_result") is True and policy.get("human_review_authorized") is False, "adjudication policy drift")
    thresholds = gov.get("thresholds", {})
    require(thresholds.get("status") == "PROPOSED_NOT_APPROVED" and thresholds.get("owner_thresholds_approved") is False, "threshold approval drift")
    provider = gov.get("provider_neutrality", {})
    require(provider.get("allowed_provider_types") == ["LOCAL_VLM", "PIPELINE", "CLOUD", "ON_DEVICE"], "provider type drift")
    for key in ("provider_qualification_authorized", "runtime_authorized", "pipeline_integration_authorized"):
        require(provider.get(key) is False, f"provider authority promoted: {key}")
    privacy = gov.get("privacy_boundary", {})
    for key in ("media_bytes_allowed", "media_paths_allowed", "private_media_allowed", "reviewer_pii_allowed", "synthetic_review_records_tracked"):
        require(privacy.get(key) is False, f"privacy boundary promoted: {key}")
    auth = gov.get("authorization", {})
    for key in ("human_review_authorized", "provider_qualification_authorized", "pipeline_integration_authorized", "private_media_authorized", "runtime_authorized", "owner_thresholds_approved"):
        require(auth.get(key) is False, f"authorization promoted: {key}")
    packet_schema = read_json(root, PACKET_SCHEMA_REL)
    require(isinstance(packet_schema, dict) and packet_schema.get("additionalProperties") is False, "review packet schema is not strict")
    reject_unsafe(gov, "governance")
    manifest_bytes, manifest_sha = digest(root, GOV_REL)
    require(manifest_bytes > 0 and len(manifest_sha) == 64, "governance manifest digest unavailable")
    return gov, manifest_sha


def validate_review_packet(packet: dict[str, Any], governance: dict[str, Any]) -> None:
    allowed = {"packet_version", "sample_id", "blinded_candidate_id", "rubric_version", "reviewer_slot", "criterion_scores", "automated_criterion_18", "severe_hallucination", "severe_hallucination_categories", "adjudication_required", "review_status", "adjudication_note"}
    required = allowed - {"adjudication_note"}
    require(required <= set(packet) <= allowed, "review packet fields are incomplete or unknown")
    require(packet.get("packet_version") == "1.0.0", "review packet version drift")
    require(isinstance(packet.get("sample_id"), str) and re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,127}", packet["sample_id"]), "sample id is not opaque-safe")
    require(isinstance(packet.get("blinded_candidate_id"), str) and re.fullmatch(r"candidate-[a-z0-9-]{1,64}", packet["blinded_candidate_id"]), "candidate id is not blinded-safe")
    require(packet.get("rubric_version") == governance["governance_id"], "review packet rubric identity drift")
    require(packet.get("reviewer_slot") in {"R1", "R2", "R3"}, "reviewer slot invalid")
    scores = packet.get("criterion_scores")
    require(isinstance(scores, dict) and set(scores) == {f"c{i:02d}" for i in range(1, 18)}, "human criterion score set drift")
    require(all(isinstance(value, int) and not isinstance(value, bool) and 0 <= value <= 2 for value in scores.values()), "human score outside 0..2")
    automated = packet.get("automated_criterion_18")
    require(isinstance(automated, dict) and set(automated) == {"status", "required_fields_valid", "bounds_valid", "source_version_consistent"}, "automated criterion shape drift")
    require(automated.get("status") in {"PASS", "FAIL", "NOT_RUN"}, "automated criterion status invalid")
    severe = packet.get("severe_hallucination")
    categories = packet.get("severe_hallucination_categories")
    require(isinstance(severe, bool) and isinstance(categories, list) and all(category in ALLOWED_CATEGORIES for category in categories), "severe-hallucination fields invalid")
    require(bool(categories) is severe, "severe-hallucination/category mismatch")
    require(isinstance(packet.get("adjudication_required"), bool), "adjudication flag invalid")
    require(packet.get("review_status") in {"REVIEW_PACKET_VALID", "ADJUDICATION_REQUIRED", "ADJUDICATION_COMPLETE", "UNRESOLVED"}, "review status invalid")
    if packet.get("adjudication_note") is not None:
        require(isinstance(packet["adjudication_note"], str) and len(packet["adjudication_note"]) <= 1000, "adjudication note invalid")
    reject_unsafe(packet, "packet")


def aggregate_review_packets(packets: list[dict[str, Any]], governance: dict[str, Any]) -> dict[str, Any]:
    require(len(packets) in {2, 3}, "review set must contain two reviewers or two plus R3")
    for packet in packets:
        validate_review_packet(packet, governance)
    require([packet["reviewer_slot"] for packet in packets] == (["R1", "R2"] if len(packets) == 2 else ["R1", "R2", "R3"]), "reviewer slots must be ordered R1/R2/R3")
    first, second = packets[0], packets[1]
    require(first["sample_id"] == second["sample_id"] and first["blinded_candidate_id"] == second["blinded_candidate_id"], "review identities disagree")
    differences = {key: abs(first["criterion_scores"][key] - second["criterion_scores"][key]) for key in first["criterion_scores"]}
    severe = any(packet["severe_hallucination"] for packet in packets)
    needs_r3 = max(differences.values()) > 1 or severe
    if needs_r3:
        require(len(packets) == 3, "required R3 adjudication is missing")
        require(packets[2]["adjudication_required"] is True, "R3 packet does not record adjudication")
        status = "ADJUDICATION_COMPLETE" if packets[2]["review_status"] == "ADJUDICATION_COMPLETE" else "UNRESOLVED"
    else:
        require(len(packets) == 2, "unexpected R3 packet without a trigger")
        status = "REVIEW_PACKET_VALID"
    medians = {key: statistics.median([packet["criterion_scores"][key] for packet in packets]) for key in first["criterion_scores"]}
    return {"status": status, "needs_r3": needs_r3, "criterion_medians": medians}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--require-human-review-authority", action="store_true")
    parser.add_argument("--require-provider-qualification", action="store_true")
    args = parser.parse_args(argv)
    try:
        validate_governance(args.root.resolve())
    except T7ValidationError as exc:
        print(f"T7_VALIDATION_FAILED: {exc}")
        return 1
    if args.require_human_review_authority:
        print("BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED")
        return 2
    if args.require_provider_qualification:
        print("BLOCKED_NO_RUNTIME_AND_REVIEW_EVIDENCE")
        return 3
    print("EVALUATION_GOVERNANCE_READY")
    return 0


if __name__ == "__main__":
    sys.exit(main())
