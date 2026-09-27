#!/usr/bin/env python3
"""Tests for the contract-only T8 synthetic evaluation harness."""

from __future__ import annotations

import ast
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
import run_phase1_5_t8_synthetic_evaluation as harness  # noqa: E402


def write_json(path: Path, body: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class T8HarnessTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="t8-harness-")
        self.fixture = Path(self.temp.name)
        for relative in (
            Path("docs/phase1_5/t8"),
            Path("docs/phase1_5/t7"),
            Path("docs/phase1_5/VLM_EVALUATION_RUBRIC.md"),
            Path("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json"),
            Path("docs/phase1_5/VLM_UNCERTAINTY_CONTRACT.md"),
            Path("docs/phase1_5/PIPELINE_APP_FIELD_MAPPING.md"),
            Path("docs/reference/reference_bundle.v1.schema.json"),
            Path("docs/reference/provider_analysis_envelope.v1.schema.json"),
            Path("docs/phase1_5/error_policy.v1.json"),
        ):
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

    def test_harness_runs_all_synthetic_classes(self) -> None:
        summary = harness.run(self.fixture)
        self.assertEqual(summary["fixture_class"], "CONTRACT_ONLY_SYNTHETIC")
        self.assertEqual(len(summary["cases"]), 6)
        states = {item["case_id"]: item["adjudication_state"] for item in summary["cases"]}
        self.assertEqual(states["success-no-adjudication"], "METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED")
        self.assertEqual(states["success-disagreement"], "ADJUDICATION_REQUIRED")
        self.assertEqual(states["success-severe"], "ADJUDICATION_REQUIRED")
        self.assertEqual(states["failed-provider"], "NOT_APPLICABLE")
        self.assertEqual(states["cancelled-provider"], "NOT_APPLICABLE")
        self.assertEqual(states["pipeline-incompatible"], "NOT_APPLICABLE")

    def test_cli_and_negative_authority_probes(self) -> None:
        output = subprocess.run([sys.executable, str(ROOT / "scripts/run_phase1_5_t8_synthetic_evaluation.py"), "--root", str(self.fixture)], capture_output=True, text=True, check=False)
        self.assertEqual(output.returncode, 0)
        self.assertIn("SYNTHETIC_EVALUATION_HARNESS_READY", output.stdout)
        for flag, expected in (("--require-real-provider", "BLOCKED_PROVIDER_RUNTIME_AUTHORIZATION_REQUIRED"), ("--require-human-review", "BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED"), ("--require-pipeline", "BLOCKED_PIPELINE_COMPATIBILITY_AND_AUTHORIZATION_REQUIRED")):
            with self.subTest(flag=flag):
                result = subprocess.run([sys.executable, str(ROOT / "scripts/run_phase1_5_t8_synthetic_evaluation.py"), "--root", str(self.fixture), flag], capture_output=True, text=True, check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected, result.stdout)

    def test_manifest_authority_or_fixture_marker_drift_rejected(self) -> None:
        manifest = self.read("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json")
        manifest["authority"]["human_review_authorized"] = True
        self.write("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json", manifest)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)
        shutil.copy2(ROOT / "docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json", self.fixture / "docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json")
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["fixture_class"] = "REAL_PROVIDER_EXECUTED"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)

    def test_failed_or_cancelled_with_review_packets_rejected(self) -> None:
        for relative in ("docs/phase1_5/t8/fixtures/failed_provider.json", "docs/phase1_5/t8/fixtures/cancelled_provider.json"):
            fixture = self.read(relative)
            fixture["review_packets"] = [{"reviewer_slot": "R1", "scores": 2, "severe": False}]
            self.write(relative, fixture)
            with self.assertRaises(harness.T8ValidationError):
                harness.run(self.fixture)
            shutil.copy2(ROOT / relative, self.fixture / relative)

    def test_success_contract_or_provider_metadata_drift_rejected(self) -> None:
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["contract_valid"] = False
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)
        shutil.copy2(ROOT / "docs/phase1_5/t8/fixtures/success_no_adjudication.json", self.fixture / "docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["provider_metadata"]["provider_id"] = "real-provider"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)

    def test_no_media_path_or_pii_is_allowed(self) -> None:
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["image_path"] = "C:/Users/Admin/photo.jpg"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)
        shutil.copy2(ROOT / "docs/phase1_5/t8/fixtures/success_no_adjudication.json", self.fixture / "docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["reviewer_email"] = "reviewer@example.com"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)

    def test_static_safety_and_no_real_record(self) -> None:
        source_path = ROOT / "scripts/run_phase1_5_t8_synthetic_evaluation.py"
        tree = ast.parse(source_path.read_text(encoding="utf-8"))
        forbidden = {"requests", "httpx", "urllib", "socket", "subprocess", "PIL", "cv2", "torch", "transformers", "huggingface_hub"}
        imported = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        self.assertTrue(imported.isdisjoint(forbidden), imported & forbidden)
        source = source_path.read_text(encoding="utf-8").lower()
        for token in ("urlopen(", "requests.get(", "socket(", "subprocess.", "os.system(", "curl ", "wget ", "adb "):
            self.assertNotIn(token, source)
        self.assertFalse((self.fixture / "docs/phase1_5/t8/evaluation_run_summary.v1.json").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
