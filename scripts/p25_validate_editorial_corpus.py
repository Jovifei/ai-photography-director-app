"""Validate the P25 editorial corpus without granting approval or release rights."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

EXPECTED_STATUS = "PENDING_HUMAN_CONTENT_RIGHTS_PRIVACY_REVIEW"
EXPECTED_RIGHTS_BASIS = "PROJECT_ORIGINAL_EDITORIAL_DRAFT"
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
ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")


class CorpusError(ValueError):
    pass


def _reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise CorpusError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def validate(raw: bytes) -> dict[str, object]:
    if len(raw) > 256 * 1024:
        raise CorpusError("DOCUMENT_TOO_LARGE")
    try:
        text = raw.decode("utf-8")
        document = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CorpusError("INVALID_JSON") from error
    if not isinstance(document, dict) or set(document) != ROOT_FIELDS:
        raise CorpusError("ROOT_SCHEMA_INVALID")
    if document["corpus_version"] != "1.0":
        raise CorpusError("VERSION_INVALID")
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
        if not isinstance(source_id, str) or not ID_PATTERN.fullmatch(source_id) or source_id in seen_sources:
            raise CorpusError("SOURCE_EVIDENCE_ID_INVALID")
        seen_sources.add(source_id)
        photography = entry["photography"]
        if not isinstance(photography, dict) or set(photography) != PHOTO_FIELDS:
            raise CorpusError("PHOTOGRAPHY_SCHEMA_INVALID")
        for field, value in photography.items():
            limit = 1200 if field == "director_prompt" else 800
            if not isinstance(value, str) or not value.strip() or len(value) > limit:
                raise CorpusError("TEXT_INVALID")
            if any(ord(char) < 32 or 127 <= ord(char) <= 159 for char in value):
                raise CorpusError("TEXT_INVALID")
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
