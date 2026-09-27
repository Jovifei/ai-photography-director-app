#!/usr/bin/env python3
"""Tests for the non-runtime T9 Product READY promotion gate."""

from __future__ import annotations

import ast
import copy
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import validate_phase1_5_t9_ready_promotion as gate  # noqa: E402


class T9PromotionGateTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="t9-promotion-")
        self.fixture = Path(self.temp.name)
        for relative in (
            Path("docs/phase1_5/t9"),
            Path("docs/phase1_5/error_policy.v1.json"),
            Path("docs/phase1_5/t7/evaluation_governance.v1.json"),
            Path("docs/phase1_5/t8/evaluation_run_summary.v1.schema.json"),
            Path("docs/reference/provider_analysis_envelope.v1.schema.json"),
            Path("docs/reference/reference_bundle.v1.schema.json"),
            Path("docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md"),
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
        path = self.fixture / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def test_all_tracked_fixtures_are_blocked(self) -> None:
        result = gate.run(self.fixture)
        self.assertEqual(result["policy_status"], "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED")
        self.assertEqual(len(result["cases"]), 11)
        self.assertTrue(all(case["decision"]["trusted_ready_guidance_allowed"] is False for case in result["cases"]))
        self.assertNotIn("ELIGIBLE_AFTER_ALL_REQUIRED_GATES", {case["decision"]["decision"] for case in result["cases"]})

    def test_invalid_t9_schema_fails_closed(self) -> None:
        schema = self.read("docs/phase1_5/t9/ready_promotion_candidate.v1.schema.json")
        schema["type"] = "not-a-json-schema-type"
        self.write("docs/phase1_5/t9/ready_promotion_candidate.v1.schema.json", schema)
        with self.assertRaises(gate.T9ValidationError):
            gate.load_policy(self.fixture)

    def test_policy_binds_offline_bundle_and_frozen_sources(self) -> None:
        policy, *_ = gate.load_policy(self.fixture)
        self.assertFalse(policy["offline_bundle"]["semantics_modified"])
        offline = self.fixture / "docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md"
        offline.write_text(offline.read_text(encoding="utf-8") + "\nchanged\n", encoding="utf-8")
        with self.assertRaises(gate.T9ValidationError):
            gate.load_policy(self.fixture)

    def test_blocker_precedence_is_deterministic_and_complete(self) -> None:
        policy, _, decision_validator, _ = gate.load_policy(self.fixture)
        candidate = self.read("docs/phase1_5/t9/fixtures/success_severe_unresolved.json")
        first = gate.evaluate_promotion(candidate, policy, decision_validator)
        second = gate.evaluate_promotion(candidate, policy, decision_validator)
        self.assertEqual(first, second)
        self.assertEqual(first["decision"], "BLOCKED_SYNTHETIC_EVIDENCE")
        self.assertIn("BLOCKED_SEVERE_FINDING", first["blockers"])
        self.assertIn("BLOCKED_PRODUCT_PROMOTION_NOT_AUTHORIZED", first["blockers"])

    def test_hypothetical_positive_branch_is_not_current_authority(self) -> None:
        policy, candidate_validator, decision_validator, _ = gate.load_policy(self.fixture)
        candidate = self.read("docs/phase1_5/t9/fixtures/success_threshold_unapproved.json")
        candidate.update({
            "evidence_class": "REAL_PROVIDER_RESULT",
            "threshold_status": "APPROVED",
            "provider_qualification_state": "PROVIDER_QUALIFIED",
            "human_review_state": "ACCEPTED",
            "product_promotion_authority": True,
        })
        self.assertFalse(gate.schema_errors(candidate_validator, candidate))
        hypothetical = gate.evaluate_promotion(candidate, policy, decision_validator, hypothetical=True)
        self.assertEqual(hypothetical["decision"], "ELIGIBLE_AFTER_ALL_REQUIRED_GATES")
        self.assertTrue(hypothetical["trusted_ready_guidance_allowed"])
        current = gate.evaluate_promotion(candidate, policy, decision_validator)
        self.assertFalse(current["trusted_ready_guidance_allowed"])

    def test_candidate_unknown_or_private_fields_rejected(self) -> None:
        policy, candidate_validator, decision_validator, _ = gate.load_policy(self.fixture)
        candidate = self.read("docs/phase1_5/t9/fixtures/synthetic_success_metrics_ready.json")
        candidate["image_path"] = "C:/Users/Admin/photo.jpg"
        self.assertTrue(gate.schema_errors(candidate_validator, candidate))
        candidate = self.read("docs/phase1_5/t9/fixtures/synthetic_success_metrics_ready.json")
        candidate["reference_id"] = "content://private/1"
        self.assertTrue(gate.schema_errors(candidate_validator, candidate))

    def test_authority_probes_block(self) -> None:
        script = ROOT / "scripts/validate_phase1_5_t9_ready_promotion.py"
        for flag, expected in (
            ("--require-real-ready-promotion", "BLOCKED_REAL_READY_PROMOTION_AUTHORIZATION_REQUIRED"),
            ("--require-android-ready-write", "BLOCKED_ANDROID_PRODUCT_CHANGE_NOT_AUTHORIZED"),
            ("--require-provider-qualification", "BLOCKED_PROVIDER_QUALIFICATION_NOT_AUTHORIZED"),
        ):
            with self.subTest(flag=flag):
                result = subprocess.run([sys.executable, str(script), "--root", str(self.fixture), flag], capture_output=True, text=True, check=False)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected, result.stdout)

    def test_capability_free_static_boundary(self) -> None:
        source_path = ROOT / "scripts/validate_phase1_5_t9_ready_promotion.py"
        tree = ast.parse(source_path.read_text(encoding="utf-8"))
        imported = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        self.assertTrue(imported.isdisjoint({"requests", "httpx", "urllib", "socket", "torch", "transformers", "PIL", "cv2", "adb", "subprocess"}))


if __name__ == "__main__":
    unittest.main(verbosity=2)
