"""P25 human-review gate for curated editorial knowledge.

This tool never approves content itself. It binds an external human review to the exact
editorial corpus and can export a neutral Pipeline handoff only after all 20 entries
are explicitly approved. The export is NOT a Photo Knowledge Bundle and cannot be
imported by the Android consumer.
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from p25_validate_editorial_corpus import (
    CorpusError,
    ID_PATTERN,
    MAX_CORPUS_BYTES,
    validate as validate_corpus,
)
from p23c_bundle_contract import PackError\nfrom p23c_prepare_review_pack import publish_new, read_selected

PURPOSE = "P25_CLOSED_BETA_CURATED_EDITORIAL"
REVIEW_VERSION = "1.0"
EXPORT_FORMAT = "photoai.curated-editorial-input.v1"
REVIEW_FIELDS = ("content_review", "rights_review", "privacy_review")
ROOT_KEYS = {
    "review_version",
    "purpose",
    "corpus_id",
    "corpus_sha256",
    "review_id",
    "reviewer_id",
    "rights_basis_evidence_id",
    "entries",
}
ENTRY_KEYS = {
    "reference_id",
    "source_evidence_id",
    "evidence_id",
    *REVIEW_FIELDS,
}
MAX_REVIEW_BYTES = 128 * 1024


class ReviewError(ValueError):
    """Fixed review-gate error code."""


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ReviewError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def _strict_json(raw: bytes) -> dict[str, Any]:
    if not raw or len(raw) > MAX_REVIEW_BYTES or raw.startswith(b"\xef\xbb\xbf"):
        raise ReviewError("REVIEW_DOCUMENT_INVALID")
    try:
        document = json.loads(
            raw.decode("utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate_keys,
        )
    except ReviewError:
        raise
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise ReviewError("REVIEW_DOCUMENT_INVALID") from error
    if not isinstance(document, dict):
        raise ReviewError("REVIEW_SCHEMA_INVALID")
    return document


def _opaque(value: Any, code: str) -> str:
    if not isinstance(value, str) or ID_PATTERN.fullmatch(value) is None:
        raise ReviewError(code)
    return value


def _corpus(raw: bytes) -> dict[str, Any]:
    validate_corpus(raw)
    try:
        result = json.loads(raw.decode("utf-8"))
    except Exception as error:
        raise ReviewError("CORPUS_UNEXPECTED") from error
    return result


def review_template(corpus_raw: bytes) -> dict[str, Any]:
    corpus = _corpus(corpus_raw)
    return {
        "review_version": REVIEW_VERSION,
        "purpose": PURPOSE,
        "corpus_id": corpus["corpus_id"],
        "corpus_sha256": sha256(corpus_raw),
        "review_id": None,
        "reviewer_id": None,
        "rights_basis_evidence_id": None,
        "entries": [
            {
                "reference_id": entry["reference_id"],
                "source_evidence_id": entry["source_evidence_id"],
                "evidence_id": None,
                "content_review": "PENDING",
                "rights_review": "PENDING",
                "privacy_review": "PENDING",
            }
            for entry in corpus["entries"]
        ],
    }


def validate_review(corpus_raw: bytes, review_raw: bytes) -> dict[str, Any]:
    corpus = _corpus(corpus_raw)
    review = _strict_json(review_raw)
    if set(review) != ROOT_KEYS:
        raise ReviewError("REVIEW_SCHEMA_INVALID")
    if review["review_version"] != REVIEW_VERSION or review["purpose"] != PURPOSE:
        raise ReviewError("REVIEW_VERSION_OR_PURPOSE_INVALID")
    if review["corpus_id"] != corpus["corpus_id"] or review["corpus_sha256"] != sha256(corpus_raw):
        raise ReviewError("REVIEW_BINDING_MISMATCH")
    _opaque(review["review_id"], "REVIEW_ID_INVALID")
    _opaque(review["reviewer_id"], "REVIEWER_ID_INVALID")
    _opaque(review["rights_basis_evidence_id"], "RIGHTS_EVIDENCE_ID_INVALID")
    entries = review["entries"]
    if not isinstance(entries, list) or len(entries) != 20:
        raise ReviewError("REVIEW_COVERAGE_INVALID")
    expected = {
        entry["reference_id"]: entry["source_evidence_id"]
        for entry in corpus["entries"]
    }
    seen_refs: set[str] = set()
    seen_evidence: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != ENTRY_KEYS:
            raise ReviewError("REVIEW_ENTRY_SCHEMA_INVALID")
        reference_id = _opaque(entry["reference_id"], "REVIEW_REFERENCE_ID_INVALID")
        source_id = _opaque(entry["source_evidence_id"], "REVIEW_SOURCE_ID_INVALID")
        evidence_id = _opaque(entry["evidence_id"], "REVIEW_EVIDENCE_ID_INVALID")
        if reference_id not in expected or reference_id in seen_refs:
            raise ReviewError("REVIEW_COVERAGE_INVALID")
        if source_id != expected[reference_id]:
            raise ReviewError("REVIEW_SOURCE_BINDING_MISMATCH")
        if evidence_id in seen_evidence:
            raise ReviewError("REVIEW_EVIDENCE_ID_DUPLICATE")
        if any(entry[field] != "APPROVED" for field in REVIEW_FIELDS):
            raise ReviewError("REVIEW_NOT_COMPLETE")
        seen_refs.add(reference_id)
        seen_evidence.add(evidence_id)
    if seen_refs != set(expected):
        raise ReviewError("REVIEW_COVERAGE_INVALID")
    return {
        "status": "P25_REAL_20_CONTENT_REVIEWED",
        "corpus_id": corpus["corpus_id"],
        "corpus_sha256": sha256(corpus_raw),
        "review_sha256": sha256(review_raw),
        "reference_count": 20,
        "content_approved": True,
        "rights_approved": True,
        "privacy_approved": True,
        "app_import_authorized": False,
        "public_distribution_authorized": False,
    }


def pipeline_input(corpus_raw: bytes, review_raw: bytes) -> dict[str, Any]:
    corpus = _corpus(corpus_raw)
    review_result = validate_review(corpus_raw, review_raw)
    review = json.loads(review_raw.decode("utf-8"))
    evidence_by_ref = {entry["reference_id"]: entry["evidence_id"] for entry in review["entries"]}
    return {
        "format": EXPORT_FORMAT,
        "status": "HUMAN_REVIEW_APPROVED_NOT_APP_IMPORTABLE",
        "corpus_id": corpus["corpus_id"],
        "corpus_sha256": review_result["corpus_sha256"],
        "review_sha256": review_result["review_sha256"],
        "rights_basis": corpus["rights_basis"],
        "review_id": review["review_id"],
        "rights_basis_evidence_id": review["rights_basis_evidence_id"],
        "entries": [
            {
                "reference_id": entry["reference_id"],
                "source_evidence_id": entry["source_evidence_id"],
                "review_evidence_id": evidence_by_ref[entry["reference_id"]],
                "photography": entry["photography"],
            }
            for entry in corpus["entries"]
        ],
        "app_import_authorized": False,
        "public_distribution_authorized": False,
    }


def encoded(value: dict[str, Any]) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def main(argv: list[str] | None = None) -> int:
    args = list(argv or sys.argv[1:])
    if len(args) not in (3, 4):
        print(json.dumps({"status": "BLOCKED", "code": "USAGE"}, sort_keys=True))
        return 2
    command, corpus_path = args[0], Path(args[1])
    try:
        corpus_raw = read_selected(corpus_path, ".json", MAX_CORPUS_BYTES)
        if command == "review-template" and len(args) == 3:
            output = Path(args[2])
            raw = encoded(review_template(corpus_raw))
            publish_new(output, raw)
            result = {"status": "PENDING_REVIEW_TEMPLATE_WRITTEN", "output_sha256": sha256(raw)}
        elif command == "validate-review" and len(args) == 3:
            review_raw = read_selected(Path(args[2]), ".json", MAX_REVIEW_BYTES)
            result = validate_review(corpus_raw, review_raw)
        elif command == "export-pipeline-input" and len(args) == 4:
            review_raw = read_selected(Path(args[2]), ".json", MAX_REVIEW_BYTES)
            output = Path(args[3])
            raw = encoded(pipeline_input(corpus_raw, review_raw))
            publish_new(output, raw)
            result = {
                "status": "PIPELINE_HANDOFF_WRITTEN_NOT_APP_IMPORTABLE",
                "output_sha256": sha256(raw),
                "app_import_authorized": False,
            }
        else:
            raise ReviewError("USAGE")
    except (OSError, CorpusError, ReviewError) as error:
        code = str(error) if isinstance(error, (CorpusError, ReviewError)) else "LOCAL_IO_ERROR"
        print(json.dumps({"status": "BLOCKED", "code": code}, sort_keys=True))
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
