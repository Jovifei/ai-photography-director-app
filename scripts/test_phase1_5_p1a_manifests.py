#!/usr/bin/env python3
"""Offline schema, manifest, integration, and negative tests for P1A v3."""

from __future__ import annotations

import copy
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image
from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parent.parent
P1A = ROOT / "docs" / "phase1_5" / "p1a"
VALIDATOR = ROOT / "scripts" / "validate_phase1_5_p1a_corpus.py"
sys.path.insert(0, str(ROOT / "scripts"))
from validate_phase1_5_p1a_corpus import canonical_aggregate  # noqa: E402


CATEGORIES = [
    "city_night",
    "indoor_window",
    "seaside",
    "forest",
    "street",
    "cafe_or_lifestyle_interior",
    "architecture",
    "backlight",
    "side_light",
    "low_light",
    "single_person_full_body",
    "single_person_half_body",
    "portrait_close_up",
    "two_people",
    "no_person",
    "dynamic_action",
    "occlusion",
    "complex_background",
    "minimal_background",
    "strong_color",
    "cool_color",
    "warm_color",
    "symmetry",
    "negative_space",
]
EVIDENCE_TYPES = [
    "COMMONS_API",
    "SOURCE_PAGE_RECORD",
    "LICENSE_EVIDENCE",
    "TRANSPORT",
    "MANUAL_VISUAL_REVIEW",
]


def file_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, body: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def metadata_binding(root: Path, evidence_type: str, relative_path: str) -> dict:
    path = root / relative_path
    return {
        "evidence_type": evidence_type,
        "relative_path": relative_path,
        "bytes": path.stat().st_size,
        "sha256": file_hash(path),
    }


def build_fixture(base: Path) -> tuple[Path, Path, Path]:
    root = base / "public-corpus-20260727T000000Z-r3"
    manifest_dir = base / "manifest"
    report = base / "reports" / "validator.json"
    samples = []
    for ordinal in range(20):
        sample_id = f"p1a-{101 + ordinal:03d}"
        source = root / "source" / f"{sample_id}.jpg"
        sanitized = root / "sanitized" / f"{sample_id}.jpg"
        source.parent.mkdir(parents=True, exist_ok=True)
        sanitized.parent.mkdir(parents=True, exist_ok=True)
        color = ((ordinal * 17 + 20) % 255, (ordinal * 29 + 40) % 255, (ordinal * 41 + 60) % 255)
        Image.new("RGB", (12, 12), color).save(source, "JPEG", quality=96)
        Image.new("RGB", (12, 12), color).save(sanitized, "JPEG", quality=82)
        tags = ["no_person"]
        human = {
            "status": "APPROVED",
            "review_scope": "fixture review",
            "human_presence": "NONE",
            "person_count": 0,
            "full_body_count": 0,
            "age_evidence_basis": "NOT_APPLICABLE",
            "age_evidence": "No human subject.",
            "nonvisual_source": f"https://commons.wikimedia.org/wiki/File:Fixture_{ordinal}",
            "no_person": True,
            "note": "No private-space or identifier observed.",
        }
        if ordinal == 0:
            tags = ["single_person_full_body", "single_person_half_body", "portrait_close_up"]
            human.update(
                {
                    "human_presence": "VISIBLE_DOCUMENTED_ADULT",
                    "person_count": 1,
                    "full_body_count": 1,
                    "age_evidence_basis": "OFFICIAL_SOURCE_DOCUMENTED_ADULT",
                    "age_evidence": "Official source calls the subject an adult.",
                    "no_person": False,
                }
            )
        if ordinal == 1:
            tags = ["two_people", "dynamic_action", "complex_background"]
            human.update(
                {
                    "human_presence": "VISIBLE_DOCUMENTED_ADULT",
                    "person_count": 2,
                    "full_body_count": 0,
                    "age_evidence_basis": "OFFICIAL_SOURCE_DOCUMENTED_ADULT",
                    "age_evidence": "Official source names two adult professionals.",
                    "no_person": False,
                }
            )
        metadata = root / "metadata" / sample_id
        canonical = f"https://commons.wikimedia.org/wiki/File:Fixture_{ordinal}"
        original = f"https://upload.wikimedia.org/example-{ordinal}.jpg"
        raw_api = {
            "query": {
                "pages": [
                    {
                        "pageid": 1000 + ordinal,
                        "title": f"File:Fixture {ordinal}",
                        "imageinfo": [
                            {"url": original, "descriptionurl": canonical, "mime": "image/jpeg"}
                        ],
                        "revisions": [{"revid": 2000 + ordinal}],
                    }
                ]
            }
        }
        write_json(metadata / "commons_api.json", raw_api)
        api_hash = file_hash(metadata / "commons_api.json")
        metadata_url = f"https://commons.wikimedia.org/w/api.php?fixture={ordinal}"
        source_page = {
            "pageid": 1000 + ordinal,
            "title": f"File:Fixture {ordinal}",
            "revision_id": 2000 + ordinal,
            "revision_timestamp": "2026-07-27T00:00:00Z",
            "canonical_url": canonical,
            "original_url": original,
            "mime": "image/jpeg",
            "width": 12,
            "height": 12,
            "artist": "Fixture",
            "credit": "Fixture credit",
            "attribution": "",
            "commons_api_sha256": api_hash,
            "metadata_request_url": metadata_url,
            "metadata_request_final_url": metadata_url,
            "retrieved_utc": "2026-07-27T00:00:00Z",
        }
        source_record = dict(
            source_page, sample_id=sample_id, source_page_revision_id=2000 + ordinal
        )
        write_json(metadata / "source_page_record.json", source_record)
        attribution = {
            "author": "Fixture",
            "title": f"Fixture {ordinal}",
            "page_url": canonical,
            "license_name": "CC0",
            "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
            "modified": "metadata removed",
            "recommended_attribution": "Fixture",
        }
        evidence = {
            "sample_id": sample_id,
            "license_id": "CC0",
            "license_name": "CC0",
            "license_url": attribution["license_url"],
            "usage_terms": "CC0",
            "copyrighted": "True",
            "restrictions": "None",
            "artist": "Fixture",
            "credit": "Fixture credit",
            "attribution": attribution,
            "source_page_revision_id": 2000 + ordinal,
            "source_page_url": canonical,
            "commons_api_sha256": api_hash,
            "public_domain_basis": "NOT_APPLICABLE",
            "modification_notice": "metadata removed",
        }
        write_json(metadata / "license_evidence.json", evidence)
        transport = {
            "sample_id": sample_id,
            "source_page_revision_id": 2000 + ordinal,
            "metadata_request": {
                "url": metadata_url,
                "final_url": metadata_url,
                "status": 200,
                "content_type": "application/json",
                "bytes": (metadata / "commons_api.json").stat().st_size,
                "sha256": api_hash,
                "retrieved_utc": "2026-07-27T00:00:00Z",
                "attempt": 1,
            },
        }
        write_json(metadata / "transport.json", transport)
        manual = dict(
            human, sample_id=sample_id, source_page_revision_id=2000 + ordinal, category_tags=tags
        )
        write_json(metadata / "manual_visual_review.json", manual)
        bindings = [
            metadata_binding(root, "COMMONS_API", f"metadata/{sample_id}/commons_api.json"),
            metadata_binding(
                root, "SOURCE_PAGE_RECORD", f"metadata/{sample_id}/source_page_record.json"
            ),
            metadata_binding(
                root, "LICENSE_EVIDENCE", f"metadata/{sample_id}/license_evidence.json"
            ),
            metadata_binding(root, "TRANSPORT", f"metadata/{sample_id}/transport.json"),
            metadata_binding(
                root, "MANUAL_VISUAL_REVIEW", f"metadata/{sample_id}/manual_visual_review.json"
            ),
        ]
        samples.append(
            {
                "sample_id": sample_id,
                "title": f"Fixture {ordinal}",
                "status": "APPROVED",
                "category_tags": tags,
                "source": {
                    "relative_path": source.relative_to(root).as_posix(),
                    "sha256": file_hash(source),
                    "bytes": source.stat().st_size,
                    "mime": "image/jpeg",
                    "representation": "fixture",
                    "original_url": original,
                    "parent_corpus_id": "fixture-parent",
                    "width": 12,
                    "height": 12,
                },
                "sanitized": {
                    "relative_path": sanitized.relative_to(root).as_posix(),
                    "format": "JPEG",
                    "mime": "image/jpeg",
                    "sha256": file_hash(sanitized),
                    "bytes": sanitized.stat().st_size,
                    "dimensions": [12, 12],
                    "source_dimensions": [12, 12],
                    "source_orientation": 1,
                    "orientation_normalized": False,
                    "crop_applied": False,
                    "exif_removed": True,
                    "gps_removed": True,
                    "device_metadata_removed": True,
                    "software_metadata_removed": True,
                },
                "source_page": source_page,
                "license": {
                    "id": "CC0",
                    "name": "CC0",
                    "url": attribution["license_url"],
                    "attribution": attribution,
                },
                "human_review": human,
                "metadata_evidence": bindings,
            }
        )
    person_category_target = {
        "single_person_full_body": 0,
        "single_person_half_body": 0,
        "portrait_close_up": 0,
        "two_people": 1,
        "dynamic_action": 1,
        "complex_background": 1,
    }
    present = {tag for item in samples for tag in item["category_tags"]}
    for index, category in enumerate(CATEGORIES):
        if category not in present:
            target = person_category_target.get(category, 2 + (index % 18))
            samples[target]["category_tags"].append(category)
            manual_path = root / next(
                item["relative_path"]
                for item in samples[target]["metadata_evidence"]
                if item["evidence_type"] == "MANUAL_VISUAL_REVIEW"
            )
            manual = read_json(manual_path)
            manual["category_tags"] = samples[target]["category_tags"]
            write_json(manual_path, manual)
            binding = next(
                item
                for item in samples[target]["metadata_evidence"]
                if item["evidence_type"] == "MANUAL_VISUAL_REVIEW"
            )
            binding.update(metadata_binding(root, "MANUAL_VISUAL_REVIEW", binding["relative_path"]))
    quarantine = [
        {
            "sample_id": item,
            "status": "QUARANTINED",
            "reason": "fixture quarantine",
            "old_source_sha256": "b" * 64,
            "old_sanitized_sha256": "c" * 64,
            "no_image_copied": True,
        }
        for item in ("p1a-006", "p1a-008", "p1a-019", "p1a-023", "p1a-024")
    ]
    manifest = {
        "schema_version": "3.0.0",
        "corpus_id": "phase1-5-p1a-public-corpus-20260727T000000Z-r3",
        "parent_corpus_id": "fixture-parent",
        "supersedes_reason": "fixture",
        "status": "CORPUS_R3_VALIDATED",
        "external_evidence_root_id": root.name,
        "source_repository": "fixture",
        "approved_count": 20,
        "quarantine_count": len(quarantine),
        "minimum_required_count": 20,
        "allowed_license_ids": ["CC0", "Public-Domain", "CC-BY-4.0"],
        "privacy_policy": {"owner_media": "prohibited"},
        "age_policy": {"approved_human_presence": ["NONE", "VISIBLE_DOCUMENTED_ADULT"]},
        "required_categories": CATEGORIES,
        "samples": samples,
        "quarantine": quarantine,
        "aggregate": {
            "algorithm": "sha256/canonical-json-v3",
            "sha256": canonical_aggregate(samples),
        },
    }
    manifest_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(
        P1A / "public_corpus_manifest.v3.schema.json",
        manifest_dir / "public_corpus_manifest.v3.schema.json",
    )
    write_json(manifest_dir / "public_corpus_manifest.v3.json", manifest)
    return root, manifest_dir / "public_corpus_manifest.v3.json", report


class P1AValidatorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root, self.manifest_path, self.report = build_fixture(Path(self.temp.name))

    def tearDown(self) -> None:
        self.temp.cleanup()

    def invoke(self) -> tuple[int, dict]:
        result = subprocess.run(
            [
                sys.executable,
                "-B",
                str(VALIDATOR),
                "--manifest",
                str(self.manifest_path),
                "--external-root",
                str(self.root),
                "--report-json",
                str(self.report),
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        return result.returncode, read_json(self.report)

    def mutate(self, operation) -> tuple[int, dict]:
        body = read_json(self.manifest_path)
        operation(body)
        write_json(self.manifest_path, body)
        return self.invoke()

    def binding(self, body: dict, evidence_type: str, sample_index: int = 0) -> dict:
        return next(
            item
            for item in body["samples"][sample_index]["metadata_evidence"]
            if item["evidence_type"] == evidence_type
        )

    def rewrite_metadata(
        self, body: dict, evidence_type: str, operation, sample_index: int = 0, rebind: bool = True
    ) -> None:
        binding = self.binding(body, evidence_type, sample_index)
        path = self.root / binding["relative_path"]
        item = read_json(path)
        operation(item)
        write_json(path, item)
        if rebind:
            binding.update(metadata_binding(self.root, evidence_type, binding["relative_path"]))

    # Preserved original coverage, adapted to the v3 contract.
    def test_schema_and_cli_triple_pass_without_absolute_report_path(self) -> None:
        schema = read_json(P1A / "public_corpus_manifest.v3.schema.json")
        manifest = read_json(self.manifest_path)
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(manifest)))
        code, report = self.invoke()
        self.assertEqual(0, code)
        self.assertEqual("PASS", report["status"])
        self.assertNotIn(str(self.root), json.dumps(report))

    def test_cli_missing_arguments_exit_two(self) -> None:
        result = subprocess.run(
            [sys.executable, "-B", str(VALIDATOR)], text=True, capture_output=True, check=False
        )
        self.assertEqual(2, result.returncode)

    def test_qwen_weight_license_evidence_is_metadata_only_not_authorization(self) -> None:
        evidence = read_json(P1A / "qwen_weight_license_evidence.v1.json")
        schema = read_json(P1A / "qwen_weight_license_evidence.v1.schema.json")
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(evidence)))
        self.assertEqual(
            "MODEL_CARD_METADATA_AT_IMMUTABLE_REVISION", evidence["weight_license_evidence_basis"]
        )
        self.assertEqual("NOT_LEGAL_APPROVED", evidence["legal_review_status"])
        self.assertFalse(evidence["download_authorized"])
        self.assertFalse(evidence["runtime_authorized"])
        self.assertFalse(evidence["inference_authorized"])
        self.assertFalse(evidence["app_integration_authorized"])

    def test_hash_mismatch(self) -> None:
        code, report = self.mutate(
            lambda body: body["samples"][0]["source"].update({"sha256": "0" * 64})
        )
        self.assertEqual(1, code)
        self.assertIn("SHA-256 mismatch", " ".join(report["failures"]))

    def test_decode_failure(self) -> None:
        path = self.root / "sanitized" / "p1a-101.jpg"
        path.write_bytes(b"not-an-image")
        code, report = self.invoke()
        self.assertEqual(1, code)
        self.assertIn("byte mismatch", " ".join(report["failures"]))

    def test_exif_gps_metadata_rejected(self) -> None:
        path = self.root / "sanitized" / "p1a-101.jpg"
        Image.new("RGB", (12, 12), (1, 2, 3)).save(path, "JPEG", exif=b"Exif\\x00\\x00fixture")
        code, report = self.invoke()
        self.assertEqual(1, code)
        self.assertIn("byte mismatch", " ".join(report["failures"]))

    def test_duplicate_content(self) -> None:
        def change(body):
            target, source = body["samples"][1], body["samples"][0]
            target["source"]["sha256"] = source["source"]["sha256"]
            target["sanitized"]["sha256"] = source["sanitized"]["sha256"]

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("duplicate approved", " ".join(report["failures"]))

    def test_no_person_conflict(self) -> None:
        code, report = self.mutate(
            lambda body: body["samples"][0]["category_tags"].append("no_person")
        )
        self.assertEqual(1, code)
        self.assertIn("no_person conflicts", " ".join(report["failures"]))

    def test_age_uncertain_rejected(self) -> None:
        code, report = self.mutate(
            lambda body: body["samples"][0]["human_review"].update(
                {"human_presence": "VISIBLE_AGE_UNCERTAIN"}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))

    def test_quarantine_requires_reason(self) -> None:
        code, report = self.mutate(lambda body: body["quarantine"][0].update({"reason": ""}))
        self.assertEqual(1, code)
        self.assertIn("quarantine", " ".join(report["failures"]))

    def test_full_body_requires_proof(self) -> None:
        def change(body):
            body["samples"][0]["human_review"]["full_body_count"] = 0
            self.rewrite_metadata(
                body, "MANUAL_VISUAL_REVIEW", lambda item: item.update({"full_body_count": 0})
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("full-body proof", " ".join(report["failures"]))

    def test_empty_ccby_attribution(self) -> None:
        def change(body):
            sample = body["samples"][0]
            sample["license"].update(
                {
                    "id": "CC-BY-4.0",
                    "name": "CC-BY",
                    "url": "https://creativecommons.org/licenses/by/4.0/",
                }
            )
            self.rewrite_metadata(
                body,
                "LICENSE_EVIDENCE",
                lambda item: (
                    item.update(
                        {
                            "license_id": "CC-BY-4.0",
                            "license_name": "CC-BY",
                            "license_url": "https://creativecommons.org/licenses/by/4.0/",
                        }
                    ),
                    item["attribution"].update({"author": ""}),
                ),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("CC-BY attribution missing author", " ".join(report["failures"]))

    def test_mojibake_rejected(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body, "LICENSE_EVIDENCE", lambda item: item["attribution"].update({"title": "Ã©"})
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("mojibake", " ".join(report["failures"]))

    def test_aggregate_mismatch(self) -> None:
        code, report = self.mutate(lambda body: body["aggregate"].update({"sha256": "0" * 64}))
        self.assertEqual(1, code)
        self.assertIn("aggregate", " ".join(report["failures"]))

    def test_path_traversal_and_absolute_path(self) -> None:
        code, report = self.mutate(
            lambda body: self.binding(body, "COMMONS_API").update(
                {"relative_path": "../escape.json"}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))
        self.root, self.manifest_path, self.report = build_fixture(Path(self.temp.name) / "second")
        code, report = self.mutate(
            lambda body: self.binding(body, "COMMONS_API").update(
                {"relative_path": "E:/absolute.json"}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))

    # New B3/B5 negative coverage.
    def test_schema_missing_each_required_metadata_type(self) -> None:
        for evidence_type in EVIDENCE_TYPES:
            self.root, self.manifest_path, self.report = build_fixture(
                Path(self.temp.name) / evidence_type
            )
            code, report = self.mutate(
                lambda body, evidence_type=evidence_type: body["samples"][0][
                    "metadata_evidence"
                ].__setitem__(
                    slice(None),
                    [
                        item
                        for item in body["samples"][0]["metadata_evidence"]
                        if item["evidence_type"] != evidence_type
                    ],
                )
            )
            self.assertEqual(1, code, evidence_type)
            self.assertIn("schema", " ".join(report["failures"]))

    def test_negative_metadata_hash_mismatch_each_type(self) -> None:
        for evidence_type in EVIDENCE_TYPES:
            self.root, self.manifest_path, self.report = build_fixture(
                Path(self.temp.name) / ("hash-" + evidence_type)
            )
            code, report = self.mutate(
                lambda body, evidence_type=evidence_type: self.binding(body, evidence_type).update(
                    {"sha256": "0" * 64}
                )
            )
            self.assertEqual(1, code, evidence_type)
            self.assertIn("metadata SHA-256 mismatch", " ".join(report["failures"]))

    def test_negative_metadata_bytes_mismatch_each_type(self) -> None:
        for evidence_type in EVIDENCE_TYPES:
            self.root, self.manifest_path, self.report = build_fixture(
                Path(self.temp.name) / ("bytes-" + evidence_type)
            )
            code, report = self.mutate(
                lambda body, evidence_type=evidence_type: self.binding(body, evidence_type).update(
                    {"bytes": 1}
                )
            )
            self.assertEqual(1, code, evidence_type)
            self.assertIn("metadata byte mismatch", " ".join(report["failures"]))

    def test_metadata_sample_id_mismatch(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body, "MANUAL_VISUAL_REVIEW", lambda item: item.update({"sample_id": "p1a-999"})
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("manual review sample/revision mismatch", " ".join(report["failures"]))

    def test_source_revision_mismatch(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "SOURCE_PAGE_RECORD",
                lambda item: item.update({"source_page_revision_id": 9999, "revision_id": 9999}),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("source-page record revision_id mismatch", " ".join(report["failures"]))

    def test_transport_url_mismatch(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "TRANSPORT",
                lambda item: item["metadata_request"].update({"url": "https://example.com/wrong"}),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("transport URL invalid", " ".join(report["failures"]))

    def test_manual_human_presence_mismatch(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "MANUAL_VISUAL_REVIEW",
                lambda item: item.update(
                    {
                        "human_presence": "NONE",
                        "person_count": 0,
                        "full_body_count": 0,
                        "no_person": True,
                    }
                ),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("manual review human_presence mismatch", " ".join(report["failures"]))

    def test_manual_person_and_full_body_mismatch(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "MANUAL_VISUAL_REVIEW",
                lambda item: item.update({"person_count": 2, "full_body_count": 0}),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("manual review person_count mismatch", " ".join(report["failures"]))

    def test_public_domain_false_basis_rejected(self) -> None:
        def change(body):
            sample = body["samples"][0]
            sample["license"].update(
                {
                    "id": "Public-Domain",
                    "name": "Public Domain",
                    "url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                }
            )
            self.rewrite_metadata(
                body,
                "LICENSE_EVIDENCE",
                lambda item: item.update(
                    {
                        "license_id": "Public-Domain",
                        "license_name": "Public Domain",
                        "license_url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "public_domain_basis": "False",
                        "public_domain_basis_url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "license_or_rights_statement_url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "official_source_record": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "copyrighted": "False",
                        "usage_terms": "Public domain",
                        "restrictions": "None",
                        "artist": "Fixture",
                        "credit": "Fixture",
                        "modification_notice": "metadata removed",
                    }
                ),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("invalid public_domain_basis", " ".join(report["failures"]))

    def test_public_domain_basis_url_required(self) -> None:
        def change(body):
            sample = body["samples"][0]
            sample["license"].update(
                {
                    "id": "Public-Domain",
                    "name": "Public Domain",
                    "url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                }
            )
            self.rewrite_metadata(
                body,
                "LICENSE_EVIDENCE",
                lambda item: item.update(
                    {
                        "license_id": "Public-Domain",
                        "license_name": "Public Domain",
                        "license_url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "public_domain_basis": "Government work",
                        "public_domain_basis_url": "",
                        "license_or_rights_statement_url": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "official_source_record": "https://commons.wikimedia.org/wiki/Commons:Licensing",
                        "copyrighted": "False",
                        "usage_terms": "Public domain",
                        "restrictions": "None",
                        "artist": "Fixture",
                        "credit": "Fixture",
                        "modification_notice": "metadata removed",
                    }
                ),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("invalid public_domain_basis_url", " ".join(report["failures"]))

    def test_duplicate_and_unknown_evidence_type_rejected(self) -> None:
        def duplicate(body):
            body["samples"][0]["metadata_evidence"][1]["evidence_type"] = "COMMONS_API"

        code, report = self.mutate(duplicate)
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))
        self.root, self.manifest_path, self.report = build_fixture(Path(self.temp.name) / "unknown")
        code, report = self.mutate(
            lambda body: body["samples"][0]["metadata_evidence"][1].update(
                {"evidence_type": "UNKNOWN"}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))

    def test_duplicate_metadata_path_rejected(self) -> None:
        code, report = self.mutate(
            lambda body: body["samples"][0]["metadata_evidence"][1].update(
                {"relative_path": body["samples"][0]["metadata_evidence"][0]["relative_path"]}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("duplicate metadata relative path", " ".join(report["failures"]))

    def test_metadata_absolute_and_traversal_path_rejected(self) -> None:
        code, report = self.mutate(
            lambda body: self.binding(body, "TRANSPORT").update(
                {"relative_path": "../transport.json"}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))
        self.root, self.manifest_path, self.report = build_fixture(
            Path(self.temp.name) / "absolute"
        )
        code, report = self.mutate(
            lambda body: self.binding(body, "TRANSPORT").update(
                {"relative_path": "E:/transport.json"}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))

    def test_metadata_invalid_utf8_and_json_rejected(self) -> None:
        binding = self.binding(read_json(self.manifest_path), "COMMONS_API")
        (self.root / binding["relative_path"]).write_bytes(b"\\xff\\xfe")
        code, report = self.invoke()
        self.assertEqual(1, code)
        self.assertIn("not UTF-8 JSON", " ".join(report["failures"]))
        self.root, self.manifest_path, self.report = build_fixture(
            Path(self.temp.name) / "bad-json"
        )
        binding = self.binding(read_json(self.manifest_path), "COMMONS_API")
        (self.root / binding["relative_path"]).write_text("{", encoding="utf-8")
        code, report = self.invoke()
        self.assertEqual(1, code)
        self.assertIn("not UTF-8 JSON", " ".join(report["failures"]))

    def test_duplicate_page_revision_and_urls_rejected(self) -> None:
        def change(body):
            target, source = body["samples"][1], body["samples"][0]
            for field in ("pageid", "revision_id", "canonical_url", "original_url"):
                target["source_page"][field] = source["source_page"][field]

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("duplicate approved page/revision/URL", " ".join(report["failures"]))

    def test_approved_quarantine_overlap_and_p006_required(self) -> None:
        code, report = self.mutate(
            lambda body: body["quarantine"].append(
                copy.deepcopy(body["quarantine"][0])
                | {"sample_id": body["samples"][0]["sample_id"]}
            )
        )
        self.assertEqual(1, code)
        self.assertIn("overlap", " ".join(report["failures"]))
        self.root, self.manifest_path, self.report = build_fixture(
            Path(self.temp.name) / "missing-p006"
        )
        code, report = self.mutate(
            lambda body: body["quarantine"].__setitem__(
                slice(None), [item for item in body["quarantine"] if item["sample_id"] != "p1a-006"]
            )
        )
        self.assertEqual(1, code)
        self.assertIn("required quarantine", " ".join(report["failures"]))

    def test_report_never_echoes_absolute_input_path(self) -> None:
        absolute = str(self.root / "private" / "secret.json")
        code, report = self.mutate(
            lambda body: self.binding(body, "LICENSE_EVIDENCE").update({"relative_path": absolute})
        )
        self.assertEqual(1, code)
        self.assertNotIn(absolute, json.dumps(report))

    def test_transport_content_type_rejected(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "TRANSPORT",
                lambda item: item["metadata_request"].update({"content_type": "text/html"}),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("transport status/content type invalid", " ".join(report["failures"]))

    def test_transport_retrieval_time_rejected(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "TRANSPORT",
                lambda item: item["metadata_request"].update({"retrieved_utc": "not-a-utc"}),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("transport retrieved UTC invalid", " ".join(report["failures"]))

    def test_commons_page_id_mismatch_rejected(self) -> None:
        code, report = self.mutate(
            lambda body: body["samples"][0]["source_page"].update({"pageid": 9999})
        )
        self.assertEqual(1, code)
        self.assertIn("Commons/source-page pageid mismatch", " ".join(report["failures"]))

    def test_license_evidence_source_url_mismatch_rejected(self) -> None:
        def change(body):
            self.rewrite_metadata(
                body,
                "LICENSE_EVIDENCE",
                lambda item: item.update({"source_page_url": "https://example.com/wrong"}),
            )

        code, report = self.mutate(change)
        self.assertEqual(1, code)
        self.assertIn("license evidence source URL mismatch", " ".join(report["failures"]))

    def test_missing_license_evidence_file_rejected(self) -> None:
        body = read_json(self.manifest_path)
        binding = self.binding(body, "LICENSE_EVIDENCE")
        (self.root / binding["relative_path"]).unlink()
        code, report = self.invoke()
        self.assertEqual(1, code)
        self.assertIn("missing LICENSE_EVIDENCE metadata", " ".join(report["failures"]))

    def test_metadata_unknown_field_rejected_by_schema(self) -> None:
        code, report = self.mutate(
            lambda body: self.binding(body, "TRANSPORT").update({"unexpected": True})
        )
        self.assertEqual(1, code)
        self.assertIn("schema", " ".join(report["failures"]))

    def test_declared_approved_count_mismatch_rejected(self) -> None:
        code, report = self.mutate(lambda body: body.update({"approved_count": 99}))
        self.assertEqual(1, code)
        self.assertIn("approved count is inconsistent", " ".join(report["failures"]))

    def test_external_integration_qwen_evidence_root_hashes(self) -> None:
        evidence = read_json(P1A / "qwen_weight_license_evidence.v1.json")
        base = Path(
            os.environ.get(
                "P1A_EXTERNAL_EVIDENCE_BASE", "E:/project/_benchmark_evidence/phase1-5-p1a"
            )
        )
        root = base / evidence["external_evidence_root_id"]
        self.assertTrue(root.is_dir())
        manifest = read_json(root / "evidence_manifest.json")
        self.assertTrue(all(item["model_body_downloaded"] is False for item in manifest["files"]))
        expected = {
            "README.md": evidence["evidence"]["readme_sha256"],
            "hf_model_api.json": evidence["evidence"]["model_api_sha256"],
            "hf_repository_tree.json": evidence["evidence"]["repository_tree_sha256"],
            "hf_model_card_license_semantics.html": evidence["evidence"][
                "hub_license_semantics_sha256"
            ],
            "apache-2.0.txt": evidence["evidence"]["apache_2_0_text_sha256"],
            "qwen_code_LICENSE": evidence["evidence"]["qwen_code_license_sha256"],
        }
        for name, expected_hash in expected.items():
            self.assertEqual(expected_hash, file_hash(root / name), name)


if __name__ == "__main__":
    unittest.main(verbosity=2)
