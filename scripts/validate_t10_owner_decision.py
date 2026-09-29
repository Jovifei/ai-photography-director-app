#!/usr/bin/env python3
"""Validate the explicit P1B Owner decision without opening external authority."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
T9_BASE = "dd5d297ffa26adc38470bfa5737ac82b94eaa2ec"
DECISION_REL = Path("docs/phase1_5/p1b/p1b_owner_decision.v1.json")
DECISION_HASH_MODE = "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1"
CANONICAL_DECISION_SHA256 = "45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9"
DECISION_SCHEMA_REL = Path("docs/phase1_5/p1b/p1b_owner_decision.v1.schema.json")
MANIFEST_REL = Path("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
STATUS_REL = Path("docs/current_program_status.v1.json")
DECISION_STATUS = {
    "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY": "P1B_ARTIFACT_QUARANTINE_SCOPE_APPROVED_ARTIFACT_ACQUISITION_NOT_YET_AUTHORIZED",
    "DEFER": "P1B_OWNER_DECISION_DEFERRED_NO_EXECUTION_AUTHORIZED",
    "REJECT_PRIMARY": "P1B_PRIMARY_REJECTED_NO_ALTERNATIVE_PROVIDER_AUTHORIZED",
}
FORBIDDEN_SCOPE = (
    "download_authorized",
    "runtime_authorized",
    "inference_authorized",
    "app_integration_authorized",
    "pipeline_integration_authorized",
    "private_media_authorized",
)


class T10ValidationError(ValueError):
    """Expected fail-closed T10 validation error."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise T10ValidationError(message)


def read_json(root: Path, relative: str | Path) -> Any:
    path = root / Path(relative)
    if not path.is_file():
        raise T10ValidationError(f"missing file: {Path(relative).as_posix()}")
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise T10ValidationError(f"invalid JSON: {Path(relative).as_posix()}") from exc


def decision_sha256(root: Path) -> str:
    path = root / DECISION_REL
    require(path.is_file(), f"missing digest file: {DECISION_REL.as_posix()}")
    try:
        text = path.read_bytes().decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise T10ValidationError("Owner decision identity is not strict UTF-8") from exc
    normalized = text.replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")
    return hashlib.sha256(normalized).hexdigest()


def load_current_authority(root: Path) -> dict[str, Any]:
    status = read_json(root, STATUS_REL)
    require(status.get("current_stage") == "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED", "T9 authority changed")
    t9 = status.get("t9_promotion_gate", {})
    require(t9.get("status") == "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED", "T9 promotion authority changed")
    require(t9.get("base") == "acf43a8d734675ec23da534472cade129b7b1068", "T9 base binding changed")
    require(t9.get("provider_ready_promotion_authorized") is False, "T9 Provider promotion opened")
    require(t9.get("human_review_authorized") is False, "T9 human review opened")
    require(t9.get("thresholds_approved") is False, "T9 thresholds opened")
    return status


def load_p1b_manifest(root: Path) -> tuple[dict[str, Any], str]:
    try:
        sys.path.insert(0, str(ROOT / "scripts"))
        import validate_phase1_5_p1b_readiness as p1b  # type: ignore

        manifest, manifest_sha = p1b.validate_manifest(root)
    except (ImportError, p1b.ValidationError) as exc:  # type: ignore[name-defined]
        raise T10ValidationError(f"P1B manifest validation failed: {exc}") from exc
    require(manifest.get("status") == "READY_FOR_OWNER_DECISION", "P1B is no longer owner-decision-only")
    require(manifest.get("readiness_id") == "phase1-5-p1b-qwen3-vl-2b-authorization-readiness-20260927", "P1B readiness identity changed")
    return manifest, manifest_sha


def load_tracked_owner_decision(root: Path, manifest: dict[str, Any], manifest_sha: str) -> tuple[dict[str, Any], str]:
    decision = read_json(root, DECISION_REL)
    schema = read_json(root, DECISION_SCHEMA_REL)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    errors = list(validator.iter_errors(decision))
    require(not errors, f"Owner decision schema invalid: {errors[0].message if errors else 'unknown'}")
    try:
        sys.path.insert(0, str(ROOT / "scripts"))
        import validate_phase1_5_p1b_readiness as p1b  # type: ignore

        p1b.validate_owner_decision(decision, manifest, manifest_sha)
    except (ImportError, p1b.ValidationError) as exc:  # type: ignore[name-defined]
        raise T10ValidationError(f"Owner decision contract invalid: {exc}") from exc
    return decision, decision_sha256(root)


def validate_canonical_status_projection(status: dict[str, Any], decision: dict[str, Any], decision_sha: str, manifest_sha: str) -> None:
    projection = status.get("t10_owner_decision")
    require(isinstance(projection, dict), "canonical T10 decision projection missing")
    require(projection.get("base") == T9_BASE, "T10 base projection changed")
    require(projection.get("decision_file") == DECISION_REL.as_posix(), "T10 decision path projection changed")
    require(projection.get("decision_hash_mode") == DECISION_HASH_MODE, "T10 decision hash mode projection changed")
    require(projection.get("decision_sha256") == decision_sha, "T10 decision SHA projection changed")
    require(decision_sha == CANONICAL_DECISION_SHA256, "T10 canonical decision SHA changed")
    require(projection.get("readiness_manifest_sha256") == manifest_sha, "T10 manifest SHA projection changed")
    require(projection.get("decision") == decision.get("decision"), "T10 decision projection changed")
    require(projection.get("status") == DECISION_STATUS[decision["decision"]], "T10 authority status projection changed")
    for key in FORBIDDEN_SCOPE:
        require(projection.get(key) is False, f"T10 authority projection opened: {key}")
    require(projection.get("artifact_quarantine_only") is (decision["decision"] == "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY"), "T10 quarantine projection changed")


def derive_t10_authority_state(decision: dict[str, Any], decision_sha: str, manifest_sha: str) -> dict[str, Any]:
    branch = decision["decision"]
    return {
        "status": DECISION_STATUS[branch],
        "base": T9_BASE,
        "decision": branch,
        "decision_file": DECISION_REL.as_posix(),
        "decision_hash_mode": DECISION_HASH_MODE,
        "decision_sha256": decision_sha,
        "readiness_manifest_sha256": manifest_sha,
        "artifact_quarantine_only": branch == "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY",
        **{key: False for key in FORBIDDEN_SCOPE},
    }


def run(root: Path) -> dict[str, Any]:
    status = load_current_authority(root)
    manifest, manifest_sha = load_p1b_manifest(root)
    decision, decision_sha = load_tracked_owner_decision(root, manifest, manifest_sha)
    validate_canonical_status_projection(status, decision, decision_sha, manifest_sha)
    result = derive_t10_authority_state(decision, decision_sha, manifest_sha)
    result["readiness_id"] = manifest["readiness_id"]
    result["external_actions"] = []
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--require-runtime-authority", action="store_true")
    parser.add_argument("--require-real-ready-promotion", action="store_true")
    parser.add_argument("--require-human-review", action="store_true")
    parser.add_argument("--require-pipeline", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = run(args.root.resolve())
    except T10ValidationError as exc:
        print(f"T10_VALIDATION_FAILED: {exc}")
        return 1
    probes = (
        (args.require_runtime_authority, "BLOCKED_RUNTIME_AUTHORIZATION_REQUIRED"),
        (args.require_real_ready_promotion, "BLOCKED_REAL_READY_PROMOTION_AUTHORIZATION_REQUIRED"),
        (args.require_human_review, "BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED"),
        (args.require_pipeline, "BLOCKED_PIPELINE_AUTHORIZATION_REQUIRED"),
    )
    for requested, message in probes:
        if requested:
            print(message)
            return 2
    print(result["status"])
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
