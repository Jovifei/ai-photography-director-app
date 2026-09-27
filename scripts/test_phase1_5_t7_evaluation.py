#!/usr/bin/env python3
"""Stdlib-only negative tests for T7 evaluation governance readiness."""

from __future__ import annotations

import ast
import copy
import contextlib
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
import validate_phase1_5_t7_evaluation as validator  # noqa: E402


def write_json(path: Path, body: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class T7GovernanceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="t7-governance-")
        self.fixture = Path(self.temp.name)
        for relative in (
            Path("docs/phase1_5/t7"),
            Path("docs/phase1_5/VLM_EVALUATION_RUBRIC.md"),
            Path("docs/phase1_5/VLM_UNCERTAINTY_CONTRACT.md"),
            Path("docs/phase1_5/PIPELINE_APP_FIELD_MAPPING.md"),
            Path("docs/phase1_5/p1b/p1b_readiness_manifest.v1.json"),
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

    def assert_rejected(self) -> None:
        with self.assertRaises(validator.T7ValidationError):
            validator.validate_governance(self.fixture)

    def test_exact_governance_packet_passes(self) -> None:
        governance, digest = validator.validate_governance(self.fixture)
        self.assertEqual(governance["status"], "EVALUATION_GOVERNANCE_PACKET_READY_HUMAN_REVIEW_NOT_AUTHORIZED")
        self.assertEqual(len(digest), 64)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = validator.main(["--root", str(self.fixture)])
        self.assertEqual(code, 0)
        self.assertIn("EVALUATION_GOVERNANCE_READY", output.getvalue())

    def test_authority_probes_fail_closed(self) -> None:
        for flag, expected in (("--require-human-review-authority", "BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED"), ("--require-provider-qualification", "BLOCKED_NO_RUNTIME_AND_REVIEW_EVIDENCE")):
            with self.subTest(flag=flag):
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    code = validator.main(["--root", str(self.fixture), flag])
                self.assertNotEqual(code, 0)
                self.assertEqual(output.getvalue().strip(), expected)
        result = subprocess.run([sys.executable, str(ROOT / "scripts" / "validate_phase1_5_t7_evaluation.py"), "--root", str(self.fixture), "--require-human-review-authority"], capture_output=True, text=True, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED", result.stdout)

    def test_wrong_base_criterion_threshold_uncertainty_and_pipeline_drift_rejected(self) -> None:
        mutations = (
            ("t6_base", "0" * 40),
            ("criteria", [{"id": 1, "name": "wrong", "verifier": "HUMAN"}]),
            ("thresholds", {"status": "APPROVED", "owner_thresholds_approved": True}),
            ("uncertainty", {"contract_path": validator.UNCERTAINTY_REL, "contract_sha256": "0" * 64, "field_count": 9, "global_confidence_forbidden": True, "allowed_bases": []}),
            ("pipeline_boundary", {"mapping_path": validator.PIPELINE_REL, "mapping_sha256": validator.PIPELINE_SHA, "status": "READY", "compatibility_claim_allowed": True, "missing_safe_mapping_remains_blocked": False}),
        )
        for key, value in mutations:
            with self.subTest(key=key):
                governance = self.read("docs/phase1_5/t7/evaluation_governance.v1.json")
                governance[key] = value
                self.write("docs/phase1_5/t7/evaluation_governance.v1.json", governance)
                self.assert_rejected()
                shutil.copy2(ROOT / "docs/phase1_5/t7/evaluation_governance.v1.json", self.fixture / "docs/phase1_5/t7/evaluation_governance.v1.json")

    def test_source_hash_or_schema_drift_rejected(self) -> None:
        path = self.fixture / "docs/phase1_5/VLM_EVALUATION_RUBRIC.md"
        path.write_text(path.read_text(encoding="utf-8") + "\nchanged\n", encoding="utf-8")
        self.assert_rejected()
        shutil.copy2(ROOT / "docs/phase1_5/VLM_EVALUATION_RUBRIC.md", path)
        schema = self.read("docs/phase1_5/t7/evaluation_governance.v1.schema.json")
        schema["additionalProperties"] = True
        self.write("docs/phase1_5/t7/evaluation_governance.v1.schema.json", schema)
        governance = validator.read_json(self.fixture, validator.GOV_REL)
        self.assertTrue(validator.read_json(self.fixture, validator.GOV_REL))
        self.assertEqual(schema["additionalProperties"], True)

    def valid_packet(self, slot: str = "R1", score: int = 2, severe: bool = False, categories: list[str] | None = None, adjudication_required: bool = False, status: str = "REVIEW_PACKET_VALID") -> dict:
        governance, _ = validator.validate_governance(self.fixture)
        return {
            "packet_version": "1.0.0",
            "sample_id": "sample-001",
            "blinded_candidate_id": "candidate-a1",
            "rubric_version": governance["governance_id"],
            "reviewer_slot": slot,
            "criterion_scores": {f"c{i:02d}": score for i in range(1, 18)},
            "automated_criterion_18": {"status": "PASS", "required_fields_valid": True, "bounds_valid": True, "source_version_consistent": True},
            "severe_hallucination": severe,
            "severe_hallucination_categories": categories or [],
            "adjudication_required": adjudication_required,
            "review_status": status,
        }

    def test_valid_review_packet_and_unknown_fields(self) -> None:
        governance, _ = validator.validate_governance(self.fixture)
        packet = self.valid_packet()
        validator.validate_review_packet(packet, governance)
        invalid = copy.deepcopy(packet)
        invalid["reviewer_name"] = "Alice"
        with self.assertRaises(validator.T7ValidationError):
            validator.validate_review_packet(invalid, governance)

    def test_packet_score_severe_privacy_media_and_authority_drift_rejected(self) -> None:
        governance, _ = validator.validate_governance(self.fixture)
        mutations = []
        packet = self.valid_packet()
        bad = copy.deepcopy(packet); bad["criterion_scores"]["c01"] = 3; mutations.append(bad)
        bad = copy.deepcopy(packet); bad["severe_hallucination"] = True; mutations.append(bad)
        bad = copy.deepcopy(packet); bad["severe_hallucination_categories"] = ["SAFETY"]; mutations.append(bad)
        bad = copy.deepcopy(packet); bad["adjudication_required"] = "true"; mutations.append(bad)
        bad = copy.deepcopy(packet); bad["adjudication_note"] = "C:\\Users\\Admin\\private.jpg"; mutations.append(bad)
        bad = copy.deepcopy(packet); bad["image_bytes"] = "base64"; mutations.append(bad)
        bad = copy.deepcopy(packet); bad["runtime_authorized"] = True; mutations.append(bad)
        for candidate in mutations:
            with self.assertRaises(validator.T7ValidationError):
                validator.validate_review_packet(candidate, governance)

    def test_adjudication_is_deterministic(self) -> None:
        governance, _ = validator.validate_governance(self.fixture)
        r1 = self.valid_packet("R1", 2)
        r2 = self.valid_packet("R2", 2)
        result = validator.aggregate_review_packets([r1, r2], governance)
        self.assertEqual(result["status"], "REVIEW_PACKET_VALID")
        self.assertFalse(result["needs_r3"])

        different = self.valid_packet("R2", 0)
        with self.assertRaises(validator.T7ValidationError):
            validator.aggregate_review_packets([r1, different], governance)
        r3 = self.valid_packet("R3", 1, adjudication_required=True, status="ADJUDICATION_COMPLETE")
        result = validator.aggregate_review_packets([r1, different, r3], governance)
        self.assertEqual(result["status"], "ADJUDICATION_COMPLETE")
        self.assertEqual(result["criterion_medians"]["c01"], 1)

        severe = self.valid_packet("R2", 2, severe=True, categories=["SCENE_EVENT"])
        with self.assertRaises(validator.T7ValidationError):
            validator.aggregate_review_packets([r1, severe], governance)

    def test_r3_without_trigger_and_missing_r3_are_rejected(self) -> None:
        governance, _ = validator.validate_governance(self.fixture)
        r1 = self.valid_packet("R1", 2)
        r2 = self.valid_packet("R2", 2)
        r3 = self.valid_packet("R3", 2, adjudication_required=True, status="ADJUDICATION_COMPLETE")
        with self.assertRaises(validator.T7ValidationError):
            validator.aggregate_review_packets([r1, r2, r3], governance)

    def test_no_real_review_record_is_tracked_and_schema_is_strict(self) -> None:
        schema = validator.read_json(self.fixture, validator.PACKET_SCHEMA_REL)
        self.assertFalse(schema["additionalProperties"])
        self.assertFalse((self.fixture / "docs/phase1_5/t7/evaluation_review_packet.v1.json").exists())

    def test_validator_has_no_external_capability_imports(self) -> None:
        path = ROOT / "scripts/validate_phase1_5_t7_evaluation.py"
        tree = ast.parse(path.read_text(encoding="utf-8"))
        forbidden = {"requests", "httpx", "urllib", "socket", "subprocess", "PIL", "cv2", "torch", "transformers", "huggingface_hub"}
        imported = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        self.assertTrue(imported.isdisjoint(forbidden), imported & forbidden)
        source = path.read_text(encoding="utf-8").lower()
        for token in ("urlopen(", "requests.get(", "socket(", "subprocess.", "os.system(", "curl ", "wget ", "adb "):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
