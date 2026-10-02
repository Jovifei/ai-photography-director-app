"""Validate the P25 editorial corpus without granting approval or release rights."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

EXPECTED_STATUS = "PENDING_HUMAN_CONTENT_RIGHTS_PRIVACY_REVIEW"
EXPECTED_RIGHTS_BASIS = "PROJECT_ORIGINAL_EDITORIAL_DRAFT"
EXPECTED_CORPUS_ID = "P25-PORTRAIT-EDITORIAL-20-20260919"
MAX_CORPUS_BYTES = 256 * 1024
PHOTO_FIELDS = {
    "scene",
    "background_story",
    "lighting",
    "composition",
    "subject_intent",
    "emotion",
    "pose_template",
    "camera_position",
    "director_prompt",
}
ROOT_FIELDS = {"corpus_version", "corpus_id", "status", "rights_basis", "entries"}
ENTRY_FIELDS = {"reference_id", "source_evidence_id", "photography"}
ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$", re.ASCII)
UNSAFE_TRANSPORT_TEXT = re.compile(
    r"(?:[a-z][a-z0-9+.-]*://|(?:content|file):|(?:^|\s)[a-z]:[\\/]|"
    r"/sdcard(?:/|$)|/storage(?:/|$)|\.\.[\\/])",
    re.IGNORECASE,
)


class CorpusError(ValueError):
    """Only fixed error codes should cross the CLI boundary."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise CorpusError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def _validate_text(value: Any, limit: int) -> None:
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise CorpusError("TEXT_INVALID")
    if any(
        ord(char) < 32
        or 127 <= ord(char) <= 159
        or 0xD800 <= ord(char) <= 0xDFFF
        or char in "\u2028\u2029"
        for char in value
    ):
        raise CorpusError("TEXT_INVALID")
    if UNSAFE_TRANSPORT_TEXT.search(value):
        raise CorpusError("TRANSPORT_TEXT_FORBIDDEN")


def validate(raw: bytes) -> dict[str, object]:
    if not raw:
        raise CorpusError("DOCUMENT_EMPTY")
    if len(raw) > MAX_CORPUS_BYTES:
        raise CorpusError("DOCUMENT_TOO_LARGE")
    if raw.startswith(b"\xef\xbb\xbf"):
        raise CorpusError("INVALID_UTF8")
    try:
        text = raw.decode("utf-8", errors="strict")
        document = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
    except CorpusError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError) as error:
        raise CorpusError("INVALID_JSON") from error
    if not isinstance(document, dict) or set(document) != ROOT_FIELDS:
        raise CorpusError("ROOT_SCHEMA_INVALID")
    if document["corpus_version"] != "1.0":
        raise CorpusError("VERSION_INVALID")
    if document["corpus_id"] != EXPECTED_CORPUS_ID:
        raise CorpusError("CORPUS_ID_INVALID")
    if document["status"] != EXPECTED_STATUS:
        raise CorpusError("STATUS_INVALID")
    if document["rights_basis"] != EXPECTED_RIGHTS_BASIS:
        raise CorpusError("RIGHTS_BASIS_INVALID")
    entries = document["entries"]
    if not isinstance(entries, list) or len(entries) != 20:
        raise CorpusError("REQUIRES_20")
    expected_ids = [f"PKB-PORTRAIT-{index:03d}" for index in range(1, 21)]
    seen_sources: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict) or set(entry) != ENTRY_FIELDS:
            raise CorpusError("ENTRY_SCHEMA_INVALID")
        if entry["reference_id"] != expected_ids[index]:
            raise CorpusError("REFERENCE_ID_INVALID")
        source_id = entry["source_evidence_id"]
        if (
            not isinstance(source_id, str)
            or not ID_PATTERN.fullmatch(source_id)
            or source_id in seen_sources
        ):
            raise CorpusError("SOURCE_EVIDENCE_ID_INVALID")
        seen_sources.add(source_id)
        photography = entry["photography"]
        if not isinstance(photography, dict) or set(photography) != PHOTO_FIELDS:
            raise CorpusError("PHOTOGRAPHY_SCHEMA_INVALID")
        for field, value in photography.items():
            _validate_text(value, 1200 if field == "director_prompt" else 800)
    return {
        "status": EXPECTED_STATUS,
        "reference_count": 20,
        "sha256": hashlib.sha256(raw).hexdigest(),
        "content_reviewed": False,
        "rights_reviewed": False,
        "privacy_reviewed": False,
        "public_candidate_authorized": False,
    }


def main(argv: list[str] | None = None) -> int:
    args = argv or sys.argv[1:]
    if len(args) != 1:
        print(json.dumps({"status": "BLOCKED", "code": "USAGE"}, sort_keys=True))
        return 2
    try:
        result = validate(Path(args[0]).read_bytes())
    except (OSError, CorpusError) as error:
        code = str(error) if isinstance(error, CorpusError) else "LOCAL_IO_ERROR"
        print(json.dumps({"status": "BLOCKED", "code": code}, sort_keys=True))
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
