#!/usr/bin/env python3
"""Independent offline validator for the P1A public-corpus v2 evidence contract.

The validator deliberately does not trust claimed status fields: it reads the
external source and sanitized bytes, licence records, human-review records,
quarantine records, category coverage and the deterministic aggregate.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse

from PIL import Image, UnidentifiedImageError
from jsonschema import Draft202012Validator


ALLOWED_LICENSES = {"CC0", "Public-Domain", "CC-BY-4.0"}
ALLOWED_HUMAN = {"NONE", "VISIBLE_DOCUMENTED_ADULT", "VISIBLE_AGE_UNCERTAIN"}
ADULT_BASES = {"OFFICIAL_SOURCE_DOCUMENTED_ADULT", "PUBLIC_RECORD_DATE_CONFIRMED"}
REQUIRED_QUARANTINE = {"p1a-008", "p1a-019", "p1a-023", "p1a-024"}
MOJIBAKE = re.compile(r"(?:\ufffd|Ã.|Â.|â€.)")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def json_load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def valid_url(value: Any) -> bool:
    parsed = urlparse(str(value or ""))
    return parsed.scheme == "https" and bool(parsed.netloc)


def safe_relative(value: Any) -> PurePosixPath:
    text = str(value or "")
    pure = PurePosixPath(text)
    if not text or pure.is_absolute() or ".." in pure.parts or "\\" in text or re.search(r"^[A-Za-z]:", text):
        raise ValueError("unsafe evidence path")
    return pure


def inside(root: Path, relative: Any) -> Path:
    pure = safe_relative(relative)
    candidate = (root / Path(*pure.parts)).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as error:
        raise ValueError("evidence path escapes root") from error
    return candidate


def canonical_aggregate(samples: list[dict[str, Any]]) -> str:
    projection = []
    for sample in sorted(samples, key=lambda item: item["sample_id"]):
        projection.append(
            {
                "sample_id": sample["sample_id"],
                "source_sha256": sample["source"]["sha256"],
                "sanitized_sha256": sample["sanitized"]["sha256"],
                "source_revision_id": sample["source_page"]["revision_id"],
                "license_id": sample["license"]["id"],
                "license_evidence_sha256": sample["license_evidence_sha256"],
                "human_presence": sample["human_review"]["human_presence"],
                "category_tags": sorted(sample["category_tags"]),
            }
        )
    body = json.dumps(projection, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(body).hexdigest()


def add(failures: list[str], sample: str, message: str) -> None:
    failures.append(f"{sample}: {message}")


def validate_binary(sample: dict[str, Any], root: Path, failures: list[str]) -> None:
    sample_id = sample.get("sample_id", "<unknown>")
    source_data = sample.get("source", {})
    sanitized_data = sample.get("sanitized", {})
    try:
        source = inside(root, source_data.get("relative_path"))
        sanitized = inside(root, sanitized_data.get("relative_path"))
    except ValueError as error:
        add(failures, sample_id, str(error))
        return
    for label, path, metadata in (("source", source, source_data), ("sanitized", sanitized, sanitized_data)):
        if not path.is_file():
            add(failures, sample_id, f"missing {label} file")
            continue
        if path.stat().st_size != metadata.get("bytes"):
            add(failures, sample_id, f"{label} byte mismatch")
        if sha256(path) != metadata.get("sha256"):
            add(failures, sample_id, f"{label} SHA-256 mismatch")
    if not source.is_file() or not sanitized.is_file():
        return
    try:
        with Image.open(source) as image:
            image.verify()
        with Image.open(source) as image:
            source_format = image.format
            source_size = list(image.size)
        with Image.open(sanitized) as image:
            image.verify()
        with Image.open(sanitized) as image:
            sanitized_format = image.format
            sanitized_size = list(image.size)
            info_keys = set(image.info)
            exif = image.getexif()
    except (UnidentifiedImageError, OSError) as error:
        add(failures, sample_id, f"image decode failed ({type(error).__name__})")
        return
    expected_source_mime = Image.MIME.get(source_format, "")
    if source_data.get("mime") != expected_source_mime:
        add(failures, sample_id, "source MIME mismatch")
    if source_size != [source_data.get("width"), source_data.get("height")]:
        add(failures, sample_id, "source dimensions mismatch")
    if sanitized_format != "JPEG" or sanitized_data.get("mime") != "image/jpeg":
        add(failures, sample_id, "sanitized artifact is not JPEG")
    if sanitized_size != sanitized_data.get("dimensions"):
        add(failures, sample_id, "sanitized dimensions mismatch")
    if sanitized_data.get("crop_applied") is not False:
        add(failures, sample_id, "sanitized record permits a crop")
    if sanitized_data.get("source_dimensions") != source_size:
        add(failures, sample_id, "sanitization source dimensions mismatch")
    if sanitized_size != source_size and not sanitized_data.get("orientation_normalized"):
        add(failures, sample_id, "dimension change is not an orientation normalization")
    if exif or {"exif", "xmp", "xml"}.intersection(info_keys):
        add(failures, sample_id, "sanitized EXIF/GPS/XMP metadata remains")


def validate_metadata(sample: dict[str, Any], root: Path, failures: list[str]) -> None:
    sample_id = sample.get("sample_id", "<unknown>")
    source_page = sample.get("source_page", {})
    for field in ("pageid", "title", "revision_id", "revision_timestamp"):
        if source_page.get(field) in (None, ""):
            add(failures, sample_id, f"source page missing {field}")
    for field in ("canonical_url", "original_url"):
        if not valid_url(source_page.get(field)):
            add(failures, sample_id, f"source page has invalid {field}")
    if not source_page.get("commons_api_sha256"):
        add(failures, sample_id, "source page lacks Commons API SHA-256")
    license_data = sample.get("license", {})
    if license_data.get("id") not in ALLOWED_LICENSES:
        add(failures, sample_id, "unsupported license")
    try:
        license_path = inside(root, sample.get("license_evidence_relative_path"))
    except ValueError as error:
        add(failures, sample_id, str(error))
        return
    if not license_path.is_file():
        add(failures, sample_id, "missing license evidence")
        return
    if sha256(license_path) != sample.get("license_evidence_sha256"):
        add(failures, sample_id, "license evidence SHA-256 mismatch")
    evidence = json_load(license_path)
    if evidence.get("license_id") != license_data.get("id"):
        add(failures, sample_id, "license evidence does not match sample license")
    if evidence.get("source_page_revision_id") != source_page.get("revision_id"):
        add(failures, sample_id, "license evidence source revision mismatch")
    attribution = evidence.get("attribution", {})
    if license_data.get("id") == "CC-BY-4.0":
        for field in ("author", "title", "page_url", "license_name", "license_url", "modified", "recommended_attribution"):
            if not str(attribution.get(field, "")).strip():
                add(failures, sample_id, f"CC-BY attribution missing {field}")
    if license_data.get("id") == "Public-Domain" and not str(evidence.get("public_domain_basis", "")).strip():
        add(failures, sample_id, "public-domain item has no basis")
    review = sample.get("human_review", {})
    if review.get("human_presence") not in ALLOWED_HUMAN:
        add(failures, sample_id, "invalid human presence state")
    tags = set(sample.get("category_tags", []))
    presence = review.get("human_presence")
    if ("no_person" in tags) != (presence == "NONE"):
        add(failures, sample_id, "no_person conflicts with human presence")
    if presence == "VISIBLE_AGE_UNCERTAIN":
        add(failures, sample_id, "age-uncertain human sample cannot be approved")
    if presence == "VISIBLE_DOCUMENTED_ADULT":
        if review.get("age_evidence_basis") not in ADULT_BASES or not str(review.get("age_evidence", "")).strip() or not valid_url(review.get("nonvisual_source")):
            add(failures, sample_id, "documented-adult evidence is incomplete")
    if "single_person_full_body" in tags and not (review.get("person_count") == 1 and review.get("full_body_count", 0) >= 1):
        add(failures, sample_id, "single_person_full_body lacks exactly-one/full-body proof")
    if "two_people" in tags and review.get("person_count") != 2:
        add(failures, sample_id, "two_people does not have exactly two reviewed people")
    text = json.dumps({"source": source_page, "license": evidence, "review": review}, ensure_ascii=False)
    if MOJIBAKE.search(text):
        add(failures, sample_id, "mojibake detected in reviewable metadata")


def validate_manifest(manifest: dict[str, Any], root: Path, schema: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    failures.extend("schema: " + error.message for error in Draft202012Validator(schema).iter_errors(manifest))
    if manifest.get("schema_version") != "2.0.0":
        failures.append("manifest: unsupported schema version")
    serialized = json.dumps(manifest, ensure_ascii=False)
    if re.search(r"(?<![A-Za-z])[A-Za-z]:[\\/]", serialized) or "\\\\" in serialized or '"../' in serialized:
        failures.append("manifest: absolute or traversal path present")
    samples = manifest.get("samples", [])
    if manifest.get("approved_count") != len(samples) or len(samples) < manifest.get("minimum_required_count", 20):
        failures.append("manifest: approved count is inconsistent or below minimum")
    sample_ids = [item.get("sample_id") for item in samples]
    if len(sample_ids) != len(set(sample_ids)):
        failures.append("manifest: duplicate sample IDs")
    source_hashes = [item.get("source", {}).get("sha256") for item in samples]
    sanitized_hashes = [item.get("sanitized", {}).get("sha256") for item in samples]
    if len(source_hashes) != len(set(source_hashes)) or len(sanitized_hashes) != len(set(sanitized_hashes)):
        failures.append("manifest: duplicate approved image content")
    observed_categories = {tag for item in samples for tag in item.get("category_tags", [])}
    missing = sorted(set(manifest.get("required_categories", [])) - observed_categories)
    if missing:
        failures.append("manifest: missing required categories " + ", ".join(missing))
    for sample in samples:
        if sample.get("status") != "APPROVED" or sample.get("human_review", {}).get("status") != "APPROVED":
            add(failures, sample.get("sample_id", "<unknown>"), "sample is not approved")
        validate_binary(sample, root, failures)
        validate_metadata(sample, root, failures)
    quarantine = manifest.get("quarantine", [])
    actual_quarantine = {item.get("sample_id") for item in quarantine if item.get("status") == "QUARANTINED"}
    missing_quarantine = REQUIRED_QUARANTINE - actual_quarantine
    if missing_quarantine:
        failures.append("manifest: required quarantine records missing " + ", ".join(sorted(missing_quarantine)))
    for record in quarantine:
        if record.get("status") != "QUARANTINED" or not str(record.get("reason", "")).strip():
            failures.append("quarantine: invalid status or empty reason")
        if record.get("sample_id") in REQUIRED_QUARANTINE and not record.get("no_image_copied"):
            failures.append(f"{record.get('sample_id')}: quarantined source must not be copied as r2 image evidence")
    if canonical_aggregate(samples) != manifest.get("aggregate", {}).get("sha256"):
        failures.append("manifest: aggregate SHA-256 mismatch")
    return failures


def run(manifest_path: Path, external_root: Path, report_path: Path) -> int:
    manifest = json_load(manifest_path)
    schema_path = manifest_path.with_name("public_corpus_manifest.v2.schema.json")
    if not schema_path.is_file():
        raise FileNotFoundError("v2 schema must sit beside the manifest")
    failures = validate_manifest(manifest, external_root, json_load(schema_path))
    report = {"validator": "phase1_5_p1a_corpus_v2", "status": "PASS" if not failures else "FAIL", "corpus_id": manifest.get("corpus_id"), "approved_count": manifest.get("approved_count"), "quarantine_count": manifest.get("quarantine_count"), "aggregate_sha256": manifest.get("aggregate", {}).get("sha256"), "failure_count": len(failures), "failures": failures}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"P1A corpus validation: {report['status']} ({report['failure_count']} failures)")
    return 0 if not failures else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--external-root", type=Path)
    parser.add_argument("--evidence-root", type=Path, help="Deprecated compatibility alias for --external-root")
    parser.add_argument("--report-json", type=Path)
    arguments = parser.parse_args()
    external_root = arguments.external_root or arguments.evidence_root
    if not arguments.manifest or not external_root or not arguments.report_json:
        parser.error("--manifest, --external-root (or --evidence-root), and --report-json are required")
    return run(arguments.manifest.resolve(), external_root.resolve(), arguments.report_json.resolve())


if __name__ == "__main__":
    raise SystemExit(main())
