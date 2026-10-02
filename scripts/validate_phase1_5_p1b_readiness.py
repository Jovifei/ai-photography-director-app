#!/usr/bin/env python3
"""Validate the metadata-only P1B readiness packet without external access."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_REL = Path("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
T5_HEAD = "0b04abdc57a3fa332689cfe664f9bcb997160e5e"
PRODUCT_SOURCE = "a69ede68f3e54e5ab006dbfd7a65c33040590f93"
T3_BASE = "14a56f49e1220eb8139bf7280c747124118fdb21"
MODEL_ID = "Qwen/Qwen3-VL-2B-Instruct"
REVISION = "89644892e4d85e24eaac8bacfd4f463576704203"
WEIGHT_NAME = "model.safetensors"
WEIGHT_BYTES = 4255140312
WEIGHT_SHA = "7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0"
REPOSITORY_FILE_COUNT = 12
REPOSITORY_TOTAL_BYTES = 4266648961
CORPUS_REL = "docs/phase1_5/p1a/public_corpus_manifest.v3.json"
CONTRACT_RELS = {
    "reference_bundle": "docs/reference/reference_bundle.v1.schema.json",
    "provider_envelope": "docs/reference/provider_analysis_envelope.v1.schema.json",
    "error_policy": "docs/phase1_5/error_policy.v1.json",
}
SOURCE_BINDINGS = [
    ("docs/phase1_5/p1a/primary_artifact_authorization_draft.v1.json", 2171, "7fcab9f1c351f5473a8bde7b2e23185d2888610c27e1fb6d6c4a0e0ab718d5b5"),
    ("docs/phase1_5/p1a/qwen_candidate_inventory.v1.json", 4192, "ba432763ee70321eaac4e0314588701f25a833471030d33fee9dd6ee404bc9a1"),
    ("docs/phase1_5/p1a/qwen_weight_license_evidence.v1.json", 2485, "08ac3a64b9bf4e189c8955bd8106b37a8deb20d8d44f9be9d131c7392f95adac"),
    (CORPUS_REL, 126442, "9da0ccdeaa367c1c15e314fb2c64a8e487ddf028c0ec532d30c097c3be62efa6"),
    (CONTRACT_RELS["reference_bundle"], 3292, "387e1f4749db3dbe34a5aab0e9618a05f1c4349eb02c567ebfc180a435301700"),
    (CONTRACT_RELS["provider_envelope"], 8488, "014f69f0eda46b806b7632e6440d85be9a5ccb78461a2a78418fb5c4ac78f194"),
    (CONTRACT_RELS["error_policy"], 5599, "821466ba68385f3c9628d96dbfc5b27e1d5628ef554b879e086aacc6b42ea7f9"),
]
FALSE_AUTH = (
    "download_authorized", "runtime_authorized", "inference_authorized",
    "app_integration_authorized", "pipeline_integration_authorized",
    "private_media_authorized", "execution_authorized",
)
PRIVATE_PATH_RE = re.compile(r"(?:^[A-Za-z]:[\\/]|^\\\\|(?:^|/)(?:Users|home|mnt|private|secrets?)(?:/|$)|^file://)", re.IGNORECASE)


class ValidationError(ValueError):
    """A bounded, expected packet validation failure."""


def read_json(root: Path, relative: str | Path) -> Any:
    path = root / Path(relative)
    if not path.is_file():
        raise ValidationError(f"missing file: {Path(relative).as_posix()}")
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValidationError(f"invalid JSON: {Path(relative).as_posix()}") from exc


def file_digest(root: Path, relative: str) -> tuple[int, str]:
    path = root / Path(relative)
    if not path.is_file():
        raise ValidationError(f"missing bound source: {relative}")
    data = path.read_bytes()
    return len(data), hashlib.sha256(data).hexdigest()


def fail_if_private_string(value: Any, location: str) -> None:
    if isinstance(value, str):
        if PRIVATE_PATH_RE.search(value) or ".." in Path(value).parts:
            raise ValidationError(f"private or parent path in {location}")
        return
    if isinstance(value, dict):
        for key, child in value.items():
            fail_if_private_string(key, f"{location}.<key>")
            fail_if_private_string(child, f"{location}.{key}")
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            fail_if_private_string(child, f"{location}[{index}]")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def validate_source_bindings(root: Path, manifest: dict[str, Any]) -> None:
    actual_sources = manifest.get("p1a_sources")
    require(isinstance(actual_sources, list), "p1a_sources must be a list")
    expected = [{"path": path, "bytes": size, "sha256": digest} for path, size, digest in SOURCE_BINDINGS]
    require(actual_sources == expected, "P1A source bindings differ from the frozen packet")
    for relative, expected_bytes, expected_sha in SOURCE_BINDINGS:
        actual_bytes, actual_sha = file_digest(root, relative)
        require(actual_bytes == expected_bytes, f"byte count mismatch: {relative}")
        require(actual_sha == expected_sha, f"SHA-256 mismatch: {relative}")


def validate_p1a_records(root: Path) -> None:
    primary = read_json(root, "docs/phase1_5/p1a/primary_artifact_authorization_draft.v1.json")
    inventory = read_json(root, "docs/phase1_5/p1a/qwen_candidate_inventory.v1.json")
    license_record = read_json(root, "docs/phase1_5/p1a/qwen_weight_license_evidence.v1.json")
    corpus = read_json(root, CORPUS_REL)
    require(primary.get("status") == "READY_FOR_OWNER_DOWNLOAD_DECISION", "P1A primary status changed")
    for key in ("download_authorized", "runtime_authorized", "inference_authorized", "app_integration_authorized"):
        require(primary.get(key) is False, f"P1A authorization is not false: {key}")
    require(primary.get("model_id") == MODEL_ID, "P1A model identity changed")
    require(primary.get("immutable_revision") == REVISION, "P1A revision changed")
    require(primary.get("expected_weight") == {"name": WEIGHT_NAME, "bytes": WEIGHT_BYTES, "lfs_oid_sha256": WEIGHT_SHA}, "P1A weight binding changed")
    require(primary.get("source_domains") == ["huggingface.co", "us.aws.cdn.hf.co"], "P1A source domains changed")
    require(primary.get("weight_license_evidence", {}).get("legal_review_status") == "NOT_LEGAL_APPROVED", "P1A legal status promoted")
    require(primary.get("observed_transport", {}).get("artifact_redirect_state") == "REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED", "P1A transport state promoted")
    require(inventory.get("artifact_bytes_downloaded") is False, "inventory says artifact bytes were downloaded")
    require(inventory.get("model_weights_downloaded") is False, "inventory says model weights were downloaded")
    inventory_primary = inventory.get("primary", {})
    require(inventory_primary.get("model_id") == MODEL_ID, "inventory model identity changed")
    require(inventory_primary.get("immutable_revision") == REVISION, "inventory revision changed")
    require(inventory_primary.get("repository_file_count") == REPOSITORY_FILE_COUNT, "inventory file count changed")
    require(inventory_primary.get("repository_total_bytes") == REPOSITORY_TOTAL_BYTES, "inventory total bytes changed")
    require(inventory_primary.get("weight_artifacts") == [{"name": WEIGHT_NAME, "bytes": WEIGHT_BYTES, "lfs_oid_sha256": WEIGHT_SHA}], "inventory weight binding changed")
    require(license_record.get("evidence_status") == "WEIGHT_LICENSE_EVIDENCE_READY_FOR_INDEPENDENT_REVIEW", "license evidence status changed")
    require(license_record.get("model_id") == MODEL_ID, "license model identity changed")
    require(license_record.get("immutable_revision") == REVISION, "license revision changed")
    require(license_record.get("legal_review_status") == "NOT_LEGAL_APPROVED", "license legal status promoted")
    for key in ("download_authorized", "runtime_authorized", "inference_authorized", "app_integration_authorized"):
        require(license_record.get(key) is False, f"license authorization is not false: {key}")
    require(license_record.get("model_artifact") == {"path": WEIGHT_NAME, "bytes": WEIGHT_BYTES, "lfs_sha256": WEIGHT_SHA, "repository_file_count": REPOSITORY_FILE_COUNT, "repository_total_bytes": REPOSITORY_TOTAL_BYTES}, "license artifact binding changed")
    require(license_record.get("external_evidence", {}).get("model_body_downloaded") is False, "license evidence says model body was downloaded")
    require(corpus.get("status") == "CORPUS_R3_VALIDATED", "corpus status changed")
    require(corpus.get("approved_count") == 21, "corpus approved count changed")
    require(corpus.get("quarantine_count") == 8, "corpus quarantine count changed")
    require(corpus.get("privacy_policy", {}).get("owner_media") == "prohibited", "owner media policy changed")


def validate_owner_decision(decision: dict[str, Any], manifest: dict[str, Any], manifest_sha: str) -> None:
    """Validate a hypothetical future decision without persisting or executing it."""
    required = {"schema_version", "decision_id", "readiness_id", "decision", "recorded_at", "scope"}
    require(required <= set(decision), "owner decision misses required fields")
    require(set(decision) <= required | {"artifact_binding"}, "owner decision has unknown fields")
    require(decision.get("schema_version") == "1.0.0", "owner decision schema version changed")
    require(isinstance(decision.get("decision_id"), str) and decision["decision_id"], "owner decision id missing")
    require(isinstance(decision.get("recorded_at"), str) and decision["recorded_at"], "owner decision timestamp missing")
    require(decision.get("decision") in {"DEFER", "REJECT_PRIMARY", "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY"}, "owner decision is unsupported")
    require(decision.get("readiness_id") == manifest.get("readiness_id"), "owner decision readiness identity changed")
    scope = decision.get("scope")
    required_scope = {"artifact_quarantine_only", "download_authorized", "runtime_authorized", "inference_authorized", "app_integration_authorized", "pipeline_integration_authorized", "private_media_authorized"}
    require(isinstance(scope, dict) and set(scope) == required_scope, "owner decision scope shape changed")
    for key in ("download_authorized", "runtime_authorized", "inference_authorized", "app_integration_authorized", "pipeline_integration_authorized", "private_media_authorized"):
        require(scope.get(key) is False, f"owner decision attempts forbidden authorization: {key}")
    quarantine = decision.get("decision") == "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY"
    require(scope.get("artifact_quarantine_only") is quarantine, "owner decision quarantine scope disagrees")
    if quarantine:
        binding = decision.get("artifact_binding")
        require(isinstance(binding, dict), "quarantine decision lacks exact artifact binding")
        require(binding == {"model_id": manifest["primary_artifact"]["model_id"], "immutable_revision": manifest["primary_artifact"]["immutable_revision"], "weight_filename": manifest["primary_artifact"]["weight_filename"], "weight_bytes": manifest["primary_artifact"]["weight_bytes"], "weight_lfs_sha256": manifest["primary_artifact"]["weight_lfs_sha256"], "readiness_manifest_sha256": manifest_sha}, "quarantine decision artifact binding differs")
    else:
        require("artifact_binding" not in decision, "non-quarantine decision contains an artifact binding")


def validate_manifest(root: Path) -> tuple[dict[str, Any], str]:
    manifest = read_json(root, MANIFEST_REL)
    require(isinstance(manifest, dict), "readiness manifest must be an object")
    require(manifest.get("schema_version") == "1.0.0", "readiness schema version changed")
    require(manifest.get("status") == "READY_FOR_OWNER_DECISION", "readiness status is not owner-decision-only")
    require(manifest.get("t5_delivery_head") == T5_HEAD, "T5 base identity changed")
    require(manifest.get("reviewed_product_source") == PRODUCT_SOURCE, "reviewed product source changed")
    require(manifest.get("t3_base") == T3_BASE, "T3 base identity changed")
    require(isinstance(manifest.get("readiness_id"), str) and manifest["readiness_id"], "readiness_id missing")
    validate_source_bindings(root, manifest)
    validate_p1a_records(root)
    require(manifest.get("primary_artifact") == {"model_id": MODEL_ID, "immutable_revision": REVISION, "weight_filename": WEIGHT_NAME, "weight_bytes": WEIGHT_BYTES, "weight_lfs_sha256": WEIGHT_SHA, "repository_file_count": REPOSITORY_FILE_COUNT, "repository_total_bytes": REPOSITORY_TOTAL_BYTES, "source_domains": ["huggingface.co", "us.aws.cdn.hf.co"]}, "readiness primary artifact binding changed")
    require(manifest.get("corpus") == {"manifest": CORPUS_REL, "approved_count": 21, "quarantine_count": 8, "owner_media": "prohibited", "status": "CORPUS_R3_VALIDATED"}, "readiness corpus binding changed")
    require(manifest.get("contract_identities") == CONTRACT_RELS, "contract identity binding changed")
    authorization = manifest.get("authorization")
    require(isinstance(authorization, dict), "readiness authorization missing")
    for key in FALSE_AUTH:
        require(authorization.get(key) is False, f"readiness authorization is not false: {key}")
    require(authorization.get("legal_review_status") == "NOT_LEGAL_APPROVED", "readiness legal status promoted")
    require(authorization.get("transport_status") == "REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED", "readiness transport status promoted")
    require(authorization.get("owner_decision") == "NONE_TRACKED", "T6 contains an Owner decision")
    require(isinstance(manifest.get("stop_conditions"), list) and manifest["stop_conditions"], "stop conditions missing")
    fail_if_private_string(manifest, "manifest")
    manifest_bytes, manifest_sha = file_digest(root, MANIFEST_REL.as_posix())
    require(manifest_bytes > 0 and len(manifest_sha) == 64, "readiness manifest digest unavailable")
    return manifest, manifest_sha


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--require-execution-authority", action="store_true", help="probe the fail-closed execution boundary")
    args = parser.parse_args(argv)
    try:
        validate_manifest(args.root.resolve())
    except ValidationError as exc:
        print(f"P1B_VALIDATION_FAILED: {exc}")
        return 1
    if args.require_execution_authority:
        print("BLOCKED_OWNER_AUTHORIZATION_REQUIRED")
        return 2
    print("READY_FOR_OWNER_DECISION")
    return 0


if __name__ == "__main__":
    sys.exit(main())
