#!/usr/bin/env python3
"""Validate T11's metadata-only artifact acquisition preflight."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_REL = Path("docs/phase1_5/t11/artifact_acquisition_preflight.v1.json")
MANIFEST_SCHEMA_REL = Path("docs/phase1_5/t11/artifact_acquisition_preflight.v1.schema.json")
STATUS_REL = Path("docs/current_program_status.v1.json")
MANIFEST_HASH_MODE = "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1"
INPUT_SCHEMAS = {
    "legal_review": Path("docs/phase1_5/t11/legal_review_input.v1.schema.json"),
    "transport": Path("docs/phase1_5/t11/transport_verification_input.v1.schema.json"),
    "fresh_metadata": Path("docs/phase1_5/t11/fresh_metadata_input.v1.schema.json"),
    "destination": Path("docs/phase1_5/t11/quarantine_destination_input.v1.schema.json"),
    "owner_decision": Path("docs/phase1_5/t11/artifact_acquisition_owner_decision.v1.schema.json"),
}
T11_BASE = "2ff77cadc8696cbd06a504458da4d58c74aa6133"
T10_DECISION_HASH_MODE = "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1"
T10_DECISION_SHA256 = "45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9"
T10_DECISION_BLOB = "74d980e7dccdc67f659836b39126557d91e5460b"
T10_DECISION_REL = Path("docs/phase1_5/p1b/p1b_owner_decision.v1.json")
T10_MANIFEST_REL = Path("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
P1B_MANIFEST_SHA256 = "166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a"
P1B_READINESS_ID = "phase1-5-p1b-qwen3-vl-2b-authorization-readiness-20260927"
PROPOSED_DOMAINS = ("huggingface.co", "us.aws.cdn.hf.co")
ARTIFACT = {
    "publisher": "Qwen",
    "model_id": "Qwen/Qwen3-VL-2B-Instruct",
    "immutable_revision": "89644892e4d85e24eaac8bacfd4f463576704203",
    "weight_filename": "model.safetensors",
    "weight_bytes": 4255140312,
    "weight_lfs_sha256": "7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0",
}
BLOCKERS = (
    "LEGAL_REVIEW_REQUIRED",
    "TRANSPORT_VERIFICATION_REQUIRED",
    "FRESH_METADATA_REVALIDATION_REQUIRED",
    "QUARANTINE_DESTINATION_REQUIRED",
    "OWNER_ACQUISITION_AUTHORIZATION_REQUIRED",
)
FORBIDDEN_AUTHORITY_FLAGS = (
    "download_authorized",
    "runtime_authorized",
    "inference_authorized",
    "app_integration_authorized",
    "pipeline_integration_authorized",
    "private_media_authorized",
)
T11_FORBIDDEN_AUTHORITY_FLAGS = (
    "runtime_authorized",
    "inference_authorized",
    "app_integration_authorized",
    "pipeline_integration_authorized",
    "private_media_authorized",
)
PRIVATE_PATH_RE = re.compile(
    r"(?:^[A-Za-z]:[\\/]|^\\\\|(?:^|/)(?:Users|home|mnt|private|secrets?)(?:/|$)|^file://)",
    re.IGNORECASE,
)


class ValidationError(ValueError):
    """Expected fail-closed T11 validation error."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def read_json(root: Path, relative: str | Path) -> Any:
    path = root / Path(relative)
    if not path.is_file():
        raise ValidationError(f"missing file: {Path(relative).as_posix()}")
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValidationError(f"invalid JSON: {Path(relative).as_posix()}") from exc


def normalize_eol(data: bytes) -> bytes:
    try:
        text = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ValidationError("source representation is not strict UTF-8") from exc
    return text.replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")


def classify_source_representation(checkout_bytes: bytes, git_blob_bytes: bytes) -> str:
    """Classify byte equality without changing or re-authorizing frozen P1B tuples."""
    if checkout_bytes == git_blob_bytes:
        return "IDENTICAL_BYTES"
    if normalize_eol(checkout_bytes) == normalize_eol(git_blob_bytes):
        return "EOL_ONLY_CHECKOUT_REPRESENTATION_DIFFERENCE"
    return "SOURCE_CONTENT_DRIFT_STOP"


def git_blob_sha1(data: bytes) -> str:
    """Calculate a Git blob ID for normalized text without invoking Git or the network."""
    normalized = normalize_eol(data)
    header = f"blob {len(normalized)}\0".encode("ascii")
    return hashlib.sha1(header + normalized).hexdigest()


def canonical_input_sha256(record: dict[str, Any]) -> str:
    """Hash a future input object's canonical UTF-8 JSON representation."""
    payload = json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def preflight_manifest_sha256(root: Path) -> str:
    """Return the portable T11 manifest identity, normalizing EOL only."""
    path = root / MANIFEST_REL
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise ValidationError("cannot read T11 preflight manifest for identity") from exc
    return hashlib.sha256(normalize_eol(data)).hexdigest()


def reject_private_strings(value: Any, location: str) -> None:
    if isinstance(value, str):
        normalized = value.replace("\\", "/")
        require(not PRIVATE_PATH_RE.search(normalized), f"absolute/private path in {location}")
        require(".." not in Path(normalized).parts, f"parent path in {location}")
    elif isinstance(value, dict):
        for key, child in value.items():
            reject_private_strings(key, f"{location}.<key>")
            reject_private_strings(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_private_strings(child, f"{location}[{index}]")


def validate_schema(root: Path, schema_rel: Path, value: Any, label: str) -> None:
    schema = read_json(root, schema_rel)
    try:
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        errors = sorted(validator.iter_errors(value), key=lambda error: list(map(str, error.path)))
    except Exception as exc:
        raise ValidationError(f"invalid {label} schema") from exc
    if errors:
        first = errors[0]
        path = ".".join(str(part) for part in first.path) or "$"
        raise ValidationError(f"{label} schema invalid at {path}: {first.message}")


def load_t10_authority(root: Path) -> dict[str, Any]:
    """Reuse T10's accepted validator and add the fixed Git-object binding."""
    scripts = ROOT / "scripts"
    if str(scripts) not in sys.path:
        sys.path.insert(0, str(scripts))
    try:
        import validate_t10_owner_decision as t10  # type: ignore
    except ImportError as exc:
        raise ValidationError(f"T10 authority validator unavailable: {exc}") from exc
    try:
        authority = t10.run(root)
    except t10.T10ValidationError as exc:
        raise ValidationError(f"T10 authority validation failed: {exc}") from exc
    require(authority.get("decision_hash_mode") == T10_DECISION_HASH_MODE, "T10 decision hash mode changed")
    require(authority.get("decision_sha256") == T10_DECISION_SHA256, "T10 portable decision SHA changed")
    require(authority.get("decision") == "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY", "T10 Owner decision is not quarantine-only")
    require(authority.get("artifact_quarantine_only") is True, "T10 quarantine-only choice changed")
    require(all(authority.get(key) is False for key in FORBIDDEN_AUTHORITY_FLAGS), "T10 execution authority opened")
    decision_path = root / T10_DECISION_REL
    require(decision_path.is_file(), "T10 Owner decision file missing")
    try:
        blob_sha = git_blob_sha1(decision_path.read_bytes())
    except OSError as exc:
        raise ValidationError("cannot read T10 Owner decision") from exc
    require(blob_sha == T10_DECISION_BLOB, "T10 Owner decision Git blob changed")
    return {
        "path": T10_DECISION_REL.as_posix(),
        "decision_hash_mode": authority["decision_hash_mode"],
        "portable_decision_sha256": authority["decision_sha256"],
        "git_blob_sha1": blob_sha,
        "decision": authority["decision"],
        "artifact_quarantine_only": authority["artifact_quarantine_only"],
        **{key: authority[key] for key in FORBIDDEN_AUTHORITY_FLAGS},
    }


def load_p1b_authority(root: Path) -> tuple[dict[str, Any], str]:
    """Reuse the frozen P1B validator; never normalize or repair its source tuples."""
    scripts = ROOT / "scripts"
    if str(scripts) not in sys.path:
        sys.path.insert(0, str(scripts))
    try:
        import validate_phase1_5_p1b_readiness as p1b  # type: ignore
    except ImportError as exc:
        raise ValidationError(f"SOURCE_CONTENT_DRIFT: P1B validator unavailable: {exc}") from exc
    try:
        manifest, manifest_sha = p1b.validate_manifest(root)
    except p1b.ValidationError as exc:
        raise ValidationError(f"SOURCE_CONTENT_DRIFT: P1B validator failed: {exc}") from exc
    require(manifest_sha == P1B_MANIFEST_SHA256, "SOURCE_CONTENT_DRIFT: P1B manifest SHA changed")
    authorization = manifest.get("authorization", {})
    require(authorization.get("owner_decision") == "NONE_TRACKED", "P1B frozen owner_decision field changed")
    require(authorization.get("legal_review_status") == "NOT_LEGAL_APPROVED", "P1B legal state changed")
    require(authorization.get("transport_status") == "REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED", "P1B transport state changed")
    require(all(authorization.get(key) is False for key in (
        "download_authorized", "runtime_authorized", "inference_authorized",
        "app_integration_authorized", "pipeline_integration_authorized",
        "private_media_authorized", "execution_authorized",
    )), "P1B execution authority opened")
    return manifest, manifest_sha


def load_preflight_manifest(root: Path) -> dict[str, Any]:
    manifest = read_json(root, MANIFEST_REL)
    require(isinstance(manifest, dict), "T11 preflight manifest must be an object")
    validate_schema(root, MANIFEST_SCHEMA_REL, manifest, "T11 preflight manifest")
    reject_private_strings(manifest, "T11 manifest")
    return manifest


def _validate_manifest_bindings(
    root: Path,
    manifest: dict[str, Any],
    t10: dict[str, Any],
    p1b_manifest: dict[str, Any],
    p1b_sha: str,
) -> None:
    require(manifest.get("accepted_t10_r3_base") == T11_BASE, "T11 accepted base changed")
    require(manifest.get("t10_owner_decision") == t10, "T11 T10 authority binding changed")
    p1b_binding = manifest.get("p1b_readiness", {})
    require(p1b_binding.get("path") == T10_MANIFEST_REL.as_posix(), "T11 P1B manifest path changed")
    require(p1b_binding.get("readiness_id") == P1B_READINESS_ID, "T11 P1B readiness ID changed")
    require(p1b_binding.get("readiness_manifest_sha256") == p1b_sha == P1B_MANIFEST_SHA256, "T11 P1B manifest identity changed")
    require(p1b_binding.get("manifest_authorization_owner_decision") == "NONE_TRACKED", "T11 rewrites T6 decision history")
    require(p1b_binding.get("legal_review_status") == "NOT_LEGAL_APPROVED", "T11 legal status promoted")
    require(p1b_binding.get("transport_status") == "REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED", "T11 transport status promoted")
    require(manifest.get("artifact") == ARTIFACT, "T11 exact artifact identity changed")
    require(manifest.get("proposed_transport_domains") == list(PROPOSED_DOMAINS), "T11 proposed domain allowlist changed")
    binding = manifest.get("p1b_source_binding", {})
    require(binding.get("authority_mode") == "FROZEN_P1B_CHECKOUT_BINDING_V1", "T11 P1B authority mode changed")
    require(binding.get("cross_platform_canonical") is False, "T11 overstates cross-platform P1B identity")
    require(binding.get("p1a_sources") == p1b_manifest.get("p1a_sources"), "T11 replaced frozen P1B source tuples")
    require(binding.get("drift_action") == "SOURCE_CONTENT_DRIFT_STOP", "T11 P1B drift action changed")
    diagnostics = binding.get("eol_diagnostics", {})
    require(diagnostics.get("classification") == "NON_AUTHORITY_DIAGNOSTIC_ONLY", "P1B EOL diagnostics promoted to authority")
    require(all(
        row.get("normalized_content_matches_git_blob_bytes") is True
        and row.get("representation_note") == "EOL_ONLY_CHECKOUT_REPRESENTATION_DIFFERENCE"
        and row.get("eol_normalized_sha256") == row.get("git_blob_sha256")
        for row in diagnostics.get("rows", [])
    ), "P1B EOL diagnostic reports source-content drift")
    require(diagnostics.get("rows"), "P1B EOL diagnostic rows missing")
    evidence = manifest.get("current_evidence_status", {})
    require(evidence == {
        "legal_review": "NOT_LEGAL_APPROVED",
        "transport_verification": "REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED",
        "fresh_metadata": "NOT_RUN",
        "quarantine_destination": "NOT_DECLARED",
        "future_acquisition_owner_decision": "NONE_TRACKED",
    }, "T11 current evidence status was promoted")
    require(manifest.get("blockers") == list(BLOCKERS), "T11 blocker set changed")
    authorization = manifest.get("authorization", {})
    require(authorization.get("acquisition_authorized") is False, "T11 acquisition authority opened")
    require(authorization.get("artifact_body_access_authorized") is False, "T11 artifact body access authority opened")
    require(authorization.get("network_access_authorized") is False, "T11 network authority opened")
    require(all(authorization.get(key) is False for key in T11_FORBIDDEN_AUTHORITY_FLAGS), "T11 downstream authority opened")
    state = manifest.get("state_machine", {})
    require(state.get("current_state") == "BLOCKED_LEGAL_REVIEW", "T11 current blocker changed")
    require(state.get("current_record_can_report_evidence_complete") is False, "T11 tracked evidence is marked complete")
    require(state.get("current_record_can_report_acquisition_authorized") is False, "T11 tracked acquisition is authorized")
    require(state.get("current_record_can_report_artifact_acquisition_ready") is False, "T11 tracked artifact is marked ready")
    safety = manifest.get("safety_boundary", {})
    require(all(value is False for value in safety.values()), "T11 safety boundary reports an external action")


def validate_canonical_status_projection(root: Path, manifest: dict[str, Any]) -> None:
    status = read_json(root, STATUS_REL)
    require(status.get("current_stage") == "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED", "T11 must remain nested under the existing T9 top-level stage")
    require(status.get("current_product_state") == "OFFLINE_ANDROID_PRODUCT_FLOW_VALIDATED", "T11 product state changed")
    require(status.get("next_authorized_stage") == "EXTERNAL_LEGAL_TRANSPORT_DESTINATION_EVIDENCE_OR_OWNER_DEFER", "T11 next authorized stage changed")
    manifest_sha = preflight_manifest_sha256(root)
    expected = {
        "stage_status": "ARTIFACT_ACQUISITION_PREFLIGHT_READY_EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED",
        "current_blocker": manifest["current_status"],
        "base": manifest["accepted_t10_r3_base"],
        "manifest": MANIFEST_REL.as_posix(),
        "manifest_id": manifest["manifest_id"],
        "manifest_hash_mode": MANIFEST_HASH_MODE,
        "manifest_sha256": manifest_sha,
        "t10_decision_hash_mode": manifest["t10_owner_decision"]["decision_hash_mode"],
        "t10_decision_sha256": manifest["t10_owner_decision"]["portable_decision_sha256"],
        "legal_review_status": manifest["current_evidence_status"]["legal_review"],
        "transport_status": manifest["current_evidence_status"]["transport_verification"],
        "fresh_metadata_status": manifest["current_evidence_status"]["fresh_metadata"],
        "quarantine_destination_status": manifest["current_evidence_status"]["quarantine_destination"],
        "owner_acquisition_decision": manifest["current_evidence_status"]["future_acquisition_owner_decision"],
        "artifact_acquisition_authorized": manifest["authorization"]["acquisition_authorized"],
        "artifact_body_access_authorized": manifest["authorization"]["artifact_body_access_authorized"],
        "network_access_authorized": manifest["authorization"]["network_access_authorized"],
        "runtime_authorized": manifest["authorization"]["runtime_authorized"],
        "inference_authorized": manifest["authorization"]["inference_authorized"],
        "app_integration_authorized": manifest["authorization"]["app_integration_authorized"],
        "pipeline_integration_authorized": manifest["authorization"]["pipeline_integration_authorized"],
        "private_media_authorized": manifest["authorization"]["private_media_authorized"],
    }
    require(status.get("t11_artifact_acquisition_preflight") == expected, "T11 canonical status projection drift")


def _validate_input(root: Path, name: str, value: dict[str, Any]) -> None:
    require(isinstance(value, dict), f"{name} input must be an object")
    validate_schema(root, INPUT_SCHEMAS[name], value, name)
    reject_private_strings(value, f"{name} input")


def validate_legal_review_input(root: Path, value: dict[str, Any], manifest: dict[str, Any]) -> None:
    _validate_input(root, "legal_review", value)
    require(value.get("artifact_binding") == manifest["artifact"], "legal review artifact binding changed")
    license_binding = value.get("license_evidence_binding", {})
    expected = next(
        (row for row in manifest["p1b_source_binding"]["p1a_sources"] if row["path"] == license_binding.get("path")),
        None,
    )
    require(expected is not None, "legal review license evidence is not P1B-bound")
    require(license_binding.get("p1b_frozen_checkout_bytes") == expected["bytes"], "legal review license byte binding changed")
    require(license_binding.get("p1b_frozen_checkout_sha256") == expected["sha256"], "legal review license hash binding changed")
    require(value.get("scope") == "MODEL_WEIGHT_QUARANTINE_ACQUISITION_ONLY", "legal review scope exceeds quarantine acquisition")
    require(value.get("model_card_metadata", {}).get("metadata_is_legal_approval") is False, "model-card metadata cannot act as legal approval")


def validate_transport_verification_input(root: Path, value: dict[str, Any], manifest: dict[str, Any]) -> None:
    _validate_input(root, "transport", value)
    require(value.get("artifact_binding") == manifest["artifact"], "transport artifact binding changed")
    allowed = set(manifest["proposed_transport_domains"])
    domains = [value.get("initial_domain"), *value.get("redirect_domains", []), value.get("final_domain")]
    require(all(isinstance(domain, str) and domain in allowed for domain in domains), "transport chain contains an unlisted domain")
    if value.get("artifact_body_accessed") is True:
        raise ValidationError("transport input reports out-of-scope artifact body access")


def validate_fresh_metadata_input(root: Path, value: dict[str, Any], manifest: dict[str, Any]) -> None:
    _validate_input(root, "fresh_metadata", value)
    expected = {**manifest["artifact"], "license_value": "apache-2.0"}
    require(value.get("expected_identity") == expected, "fresh metadata expected identity changed")
    observed = value.get("observed_identity", {})
    require(value.get("fallback_permitted") is False, "fresh metadata fallback is forbidden")
    if value.get("fresh_metadata_status") == "MATCHED_IMMUTABLE_IDENTITY":
        require(observed == expected, "ARTIFACT_IDENTITY_DRIFT")


def validate_quarantine_destination_input(root: Path, value: dict[str, Any], manifest: dict[str, Any]) -> None:
    _validate_input(root, "destination", value)
    require(value.get("available_bytes", 0) >= manifest["artifact"]["weight_bytes"], "quarantine destination capacity is insufficient")
    require(value.get("actual_path_tracked") is False, "actual quarantine path must remain untracked")


def validate_future_acquisition_decision(
    root: Path,
    value: dict[str, Any],
    manifest: dict[str, Any],
    t10: dict[str, Any],
    inputs: dict[str, dict[str, Any]],
) -> None:
    _validate_input(root, "owner_decision", value)
    expected_t10 = {key: t10[key] for key in ("path", "decision_hash_mode", "portable_decision_sha256", "git_blob_sha1")}
    require(value.get("t10_authority_binding") == expected_t10, "future Owner decision T10 binding changed")
    expected_t11 = {
        "path": MANIFEST_REL.as_posix(),
        "manifest_id": manifest["manifest_id"],
        "manifest_hash_mode": MANIFEST_HASH_MODE,
        "manifest_sha256": preflight_manifest_sha256(root),
    }
    require(value.get("t11_manifest_binding") == expected_t11, "future Owner decision T11 manifest binding changed")
    require(value.get("artifact_binding") == manifest["artifact"], "future Owner decision artifact binding changed")
    binding = value.get("evidence_binding", {})
    expected_evidence = {
        "legal_review_input_sha256": canonical_input_sha256(inputs["legal_review"]),
        "transport_verification_input_sha256": canonical_input_sha256(inputs["transport"]),
        "fresh_metadata_input_sha256": canonical_input_sha256(inputs["fresh_metadata"]),
        "destination_id": inputs["destination"]["destination_id"],
    }
    require(binding == expected_evidence, "future Owner decision evidence binding changed")
    require(value.get("approved_domains") == list(manifest["proposed_transport_domains"]), "future Owner decision domain approval changed")


def _result(
    root: Path,
    manifest: dict[str, Any],
    state: str,
    blockers: list[str],
    *,
    acquisition_authorized: bool | None = None,
    artifact_body_access_authorized: bool | None = None,
    hypothetical_inputs_only: bool = False,
    hypothetical_evidence_status: dict[str, str] | None = None,
) -> dict[str, Any]:
    t10 = manifest["t10_owner_decision"]
    p1b = manifest["p1b_readiness"]
    authorization = manifest["authorization"]
    tracked_evidence_status = manifest["current_evidence_status"]
    effective_evidence_status = {**tracked_evidence_status, **(hypothetical_evidence_status or {})}
    return {
        "stage_status": "ARTIFACT_ACQUISITION_PREFLIGHT_READY_EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED",
        "state": state,
        "blockers": blockers,
        "base": T11_BASE,
        "manifest_id": manifest["manifest_id"],
        "t11_manifest_hash_mode": MANIFEST_HASH_MODE,
        "t11_manifest_sha256": preflight_manifest_sha256(root),
        "t10_decision_hash_mode": t10["decision_hash_mode"],
        "t10_decision_sha256": t10["portable_decision_sha256"],
        "p1b_manifest_sha256": p1b["readiness_manifest_sha256"],
        "legal_review_status": effective_evidence_status["legal_review"],
        "transport_status": effective_evidence_status["transport_verification"],
        "fresh_metadata_status": effective_evidence_status["fresh_metadata"],
        "quarantine_destination_status": effective_evidence_status["quarantine_destination"],
        "owner_acquisition_decision": effective_evidence_status["future_acquisition_owner_decision"],
        "tracked_current_evidence_status": tracked_evidence_status,
        "tracked_current_blocker": manifest["current_status"],
        "evidence_status_source": "HYPOTHETICAL_UNIT_INPUTS" if hypothetical_inputs_only else "TRACKED_MANIFEST",
        "acquisition_authorized": authorization["acquisition_authorized"] if acquisition_authorized is None else acquisition_authorized,
        "artifact_body_access_authorized": authorization["artifact_body_access_authorized"] if artifact_body_access_authorized is None else artifact_body_access_authorized,
        "network_access_authorized": authorization["network_access_authorized"],
        "runtime_authorized": authorization["runtime_authorized"],
        "inference_authorized": authorization["inference_authorized"],
        "app_integration_authorized": authorization["app_integration_authorized"],
        "pipeline_integration_authorized": authorization["pipeline_integration_authorized"],
        "private_media_authorized": authorization["private_media_authorized"],
        "p1b_source_binding": manifest["p1b_source_binding"],
        "hypothetical_inputs_only": hypothetical_inputs_only or acquisition_authorized is not None or artifact_body_access_authorized is not None,
        "external_actions": [],
    }


def evaluate_preflight(
    root: Path,
    *,
    legal_review: dict[str, Any] | None = None,
    transport: dict[str, Any] | None = None,
    fresh_metadata: dict[str, Any] | None = None,
    destination: dict[str, Any] | None = None,
    owner_decision: dict[str, Any] | None = None,
) -> dict[str, Any]:
    manifest = load_preflight_manifest(root)
    t10 = load_t10_authority(root)
    p1b_manifest, p1b_sha = load_p1b_authority(root)
    _validate_manifest_bindings(root, manifest, t10, p1b_manifest, p1b_sha)
    validate_canonical_status_projection(root, manifest)
    validate_canonical_status_projection(root, manifest)

    inputs = {
        "legal_review": legal_review,
        "transport": transport,
        "fresh_metadata": fresh_metadata,
        "destination": destination,
    }
    hypothetical_inputs_only = any(value is not None for value in inputs.values()) or owner_decision is not None
    validators = {
        "legal_review": validate_legal_review_input,
        "transport": validate_transport_verification_input,
        "fresh_metadata": validate_fresh_metadata_input,
        "destination": validate_quarantine_destination_input,
    }
    for name, value in inputs.items():
        if value is not None:
            validators[name](root, value, manifest)

    legal_disposition = legal_review.get("disposition") if legal_review is not None else None
    transport_status = transport.get("transport_status") if transport is not None else None
    metadata_status = fresh_metadata.get("fresh_metadata_status") if fresh_metadata is not None else None
    owner_choice = owner_decision.get("decision") if owner_decision is not None else None
    if owner_decision is not None and (
        legal_disposition != "APPROVED_FOR_QUARANTINE_ACQUISITION"
        or transport_status != "VERIFIED_ALLOWED_DOMAIN_CHAIN"
        or metadata_status != "MATCHED_IMMUTABLE_IDENTITY"
        or destination is None
    ):
        raise ValidationError("future Owner decision cannot be evaluated before all evidence gates pass")

    hypothetical_evidence_status: dict[str, str] = {}
    if legal_disposition is not None:
        hypothetical_evidence_status["legal_review"] = legal_disposition
    if transport_status is not None:
        hypothetical_evidence_status["transport_verification"] = transport_status
    if metadata_status is not None:
        hypothetical_evidence_status["fresh_metadata"] = metadata_status
    if destination is not None:
        hypothetical_evidence_status["quarantine_destination"] = "DECLARED"
    if owner_choice is not None:
        hypothetical_evidence_status["future_acquisition_owner_decision"] = owner_choice

    hypothetical_blockers: list[str] = []
    if hypothetical_inputs_only:
        if legal_disposition != "REJECTED" and legal_disposition != "APPROVED_FOR_QUARANTINE_ACQUISITION":
            hypothetical_blockers.append(BLOCKERS[0])
        if legal_disposition != "REJECTED" and (transport_status != "VERIFIED_ALLOWED_DOMAIN_CHAIN" or transport is None or transport.get("identity_match") is not True):
            hypothetical_blockers.append(BLOCKERS[1])
        if legal_disposition != "REJECTED" and metadata_status != "MATCHED_IMMUTABLE_IDENTITY":
            hypothetical_blockers.append(BLOCKERS[2])
        if legal_disposition != "REJECTED" and destination is None:
            hypothetical_blockers.append(BLOCKERS[3])
        if legal_disposition != "REJECTED" and owner_decision is None:
            hypothetical_blockers.append(BLOCKERS[4])
    else:
        hypothetical_blockers = list(BLOCKERS)

    def make_result(state: str, blockers: list[str], **kwargs: Any) -> dict[str, Any]:
        return _result(
            root,
            manifest,
            state,
            blockers,
            hypothetical_inputs_only=hypothetical_inputs_only,
            hypothetical_evidence_status=hypothetical_evidence_status,
            **kwargs,
        )

    result = make_result("BLOCKED_LEGAL_REVIEW", hypothetical_blockers)
    if legal_review is None:
        return result
    disposition = legal_disposition
    if disposition == "REJECTED":
        return make_result("ARTIFACT_ACQUISITION_REJECTED_LEGAL", [])
    if disposition != "APPROVED_FOR_QUARANTINE_ACQUISITION":
        return result

    if transport is None:
        return make_result("BLOCKED_TRANSPORT_VERIFICATION", hypothetical_blockers)
    if transport["transport_status"] in {"IDENTITY_DRIFT", "REJECTED_UNLISTED_DOMAIN", "REJECTED_ARTIFACT_BODY_ACCESSED"}:
        raise ValidationError("TRANSPORT_OR_ARTIFACT_IDENTITY_DRIFT_STOP")
    if transport["transport_status"] != "VERIFIED_ALLOWED_DOMAIN_CHAIN" or transport["identity_match"] is not True:
        return make_result("BLOCKED_TRANSPORT_VERIFICATION", hypothetical_blockers)

    if fresh_metadata is None:
        return make_result("BLOCKED_FRESH_METADATA", hypothetical_blockers)
    if fresh_metadata["fresh_metadata_status"] == "IDENTITY_DRIFT":
        raise ValidationError("ARTIFACT_IDENTITY_DRIFT_STOP")
    if fresh_metadata["fresh_metadata_status"] != "MATCHED_IMMUTABLE_IDENTITY":
        return make_result("BLOCKED_FRESH_METADATA", hypothetical_blockers)

    if destination is None:
        return make_result("BLOCKED_QUARANTINE_DESTINATION", hypothetical_blockers)
    if owner_decision is None:
        return make_result("READY_FOR_OWNER_ACQUISITION_DECISION", [BLOCKERS[4]])

    validate_future_acquisition_decision(root, owner_decision, manifest, t10, {
        "legal_review": legal_review,
        "transport": transport,
        "fresh_metadata": fresh_metadata,
        "destination": destination,
    })
    decision = owner_decision["decision"]
    if decision == "DEFER":
        return make_result("OWNER_DEFERRED", [])
    if decision == "REJECT_ACQUISITION":
        return make_result("ARTIFACT_ACQUISITION_REJECTED_OWNER", [])
    return make_result(
        "READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN",
        [],
        acquisition_authorized=True,
        artifact_body_access_authorized=True,
    )


def run(root: Path) -> dict[str, Any]:
    return evaluate_preflight(root)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--require-artifact-access", action="store_true")
    parser.add_argument("--require-network-probe", action="store_true")
    parser.add_argument("--require-runtime", action="store_true")
    parser.add_argument("--require-private-media", action="store_true")
    parser.add_argument("--require-pipeline", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = run(args.root.resolve())
    except ValidationError as exc:
        print(f"T11_PREFLIGHT_FAILED: {exc}")
        return 1
    probes = (
        (args.require_artifact_access, "BLOCKED_ARTIFACT_ACQUISITION_AUTHORIZATION_REQUIRED"),
        (args.require_network_probe, "BLOCKED_TRANSPORT_VERIFICATION_AUTHORITY_REQUIRED"),
        (args.require_runtime, "BLOCKED_RUNTIME_AUTHORIZATION_REQUIRED"),
        (args.require_private_media, "BLOCKED_PRIVATE_MEDIA_AUTHORIZATION_REQUIRED"),
        (args.require_pipeline, "BLOCKED_PIPELINE_AUTHORIZATION_REQUIRED"),
    )
    for requested, message in probes:
        if requested:
            print(message)
            return 2
    print(result["state"])
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
