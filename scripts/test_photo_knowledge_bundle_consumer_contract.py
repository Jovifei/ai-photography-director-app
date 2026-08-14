#!/usr/bin/env python3
"""Offline compatibility tests for the app-local Photo Knowledge Bundle Consumer v1."""
from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "docs" / "reference" / "photo_knowledge_bundle_consumer.v1.schema.json"
FIXTURE_PATH = ROOT / "docs" / "reference" / "fixtures" / "photo_knowledge_bundle_consumer.v1.json"
FORBIDDEN_KEYS = frozenset(
    {
        "image", "image_bytes", "thumbnail", "base64", "uri", "url", "path", "file_path",
        "filename", "exif", "gps", "account", "account_id", "user", "user_id", "device",
        "device_id", "database", "database_key", "credential", "pairing_code", "token",
        "certificate", "prompt", "model", "model_id", "model_revision", "model_weight",
        "raw_output",
    }
)
UNSAFE_TRANSPORT_TEXT = re.compile(r"(?:\b(?:content|file|https?)://|[A-Za-z]:[\\/]|(?:^|\s)/[^\s]+)", re.IGNORECASE)


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def schema_errors(instance: object, schema: dict) -> list[object]:
    return sorted(Draft202012Validator(schema).iter_errors(instance), key=str)


def private_boundary_errors(value: object, path: str = "$") -> list[str]:
    if isinstance(value, dict):
        errors: list[str] = []
        for key, nested in value.items():
            if key.lower() in FORBIDDEN_KEYS:
                errors.append(f"{path}.{key}: forbidden field")
            errors.extend(private_boundary_errors(nested, f"{path}.{key}"))
        return errors
    if isinstance(value, list):
        return [error for index, nested in enumerate(value) for error in private_boundary_errors(nested, f"{path}[{index}]")]
    if isinstance(value, str) and UNSAFE_TRANSPORT_TEXT.search(value):
        return [f"{path}: transport-like text is forbidden"]
    return []


def semantic_errors(instance: dict) -> list[str]:
    reference_ids = [item["reference_id"] for item in instance.get("references", []) if isinstance(item, dict) and "reference_id" in item]
    errors = private_boundary_errors(instance)
    if len(reference_ids) != len(set(reference_ids)):
        errors.append("$.references: duplicate reference_id")
    return errors


def check(name: str, assertion: bool) -> None:
    if not assertion:
        raise AssertionError(name)
    print(f"PASS {name}")


def rejects(name: str, candidate: dict, schema: dict, semantic: bool = False) -> None:
    errors = schema_errors(candidate, schema)
    if semantic:
        errors += semantic_errors(candidate)
    check(name, bool(errors))


def main() -> int:
    schema = load(SCHEMA_PATH)
    fixture = load(FIXTURE_PATH)
    Draft202012Validator.check_schema(schema)
    check("SCHEMA_SELF_CHECK", True)
    check("FIXTURE_SCHEMA_ACCEPTANCE", not schema_errors(fixture, schema))
    check("FIXTURE_PRIVACY_BOUNDARY", not semantic_errors(fixture))

    unknown_version = copy.deepcopy(fixture)
    unknown_version["contract_version"] = "2.0"
    rejects("REJECT_UNKNOWN_VERSION", unknown_version, schema)

    uri_identifier = copy.deepcopy(fixture)
    uri_identifier["references"][0]["reference_id"] = "content://media/private/1"
    rejects("REJECT_URI_IDENTIFIER", uri_identifier, schema)

    image_field = copy.deepcopy(fixture)
    image_field["references"][0]["image_uri"] = "content://media/private/1"
    rejects("REJECT_IMAGE_URI_FIELD", image_field, schema, semantic=True)

    model_field = copy.deepcopy(fixture)
    model_field["source"]["model_weight"] = "fixture-weight"
    rejects("REJECT_MODEL_WEIGHT_FIELD", model_field, schema, semantic=True)

    unsafe_text = copy.deepcopy(fixture)
    unsafe_text["references"][0]["photography"]["director_prompt"] = "Read C:\\private\\photo.jpg before capture."
    rejects("REJECT_PATH_LIKE_GUIDANCE", unsafe_text, schema, semantic=True)

    duplicate_reference = copy.deepcopy(fixture)
    duplicate_reference["references"].append(copy.deepcopy(duplicate_reference["references"][0]))
    rejects("REJECT_DUPLICATE_REFERENCE_ID", duplicate_reference, schema, semantic=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        raise SystemExit(1)
