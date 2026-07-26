#!/usr/bin/env python3
"""Fast offline contract tests for P1A manifests; does not touch network or models."""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parent.parent
P1A = ROOT / "docs" / "phase1_5" / "p1a"


def read(name: str) -> dict:
    return json.loads((P1A / name).read_text(encoding="utf-8"))


class P1AManifestTests(unittest.TestCase):
    def test_public_corpus_matches_schema_and_policy(self) -> None:
        manifest = read("public_corpus_manifest.json")
        schema = read("public_corpus_manifest.schema.json")
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(manifest)))
        self.assertEqual(20, manifest["minimum_required_count"])
        self.assertEqual(20, manifest["approved_count"])
        self.assertEqual(20, len(manifest["samples"]))
        self.assertNotIn(":\\", json.dumps(manifest))
        self.assertNotIn("E:/", json.dumps(manifest))
        allowed = {"CC0", "Public-Domain", "CC-BY-4.0"}
        self.assertTrue(all(item["license"]["id"] in allowed for item in manifest["samples"]))
        self.assertTrue(all(item["manual_review"]["status"] == "APPROVED" for item in manifest["samples"]))
        self.assertTrue(all(item["sanitized"]["exif_removed"] for item in manifest["samples"]))
        self.assertTrue(all(item["sanitized"]["gps_removed"] for item in manifest["samples"]))

    def test_qwen_inventory_is_metadata_only_and_frozen(self) -> None:
        inventory = read("qwen_candidate_inventory.v1.json")
        schema = read("qwen_candidate_inventory.v1.schema.json")
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(inventory)))
        self.assertFalse(inventory["artifact_bytes_downloaded"])
        self.assertFalse(inventory["model_weights_downloaded"])
        self.assertEqual("Qwen/Qwen3-VL-2B-Instruct", inventory["primary"]["model_id"])
        self.assertEqual(
            "89644892e4d85e24eaac8bacfd4f463576704203",
            inventory["primary"]["immutable_revision"],
        )
        self.assertEqual(
            "7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0",
            inventory["primary"]["weight_artifacts"][0]["lfs_oid_sha256"],
        )

    def test_primary_authorization_draft_is_not_permission(self) -> None:
        draft = read("primary_artifact_authorization_draft.v1.json")
        schema = read("primary_artifact_authorization_draft.v1.schema.json")
        self.assertEqual([], list(Draft202012Validator(schema).iter_errors(draft)))
        self.assertEqual("READY_FOR_OWNER_DOWNLOAD_DECISION", draft["status"])
        self.assertFalse(draft["download_authorized"])
        self.assertFalse(draft["runtime_authorized"])
        self.assertFalse(draft["inference_authorized"])
        self.assertEqual(
            "89644892e4d85e24eaac8bacfd4f463576704203",
            draft["immutable_revision"],
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
