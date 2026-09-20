"""Build a deterministic PKB1 fixture for internal handoff tests only.

This module is not a Pipeline exporter and grants no review, import, release, or
distribution authority. Production bundles must still be emitted by the real Pipeline.
"""
from __future__ import annotations

from typing import Any

from p23c_bundle_contract import PHOTO_FIELDS, canonical_payload, sha256

CURATED_FORMAT = "photoai.curated-editorial-input.v1"
CURATED_STATUS = "HUMAN_REVIEW_APPROVED_NOT_APP_IMPORTABLE"
TEST_PRODUCER = "synthetic_internal_handoff_test"
TEST_RELEASE = "not_for_release"


def build_test_bundle(curated: dict[str, Any]) -> dict[str, Any]:
    """Map a validated simulated handoff to a clearly synthetic PKB1 fixture."""
    if curated.get("format") != CURATED_FORMAT or curated.get("status") != CURATED_STATUS:
        raise ValueError("CURATED_HANDOFF_INVALID")
    if curated.get("app_import_authorized") is not False or curated.get("public_distribution_authorized") is not False:
        raise ValueError("CURATED_AUTHORITY_INVALID")
    entries = curated.get("entries")
    if not isinstance(entries, list) or len(entries) != 20:
        raise ValueError("CURATED_ENTRY_COUNT_INVALID")
    references: list[dict[str, Any]] = []
    seen: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != {
            "reference_id", "source_evidence_id", "review_evidence_id", "photography"
        }:
            raise ValueError("CURATED_ENTRY_SCHEMA_INVALID")
        reference_id = entry["reference_id"]
        photography = entry["photography"]
        if not isinstance(reference_id, str) or reference_id in seen:
            raise ValueError("CURATED_REFERENCE_INVALID")
        if not isinstance(photography, dict) or set(photography) != set(PHOTO_FIELDS):
            raise ValueError("CURATED_PHOTOGRAPHY_SCHEMA_INVALID")
        seen.add(reference_id)
        references.append({"reference_id": reference_id, "photography": dict(photography)})
    bundle = {
        "bundle_id": "p25s_internal_handoff_fixture",
        "contract_version": "1.0",
        "integrity": {"algorithm": "SHA-256", "payload_sha256": "0" * 64},
        "references": references,
        "source": {"origin": "PIPELINE", "producer_id": TEST_PRODUCER, "release_id": TEST_RELEASE},
    }
    bundle["integrity"]["payload_sha256"] = sha256(canonical_payload(bundle))
    return bundle
