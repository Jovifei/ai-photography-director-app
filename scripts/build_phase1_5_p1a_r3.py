#!/usr/bin/env python3
"""Build the immutable external P1A r3 corpus from reviewed public inputs.

The script deliberately has no network capability.  Network material must already be
in the approved scratch root, where its retrieval was separately recorded.  It refuses
to replace an evidence root and only emits public-corpus material outside Git.
"""

from __future__ import annotations

import hashlib
import json
import shutil
import sys
from datetime import UTC, datetime
from html import unescape
from pathlib import Path
from re import sub
from typing import Any

from PIL import Image


R2_ROOT = Path("E:/project/_benchmark_evidence/phase1-5-p1a/public-corpus-20260727T150809Z-r2")
SCRATCH = Path("E:/Claude_allow/Download/phase1-5-p1a/20260729T010500Z")
PROJECT_ROOT = Path(__file__).resolve().parent.parent
R3_ROOT = Path("E:/project/_benchmark_evidence/phase1-5-p1a/public-corpus-20260729T015100Z-r3")
R2_MANIFEST = R2_ROOT / "manifests" / "public_corpus_manifest.v2.json"
R3_MANIFEST = "public_corpus_manifest.v3.json"
UTC_NOW = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")

NEW_SAMPLES = {
    "p1a-030": {
        "image": SCRATCH / "candidates" / "p1a-030-candidate.jpg",
        "metadata": SCRATCH / "metadata" / "p1a-030-exact-revision-valid.json",
        "tags": [
            "architecture",
            "indoor_window",
            "minimal_background",
            "negative_space",
            "no_person",
            "side_light",
        ],
        "review": {
            "status": "APPROVED",
            "review_scope": "public-corpus suitability, human presence, privacy, and category match",
            "human_presence": "NONE",
            "person_count": 0,
            "full_body_count": 0,
            "age_evidence_basis": "NOT_APPLICABLE",
            "age_evidence": "No human subject is present after visual review.",
            "nonvisual_source": "https://commons.wikimedia.org/w/index.php?title=File:Interior_of_the_Warsaw_Public_Library_01.jpg&oldid=467381862",
            "no_person": True,
            "note": "A reviewed public Warsaw library interior with no person, address, plate, or private residential content observed.",
        },
    },
    "p1a-031": {
        "image": SCRATCH / "candidates" / "p1a-031-michael-foale.jpg",
        "metadata": SCRATCH / "metadata" / "p1a-031-exact-revision-valid.json",
        "tags": ["cool_color", "portrait_close_up", "single_person_half_body"],
        "review": {
            "status": "APPROVED",
            "review_scope": "public-corpus suitability, human presence, age evidence, privacy, and category match",
            "human_presence": "VISIBLE_DOCUMENTED_ADULT",
            "person_count": 1,
            "full_body_count": 0,
            "age_evidence_basis": "OFFICIAL_SOURCE_DOCUMENTED_ADULT",
            "age_evidence": "NASA records C. Michael Foale as born in 1957; the exact 2013 official NASA portrait therefore depicts an adult.",
            "nonvisual_source": "https://www.nasa.gov/history/SP-4225/documentation/mir-summaries/nasa5/biographies/biographies.htm",
            "no_person": False,
            "note": "One documented adult in a public NASA portrait; the frame is half-body, not a full-body sample.",
        },
    },
    "p1a-032": {
        "image": SCRATCH / "candidates" / "p1a-032-gordon-cooper.jpg",
        "metadata": SCRATCH / "metadata" / "p1a-032-exact-revision-valid.json",
        "tags": ["minimal_background", "single_person_full_body", "warm_color"],
        "review": {
            "status": "APPROVED",
            "review_scope": "public-corpus suitability, human presence, age evidence, privacy, and category match",
            "human_presence": "VISIBLE_DOCUMENTED_ADULT",
            "person_count": 1,
            "full_body_count": 1,
            "age_evidence_basis": "OFFICIAL_SOURCE_DOCUMENTED_ADULT",
            "age_evidence": "NASA records L. Gordon Cooper Jr. as born in 1927; the exact 1963 full-length NASA portrait therefore depicts an adult.",
            "nonvisual_source": "https://www.nasa.gov/people/leroy-gordon-cooper-jr/",
            "no_person": False,
            "note": "One documented adult is visibly full-length; no private source, minor, or sensitive subject is used.",
        },
    },
}

PD_SPECS = {
    "p1a-012": {
        "public_domain_basis": "Exact revision uses PD-USGov-FSA for a U.S. Office of War Information/FSA photograph transferred to the Library of Congress.",
        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Template:PD-USGov-FSA",
        "license_or_rights_statement_url": "https://www.loc.gov/pictures/collection/fsac/",
        "official_source_record": "https://www.loc.gov/pictures/item/2017878866/",
        "restrictions": "No known restrictions on publication.",
    },
    "p1a-027": {
        "public_domain_basis": "Exact revision uses PD-USGov-HHS-NIH; the National Cancer Institute record says reuse restrictions are none.",
        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Template:PD-USGov-HHS-NIH",
        "license_or_rights_statement_url": "https://visualsonline.cancer.gov/details.cfm?imageid=7476",
        "official_source_record": "https://visualsonline.cancer.gov/details.cfm?imageid=7476",
        "restrictions": "Reuse restrictions: none; credit the source and/or author.",
    },
    "p1a-028": {
        "public_domain_basis": "Exact revision uses PD-two, PD-New Zealand, and PD-US-1996 for Albert Percy Godber (1875-1949), with a Public Domain Mark statement.",
        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Template:PD-New_Zealand",
        "license_or_rights_statement_url": "https://tiaki.natlib.govt.nz/#details=ecatalogue.314082",
        "official_source_record": "https://tiaki.natlib.govt.nz/#details=ecatalogue.314082",
        "restrictions": "Any publication must carry the credit Godber Collection, Alexander Turnbull Library.",
    },
    "p1a-029": {
        "public_domain_basis": "Exact revision uses PD-USGov-NASA for NASA Johnson Space Center photo iss074e0403696.",
        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Template:PD-USGov-NASA",
        "license_or_rights_statement_url": "https://www.nasa.gov/image-article/nasa-astronauts-conduct-spacewalk/",
        "official_source_record": "https://images.nasa.gov/details/iss074e0403696",
        "restrictions": "NASA identifiers, insignia, and emblems have separate use rules; no copyright restriction is asserted for this federal work.",
    },
    "p1a-031": {
        "public_domain_basis": "Exact revision uses PD-USGov-NASA for NASA JSC image JSC2013-E-031458.",
        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Template:PD-USGov-NASA",
        "license_or_rights_statement_url": "https://www.nasa.gov/image-article/nasa-media-usage-guidelines/",
        "official_source_record": "https://images.nasa.gov/details/JSC2013-E-031458",
        "restrictions": "NASA identifiers, insignia, and emblems have separate use rules; no copyright restriction is asserted for this federal work.",
    },
    "p1a-032": {
        "public_domain_basis": "Exact revision uses PD-USGov-NASA for NASA JSC image S63-01755.",
        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Template:PD-USGov-NASA",
        "license_or_rights_statement_url": "https://www.nasa.gov/image-article/nasa-media-usage-guidelines/",
        "official_source_record": "https://images.nasa.gov/details/S63-01755",
        "restrictions": "NASA identifiers, insignia, and emblems have separate use rules; no copyright restriction is asserted for this federal work.",
    },
}

EXACT_RAW = {
    "p1a-012": SCRATCH / "metadata" / "p1a-012-exact-revision-valid.json",
    "p1a-027": SCRATCH / "metadata" / "p1a-027-exact-revision-valid.json",
    "p1a-028": SCRATCH / "metadata" / "p1a-028-exact-revision-valid.json",
    "p1a-029": SCRATCH / "metadata" / "p1a-029-exact-revision-valid.json",
}


def fail(message: str) -> None:
    raise SystemExit(f"ERROR: {message}")


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, body: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def metadata_binding(root: Path, kind: str, path: Path) -> dict[str, Any]:
    return {
        "evidence_type": kind,
        "relative_path": path.relative_to(root).as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def strip_html(value: Any) -> str:
    return unescape(sub(r"<[^>]*>", "", str(value or ""))).strip()


def image_properties(path: Path) -> tuple[str, list[int]]:
    with Image.open(path) as image:
        return Image.MIME.get(image.format, ""), list(image.size)


def https_license(license_data: dict[str, Any], sample_id: str) -> dict[str, Any]:
    result = dict(license_data)
    fallback = PD_SPECS.get(sample_id, {}).get(
        "public_domain_basis_url", "https://commons.wikimedia.org/wiki/Commons:Licensing"
    )
    url = str(result.get("url") or fallback)
    if url.startswith("http://"):
        url = "https://" + url[len("http://") :]
    result["url"] = url
    attribution = dict(result.get("attribution", {}))
    attribution_url = str(attribution.get("license_url") or url)
    if attribution_url.startswith("http://"):
        attribution_url = "https://" + attribution_url[len("http://") :]
    attribution["license_url"] = attribution_url
    result["attribution"] = attribution
    return result


def api_parts(raw: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    pages = raw.get("query", {}).get("pages", [])
    if len(pages) != 1:
        fail("exact metadata did not contain one Commons page")
    page = pages[0]
    image_info = page.get("imageinfo", [])
    revisions = page.get("revisions", [])
    if len(image_info) != 1 or len(revisions) != 1:
        fail("exact metadata is missing image information or revision")
    return page, image_info[0], revisions[0]


def request_url(revision_id: int) -> str:
    return (
        "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
        f"&revids={revision_id}&prop=imageinfo%7Crevisions&iiprop=url%7Csize%7Cmime%7Csha1%7Ctimestamp%7Cextmetadata"
        "&rvprop=ids%7Ctimestamp%7Ccontent&rvslots=main"
    )


def source_page_from_raw(
    raw: dict[str, Any], api_hash: str
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    page, image_info, revision = api_parts(raw)
    metadata_url = request_url(int(revision["revid"]))
    ext = image_info.get("extmetadata", {})
    return (
        {
            "pageid": page["pageid"],
            "title": page["title"],
            "revision_id": revision["revid"],
            "revision_timestamp": revision["timestamp"],
            "canonical_url": image_info["descriptionurl"],
            "original_url": image_info["url"],
            "mime": image_info["mime"],
            "width": image_info["width"],
            "height": image_info["height"],
            "artist": strip_html(ext.get("Artist", {}).get("value")),
            "credit": strip_html(ext.get("Credit", {}).get("value")),
            "attribution": "",
            "commons_api_sha256": api_hash,
            "metadata_request_url": metadata_url,
            "metadata_request_final_url": metadata_url,
            "retrieved_utc": UTC_NOW,
        },
        revision,
        ext,
    )


def make_derivative(source: Path, target: Path) -> None:
    with Image.open(source) as image:
        image.convert("RGB").save(
            target, format="JPEG", quality=92, optimize=True, progressive=True
        )


def license_evidence(
    sample_id: str,
    source_page: dict[str, Any],
    ext: dict[str, Any],
    revision: dict[str, Any],
    license_id: str,
) -> dict[str, Any]:
    wikitext = revision.get("slots", {}).get("main", {}).get("content", "")
    evidence = {
        "sample_id": sample_id,
        "source_page_revision_id": source_page["revision_id"],
        "source_page_url": source_page["canonical_url"],
        "commons_api_sha256": source_page["commons_api_sha256"],
        "license_id": license_id,
        "license_name": str(ext.get("LicenseShortName", {}).get("value") or license_id),
        "license_url": str(
            ext.get("LicenseUrl", {}).get("value")
            or "https://commons.wikimedia.org/wiki/Commons:Licensing"
        ),
        "usage_terms": strip_html(ext.get("UsageTerms", {}).get("value")),
        "copyrighted": str(ext.get("Copyrighted", {}).get("value")),
        "restrictions": strip_html(ext.get("Restrictions", {}).get("value"))
        or "No additional restriction recorded in Commons metadata.",
        "artist": source_page["artist"],
        "credit": source_page["credit"],
        "attribution": {
            "author": source_page["artist"] or "See exact Commons revision",
            "title": source_page["title"],
            "page_url": source_page["canonical_url"],
            "license_name": str(ext.get("LicenseShortName", {}).get("value") or license_id),
            "license_url": str(
                ext.get("LicenseUrl", {}).get("value")
                or "https://commons.wikimedia.org/wiki/Commons:Licensing"
            ),
            "modified": "Sanitized JPEG derivative; EXIF and source metadata removed.",
            "recommended_attribution": source_page["credit"]
            or source_page["artist"]
            or "See source page",
        },
        "modification_notice": "Sanitized JPEG derivative; EXIF and source metadata removed.",
        "exact_revision_wikitext_sha256": hashlib.sha256(wikitext.encode("utf-8")).hexdigest(),
        "exact_revision_wikitext": wikitext,
    }
    if license_id == "Public-Domain":
        if sample_id not in PD_SPECS:
            fail(f"missing Public Domain specification for {sample_id}")
        evidence.update(PD_SPECS[sample_id])
        evidence["restrictions"] = PD_SPECS[sample_id]["restrictions"]
    return evidence


def add_sample(
    root: Path, sample: dict[str, Any], raw_path: Path, source_file: Path, source_from_r2: bool
) -> dict[str, Any]:
    sample_id = sample["sample_id"]
    metadata_dir = root / "metadata" / sample_id
    metadata_dir.mkdir(parents=True, exist_ok=False)
    raw_target = metadata_dir / "commons_api.json"
    shutil.copy2(raw_path, raw_target)
    api_hash = sha256(raw_target)
    raw = read_json(raw_target)
    if raw_path in EXACT_RAW.values() or sample_id in NEW_SAMPLES:
        source_page, revision, ext = source_page_from_raw(raw, api_hash)
    else:
        source_page, revision, ext = source_page_from_raw(raw, api_hash)
        source_page["retrieved_utc"] = sample["source_page"]["retrieved_utc"]
        previous_transport = read_json(R2_ROOT / "metadata" / sample_id / "transport.json")[
            "metadata_request"
        ]
        source_page["metadata_request_url"] = previous_transport["url"]
        source_page["metadata_request_final_url"] = previous_transport["final_url"]

    source_target = root / "source" / f"{sample_id}{source_file.suffix.lower()}"
    sanitized_target = root / "sanitized" / f"{sample_id}.jpg"
    source_target.parent.mkdir(parents=True, exist_ok=True)
    sanitized_target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, source_target)
    if source_from_r2:
        shutil.copy2(R2_ROOT / sample["sanitized"]["relative_path"], sanitized_target)
    else:
        make_derivative(source_target, sanitized_target)

    source_record = dict(
        source_page, sample_id=sample_id, source_page_revision_id=source_page["revision_id"]
    )
    write_json(metadata_dir / "source_page_record.json", source_record)
    license_id = sample["license"]["id"]
    if source_from_r2 and sample_id not in EXACT_RAW:
        license_body = read_json(R2_ROOT / "metadata" / sample_id / "license_evidence.json")
        license_body.update(
            {
                "sample_id": sample_id,
                "source_page_revision_id": source_page["revision_id"],
                "source_page_url": source_page["canonical_url"],
                "commons_api_sha256": api_hash,
            }
        )
    else:
        license_body = license_evidence(sample_id, source_page, ext, revision, license_id)
    write_json(metadata_dir / "license_evidence.json", license_body)
    request = {
        "url": source_page["metadata_request_url"],
        "final_url": source_page["metadata_request_final_url"],
        "status": 200,
        "content_type": "application/json",
        "bytes": raw_target.stat().st_size,
        "sha256": api_hash,
        "retrieved_utc": source_page["retrieved_utc"],
        "attempt": 1,
    }
    write_json(
        metadata_dir / "transport.json",
        {
            "sample_id": sample_id,
            "source_page_revision_id": source_page["revision_id"],
            "metadata_request": request,
        },
    )
    review = dict(
        sample["human_review"],
        sample_id=sample_id,
        source_page_revision_id=source_page["revision_id"],
        category_tags=sample["category_tags"],
    )
    write_json(metadata_dir / "manual_visual_review.json", review)

    source_mime, source_dimensions = image_properties(source_target)
    source = dict(sample["source"])
    source.pop("download_transport", None)
    source.pop("downloaded_url", None)
    source["parent_corpus_id"] = sample["source"].get("parent_corpus_id", "phase1-5-p1a-public-corpus-r2")
    source.update(
        {
            "relative_path": source_target.relative_to(root).as_posix(),
            "bytes": source_target.stat().st_size,
            "sha256": sha256(source_target),
            "original_url": source_page["original_url"],
            "width": source_dimensions[0],
            "height": source_dimensions[1],
            "mime": source_mime,
        }
    )
    sanitized = dict(sample["sanitized"])
    sanitized.update(
        {
            "relative_path": sanitized_target.relative_to(root).as_posix(),
            "bytes": sanitized_target.stat().st_size,
            "sha256": sha256(sanitized_target),
            "dimensions": list(Image.open(sanitized_target).size),
            "source_dimensions": source_dimensions,
        }
    )
    if source_from_r2:
        sanitized["source_orientation"] = sample["sanitized"].get("source_orientation", 1)
    else:
        sanitized.update(
            {
                "source_orientation": 1,
                "orientation_normalized": True,
                "crop_applied": False,
                "exif_removed": True,
                "gps_removed": True,
                "device_metadata_removed": True,
                "software_metadata_removed": True,
            }
        )
    result = dict(
        sample,
        source=source,
        sanitized=sanitized,
        source_page=source_page,
        license=https_license(sample["license"], sample_id),
    )
    for legacy_field in ("license_evidence_relative_path", "license_evidence_sha256", "metadata_relative_paths"):
        result.pop(legacy_field, None)
    result["metadata_evidence"] = [
        metadata_binding(root, "COMMONS_API", metadata_dir / "commons_api.json"),
        metadata_binding(root, "SOURCE_PAGE_RECORD", metadata_dir / "source_page_record.json"),
        metadata_binding(root, "LICENSE_EVIDENCE", metadata_dir / "license_evidence.json"),
        metadata_binding(root, "TRANSPORT", metadata_dir / "transport.json"),
        metadata_binding(root, "MANUAL_VISUAL_REVIEW", metadata_dir / "manual_visual_review.json"),
    ]
    return result


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
                "human_presence": sample["human_review"]["human_presence"],
                "category_tags": sorted(sample["category_tags"]),
                "metadata_evidence": sorted(
                    (
                        {"evidence_type": item["evidence_type"], "sha256": item["sha256"]}
                        for item in sample["metadata_evidence"]
                    ),
                    key=lambda item: item["evidence_type"],
                ),
            }
        )
    return hashlib.sha256(
        json.dumps(projection, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
            "utf-8"
        )
    ).hexdigest()


def main() -> int:
    if R3_ROOT.exists():
        fail("refusing to replace an existing r3 evidence root")
    if not R2_MANIFEST.is_file():
        fail("r2 manifest is absent")
    for item in NEW_SAMPLES.values():
        if not item["image"].is_file() or not item["metadata"].is_file():
            fail("reviewed new-sample scratch input is absent")
    for path in EXACT_RAW.values():
        if not path.is_file():
            fail("exact Public Domain revision input is absent")
    R3_ROOT.mkdir(parents=True, exist_ok=False)
    r2 = read_json(R2_MANIFEST)
    rejected = {"p1a-006", "p1a-025"}
    approved: list[dict[str, Any]] = []
    for previous in r2["samples"]:
        if previous["sample_id"] in rejected:
            continue
        sample_id = previous["sample_id"]
        raw = EXACT_RAW.get(sample_id, R2_ROOT / "metadata" / sample_id / "commons_api.json")
        source = R2_ROOT / previous["source"]["relative_path"]
        approved.append(add_sample(R3_ROOT, dict(previous), raw, source, True))
    for sample_id, spec in NEW_SAMPLES.items():
        raw = read_json(spec["metadata"])
        page, image_info, revision = api_parts(raw)
        ext = image_info.get("extmetadata", {})
        license_id = (
            "CC0" if str(ext.get("LicenseShortName", {}).get("value")) == "CC0" else "Public-Domain"
        )
        placeholder = {
            "sample_id": sample_id,
            "title": page["title"],
            "status": "APPROVED",
            "category_tags": spec["tags"],
            "source": {
                "relative_path": "",
                "sha256": "",
                "bytes": 1,
                "mime": image_info["mime"],
                "representation": "original-public-image",
                "original_url": image_info["url"],
                "parent_corpus_id": r2["corpus_id"],
                "width": image_info["width"],
                "height": image_info["height"],
            },
            "sanitized": {
                "relative_path": "",
                "format": "JPEG",
                "mime": "image/jpeg",
                "bytes": 1,
                "sha256": "",
                "dimensions": [1, 1],
                "source_dimensions": [1, 1],
                "source_orientation": 1,
                "orientation_normalized": True,
                "crop_applied": False,
                "exif_removed": True,
                "gps_removed": True,
                "device_metadata_removed": True,
                "software_metadata_removed": True,
            },
            "source_page": {},
            "license": {
                "id": license_id,
                "name": str(ext.get("LicenseShortName", {}).get("value") or license_id),
                "url": str(
                    ext.get("LicenseUrl", {}).get("value")
                    or "https://commons.wikimedia.org/wiki/Commons:Licensing"
                ),
                "attribution": {
                    "author": strip_html(ext.get("Artist", {}).get("value"))
                    or "See exact Commons revision",
                    "title": page["title"],
                    "page_url": image_info["descriptionurl"],
                    "license_name": str(ext.get("LicenseShortName", {}).get("value") or license_id),
                    "license_url": str(
                        ext.get("LicenseUrl", {}).get("value")
                        or "https://commons.wikimedia.org/wiki/Commons:Licensing"
                    ),
                    "modified": "Sanitized JPEG derivative; EXIF and source metadata removed.",
                    "recommended_attribution": strip_html(ext.get("Credit", {}).get("value"))
                    or "See source page",
                },
            },
            "human_review": spec["review"],
            "metadata_evidence": [],
        }
        approved.append(add_sample(R3_ROOT, placeholder, spec["metadata"], spec["image"], False))
    approved.sort(key=lambda item: item["sample_id"])
    prior_by_id = {item["sample_id"]: item for item in r2["samples"]}
    quarantine = []
    for item in r2["quarantine"]:
        quarantine.append(
            {
                "sample_id": item["sample_id"],
                "status": "QUARANTINED",
                "reason": item["reason"],
                "old_source_sha256": item["old_source_sha256"],
                "old_sanitized_sha256": item["old_sanitized_sha256"],
                "no_image_copied": True,
            }
        )
    quarantine.extend(
        [
            {
                "sample_id": "p1a-006",
                "status": "QUARANTINED",
                "reason": "Bedroom/private-interior content conflicts with the earlier no-private-space manual review; retained only as r2 audit history.",
                "old_source_sha256": prior_by_id["p1a-006"]["source"]["sha256"],
                "old_sanitized_sha256": prior_by_id["p1a-006"]["sanitized"]["sha256"],
                "no_image_copied": True,
            },
            {
                "sample_id": "p1a-025",
                "status": "QUARANTINED",
                "reason": "The official source imposes usage/contact conditions, so Public Domain license closure fails despite the 82-year nonvisual age evidence.",
                "old_source_sha256": prior_by_id["p1a-025"]["source"]["sha256"],
                "old_sanitized_sha256": prior_by_id["p1a-025"]["sanitized"]["sha256"],
                "no_image_copied": True,
            },
        ]
    )
    quarantine.sort(key=lambda item: item["sample_id"])
    manifest = {
        "schema_version": "3.0.0",
        "corpus_id": "phase1-5-p1a-public-corpus-20260729T015100Z-r3",
        "parent_corpus_id": r2["corpus_id"],
        "supersedes_reason": "r3 rebinding: complete five-evidence bindings; p1a-006 private-bedroom and p1a-025 license conflict quarantined.",
        "status": "CORPUS_R3_VALIDATED",
        "external_evidence_root_id": R3_ROOT.name,
        "source_repository": "Wikimedia Commons public files with exact revision metadata",
        "approved_count": len(approved),
        "quarantine_count": len(quarantine),
        "minimum_required_count": 20,
        "allowed_license_ids": ["CC0", "Public-Domain", "CC-BY-4.0"],
        "privacy_policy": {"owner_media": "prohibited"},
        "age_policy": {"approved_human_presence": ["NONE", "VISIBLE_DOCUMENTED_ADULT"]},
        "required_categories": r2["required_categories"],
        "samples": approved,
        "quarantine": quarantine,
        "aggregate": {
            "algorithm": "sha256/canonical-json-v3",
            "sha256": canonical_aggregate(approved),
        },
    }
    manifests = R3_ROOT / "manifests"
    manifests.mkdir(parents=True, exist_ok=True)
    shutil.copy2(
        PROJECT_ROOT / "docs" / "phase1_5" / "p1a" / "public_corpus_manifest.v3.schema.json",
        manifests / "public_corpus_manifest.v3.schema.json",
    )
    write_json(manifests / R3_MANIFEST, manifest)
    write_json(
        R3_ROOT / "reports" / "generation_summary.json",
        {
            "status": "PASS",
            "approved_count": len(approved),
            "quarantine_count": len(quarantine),
            "model_body_downloaded": False,
            "owner_media_accessed": False,
            "aggregate_sha256": manifest["aggregate"]["sha256"],
        },
    )
    print(
        json.dumps(
            {
                "status": "PASS",
                "root_id": R3_ROOT.name,
                "approved_count": len(approved),
                "quarantine_count": len(quarantine),
                "aggregate_sha256": manifest["aggregate"]["sha256"],
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
