#!/usr/bin/env python3
"""Tests for the metadata-only T10 Owner decision capture."""

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

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import validate_phase1_5_p1b_readiness as p1b  # noqa: E402
import validate_t10_owner_decision as gate  # noqa: E402


class T10OwnerDecisionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="t10-owner-decision-")
        self.fixture = Path(self.temp.name)
        for relative in (
            Path("docs/phase1_5/p1a"),
            Path("docs/phase1_5/p1b"),
            Path("docs/phase1_5/error_policy.v1.json"),
            Path("docs/reference"),
            Path("docs/current_program_status.v1.json"),
            Path("docs/CURRENT_PROGRAM_STATUS.md"),
            Path("docs/NEXT_GATE_MATRIX.md"),
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
        path.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def test_authorize_artifact_quarantine_only_validates(self) -> None:
        result = gate.run(self.fixture)
        self.assertEqual(result["decision"], "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY")
        self.assertEqual(result["status"], "P1B_ARTIFACT_QUARANTINE_SCOPE_APPROVED_ARTIFACT_ACQUISITION_NOT_YET_AUTHORIZED")
        self.assertTrue(result["artifact_quarantine_only"])
        self.assertTrue(all(result[key] is False for key in gate.FORBIDDEN_SCOPE))
        self.assertEqual(result["base"], gate.T9_BASE)

    def test_decision_hash_is_portable_across_lf_crlf_and_mixed_eol(self) -> None:
        decision_path = self.fixture / "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
        original_text = decision_path.read_bytes().decode("utf-8", errors="strict")
        normalized_text = original_text.replace("\r\n", "\n").replace("\r", "\n")
        pure_lf = normalized_text.encode("utf-8")
        pure_crlf = pure_lf.replace(b"\n", b"\r\n")
        mixed = b"".join(
            line.replace(b"\n", b"\r\n" if index % 2 else b"\n")
            for index, line in enumerate(pure_lf.splitlines(keepends=True))
        )

        expected = "45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9"
        normalized = normalized_text.encode("utf-8")
        self.assertEqual(len(normalized), 969)
        representations = (pure_lf, pure_crlf, mixed)
        raw_hashes = {hashlib.sha256(candidate).hexdigest() for candidate in representations}
        self.assertEqual(len(raw_hashes), 3)

        authorities = []
        for candidate in representations:
            with self.subTest(raw_sha256=hashlib.sha256(candidate).hexdigest()):
                decision_path.write_bytes(candidate)
                authority = gate.run(self.fixture)
                self.assertEqual(authority["decision_sha256"], expected)
                self.assertEqual(authority["decision_hash_mode"], "UTF8_TEXT_LF_NORMALIZED_SHA256_V1")
                self.assertEqual(authority["decision"], "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY")
                self.assertTrue(authority["artifact_quarantine_only"])
                self.assertTrue(all(authority[key] is False for key in gate.FORBIDDEN_SCOPE))
                authorities.append(authority)

        self.assertEqual(authorities[0], authorities[1])
        self.assertEqual(authorities[0], authorities[2])

    def test_non_eol_formatting_mutation_is_rejected_by_identity_projection(self) -> None:
        decision_path = self.fixture / "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
        original = decision_path.read_bytes().decode("utf-8", errors="strict")
        normalized = original.replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")
        changed = normalized.replace(b"{\n", b"{ \n", 1)
        self.assertNotEqual(changed, normalized)
        self.assertEqual(json.loads(changed.decode("utf-8")), self.read("docs/phase1_5/p1b/p1b_owner_decision.v1.json"))
        self.assertNotEqual(hashlib.sha256(changed).hexdigest(), hashlib.sha256(normalized).hexdigest())
        decision_path.write_bytes(changed)

        with self.assertRaisesRegex(gate.T10ValidationError, "T10 decision SHA projection changed"):
            gate.run(self.fixture)

    def test_malformed_utf8_decision_fails_closed(self) -> None:
        decision_path = self.fixture / "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
        decision_path.write_bytes(b"\xff\xfe")
        with self.assertRaises(gate.T10ValidationError):
            gate.run(self.fixture)

    def test_all_three_decisions_are_supported_by_existing_p1b_contract(self) -> None:
        manifest, manifest_sha = p1b.validate_manifest(self.fixture)
        decision = self.read("docs/phase1_5/p1b/p1b_owner_decision.v1.json")
        for branch in ("DEFER", "REJECT_PRIMARY"):
            candidate = copy.deepcopy(decision)
            candidate["decision"] = branch
            candidate["scope"]["artifact_quarantine_only"] = False
            candidate.pop("artifact_binding", None)
            p1b.validate_owner_decision(candidate, manifest, manifest_sha)

    def test_wrong_artifact_binding_fails_closed(self) -> None:
        decision = self.read("docs/phase1_5/p1b/p1b_owner_decision.v1.json")
        decision["artifact_binding"]["weight_bytes"] += 1
        self.write("docs/phase1_5/p1b/p1b_owner_decision.v1.json", decision)
        with self.assertRaises(gate.T10ValidationError):
            gate.run(self.fixture)

    def test_forbidden_scope_fails_closed(self) -> None:
        decision = self.read("docs/phase1_5/p1b/p1b_owner_decision.v1.json")
        decision["scope"]["download_authorized"] = True
        self.write("docs/phase1_5/p1b/p1b_owner_decision.v1.json", decision)
        with self.assertRaises(gate.T10ValidationError):
            gate.run(self.fixture)

    def test_decision_integrity_negative_matrix(self) -> None:
        base = self.read("docs/phase1_5/p1b/p1b_owner_decision.v1.json")
        cases: list[tuple[str, callable]] = [
            ("wrong_model_id", lambda d: d["artifact_binding"].update(model_id="wrong/model")),
            ("wrong_revision", lambda d: d["artifact_binding"].update(immutable_revision="0" * 40)),
            ("wrong_weight_bytes", lambda d: d["artifact_binding"].update(weight_bytes=1)),
            ("wrong_weight_sha", lambda d: d["artifact_binding"].update(weight_lfs_sha256="0" * 64)),
            ("wrong_manifest_sha", lambda d: d["artifact_binding"].update(readiness_manifest_sha256="0" * 64)),
            ("wrong_readiness_id", lambda d: d.update(readiness_id="wrong-readiness")),
            ("missing_artifact_binding", lambda d: d.pop("artifact_binding")),
            ("defer_with_binding", lambda d: d.update(decision="DEFER")),
            ("reject_with_binding", lambda d: d.update(decision="REJECT_PRIMARY")),
            ("unsupported_decision", lambda d: d.update(decision="AUTHORIZE_RUNTIME")),
        ]
        for scope_key in gate.FORBIDDEN_SCOPE:
            cases.append((f"forbidden_{scope_key}", lambda d, key=scope_key: d["scope"].update({key: True})))
        for label, mutate in cases:
            with self.subTest(case=label):
                candidate = copy.deepcopy(base)
                mutate(candidate)
                self.write("docs/phase1_5/p1b/p1b_owner_decision.v1.json", candidate)
                with self.assertRaises(gate.T10ValidationError):
                    gate.run(self.fixture)
                self.write("docs/phase1_5/p1b/p1b_owner_decision.v1.json", base)

    def test_canonical_projection_mismatch_fails_closed(self) -> None:
        status = self.read("docs/current_program_status.v1.json")
        status["t10_owner_decision"]["decision"] = "DEFER"
        self.write("docs/current_program_status.v1.json", status)
        with self.assertRaises(gate.T10ValidationError):
            gate.run(self.fixture)

    def test_authority_probes_block(self) -> None:
        for flag, expected in (
            ("--require-runtime-authority", "BLOCKED_RUNTIME_AUTHORIZATION_REQUIRED"),
            ("--require-real-ready-promotion", "BLOCKED_REAL_READY_PROMOTION_AUTHORIZATION_REQUIRED"),
            ("--require-human-review", "BLOCKED_HUMAN_REVIEW_AUTHORIZATION_REQUIRED"),
            ("--require-pipeline", "BLOCKED_PIPELINE_AUTHORIZATION_REQUIRED"),
        ):
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/validate_t10_owner_decision.py"), "--root", str(self.fixture), flag],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 2, flag)
            self.assertIn(expected, result.stdout, flag)

    def test_validator_has_no_external_capability_imports(self) -> None:
        source = (ROOT / "scripts/validate_t10_owner_decision.py").read_text(encoding="utf-8")
        for forbidden in ("requests", "socket", "urllib", "subprocess", "adb"):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
