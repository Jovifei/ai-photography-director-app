#!/usr/bin/env python3
"""Offline integrity validator for the external Phase 1.5 P1A public corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from pathlib import Path, PurePosixPath
from typing import Any

from PIL import Image
from jsonschema import Draft202012Validator


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = REPOSITORY_ROOT / "docs" / "phase1_5" / "p1a" / "public_corpus_manifest.json"
SCHEMA_PATH = REPOSITORY_ROOT / "docs" / "phase1_5" / "p1a" / "public_corpus_manifest.schema.json"
ALLOWED_LICENSES = {"CC0", "Public-Domain", "CC-BY-4.0"}
REQUIRED_TAGS = {
    "city_night",
    "street_environment",
    "beach",
    "forest",
    "cafe_interior",
    "architecture",
    "single_adult_closeup",
    "single_adult_half_body",
    "two_adults",
    "dynamic_action",
    "complex_background",
    "minimal_background",
    "strong_color",
    "cool_color",
    "warm_color",
    "symmetry",
    "negative_space",
}


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def relative_evidence_path(value: str) -> Path:
    pure = PurePosixPath(value)
    if not value or pure.is_absolute() or ".." in pure.parts or ":" in value:
        raise ValueError(f"Evidence path is not a safe relative POSIX path: {value!r}")
    return Path(*pure.parts)


def resolve_within(root: Path, relative: str) -> Path:
    candidate = (root / relative_evidence_path(relative)).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as error:
        raise ValueError(f"Evidence path escapes supplied root: {relative!r}") from error
    return candidate


def validate_manifest(manifest: dict[str, Any], schema: dict[str, Any]) -> list[str]:
    failures = [
        f"schema: {error.message}"
        for error in Draft202012Validator(schema).iter_errors(manifest)
    ]
    serialized = json.dumps(manifest, ensure_ascii=False)
    if re.search(r"(?<![A-Za-z])[A-Za-z]:[\\/]", serialized):
        failures.append("manifest must not contain Windows absolute paths")
    if manifest["approved_count"] != len(manifest["samples"]):
        failures.append("approved_count does not equal sample count")
    if manifest["approved_count"] < manifest["minimum_required_count"]:
        failures.append("approved count is below minimum")
    sample_ids = [sample["sample_id"] for sample in manifest["samples"]]
    if len(sample_ids) != len(set(sample_ids)):
        failures.append("duplicate sample IDs")
    all_tags = {
        tag
        for sample in manifest["samples"]
        for tag in (
            sample["tags"]
            + sample.get("lighting_tags", [])
            + sample.get("composition_tags", [])
        )
    }
    missing_tags = sorted(REQUIRED_TAGS - all_tags)
    if missing_tags:
        failures.append(f"required coverage tags missing: {', '.join(missing_tags)}")
    for sample in manifest["samples"]:
        if sample["license"]["id"] not in ALLOWED_LICENSES:
            failures.append(f"{sample['sample_id']}: non-approved license")
        if sample["manual_review"]["status"] != "APPROVED":
            failures.append(f"{sample['sample_id']}: manual review not approved")
        for section, key in (("source", "source_relative_path"), ("sanitized", "relative_path")):
            try:
                relative_evidence_path(sample[section][key])
            except ValueError as error:
                failures.append(f"{sample['sample_id']}: {error}")
    return failures


def validate_evidence(manifest: dict[str, Any], root: Path) -> list[str]:
    failures: list[str] = []
    for sample in manifest["samples"]:
        source = resolve_within(root, sample["source"]["source_relative_path"])
        sanitized = resolve_within(root, sample["sanitized"]["relative_path"])
        for label, path, expected_bytes, expected_sha in (
            ("source", source, sample["source"]["bytes"], sample["source"]["sha256"]),
            ("sanitized", sanitized, sample["sanitized"]["bytes"], sample["sanitized"]["sha256"]),
        ):
            if not path.is_file():
                failures.append(f"{sample['sample_id']}: missing {label} evidence")
                continue
            if path.stat().st_size != expected_bytes:
                failures.append(f"{sample['sample_id']}: {label} byte mismatch")
            if sha256(path) != expected_sha:
                failures.append(f"{sample['sample_id']}: {label} SHA-256 mismatch")
        if sanitized.is_file():
            with sanitized.open("rb") as source_file:
                if source_file.read(3) != b"\xff\xd8\xff":
                    failures.append(f"{sample['sample_id']}: sanitized file is not JPEG")
            with Image.open(sanitized) as image:
                image.verify()
            with Image.open(sanitized) as image:
                if image.format != "JPEG":
                    failures.append(f"{sample['sample_id']}: sanitized MIME mismatch")
                if image.size != (sample["source"]["width"], sample["source"]["height"]):
                    failures.append(f"{sample['sample_id']}: sanitized dimensions differ from manifest")
                if image.getexif():
                    failures.append(f"{sample['sample_id']}: EXIF remains after sanitization")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-root", type=Path, required=True)
    arguments = parser.parse_args()
    manifest = load_json(MANIFEST_PATH)
    schema = load_json(SCHEMA_PATH)
    root = arguments.evidence_root.resolve()
    failures = validate_manifest(manifest, schema)
    failures.extend(validate_evidence(manifest, root))
    if failures:
        print("P1A corpus validation: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print(f"P1A corpus validation: PASS ({manifest['approved_count']} approved samples)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
