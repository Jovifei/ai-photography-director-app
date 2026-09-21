"""Build a deterministic PKB1 fixture for internal handoff tests only.

This is not a Pipeline exporter or an approval verifier. Declarations and hashes
are checked for shape only; this adapter never certifies their real-world evidence.
The resulting bundle always carries synthetic producer/release identifiers.
"""
from __future__ import annotations

from typing import Any

from p23c_bundle_contract import (
    PHOTO_FIELDS, SHA256, canonical_payload, encoded_json, opaque, sha256,
    validate_bundle,
)

CURATED_FORMAT = "photoai.curated-editorial-input.v1"
CURATED_STATUS = "HUMAN_REVIEW_APPROVED_NOT_APP_IMPORTABLE"
TEST_PRODUCER = "synthetic_internal_handoff_test"
TEST_RELEASE = "not_for_release"
CURATED_KEYS = {
    "format", "status", "corpus_id", "corpus_sha256", "review_sha256",
    "rights_basis", "review_id", "rights_basis_evidence_id", "entries",
    "app_import_authorized", "public_distribution_authorized",
}


def build_test_bundle(curated: dict[str, Any]) -> dict[str, Any]:
    """Map an internal simulated handoff; reject malformed inputs before hashing."""
    if type(curated) is not dict or set(curated) != CURATED_KEYS:
        raise ValueError("CURATED_HANDOFF_SCHEMA_INVALID")
    if curated["format"] != CURATED_FORMAT or curated["status"] != CURATED_STATUS:
        raise ValueError("CURATED_HANDOFF_INVALID")
    if curated["app_import_authorized"] is not False or curated["public_distribution_authorized"] is not False:
        raise ValueError("CURATED_AUTHORITY_INVALID")
    for field in ("corpus_id", "review_id", "rights_basis_evidence_id"):
        opaque(curated[field])
    for field in ("corpus_sha256", "review_sha256"):
        if type(curated[field]) is not str or SHA256.fullmatch(curated[field]) is None:
            raise ValueError("CURATED_HASH_INVALID")
    if curated["rights_basis"] != "PROJECT_ORIGINAL_EDITORIAL_DRAFT":
        raise ValueError("CURATED_RIGHTS_BASIS_INVALID")
    entries = curated["entries"]
    if type(entries) is not list or len(entries) != 20:
        raise ValueError("CURATED_ENTRY_COUNT_INVALID")
    references: list[dict[str, Any]] = []
    seen: set[str] = set()
    seen_sources: set[str] = set()
    seen_reviews: set[str] = set()
    for entry in entries:
        if type(entry) is not dict or set(entry) != {
            "reference_id", "source_evidence_id", "review_evidence_id", "photography"
        }:
            raise ValueError("CURATED_ENTRY_SCHEMA_INVALID")
        reference_id = entry["reference_id"]
        for field, used in (("reference_id", seen), ("source_evidence_id", seen_sources),
                            ("review_evidence_id", seen_reviews)):
            opaque(entry[field])
            if entry[field] in used:
                raise ValueError("CURATED_DUPLICATE_ID")
            used.add(entry[field])
        photography = entry["photography"]
        if type(photography) is not dict or set(photography) != set(PHOTO_FIELDS):
            raise ValueError("CURATED_PHOTOGRAPHY_SCHEMA_INVALID")
        for text in photography.values():
            if type(text) is not str or any(0xD800 <= ord(c) <= 0xDFFF for c in text):
                raise ValueError("CURATED_TEXT_INVALID")
        references.append({"reference_id": reference_id, "photography": dict(photography)})
    bundle = {
        "bundle_id": "p25s_internal_handoff_fixture",
        "contract_version": "1.0",
        "integrity": {"algorithm": "SHA-256", "payload_sha256": "0" * 64},
        "references": references,
        "source": {"origin": "PIPELINE", "producer_id": TEST_PRODUCER, "release_id": TEST_RELEASE},
    }
    bundle["integrity"]["payload_sha256"] = sha256(canonical_payload(bundle))
    # Use the actual consumer-compatible contract for all text/size/transport checks.
    validate_bundle(encoded_json(bundle))
    return bundle
