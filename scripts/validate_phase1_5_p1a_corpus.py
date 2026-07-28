#!/usr/bin/env python3
"""Independent offline validator for the P1A public-corpus v3 evidence contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse

from PIL import Image, UnidentifiedImageError
from jsonschema import Draft202012Validator


ALLOWED_LICENSES = {"CC0", "Public-Domain", "CC-BY-4.0"}
ALLOWED_HUMAN = {"NONE", "VISIBLE_DOCUMENTED_ADULT"}
ADULT_BASES = {"OFFICIAL_SOURCE_DOCUMENTED_ADULT", "PUBLIC_RECORD_DATE_CONFIRMED"}
EVIDENCE_TYPES = {
    "COMMONS_API",
    "SOURCE_PAGE_RECORD",
    "LICENSE_EVIDENCE",
    "TRANSPORT",
    "MANUAL_VISUAL_REVIEW",
}
REQUIRED_QUARANTINE = {"p1a-006", "p1a-008", "p1a-019", "p1a-023", "p1a-024"}
PD_PLACEHOLDERS = {"", "false", "none", "unknown", "not applicable", "not_applicable", "n/a"}
MOJIBAKE = re.compile(r"(?:\ufffd|Ã.|Â.|â€.)")
UTC = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def json_load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def valid_url(value: Any) -> bool:
    parsed = urlparse(str(value or ""))
    return parsed.scheme == "https" and bool(parsed.netloc)


def safe_relative(value: Any) -> PurePosixPath:
    text = str(value or "")
    pure = PurePosixPath(text)
    if (
        not text
        or pure.is_absolute()
        or ".." in pure.parts
        or "\\" in text
        or re.search(r"^[A-Za-z]:", text)
    ):
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
    projection: list[dict[str, Any]] = []
    for sample in sorted(samples, key=lambda item: item["sample_id"]):
        metadata = sorted(
            (
                {"evidence_type": item["evidence_type"], "sha256": item["sha256"]}
                for item in sample["metadata_evidence"]
            ),
            key=lambda item: item["evidence_type"],
        )
        projection.append(
            {
                "sample_id": sample["sample_id"],
                "source_sha256": sample["source"]["sha256"],
                "sanitized_sha256": sample["sanitized"]["sha256"],
                "source_revision_id": sample["source_page"]["revision_id"],
                "license_id": sample["license"]["id"],
                "metadata_evidence": metadata,
                "human_presence": sample["human_review"]["human_presence"],
                "category_tags": sorted(sample["category_tags"]),
            }
        )
    body = json.dumps(projection, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
        "utf-8"
    )
    return hashlib.sha256(body).hexdigest()


def add(failures: list[str], sample: str, message: str) -> None:
    failures.append(f"{sample}: {message}")


def safe_schema_failures(manifest: Any, schema: dict[str, Any]) -> list[str]:
    errors = sorted(
        Draft202012Validator(schema).iter_errors(manifest),
        key=lambda error: list(error.absolute_path),
    )
    return [f"schema: validation error at {error.json_path or '$'}" for error in errors]


def read_metadata(sample: dict[str, Any], root: Path, failures: list[str]) -> dict[str, Any]:
    sample_id = str(sample.get("sample_id", "<unknown>"))
    found: dict[str, Any] = {}
    paths: set[str] = set()
    for binding in sample.get("metadata_evidence", []):
        evidence_type = binding.get("evidence_type")
        relative_path = binding.get("relative_path")
        if evidence_type not in EVIDENCE_TYPES:
            add(failures, sample_id, "unknown metadata evidence type")
            continue
        if evidence_type in found:
            add(failures, sample_id, "duplicate metadata evidence type")
            continue
        if relative_path in paths:
            add(failures, sample_id, "duplicate metadata relative path")
            continue
        paths.add(str(relative_path))
        try:
            path = inside(root, relative_path)
        except ValueError as error:
            add(failures, sample_id, str(error))
            continue
        if not path.is_file():
            add(failures, sample_id, f"missing {evidence_type} metadata")
            continue
        if path.stat().st_size != binding.get("bytes"):
            add(failures, sample_id, f"{evidence_type} metadata byte mismatch")
        if sha256(path) != binding.get("sha256"):
            add(failures, sample_id, f"{evidence_type} metadata SHA-256 mismatch")
        try:
            found[evidence_type] = json_load(path)
        except (UnicodeDecodeError, json.JSONDecodeError, OSError):
            add(failures, sample_id, f"{evidence_type} metadata is not UTF-8 JSON")
    missing = EVIDENCE_TYPES - set(found)
    for evidence_type in sorted(missing):
        add(failures, sample_id, f"missing {evidence_type} metadata")
    return found


def validate_binary(sample: dict[str, Any], root: Path, failures: list[str]) -> None:
    sample_id = str(sample.get("sample_id", "<unknown>"))
    source_data = sample.get("source", {})
    sanitized_data = sample.get("sanitized", {})
    try:
        source = inside(root, source_data.get("relative_path"))
        sanitized = inside(root, sanitized_data.get("relative_path"))
    except ValueError as error:
        add(failures, sample_id, str(error))
        return
    for label, path, metadata in (
        ("source", source, source_data),
        ("sanitized", sanitized, sanitized_data),
    ):
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
    except (UnidentifiedImageError, OSError):
        add(failures, sample_id, "image decode failed")
        return
    if source_data.get("mime") != Image.MIME.get(source_format, ""):
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


def api_page(api: Any) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]] | None:
    try:
        page = api["query"]["pages"][0]
        image_info = page["imageinfo"][0]
        revision = page["revisions"][0]
        return page, image_info, revision
    except (KeyError, IndexError, TypeError):
        return None


def metadata_sample_revision_matches(value: Any, sample_id: str, revision_id: Any) -> bool:
    return (
        isinstance(value, dict)
        and value.get("sample_id") == sample_id
        and value.get("source_page_revision_id") == revision_id
    )


def validate_metadata(sample: dict[str, Any], root: Path, failures: list[str]) -> None:
    sample_id = str(sample.get("sample_id", "<unknown>"))
    source_page = sample.get("source_page", {})
    license_data = sample.get("license", {})
    review = sample.get("human_review", {})
    metadata = read_metadata(sample, root, failures)
    if set(metadata) != EVIDENCE_TYPES:
        return
    commons_api = metadata["COMMONS_API"]
    source_record = metadata["SOURCE_PAGE_RECORD"]
    license_evidence = metadata["LICENSE_EVIDENCE"]
    transport = metadata["TRANSPORT"]
    manual = metadata["MANUAL_VISUAL_REVIEW"]

    parsed = api_page(commons_api)
    if parsed is None:
        add(failures, sample_id, "Commons API metadata has no page/image/revision record")
        return
    page, image_info, revision = parsed
    expected = {
        "pageid": page.get("pageid"),
        "title": page.get("title"),
        "revision_id": revision.get("revid"),
        "canonical_url": image_info.get("descriptionurl"),
        "original_url": image_info.get("url"),
    }
    for field, actual in expected.items():
        if source_page.get(field) != actual:
            add(failures, sample_id, f"Commons/source-page {field} mismatch")
    for field in ("pageid", "title", "revision_id", "canonical_url", "original_url"):
        if source_record.get(field) != source_page.get(field):
            add(failures, sample_id, f"source-page record {field} mismatch")
    if source_record.get("commons_api_sha256") != source_page.get("commons_api_sha256"):
        add(failures, sample_id, "source-page record Commons SHA mismatch")
    commons_binding = next(
        item for item in sample["metadata_evidence"] if item["evidence_type"] == "COMMONS_API"
    )
    if source_page.get("commons_api_sha256") != commons_binding.get("sha256"):
        add(failures, sample_id, "manifest Commons SHA mismatch")
    if not metadata_sample_revision_matches(
        source_record, sample_id, source_page.get("revision_id")
    ):
        add(failures, sample_id, "source-page record sample/revision mismatch")
    if not metadata_sample_revision_matches(
        license_evidence, sample_id, source_page.get("revision_id")
    ):
        add(failures, sample_id, "license evidence sample/revision mismatch")
    if not metadata_sample_revision_matches(manual, sample_id, source_page.get("revision_id")):
        add(failures, sample_id, "manual review sample/revision mismatch")
    if not metadata_sample_revision_matches(transport, sample_id, source_page.get("revision_id")):
        add(failures, sample_id, "transport sample/revision mismatch")

    request = transport.get("metadata_request", {}) if isinstance(transport, dict) else {}
    if (
        not valid_url(request.get("url"))
        or not valid_url(request.get("final_url"))
        or request.get("url") != source_page.get("metadata_request_url")
        or request.get("final_url") != source_page.get("metadata_request_final_url")
    ):
        add(failures, sample_id, "transport URL invalid")
    if request.get("status") != 200 or not str(request.get("content_type", "")).lower().startswith(
        "application/json"
    ):
        add(failures, sample_id, "transport status/content type invalid")
    if not UTC.fullmatch(str(request.get("retrieved_utc", ""))):
        add(failures, sample_id, "transport retrieved UTC invalid")
    if request.get("bytes") != commons_binding.get("bytes") or request.get(
        "sha256"
    ) != commons_binding.get("sha256"):
        add(failures, sample_id, "transport metadata bytes/hash mismatch")

    if license_data.get("id") not in ALLOWED_LICENSES or license_evidence.get(
        "license_id"
    ) != license_data.get("id"):
        add(failures, sample_id, "license evidence does not match sample license")
    if license_evidence.get("source_page_url") != source_page.get("canonical_url"):
        add(failures, sample_id, "license evidence source URL mismatch")
    if license_evidence.get("commons_api_sha256") != commons_binding.get("sha256"):
        add(failures, sample_id, "license evidence Commons SHA mismatch")
    attribution = license_evidence.get("attribution", {})
    if license_data.get("id") == "CC-BY-4.0":
        for field in (
            "author",
            "title",
            "page_url",
            "license_name",
            "license_url",
            "modified",
            "recommended_attribution",
        ):
            if not str(attribution.get(field, "")).strip():
                add(failures, sample_id, f"CC-BY attribution missing {field}")
    if license_data.get("id") == "Public-Domain":
        for field in (
            "public_domain_basis",
            "public_domain_basis_url",
            "license_or_rights_statement_url",
            "official_source_record",
            "usage_terms",
            "restrictions",
            "artist",
            "credit",
            "modification_notice",
        ):
            value = str(license_evidence.get(field, "")).strip()
            if not value or value.lower() in PD_PLACEHOLDERS:
                add(failures, sample_id, f"public-domain item has invalid {field}")
        if str(license_evidence.get("copyrighted", "")).strip().lower() not in {"true", "false"}:
            add(failures, sample_id, "public-domain item has invalid copyrighted")
        for field in (
            "public_domain_basis_url",
            "license_or_rights_statement_url",
            "official_source_record",
        ):
            if not valid_url(license_evidence.get(field)):
                add(failures, sample_id, f"public-domain item has invalid {field}")

    for field in (
        "human_presence",
        "person_count",
        "full_body_count",
        "age_evidence_basis",
        "age_evidence",
        "nonvisual_source",
        "no_person",
    ):
        if manual.get(field) != review.get(field):
            add(failures, sample_id, f"manual review {field} mismatch")
    if sorted(manual.get("category_tags", [])) != sorted(sample.get("category_tags", [])):
        add(failures, sample_id, "manual review category tags mismatch")
    if review.get("human_presence") not in ALLOWED_HUMAN:
        add(failures, sample_id, "invalid human presence state")
    tags = set(sample.get("category_tags", []))
    presence = review.get("human_presence")
    if ("no_person" in tags) != (presence == "NONE"):
        add(failures, sample_id, "no_person conflicts with human presence")
    if presence == "VISIBLE_DOCUMENTED_ADULT":
        if (
            review.get("age_evidence_basis") not in ADULT_BASES
            or not str(review.get("age_evidence", "")).strip()
            or not valid_url(review.get("nonvisual_source"))
        ):
            add(failures, sample_id, "documented-adult evidence is incomplete")
    if "single_person_full_body" in tags and not (
        review.get("person_count") == 1 and review.get("full_body_count", 0) >= 1
    ):
        add(failures, sample_id, "single_person_full_body lacks exactly-one/full-body proof")
    if "two_people" in tags and review.get("person_count") != 2:
        add(failures, sample_id, "two_people does not have exactly two reviewed people")
    text = json.dumps(
        {"source": source_page, "license": license_evidence, "review": manual}, ensure_ascii=False
    )
    if MOJIBAKE.search(text):
        add(failures, sample_id, "mojibake detected in reviewable metadata")


def validate_manifest(manifest: Any, root: Path, schema: dict[str, Any]) -> list[str]:
    failures = safe_schema_failures(manifest, schema)
    if not isinstance(manifest, dict):
        return failures + ["manifest: root is not an object"]
    if manifest.get("schema_version") != "3.0.0":
        failures.append("manifest: unsupported schema version")
    samples = manifest.get("samples", [])
    quarantine = manifest.get("quarantine", [])
    if not isinstance(samples, list) or not isinstance(quarantine, list):
        return failures + ["manifest: samples/quarantine are not lists"]
    if manifest.get("approved_count") != len(samples) or len(samples) < manifest.get(
        "minimum_required_count", 20
    ):
        failures.append("manifest: approved count is inconsistent or below minimum")
    if manifest.get("quarantine_count") != len(quarantine):
        failures.append("manifest: quarantine count is inconsistent")
    sample_ids = [item.get("sample_id") for item in samples if isinstance(item, dict)]
    quarantine_ids = [item.get("sample_id") for item in quarantine if isinstance(item, dict)]
    if len(sample_ids) != len(set(sample_ids)):
        failures.append("manifest: duplicate sample IDs")
    if len(quarantine_ids) != len(set(quarantine_ids)):
        failures.append("manifest: duplicate quarantine IDs")
    if set(sample_ids).intersection(quarantine_ids):
        failures.append("manifest: approved/quarantine sample overlap")
    required_quarantine = REQUIRED_QUARANTINE - set(quarantine_ids)
    if required_quarantine:
        failures.append("manifest: required quarantine records missing")
    for record in quarantine:
        if (
            not isinstance(record, dict)
            or record.get("status") != "QUARANTINED"
            or not str(record.get("reason", "")).strip()
            or record.get("no_image_copied") is not True
        ):
            failures.append("quarantine: invalid status, reason, or image-copy flag")
    source_hashes = [
        item.get("source", {}).get("sha256") for item in samples if isinstance(item, dict)
    ]
    sanitized_hashes = [
        item.get("sanitized", {}).get("sha256") for item in samples if isinstance(item, dict)
    ]
    if len(source_hashes) != len(set(source_hashes)) or len(sanitized_hashes) != len(
        set(sanitized_hashes)
    ):
        failures.append("manifest: duplicate approved image content")
    page_keys = []
    canonical_urls = []
    original_urls = []
    observed_categories: set[str] = set()
    for sample in samples:
        if not isinstance(sample, dict):
            continue
        source_page = sample.get("source_page", {})
        page_keys.append((source_page.get("pageid"), source_page.get("revision_id")))
        canonical_urls.append(source_page.get("canonical_url"))
        original_urls.append(source_page.get("original_url"))
        observed_categories.update(sample.get("category_tags", []))
        if (
            sample.get("status") != "APPROVED"
            or sample.get("human_review", {}).get("status") != "APPROVED"
        ):
            add(failures, str(sample.get("sample_id", "<unknown>")), "sample is not approved")
        validate_binary(sample, root, failures)
        validate_metadata(sample, root, failures)
    if (
        len(page_keys) != len(set(page_keys))
        or len(canonical_urls) != len(set(canonical_urls))
        or len(original_urls) != len(set(original_urls))
    ):
        failures.append("manifest: duplicate approved page/revision/URL")
    missing_categories = set(manifest.get("required_categories", [])) - observed_categories
    if missing_categories:
        failures.append("manifest: missing required categories")
    if canonical_aggregate(samples) != manifest.get("aggregate", {}).get("sha256"):
        failures.append("manifest: aggregate SHA-256 mismatch")
    return sorted(set(failures))


def write_report(report_path: Path, report: dict[str, Any]) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def run(manifest_path: Path, external_root: Path, report_path: Path) -> int:
    failures: list[str] = []
    manifest: Any = {}
    try:
        manifest = json_load(manifest_path)
    except (UnicodeDecodeError, json.JSONDecodeError, OSError):
        failures.append("manifest: invalid UTF-8 JSON")
    schema_path = manifest_path.with_name(manifest_path.name.replace(".json", ".schema.json"))
    try:
        schema = json_load(schema_path)
    except (UnicodeDecodeError, json.JSONDecodeError, OSError):
        schema = {}
        failures.append("manifest: schema is unavailable or invalid")
    if not external_root.is_dir():
        failures.append("manifest: external evidence root is unavailable")
    elif schema:
        failures.extend(validate_manifest(manifest, external_root, schema))
    report = {
        "validator": "phase1_5_p1a_corpus_v3",
        "status": "PASS" if not failures else "FAIL",
        "corpus_id": manifest.get("corpus_id") if isinstance(manifest, dict) else None,
        "approved_count": manifest.get("approved_count") if isinstance(manifest, dict) else None,
        "quarantine_count": manifest.get("quarantine_count")
        if isinstance(manifest, dict)
        else None,
        "aggregate_sha256": manifest.get("aggregate", {}).get("sha256")
        if isinstance(manifest, dict)
        else None,
        "failure_count": len(sorted(set(failures))),
        "failures": sorted(set(failures)),
    }
    write_report(report_path, report)
    print(f"P1A corpus validation: {report['status']} ({report['failure_count']} failures)")
    return 0 if not failures else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--external-root", type=Path)
    parser.add_argument(
        "--evidence-root", type=Path, help="Deprecated compatibility alias for --external-root"
    )
    parser.add_argument("--report-json", type=Path)
    arguments = parser.parse_args()
    external_root = arguments.external_root or arguments.evidence_root
    if not arguments.manifest or not external_root or not arguments.report_json:
        parser.error(
            "--manifest, --external-root (or --evidence-root), and --report-json are required"
        )
    return run(
        arguments.manifest.resolve(), external_root.resolve(), arguments.report_json.resolve()
    )


if __name__ == "__main__":
    raise SystemExit(main())
