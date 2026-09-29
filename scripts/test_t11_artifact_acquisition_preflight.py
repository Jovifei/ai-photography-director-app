#!/usr/bin/env python3
"""Tests for the capability-free T11 artifact acquisition preflight."""

from __future__ import annotations

import ast
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
import validate_t11_artifact_acquisition_preflight as gate  # noqa: E402


class T11ArtifactAcquisitionPreflightTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="t11-preflight-")
        self.fixture = Path(self.temp.name)
        for relative in (
            Path("docs/phase1_5/p1a"),
            Path("docs/phase1_5/p1b"),
            Path("docs/phase1_5/t11"),
            Path("docs/phase1_5/t7/evaluation_governance.v1.json"),
            Path("docs/phase1_5/t8"),
            Path("docs/phase1_5/t9"),
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

    @staticmethod
    def artifact() -> dict:
        return {
            "publisher": "Qwen",
            "model_id": "Qwen/Qwen3-VL-2B-Instruct",
            "immutable_revision": "89644892e4d85e24eaac8bacfd4f463576704203",
            "weight_filename": "model.safetensors",
            "weight_bytes": 4255140312,
            "weight_lfs_sha256": "7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0",
        }

    @classmethod
    def external_inputs(cls) -> dict:
        artifact = cls.artifact()
        return {
            "legal_review": {
                "schema_version": "1.0.0",
                "input_kind": "T11_LEGAL_REVIEW_INPUT",
                "scope": "MODEL_WEIGHT_QUARANTINE_ACQUISITION_ONLY",
                "artifact_binding": copy.deepcopy(artifact),
                "license_evidence_binding": {
                    "path": "docs/phase1_5/p1a/qwen_weight_license_evidence.v1.json",
                    "p1b_frozen_checkout_bytes": 2485,
                    "p1b_frozen_checkout_sha256": "08ac3a64b9bf4e189c8955bd8106b37a8deb20d8d44f9be9d131c7392f95adac",
                },
                "model_card_metadata": {"license_value": "apache-2.0", "metadata_is_legal_approval": False},
                "disposition": "APPROVED_FOR_QUARANTINE_ACQUISITION",
                "external_evidence_sha256": "a" * 64,
                "recorded_at": "2026-09-29T00:00:00Z",
            },
            "transport": {
                "schema_version": "1.0.0",
                "input_kind": "T11_TRANSPORT_VERIFICATION_INPUT",
                "artifact_binding": copy.deepcopy(artifact),
                "initial_domain": "huggingface.co",
                "redirect_domains": ["us.aws.cdn.hf.co"],
                "final_domain": "us.aws.cdn.hf.co",
                "transport_status": "VERIFIED_ALLOWED_DOMAIN_CHAIN",
                "identity_match": True,
                "artifact_body_accessed": False,
                "transport_evidence_sha256": "b" * 64,
                "recorded_at": "2026-09-29T00:00:00Z",
            },
            "fresh_metadata": {
                "schema_version": "1.0.0",
                "input_kind": "T11_FRESH_METADATA_INPUT",
                "expected_identity": {**copy.deepcopy(artifact), "license_value": "apache-2.0"},
                "observed_identity": {**copy.deepcopy(artifact), "license_value": "apache-2.0"},
                "fresh_metadata_status": "MATCHED_IMMUTABLE_IDENTITY",
                "fallback_permitted": False,
                "metadata_evidence_sha256": "c" * 64,
                "recorded_at": "2026-09-29T00:00:00Z",
            },
            "destination": {
                "schema_version": "1.0.0",
                "input_kind": "T11_QUARANTINE_DESTINATION_INPUT",
                "destination_id": "quarantine-test-001",
                "destination_kind": "EXTERNAL_QUARANTINE_DIRECTORY",
                "external_quarantine_directory": True,
                "repository_location": False,
                "android_project_location": False,
                "evidence_corpus_location": False,
                "product_cache_location": False,
                "app_private_location": False,
                "artifact_only": True,
                "available_bytes": 4255140312,
                "actual_path_tracked": False,
                "cleanup_policy": {
                    "policy_id": "cleanup-test-001",
                    "trigger": "OWNER_APPROVED_CLEANUP_ONLY",
                    "delete_scope": "NAMED_DESTINATION_ONLY",
                },
            },
        }

    def authorized_decision(self, inputs: dict, choice: str = "AUTHORIZE_QUARANTINE_ACQUISITION_ONLY") -> dict:
        manifest_path = self.fixture / gate.MANIFEST_REL
        manifest = gate.load_preflight_manifest(self.fixture)
        authorize = choice == "AUTHORIZE_QUARANTINE_ACQUISITION_ONLY"
        return {
            "schema_version": "1.0.0",
            "input_kind": "T11_ARTIFACT_ACQUISITION_OWNER_DECISION",
            "decision_id": "decision-test-001",
            "recorded_at": "2026-09-29T00:00:00Z",
            "decision": choice,
            "t10_authority_binding": {
                key: manifest["t10_owner_decision"][key]
                for key in ("path", "decision_hash_mode", "portable_decision_sha256", "git_blob_sha1")
            },
            "t11_manifest_binding": {
                "path": gate.MANIFEST_REL.as_posix(),
                "manifest_id": manifest["manifest_id"],
                "manifest_hash_mode": gate.MANIFEST_HASH_MODE,
                "manifest_sha256": gate.preflight_manifest_sha256(self.fixture),
            },
            "artifact_binding": copy.deepcopy(manifest["artifact"]),
            "evidence_binding": {
                "legal_review_input_sha256": gate.canonical_input_sha256(inputs["legal_review"]),
                "transport_verification_input_sha256": gate.canonical_input_sha256(inputs["transport"]),
                "fresh_metadata_input_sha256": gate.canonical_input_sha256(inputs["fresh_metadata"]),
                "destination_id": inputs["destination"]["destination_id"],
            },
            "approved_domains": ["huggingface.co", "us.aws.cdn.hf.co"],
            "scope": {
                "acquisition_authorized": authorize,
                "artifact_body_access_authorized": authorize,
                "runtime_authorized": False,
                "inference_authorized": False,
                "app_integration_authorized": False,
                "pipeline_integration_authorized": False,
                "private_media_authorized": False,
            },
        }

    def test_exact_t10_r3_identity_loads(self) -> None:
        authority = gate.load_t10_authority(self.fixture)
        self.assertEqual(authority["decision_hash_mode"], "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1")
        self.assertEqual(authority["portable_decision_sha256"], "45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9")
        self.assertEqual(authority["decision"], "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY")
        self.assertTrue(authority["artifact_quarantine_only"])
        self.assertTrue(all(authority[key] is False for key in gate.FORBIDDEN_AUTHORITY_FLAGS))

    def test_t10_base_hash_mode_and_owner_branch_drift_are_rejected(self) -> None:
        path = self.fixture / gate.MANIFEST_REL
        original = json.loads(path.read_text(encoding="utf-8"))
        cases = (
            ("wrong_base", lambda item: item.update(accepted_t10_r3_base="0" * 40)),
            ("wrong_sha", lambda item: item["t10_owner_decision"].update(portable_decision_sha256="0" * 64)),
            ("wrong_hash_mode", lambda item: item["t10_owner_decision"].update(decision_hash_mode="RAW_SHA256")),
            ("wrong_owner_choice", lambda item: item["t10_owner_decision"].update(decision="DEFER")),
        )
        for label, mutate in cases:
            with self.subTest(case=label):
                candidate = copy.deepcopy(original)
                mutate(candidate)
                path.write_text(json.dumps(candidate), encoding="utf-8")
                with self.assertRaises(gate.ValidationError):
                    gate.load_preflight_manifest(self.fixture)
        path.write_text(json.dumps(original, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def test_any_t10_execution_flag_true_is_rejected(self) -> None:
        for key in gate.FORBIDDEN_AUTHORITY_FLAGS:
            with self.subTest(flag=key):
                path = self.fixture / "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
                decision = json.loads(path.read_text(encoding="utf-8"))
                decision["scope"][key] = True
                path.write_text(json.dumps(decision), encoding="utf-8")
                with self.assertRaises(gate.ValidationError):
                    gate.load_t10_authority(self.fixture)
                source = ROOT / "docs/phase1_5/p1b/p1b_owner_decision.v1.json"
                shutil.copy2(source, path)

    def test_current_tracked_state_is_blocked_on_legal_review(self) -> None:
        result = gate.run(self.fixture)
        self.assertEqual(result["state"], "BLOCKED_LEGAL_REVIEW")
        self.assertEqual(result["stage_status"], "ARTIFACT_ACQUISITION_PREFLIGHT_READY_EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED")
        self.assertEqual(result["blockers"], [
            "LEGAL_REVIEW_REQUIRED",
            "TRANSPORT_VERIFICATION_REQUIRED",
            "FRESH_METADATA_REVALIDATION_REQUIRED",
            "QUARANTINE_DESTINATION_REQUIRED",
            "OWNER_ACQUISITION_AUTHORIZATION_REQUIRED",
        ])
        self.assertFalse(result["acquisition_authorized"])
        self.assertFalse(result["artifact_body_access_authorized"])
        self.assertFalse(result["network_access_authorized"])
        self.assertFalse(result["hypothetical_inputs_only"])

    def test_t10_run_and_nested_t11_status_projection_remain_compatible(self) -> None:
        authority = gate.load_t10_authority(self.fixture)
        self.assertEqual(authority["decision"], "AUTHORIZE_ARTIFACT_QUARANTINE_ONLY")
        manifest = gate.load_preflight_manifest(self.fixture)
        gate.validate_canonical_status_projection(self.fixture, manifest)
        status_path = self.fixture / "docs/current_program_status.v1.json"
        original = json.loads(status_path.read_text(encoding="utf-8"))
        self.assertEqual(original["current_stage"], "PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED")
        self.assertEqual(original["t11_artifact_acquisition_preflight"]["current_blocker"], "BLOCKED_LEGAL_REVIEW")

        cases = (
            ("top_level_t11", lambda status: status.update(current_stage="ARTIFACT_ACQUISITION_PREFLIGHT_READY_EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED")),
            ("missing_projection", lambda status: status.pop("t11_artifact_acquisition_preflight")),
            ("acquisition_authority", lambda status: status["t11_artifact_acquisition_preflight"].update(artifact_acquisition_authorized=True)),
            ("wrong_blocker", lambda status: status["t11_artifact_acquisition_preflight"].update(current_blocker="READY_FOR_OWNER_ACQUISITION_DECISION")),
            ("wrong_manifest_hash_mode", lambda status: status["t11_artifact_acquisition_preflight"].update(manifest_hash_mode="RAW_SHA256")),
            ("wrong_manifest_sha", lambda status: status["t11_artifact_acquisition_preflight"].update(manifest_sha256="0" * 64)),
        )
        for label, mutate in cases:
            with self.subTest(case=label):
                candidate = copy.deepcopy(original)
                mutate(candidate)
                status_path.write_text(json.dumps(candidate, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                with self.assertRaises(gate.ValidationError):
                    gate.validate_canonical_status_projection(self.fixture, manifest)

    def test_t11_manifest_hash_mode_is_portable_text_identity(self) -> None:
        self.assertTrue(hasattr(gate, "MANIFEST_HASH_MODE"))
        self.assertEqual(gate.MANIFEST_HASH_MODE, "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1")

    def test_t11_manifest_authority_and_future_owner_binding_are_cross_eol_portable(self) -> None:
        manifest_path = self.fixture / gate.MANIFEST_REL
        original = manifest_path.read_bytes()
        normalized = original.decode("utf-8", errors="strict").replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")
        lf = normalized
        crlf = normalized.replace(b"\n", b"\r\n")
        mixed = b"".join(
            line.replace(b"\n", b"\r\n" if index % 2 else b"\n")
            for index, line in enumerate(normalized.splitlines(keepends=True))
        )
        representations = (lf, crlf, mixed)
        self.assertEqual(len({hashlib.sha256(value).hexdigest() for value in representations}), 3)

        tracked_results = []
        hypothetical_results = []
        for representation in representations:
            manifest_path.write_bytes(representation)
            portable_sha = gate.preflight_manifest_sha256(self.fixture)
            self.assertEqual(portable_sha, "4aee65b8ae343ad57338f179cabfbfa798672aa704e635331aaedbaff9f49830")
            current = gate.run(self.fixture)
            self.assertEqual(current["t11_manifest_hash_mode"], "UTF8_TEXT_EOL_NORMALIZED_SHA256_V1")
            self.assertEqual(current["t11_manifest_sha256"], portable_sha)
            tracked_results.append(current)

            inputs = self.external_inputs()
            decision = self.authorized_decision(inputs)
            hypothetical = gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)
            self.assertEqual(hypothetical["state"], "READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN")
            self.assertEqual(hypothetical["evidence_status_source"], "HYPOTHETICAL_UNIT_INPUTS")
            hypothetical_results.append(hypothetical)

        self.assertEqual(tracked_results[0], tracked_results[1])
        self.assertEqual(tracked_results[0], tracked_results[2])
        self.assertEqual(hypothetical_results[0], hypothetical_results[1])
        self.assertEqual(hypothetical_results[0], hypothetical_results[2])

    def test_t11_manifest_non_eol_mutation_and_malformed_utf8_fail_closed(self) -> None:
        manifest_path = self.fixture / gate.MANIFEST_REL
        original = manifest_path.read_bytes()
        changed = original.replace(b'"manifest_id":', b'"manifest_id" :', 1)
        self.assertEqual(json.loads(changed.decode("utf-8")), json.loads(original.decode("utf-8")))
        inputs = self.external_inputs()
        stale_decision = self.authorized_decision(inputs)
        manifest_path.write_bytes(changed)
        with self.assertRaises(gate.ValidationError):
            gate.run(self.fixture)
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs, owner_decision=stale_decision)

        manifest_path.write_bytes(b"\xff\xfe")
        with self.assertRaises(gate.ValidationError):
            gate.preflight_manifest_sha256(self.fixture)
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs, owner_decision=stale_decision)

    def test_p1b_source_tuples_are_bound_exactly_as_frozen(self) -> None:
        manifest, manifest_sha = p1b.validate_manifest(self.fixture)
        result = gate.run(self.fixture)
        binding = result["p1b_source_binding"]
        self.assertEqual(binding["p1a_sources"], manifest["p1a_sources"])
        self.assertEqual(result["p1b_manifest_sha256"], manifest_sha)
        self.assertEqual(binding["authority_mode"], "FROZEN_P1B_CHECKOUT_BINDING_V1")
        self.assertFalse(binding["cross_platform_canonical"])
        self.assertTrue(all(row["normalized_content_matches_git_blob_bytes"] for row in binding["eol_diagnostics"]["rows"]))

    def test_eol_only_representation_difference_is_diagnostic(self) -> None:
        checkout = b"alpha\r\nbeta\r\n"
        blob = b"alpha\nbeta\n"
        self.assertNotEqual(hashlib.sha256(checkout).hexdigest(), hashlib.sha256(blob).hexdigest())
        self.assertEqual(gate.classify_source_representation(checkout, blob), "EOL_ONLY_CHECKOUT_REPRESENTATION_DIFFERENCE")

    def test_non_eol_p1a_content_drift_stops_without_repairing_sources(self) -> None:
        relative = "docs/phase1_5/p1a/primary_artifact_authorization_draft.v1.json"
        source = self.fixture / relative
        manifest_path = self.fixture / "docs/phase1_5/p1b/p1b_readiness_manifest.v1.json"
        original_manifest_bytes = manifest_path.read_bytes()
        changed = source.read_bytes().replace(b"READY_FOR_OWNER_DOWNLOAD_DECISION", b"READY_FOR_DIFFERENT_DECISION", 1)
        source.write_bytes(changed)
        with self.assertRaisesRegex(gate.ValidationError, "SOURCE_CONTENT_DRIFT"):
            gate.load_p1b_authority(self.fixture)
        self.assertEqual(source.read_bytes(), changed)
        self.assertEqual(manifest_path.read_bytes(), original_manifest_bytes)

    def test_model_card_metadata_alone_is_not_legal_approval(self) -> None:
        inputs = self.external_inputs()
        del inputs["legal_review"]["disposition"]
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs)

    def test_missing_legal_record_blocks(self) -> None:
        inputs = self.external_inputs()
        result = gate.evaluate_preflight(self.fixture, transport=inputs["transport"], fresh_metadata=inputs["fresh_metadata"], destination=inputs["destination"])
        self.assertEqual(result["state"], "BLOCKED_LEGAL_REVIEW")
        self.assertTrue(result["hypothetical_inputs_only"])

    def test_more_review_required_blocks_and_rejected_is_terminal(self) -> None:
        inputs = self.external_inputs()
        inputs["legal_review"]["disposition"] = "MORE_REVIEW_REQUIRED"
        result = gate.evaluate_preflight(self.fixture, **inputs)
        self.assertEqual(result["state"], "BLOCKED_LEGAL_REVIEW")
        inputs["legal_review"]["disposition"] = "REJECTED"
        result = gate.evaluate_preflight(self.fixture, **inputs)
        self.assertEqual(result["state"], "ARTIFACT_ACQUISITION_REJECTED_LEGAL")
        self.assertEqual(result["blockers"], [])

    def test_wrong_legal_model_or_revision_is_rejected(self) -> None:
        for key, value in (("model_id", "wrong/model"), ("immutable_revision", "0" * 40)):
            with self.subTest(identity=key):
                inputs = self.external_inputs()
                inputs["legal_review"]["artifact_binding"][key] = value
                with self.assertRaises(gate.ValidationError):
                    gate.evaluate_preflight(self.fixture, **inputs)

    def test_legal_input_rejects_reviewer_identity_fields(self) -> None:
        inputs = self.external_inputs()
        inputs["legal_review"]["reviewer_email"] = "reviewer@example.invalid"
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs)

    def test_unverified_transport_blocks(self) -> None:
        inputs = self.external_inputs()
        inputs["transport"]["transport_status"] = "UNVERIFIED"
        result = gate.evaluate_preflight(self.fixture, **inputs)
        self.assertEqual(result["state"], "BLOCKED_TRANSPORT_VERIFICATION")

    def test_unlisted_transport_domains_and_body_access_are_rejected(self) -> None:
        mutations = (
            ("initial", lambda item: item.update(initial_domain="evil.example")),
            ("redirect", lambda item: item.update(redirect_domains=["evil.example"])),
            ("final", lambda item: item.update(final_domain="evil.example")),
            ("body_access", lambda item: item.update(artifact_body_accessed=True)),
        )
        for label, mutate in mutations:
            with self.subTest(case=label):
                inputs = self.external_inputs()
                mutate(inputs["transport"])
                with self.assertRaises(gate.ValidationError):
                    gate.evaluate_preflight(self.fixture, **inputs)

    def test_missing_fresh_metadata_blocks(self) -> None:
        inputs = self.external_inputs()
        result = gate.evaluate_preflight(self.fixture, legal_review=inputs["legal_review"], transport=inputs["transport"], destination=inputs["destination"])
        self.assertEqual(result["state"], "BLOCKED_FRESH_METADATA")

    def test_publisher_revision_filename_bytes_and_lfs_drift_are_rejected(self) -> None:
        mutations = (
            ("publisher", "publisher", "Other"),
            ("revision", "immutable_revision", "0" * 40),
            ("filename", "weight_filename", "other.safetensors"),
            ("bytes", "weight_bytes", 1),
            ("lfs_sha", "weight_lfs_sha256", "0" * 64),
        )
        for label, key, value in mutations:
            with self.subTest(case=label):
                inputs = self.external_inputs()
                inputs["fresh_metadata"]["observed_identity"][key] = value
                inputs["fresh_metadata"]["fresh_metadata_status"] = "IDENTITY_DRIFT"
                with self.assertRaises(gate.ValidationError):
                    gate.evaluate_preflight(self.fixture, **inputs)

    def test_missing_destination_blocks(self) -> None:
        inputs = self.external_inputs()
        result = gate.evaluate_preflight(self.fixture, legal_review=inputs["legal_review"], transport=inputs["transport"], fresh_metadata=inputs["fresh_metadata"])
        self.assertEqual(result["state"], "BLOCKED_QUARANTINE_DESTINATION")

    def test_repository_android_corpus_and_product_destination_are_rejected(self) -> None:
        mutations = (
            ("repository", "repository_location"),
            ("android", "android_project_location"),
            ("corpus", "evidence_corpus_location"),
            ("product_cache", "product_cache_location"),
        )
        for label, key in mutations:
            with self.subTest(case=label):
                inputs = self.external_inputs()
                inputs["destination"][key] = True
                with self.assertRaises(gate.ValidationError):
                    gate.evaluate_preflight(self.fixture, **inputs)

    def test_insufficient_destination_capacity_and_absolute_path_are_rejected(self) -> None:
        for label, mutate in (
            ("capacity", lambda item: item.update(available_bytes=4255140311)),
            ("absolute_path", lambda item: item.update(destination_path="C:/private/model")),
        ):
            with self.subTest(case=label):
                inputs = self.external_inputs()
                mutate(inputs["destination"])
                with self.assertRaises(gate.ValidationError):
                    gate.evaluate_preflight(self.fixture, **inputs)

    def test_complete_hypothetical_external_evidence_reaches_owner_decision_gate(self) -> None:
        result = gate.evaluate_preflight(self.fixture, **self.external_inputs())
        self.assertEqual(result["state"], "READY_FOR_OWNER_ACQUISITION_DECISION")
        self.assertEqual(result["legal_review_status"], "APPROVED_FOR_QUARANTINE_ACQUISITION")
        self.assertEqual(result["transport_status"], "VERIFIED_ALLOWED_DOMAIN_CHAIN")
        self.assertEqual(result["fresh_metadata_status"], "MATCHED_IMMUTABLE_IDENTITY")
        self.assertEqual(result["quarantine_destination_status"], "DECLARED")
        self.assertEqual(result["owner_acquisition_decision"], "NONE_TRACKED")
        self.assertEqual(result["tracked_current_blocker"], "BLOCKED_LEGAL_REVIEW")
        self.assertEqual(result["tracked_current_evidence_status"]["legal_review"], "NOT_LEGAL_APPROVED")
        self.assertEqual(result["evidence_status_source"], "HYPOTHETICAL_UNIT_INPUTS")
        self.assertEqual(result["blockers"], ["OWNER_ACQUISITION_AUTHORIZATION_REQUIRED"])
        self.assertFalse(result["acquisition_authorized"])
        self.assertFalse(result["artifact_body_access_authorized"])
        self.assertTrue(result["hypothetical_inputs_only"])

    def test_future_owner_decision_requires_all_evidence_bindings(self) -> None:
        inputs = self.external_inputs()
        decision = self.authorized_decision(inputs)
        decision["evidence_binding"].pop("transport_verification_input_sha256")
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)

    def test_future_decision_must_bind_current_t10_hash(self) -> None:
        inputs = self.external_inputs()
        decision = self.authorized_decision(inputs)
        decision["t10_authority_binding"]["portable_decision_sha256"] = "0" * 64
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)

    def test_future_decision_must_bind_current_t11_manifest_sha(self) -> None:
        inputs = self.external_inputs()
        decision = self.authorized_decision(inputs)
        decision["t11_manifest_binding"]["manifest_sha256"] = "0" * 64
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)

    def test_future_decision_must_bind_portable_t11_manifest_mode(self) -> None:
        inputs = self.external_inputs()
        decision = self.authorized_decision(inputs)
        decision["t11_manifest_binding"]["manifest_hash_mode"] = "RAW_SHA256"
        with self.assertRaises(gate.ValidationError):
            gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)

    def test_future_positive_decision_cannot_enable_forbidden_authority(self) -> None:
        for key in ("runtime_authorized", "inference_authorized", "app_integration_authorized", "pipeline_integration_authorized", "private_media_authorized"):
            with self.subTest(flag=key):
                inputs = self.external_inputs()
                decision = self.authorized_decision(inputs)
                decision["scope"][key] = True
                with self.assertRaises(gate.ValidationError):
                    gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)

    def test_hypothetical_quarantine_authorization_requires_separate_execution_plan(self) -> None:
        inputs = self.external_inputs()
        decision = self.authorized_decision(inputs)
        result = gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)
        self.assertEqual(result["state"], "READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN")
        self.assertTrue(result["acquisition_authorized"])
        self.assertTrue(result["artifact_body_access_authorized"])
        self.assertTrue(all(result[key] is False for key in gate.T11_FORBIDDEN_AUTHORITY_FLAGS))
        self.assertEqual(result["owner_acquisition_decision"], "AUTHORIZE_QUARANTINE_ACQUISITION_ONLY")
        self.assertEqual(result["tracked_current_evidence_status"]["future_acquisition_owner_decision"], "NONE_TRACKED")
        self.assertEqual(result["blockers"], [])

    def test_hypothetical_owner_defer_and_reject_are_terminal_without_authority(self) -> None:
        expected = (
            ("DEFER", "OWNER_DEFERRED"),
            ("REJECT_ACQUISITION", "ARTIFACT_ACQUISITION_REJECTED_OWNER"),
        )
        for choice, state in expected:
            with self.subTest(decision=choice):
                inputs = self.external_inputs()
                decision = self.authorized_decision(inputs, choice)
                result = gate.evaluate_preflight(self.fixture, **inputs, owner_decision=decision)
                self.assertEqual(result["state"], state)
                self.assertFalse(result["acquisition_authorized"])
                self.assertFalse(result["artifact_body_access_authorized"])
                self.assertNotIn(result["state"], {"READY_FOR_OWNER_ACQUISITION_DECISION", "READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN"})
                self.assertEqual(result["blockers"], [])

    def test_tracked_state_cannot_reach_either_positive_state(self) -> None:
        result = gate.run(self.fixture)
        self.assertNotIn(result["state"], {"READY_FOR_OWNER_ACQUISITION_DECISION", "READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN"})
        self.assertEqual(result["state"], "BLOCKED_LEGAL_REVIEW")

    def test_validator_has_no_network_downloader_model_or_device_capabilities(self) -> None:
        source_path = ROOT / "scripts/validate_t11_artifact_acquisition_preflight.py"
        tree = ast.parse(source_path.read_text(encoding="utf-8"))
        imported: set[str] = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported.add(node.module.split(".")[0])
        forbidden = {"requests", "httpx", "urllib", "socket", "huggingface_hub", "torch", "transformers", "PIL", "cv2", "subprocess", "adb"}
        self.assertTrue(imported.isdisjoint(forbidden), imported & forbidden)

    def test_all_authority_probes_block(self) -> None:
        script = ROOT / "scripts/validate_t11_artifact_acquisition_preflight.py"
        for flag, expected in (
            ("--require-artifact-access", "BLOCKED_ARTIFACT_ACQUISITION_AUTHORIZATION_REQUIRED"),
            ("--require-network-probe", "BLOCKED_TRANSPORT_VERIFICATION_AUTHORITY_REQUIRED"),
            ("--require-runtime", "BLOCKED_RUNTIME_AUTHORIZATION_REQUIRED"),
            ("--require-private-media", "BLOCKED_PRIVATE_MEDIA_AUTHORIZATION_REQUIRED"),
            ("--require-pipeline", "BLOCKED_PIPELINE_AUTHORIZATION_REQUIRED"),
        ):
            with self.subTest(flag=flag):
                result = subprocess.run([sys.executable, str(script), "--root", str(self.fixture), flag], capture_output=True, text=True, check=False)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(expected, result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
