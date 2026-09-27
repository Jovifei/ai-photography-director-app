#!/usr/bin/env python3
"""Stdlib-only tests for the metadata-only P1B readiness boundary."""

from __future__ import annotations

import ast
import contextlib
import copy
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import validate_phase1_5_p1b_readiness as validator  # noqa: E402


def write_json(path: Path, body: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class P1BReadinessTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="p1b-readiness-")
        self.fixture = Path(self.temp.name)
        for relative in (Path("docs/phase1_5/p1a"), Path("docs/reference"), Path("docs/phase1_5/error_policy.v1.json"), Path("docs/phase1_5/p1b")):
            source = ROOT / relative
            destination = self.fixture / relative
            if source.is_dir():
                shutil.copytree(source, destination)
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def read(self, relative: str) -> dict:
        return json.loads((self.fixture / relative).read_text(encoding="utf-8"))

    def write(self, relative: str, body: object) -> None:
        write_json(self.fixture / relative, body)

    def assert_rejected(self) -> None:
        with self.assertRaises(validator.ValidationError):
            validator.validate_manifest(self.fixture)

    def test_exact_tracked_packet_passes(self) -> None:
        manifest, digest = validator.validate_manifest(self.fixture)
        self.assertEqual(manifest["status"], "READY_FOR_OWNER_DECISION")
        self.assertEqual(len(digest), 64)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = validator.main(["--root", str(self.fixture)])
        self.assertEqual(code, 0)
        self.assertIn("READY_FOR_OWNER_DECISION", output.getvalue())

    def test_execution_authority_probe_fails_closed(self) -> None:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = validator.main(["--root", str(self.fixture), "--require-execution-authority"])
        self.assertNotEqual(code, 0)
        self.assertEqual(output.getvalue().strip(), "BLOCKED_OWNER_AUTHORIZATION_REQUIRED")
        result = subprocess.run([sys.executable, str(ROOT / "scripts" / "validate_phase1_5_p1b_readiness.py"), "--root", str(self.fixture), "--require-execution-authority"], capture_output=True, text=True, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout.strip(), "BLOCKED_OWNER_AUTHORIZATION_REQUIRED")

    def test_wrong_revision_is_rejected(self) -> None:
        manifest = self.read("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
        manifest["primary_artifact"]["immutable_revision"] = "0" * 40
        self.write("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json", manifest)
        self.assert_rejected()

    def test_wrong_artifact_sha_and_bytes_are_rejected(self) -> None:
        original = ROOT / "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json"
        for key, value in (("weight_lfs_sha256", "f" * 64), ("weight_bytes", 1)):
            with self.subTest(key=key):
                manifest = self.read("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
                manifest["primary_artifact"][key] = value
                self.write("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json", manifest)
                self.assert_rejected()
                shutil.copy2(original, self.fixture / "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")

    def test_inventory_mismatch_is_rejected(self) -> None:
        inventory = self.read("docs/phase1_5/p1a/qwen_candidate_inventory.v1.json")
        inventory["primary"]["repository_total_bytes"] += 1
        self.write("docs/phase1_5/p1a/qwen_candidate_inventory.v1.json", inventory)
        self.assert_rejected()

    def test_authorization_legal_and_transport_drift_is_rejected(self) -> None:
        cases = (("primary_artifact_authorization_draft.v1.json", "download_authorized", True), ("qwen_weight_license_evidence.v1.json", "legal_review_status", "APPROVED"), ("primary_artifact_authorization_draft.v1.json", "transport", "APPROVED"))
        for filename, key, value in cases:
            with self.subTest(filename=filename, key=key):
                source = self.read(f"docs/phase1_5/p1a/{filename}")
                if key == "transport":
                    source["observed_transport"]["artifact_redirect_state"] = value
                else:
                    source[key] = value
                self.write(f"docs/phase1_5/p1a/{filename}", source)
                self.assert_rejected()
                shutil.copy2(ROOT / "docs/phase1_5/p1a" / filename, self.fixture / "docs/phase1_5/p1a" / filename)

    def test_corpus_and_owner_media_drift_is_rejected(self) -> None:
        original = ROOT / "docs/phase1_5/p1a/public_corpus_manifest.v3.json"
        for mutation in ("approved_count", "quarantine_count"):
            corpus = self.read("docs/phase1_5/p1a/public_corpus_manifest.v3.json")
            corpus[mutation] += 1
            self.write("docs/phase1_5/p1a/public_corpus_manifest.v3.json", corpus)
            self.assert_rejected()
            shutil.copy2(original, self.fixture / "docs/phase1_5/p1a/public_corpus_manifest.v3.json")
        corpus = self.read("docs/phase1_5/p1a/public_corpus_manifest.v3.json")
        corpus["privacy_policy"]["owner_media"] = "allowed"
        self.write("docs/phase1_5/p1a/public_corpus_manifest.v3.json", corpus)
        self.assert_rejected()

    def test_wrong_base_missing_contract_and_private_path_are_rejected(self) -> None:
        original = ROOT / "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json"
        manifest = self.read("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
        manifest["t5_delivery_head"] = "1" * 40
        self.write("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json", manifest)
        self.assert_rejected()
        shutil.copy2(original, self.fixture / "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
        (self.fixture / "docs/reference/reference_bundle.v1.schema.json").unlink()
        self.assert_rejected()
        shutil.copy2(ROOT / "docs/reference/reference_bundle.v1.schema.json", self.fixture / "docs/reference/reference_bundle.v1.schema.json")
        for candidate in ("C:/Users/Admin/private.jpg", "docs/phase1_5/../private.json"):
            manifest = self.read("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")
            manifest["stop_conditions"].append(candidate)
            self.write("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json", manifest)
            self.assert_rejected()
            shutil.copy2(original, self.fixture / "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json")

    def test_future_decision_cannot_authorize_runtime_or_private_media(self) -> None:
        manifest, manifest_sha = validator.validate_manifest(self.fixture)
        valid = {"schema_version": "1.0.0", "decision_id": "owner-decision-fixture", "readiness_id": manifest["readiness_id"], "decision": "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY", "recorded_at": "2026-09-27T00:00:00Z", "scope": {"artifact_quarantine_only": True, "download_authorized": False, "runtime_authorized": False, "inference_authorized": False, "app_integration_authorized": False, "pipeline_integration_authorized": False, "private_media_authorized": False}, "artifact_binding": {"model_id": manifest["primary_artifact"]["model_id"], "immutable_revision": manifest["primary_artifact"]["immutable_revision"], "weight_filename": manifest["primary_artifact"]["weight_filename"], "weight_bytes": manifest["primary_artifact"]["weight_bytes"], "weight_lfs_sha256": manifest["primary_artifact"]["weight_lfs_sha256"], "readiness_manifest_sha256": manifest_sha}}
        validator.validate_owner_decision(valid, manifest, manifest_sha)
        invalid = copy.deepcopy(valid)
        invalid["scope"]["runtime_authorized"] = True
        with self.assertRaises(validator.ValidationError):
            validator.validate_owner_decision(invalid, manifest, manifest_sha)

    def test_schema_is_strict_and_no_positive_decision_is_tracked(self) -> None:
        schema = self.read("docs/phase1_5/p1b/p1b_owner_decision.v1.schema.json")
        self.assertEqual(schema["additionalProperties"], False)
        self.assertIn("AUTHORIZE_ARTIFACT_QUARANTINE_ONLY", schema["properties"]["decision"]["enum"])
        self.assertFalse((self.fixture / "docs/phase1_5/p1b/p1b_owner_decision.v1.json").exists())

    def test_validator_has_no_external_capability_imports_or_commands(self) -> None:
        source_path = ROOT / "scripts/validate_phase1_5_p1b_readiness.py"
        tree = ast.parse(source_path.read_text(encoding="utf-8"))
        forbidden_modules = {"requests", "httpx", "urllib", "urllib3", "socket", "subprocess", "huggingface_hub", "transformers", "torch"}
        imported = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        self.assertTrue(imported.isdisjoint(forbidden_modules), imported & forbidden_modules)
        source = source_path.read_text(encoding="utf-8").lower()
        for token in ("urlopen(", "requests.get(", "socket(", "subprocess.", "os.system(", "popen(", "curl ", "wget ", "git lfs"):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
