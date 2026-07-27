#!/usr/bin/env python3
"""Offline negative tests for the independently executable P1A corpus validator."""

from __future__ import annotations

import copy
import hashlib
import json
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
    "city_night", "indoor_window", "seaside", "forest", "street", "cafe_or_lifestyle_interior",
    "architecture", "backlight", "side_light", "low_light", "single_person_full_body",
    "single_person_half_body", "portrait_close_up", "two_people", "no_person", "dynamic_action",
    "occlusion", "complex_background", "minimal_background", "strong_color", "cool_color",
    "warm_color", "symmetry", "negative_space",
]


def file_hash(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, body: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build_fixture(base: Path) -> tuple[Path, Path, Path]:
    root = base / "public-corpus-20260727T000000Z-r2"
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
        human = {"status": "APPROVED", "human_presence": "NONE", "person_count": 0, "full_body_count": 0, "age_evidence_basis": "NOT_APPLICABLE", "age_evidence": "No human subject.", "nonvisual_source": "https://commons.wikimedia.org/wiki/File:Example", "no_person": True}
        tags = ["no_person", CATEGORIES[ordinal]]
        if ordinal == 0:
            human = {"status": "APPROVED", "human_presence": "VISIBLE_DOCUMENTED_ADULT", "person_count": 1, "full_body_count": 1, "age_evidence_basis": "OFFICIAL_SOURCE_DOCUMENTED_ADULT", "age_evidence": "Official source calls the person an adult.", "nonvisual_source": "https://commons.wikimedia.org/wiki/File:Adult", "no_person": False}
            tags = ["single_person_full_body", "single_person_half_body", "portrait_close_up"]
        if ordinal == 1:
            human = {"status": "APPROVED", "human_presence": "VISIBLE_DOCUMENTED_ADULT", "person_count": 2, "full_body_count": 0, "age_evidence_basis": "OFFICIAL_SOURCE_DOCUMENTED_ADULT", "age_evidence": "Official source names two adult professionals.", "nonvisual_source": "https://commons.wikimedia.org/wiki/File:Adults", "no_person": False}
            tags = ["two_people", "dynamic_action", "complex_background"]
        metadata = root / "metadata" / sample_id
        source_page = {"pageid": 1000 + ordinal, "title": f"File:Fixture {ordinal}", "revision_id": 2000 + ordinal, "revision_timestamp": "2026-07-27T00:00:00Z", "canonical_url": "https://commons.wikimedia.org/wiki/File:Fixture", "original_url": f"https://upload.wikimedia.org/example-{ordinal}.jpg", "mime": "image/jpeg", "width": 12, "height": 12, "commons_api_sha256": "a" * 64}
        evidence = {"license_id": "CC0", "license_name": "CC0", "license_url": "https://creativecommons.org/publicdomain/zero/1.0/", "source_page_revision_id": 2000 + ordinal, "attribution": {"author": "Fixture", "title": f"Fixture {ordinal}", "page_url": source_page["canonical_url"], "license_name": "CC0", "license_url": "https://creativecommons.org/publicdomain/zero/1.0/", "modified": "metadata removed", "recommended_attribution": "Fixture"}, "public_domain_basis": "NOT_APPLICABLE"}
        write_json(metadata / "commons_api.json", {"fixture": ordinal})
        write_json(metadata / "source_page_record.json", source_page)
        write_json(metadata / "license_evidence.json", evidence)
        write_json(metadata / "transport.json", {"status": 200})
        write_json(metadata / "manual_visual_review.json", human)
        samples.append({"sample_id": sample_id, "title": f"Fixture {ordinal}", "status": "APPROVED", "category_tags": tags, "source": {"relative_path": source.relative_to(root).as_posix(), "sha256": file_hash(source), "bytes": source.stat().st_size, "mime": "image/jpeg", "width": 12, "height": 12, "representation": "fixture", "original_url": source_page["original_url"]}, "sanitized": {"relative_path": sanitized.relative_to(root).as_posix(), "format": "JPEG", "mime": "image/jpeg", "sha256": file_hash(sanitized), "bytes": sanitized.stat().st_size, "dimensions": [12, 12], "source_dimensions": [12, 12], "source_orientation": 1, "orientation_normalized": False, "crop_applied": False, "exif_removed": True, "gps_removed": True, "device_metadata_removed": True, "software_metadata_removed": True}, "source_page": source_page, "license": {"id": "CC0", "name": "CC0", "url": "https://creativecommons.org/publicdomain/zero/1.0/", "attribution": evidence["attribution"]}, "license_evidence_relative_path": f"metadata/{sample_id}/license_evidence.json", "license_evidence_sha256": file_hash(metadata / "license_evidence.json"), "human_review": human, "metadata_relative_paths": [f"metadata/{sample_id}/{name}" for name in ("commons_api.json", "source_page_record.json", "license_evidence.json", "transport.json", "manual_visual_review.json")]})
    # Do not assign human-only categories to synthetic no-person samples.
    for item in samples:
        if item["human_review"]["human_presence"] == "NONE":
            item["category_tags"] = [tag for tag in item["category_tags"] if tag not in {"single_person_full_body", "two_people"}]
    # The remaining required categories that are not naturally placed above are valid no-person reviews.
    present = {tag for item in samples for tag in item["category_tags"]}
    for index, category in enumerate(CATEGORIES):
        if category not in present:
            samples[2 + (index % 18)]["category_tags"].append(category)
    manifest = {"schema_version": "2.0.0", "corpus_id": "phase1-5-p1a-public-corpus-20260727T000000Z-r2", "parent_corpus_id": "phase1-5-p1a-public-corpus-parent", "supersedes_reason": "fixture", "status": "CORPUS_R2_VALIDATED", "external_evidence_root_id": root.name, "source_repository": "fixture", "approved_count": 20, "quarantine_count": 4, "minimum_required_count": 20, "allowed_license_ids": ["CC0", "Public-Domain", "CC-BY-4.0"], "privacy_policy": {"owner_media": "prohibited"}, "age_policy": {"approved_human_presence": ["NONE", "VISIBLE_DOCUMENTED_ADULT"]}, "required_categories": CATEGORIES, "samples": samples, "quarantine": [{"sample_id": item, "status": "QUARANTINED", "reason": "fixture quarantine", "old_source_sha256": "b" * 64, "old_sanitized_sha256": "c" * 64, "no_image_copied": True} for item in ("p1a-008", "p1a-019", "p1a-023", "p1a-024")], "aggregate": {"algorithm": "fixture", "sha256": canonical_aggregate(samples)}}
    manifest_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(P1A / "public_corpus_manifest.v2.schema.json", manifest_dir / "public_corpus_manifest.v2.schema.json")
    write_json(manifest_dir / "public_corpus_manifest.v2.json", manifest)
    return root, manifest_dir / "public_corpus_manifest.v2.json", report


class P1AValidatorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root, self.manifest_path, self.report = build_fixture(Path(self.temp.name))

    def tearDown(self) -> None:
        self.temp.cleanup()

    def invoke(self) -> tuple[int, dict]:
        result = subprocess.run([sys.executable, "-B", str(VALIDATOR), "--manifest", str(self.manifest_path), "--external-root", str(self.root), "--report-json", str(self.report)], text=True, capture_output=True, check=False)
        return result.returncode, json.loads(self.report.read_text(encoding="utf-8"))

    def mutate(self, operation) -> tuple[int, dict]:
        body = json.loads(self.manifest_path.read_text(encoding="utf-8"))
        operation(body)
        write_json(self.manifest_path, body)
        return self.invoke()

    def test_schema_and_cli_triple_pass_without_absolute_report_path(self) -> None:
        schema = json.loads((P1A / "public_corpus_manifest.v2.schema.json").read_text(encoding="utf-8"))
        manifest = json.loads(self.manifest_path.read_text(encoding="utf-8"))
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(manifest)))
        code, report = self.invoke()
        self.assertEqual(0, code)
        self.assertEqual("PASS", report["status"])
        self.assertNotIn(str(self.root), json.dumps(report))

    def test_cli_missing_arguments_exit_two(self) -> None:
        result = subprocess.run([sys.executable, "-B", str(VALIDATOR)], text=True, capture_output=True, check=False)
        self.assertEqual(2, result.returncode)

    def test_qwen_weight_license_evidence_is_metadata_only_not_authorization(self) -> None:
        evidence = json.loads((P1A / "qwen_weight_license_evidence.v1.json").read_text(encoding="utf-8"))
        schema = json.loads((P1A / "qwen_weight_license_evidence.v1.schema.json").read_text(encoding="utf-8"))
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(evidence)))
        self.assertEqual("MODEL_CARD_METADATA_AT_IMMUTABLE_REVISION", evidence["weight_license_evidence_basis"])
        self.assertEqual("NOT_LEGAL_APPROVED", evidence["legal_review_status"])
        self.assertFalse(evidence["download_authorized"])
        self.assertFalse(evidence["runtime_authorized"])
        self.assertFalse(evidence["inference_authorized"])
        self.assertFalse(evidence["app_integration_authorized"])

    def test_hash_mismatch(self) -> None:
        code, report = self.mutate(lambda body: body["samples"][0]["source"].update({"sha256": "0" * 64}))
        self.assertEqual(1, code); self.assertIn("SHA-256 mismatch", " ".join(report["failures"]))

    def test_decode_failure(self) -> None:
        path = self.root / "sanitized" / "p1a-101.jpg"; path.write_bytes(b"not-an-image")
        code, report = self.invoke()
        self.assertEqual(1, code); self.assertIn("byte mismatch", " ".join(report["failures"]))

    def test_exif_gps_metadata_rejected(self) -> None:
        path = self.root / "sanitized" / "p1a-101.jpg"
        Image.new("RGB", (12, 12), (1, 2, 3)).save(path, "JPEG", exif=b"Exif\x00\x00fixture")
        code, report = self.invoke()
        self.assertEqual(1, code); self.assertIn("byte mismatch", " ".join(report["failures"]))

    def test_duplicate_content(self) -> None:
        def change(body):
            target, source = body["samples"][1], body["samples"][0]
            target["source"]["sha256"] = source["source"]["sha256"]
            target["sanitized"]["sha256"] = source["sanitized"]["sha256"]
        code, report = self.mutate(change)
        self.assertEqual(1, code); self.assertIn("duplicate approved", " ".join(report["failures"]))

    def test_no_person_conflict(self) -> None:
        code, report = self.mutate(lambda body: body["samples"][0]["category_tags"].append("no_person"))
        self.assertEqual(1, code); self.assertIn("no_person conflicts", " ".join(report["failures"]))

    def test_age_uncertain_rejected(self) -> None:
        code, report = self.mutate(lambda body: body["samples"][0]["human_review"].update({"human_presence": "VISIBLE_AGE_UNCERTAIN"}))
        self.assertEqual(1, code); self.assertIn("age-uncertain", " ".join(report["failures"]))

    def test_quarantine_requires_reason(self) -> None:
        code, report = self.mutate(lambda body: body["quarantine"][0].update({"reason": ""}))
        self.assertEqual(1, code); self.assertIn("empty reason", " ".join(report["failures"]))

    def test_full_body_requires_proof(self) -> None:
        code, report = self.mutate(lambda body: body["samples"][0]["human_review"].update({"full_body_count": 0}))
        self.assertEqual(1, code); self.assertIn("full-body proof", " ".join(report["failures"]))

    def test_empty_ccby_attribution(self) -> None:
        def change(body):
            sample = body["samples"][0]; sample["license"]["id"] = "CC-BY-4.0"
            evidence = self.root / sample["license_evidence_relative_path"]
            item = json.loads(evidence.read_text(encoding="utf-8")); item["license_id"] = "CC-BY-4.0"; item["attribution"]["author"] = ""; write_json(evidence, item); sample["license_evidence_sha256"] = file_hash(evidence)
        code, report = self.mutate(change)
        self.assertEqual(1, code); self.assertIn("CC-BY attribution missing author", " ".join(report["failures"]))

    def test_mojibake_rejected(self) -> None:
        def change(body):
            evidence = self.root / body["samples"][0]["license_evidence_relative_path"]
            item = json.loads(evidence.read_text(encoding="utf-8")); item["attribution"]["title"] = "Ã©"; write_json(evidence, item); body["samples"][0]["license_evidence_sha256"] = file_hash(evidence)
        code, report = self.mutate(change)
        self.assertEqual(1, code); self.assertIn("mojibake", " ".join(report["failures"]))

    def test_aggregate_mismatch(self) -> None:
        code, report = self.mutate(lambda body: body["aggregate"].update({"sha256": "0" * 64}))
        self.assertEqual(1, code); self.assertIn("aggregate", " ".join(report["failures"]))

    def test_path_traversal_and_absolute_path(self) -> None:
        code, report = self.mutate(lambda body: body["samples"][0]["source"].update({"relative_path": "../escape.jpg"}))
        self.assertEqual(1, code); self.assertIn("unsafe evidence path", " ".join(report["failures"]))
        self.root, self.manifest_path, self.report = build_fixture(Path(self.temp.name) / "second")
        code, report = self.mutate(lambda body: body["samples"][0]["source"].update({"relative_path": "E:/absolute.jpg"}))
        self.assertEqual(1, code); self.assertIn("unsafe evidence path", " ".join(report["failures"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
