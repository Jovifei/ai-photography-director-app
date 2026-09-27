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
        self.assertEqual(len(summary["cases"]), 8)
        states = {item["case_id"]: item["adjudication_state"] for item in summary["cases"]}
        self.assertEqual(states["success-no-adjudication"], "METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED")
        self.assertEqual(states["success-disagreement"], "ADJUDICATION_REQUIRED")
        self.assertEqual(states["success-severe"], "ADJUDICATION_REQUIRED")
        self.assertEqual(states["success-disagreement-r3"], "METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED")
        self.assertEqual(states["success-severe-r3"], "METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED")
        self.assertEqual(states["failed-provider"], "NOT_APPLICABLE")
        self.assertEqual(states["cancelled-provider"], "NOT_APPLICABLE")
        self.assertEqual(states["pipeline-incompatible"], "NOT_APPLICABLE")

    def test_frozen_schema_semantics_and_policy_are_executed(self) -> None:
        mutations = [
            ("unknown envelope field", lambda envelope: envelope.update({"unexpected": True})),
            ("provider type", lambda envelope: envelope.update({"provider_type": "LOCAL_VLM"})),
            ("timestamp order", lambda envelope: envelope.update({"completed_at_utc": "2026-09-26T23:59:59Z"})),
            ("timestamp format", lambda envelope: envelope.update({"started_at_utc": "not-a-date"})),
            ("success output version", lambda envelope: envelope.update({"output_schema_version": "9.9"})),
            ("artifact hash", lambda envelope: envelope.update({"model_artifact_sha256": "not-a-hash"})),
            ("bundle semantic text", lambda envelope: envelope["bundle"].update({"scene": " leading-space"})),
        ]
        for name, mutate in mutations:
            with self.subTest(name=name):
                fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
                mutate(fixture["envelope"])
                self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
                with self.assertRaises(harness.T8ValidationError):
                    harness.run(self.fixture)
                shutil.copy2(ROOT / "docs/phase1_5/t8/fixtures/success_no_adjudication.json", self.fixture / "docs/phase1_5/t8/fixtures/success_no_adjudication.json")

        failed = self.read("docs/phase1_5/t8/fixtures/failed_provider.json")
        failed["envelope"]["retryable"] = False
        self.write("docs/phase1_5/t8/fixtures/failed_provider.json", failed)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)

    def test_success_reference_identity_and_bundle_version_are_gated(self) -> None:
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["envelope"]["bundle"]["reference_id"] = "synthetic-reference-other"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)
        shutil.copy2(ROOT / "docs/phase1_5/t8/fixtures/success_no_adjudication.json", self.fixture / "docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["envelope"]["bundle"]["version"] = "9.9"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)

    def test_blinded_projection_is_allowlist_only_and_provider_invariant(self) -> None:
        envelope = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")["envelope"]
        envelope["warnings"] = ["provider-model metadata must not reach reviewers"]
        payload = harness.build_blinded_review_payload(envelope, "candidate-synthetic-a")
        self.assertNotIn("provider_id", payload)
        self.assertNotIn("provider_type", payload)
        self.assertNotIn("model_id", payload)
        self.assertNotIn("model_revision", payload)
        self.assertNotIn("model_artifact_sha256", payload)
        self.assertNotIn("runtime_id", payload)
        self.assertNotIn("provenance", payload)
        self.assertNotIn("warnings", payload)
        self.assertNotIn("provider-model metadata", json.dumps(payload, ensure_ascii=False))
        self.assertEqual(payload["blinded_candidate_id"], "candidate-synthetic-a")
        mutated = copy.deepcopy(envelope)
        mutated.update({"provider_id": "another-synthetic-provider", "provider_type": "CLOUD", "model_id": "other-model", "model_revision": "other-revision", "model_artifact_sha256": "b" * 64, "runtime_id": "other-runtime"})
        mutated["provenance"]["producer_release"] = "other-release"
        self.assertEqual(payload, harness.build_blinded_review_payload(mutated, "candidate-synthetic-a"))

    def test_r1_r2_r3_lifecycle_and_severe_finding_persistence(self) -> None:
        summaries = {item["case_id"]: item for item in harness.run(self.fixture)["cases"]}
        self.assertEqual(summaries["success-no-adjudication"]["raw_score"], 36)
        self.assertEqual(summaries["success-no-adjudication"]["normalized_quality"], 100.0)
        self.assertIsNone(summaries["success-disagreement"]["raw_score"])
        self.assertEqual(summaries["success-disagreement"]["adjudication_result"], "REQUIRED")
        self.assertEqual(summaries["success-disagreement-r3"]["adjudication_result"], "COMPLETE")
        self.assertEqual(summaries["success-disagreement-r3"]["raw_score"], 19)
        self.assertIsNone(summaries["success-severe"]["raw_score"])
        self.assertEqual(summaries["success-severe-r3"]["adjudication_result"], "COMPLETE")
        self.assertEqual(summaries["success-severe-r3"]["raw_score"], 36)
        self.assertIn("SEVERE_HALLUCINATION_FLAGGED", summaries["success-severe-r3"]["hard_findings"])

    def test_automated_criterion_18_is_derived_and_summary_schema_is_enforced(self) -> None:
        summary = harness.run(self.fixture)
        normal = next(item for item in summary["cases"] if item["case_id"] == "success-no-adjudication")
        self.assertEqual(normal["criterion_scores"]["c18"], 2)
        self.assertEqual(normal["summary_contract"]["raw_score"], 36)
        self.assertEqual(normal["summary_contract"]["candidate_blind_id"], "candidate-synthetic-a")

        manifest = self.read("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json")
        manifest["unexpected"] = True
        self.write("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json", manifest)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)
        shutil.copy2(ROOT / "docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json", self.fixture / "docs/phase1_5/t8/synthetic_evaluation_manifest.v1.json")

        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["review_packets"][0]["automated_criterion_18"] = {"status": "FAIL"}
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)

    def test_tracked_t8_schemas_pass_draft202012_self_check(self) -> None:
        envelope, bundle, manifest, summary = harness.load_contract_validators(self.fixture)
        self.assertIsInstance(envelope, harness.Draft202012Validator)
        self.assertIsInstance(bundle, harness.Draft202012Validator)
        self.assertIsInstance(manifest, harness.Draft202012Validator)
        self.assertIsInstance(summary, harness.Draft202012Validator)
        manifest_schema = self.read("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.schema.json")
        manifest_schema["type"] = "not-a-json-schema-type"
        self.write("docs/phase1_5/t8/synthetic_evaluation_manifest.v1.schema.json", manifest_schema)
        with self.assertRaises(harness.T8ValidationError):
            harness.load_contract_validators(self.fixture)

    def test_non_success_outcomes_never_score_or_fallback(self) -> None:
        summaries = {item["case_id"]: item for item in harness.run(self.fixture)["cases"]}
        for case_id in ("failed-provider", "cancelled-provider", "pipeline-incompatible"):
            with self.subTest(case_id=case_id):
                result = summaries[case_id]
                self.assertFalse(result["quality_review_eligible"])
                self.assertIsNone(result["raw_score"])
                self.assertIsNone(result["normalized_quality"])
                self.assertFalse(result["demo_fallback_used"])
        pipeline = summaries["pipeline-incompatible"]
        self.assertFalse(pipeline["retryable"])
        self.assertEqual(pipeline["product_action"], "SHOW_UNAVAILABLE")
        self.assertEqual(pipeline["fallback_policy"], "USER_EXPLICIT_OUT_OF_ENVELOPE_ONLY")

    def test_uncertainty_and_thresholds_remain_fail_closed(self) -> None:
        envelope = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")["envelope"]
        envelope["uncertainty_flags"]["emotion"]["level"] = "HIGH"
        payload = harness.build_blinded_review_payload(envelope, "candidate-synthetic-a")
        self.assertEqual(payload["uncertainty_flags"]["emotion"]["level"], "HIGH")
        fixture = self.read("docs/phase1_5/t8/fixtures/success_no_adjudication.json")
        fixture["threshold_status"] = "APPROVED"
        self.write("docs/phase1_5/t8/fixtures/success_no_adjudication.json", fixture)
        with self.assertRaises(harness.T8ValidationError):
            harness.run(self.fixture)
        shutil.copy2(ROOT / "docs/phase1_5/t8/fixtures/success_no_adjudication.json", self.fixture / "docs/phase1_5/t8/fixtures/success_no_adjudication.json")

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
