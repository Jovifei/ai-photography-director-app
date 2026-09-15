"""No real approvals. In-memory attestations below exercise mechanics only."""
from __future__ import annotations

import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from p23c_bundle_contract import PackError, encoded_json, sha256
from p23c_prepare_review_pack import (
    ARCHIVE_NAMES, MAX_ARCHIVE_BYTES, REVIEW_FIELDS, candidate_bytes, main,
    publish_new, read_selected, review_template, validate_receipt, verify_candidate,
)
from test_p23c_bundle_contract import synthetic_bundle


def synthetic_receipt(raw: bytes, purpose: str = "SYNTHETIC_TEST") -> dict:
    receipt = review_template(raw, purpose)
    for field in ("review_id", "reviewer_id", "source_evidence_id", "producer_qualification_evidence_id"):
        receipt[field] = "synthetic_test_only"
    for item in receipt["entries"]:
        item["evidence_id"] = "synthetic_test_only"
        for field in REVIEW_FIELDS:
            item[field] = "APPROVED"
    return receipt


class ReviewTests(unittest.TestCase):
    def setUp(self):
        self.raw = encoded_json(synthetic_bundle())
        self.receipt = synthetic_receipt(self.raw)

    def test_template_is_pending_and_has_no_fake_identity(self):
        receipt = review_template(self.raw, "SYNTHETIC_TEST")
        self.assertIsNone(receipt["reviewer_id"])
        self.assertTrue(all(item[field] == "PENDING" for item in receipt["entries"] for field in REVIEW_FIELDS))
        with self.assertRaises(PackError):
            validate_receipt(self.raw, encoded_json(receipt), "SYNTHETIC_TEST")

    def test_success_does_not_authenticate_or_authorize(self):
        raw = candidate_bytes(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST")
        result = verify_candidate(raw, "SYNTHETIC_TEST")
        self.assertEqual("UNSIGNED_NOT_FOR_DISTRIBUTION", result["status"])
        for field in ("publisher_authenticated", "app_import_authorized", "release_authorized"):
            self.assertIs(result[field], False)

    def test_reproducible_archive_preserves_exact_input(self):
        first = candidate_bytes(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST")
        self.assertEqual(first, candidate_bytes(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST"))
        with zipfile.ZipFile(io.BytesIO(first)) as archive:
            self.assertEqual(ARCHIVE_NAMES, tuple(archive.namelist()))
            self.assertEqual(self.raw, archive.read("bundle.json"))
            self.assertEqual(encoded_json(self.receipt), archive.read("review-receipt.json"))

    def test_twenty_entry_roundtrip(self):
        raw = encoded_json(synthetic_bundle(20))
        archive = candidate_bytes(raw, encoded_json(synthetic_receipt(raw)), "SYNTHETIC_TEST")
        self.assertEqual(20, verify_candidate(archive, "SYNTHETIC_TEST")["reference_count"])

    def test_public_profile_checks_twenty_but_remains_unsigned(self):
        # Simulated assertions, NOT evidence that any public content was reviewed.
        raw = encoded_json(synthetic_bundle(20))
        archive = candidate_bytes(raw, encoded_json(synthetic_receipt(raw, "PUBLIC_CANDIDATE")), "PUBLIC_CANDIDATE")
        self.assertIs(verify_candidate(archive, "PUBLIC_CANDIDATE")["release_authorized"], False)
        with self.assertRaisesRegex(PackError, "REQUIRES_20"):
            candidate_bytes(self.raw, encoded_json(synthetic_receipt(self.raw, "PUBLIC_CANDIDATE")), "PUBLIC_CANDIDATE")

    def test_synthetic_receipt_cannot_be_reused_as_public(self):
        with self.assertRaisesRegex(PackError, "PURPOSE_MISMATCH"):
            candidate_bytes(self.raw, encoded_json(self.receipt), "PUBLIC_CANDIDATE")

    def test_reformatting_requires_new_review_binding(self):
        with self.assertRaisesRegex(PackError, "REVIEW_BINDING_MISMATCH"):
            candidate_bytes(self.raw + b"\n", encoded_json(self.receipt), "SYNTHETIC_TEST")

    def test_changed_bundle_rejected_even_with_fresh_valid_digest(self):
        raw = encoded_json(synthetic_bundle(3))
        with self.assertRaisesRegex(PackError, "REVIEW_BINDING_MISMATCH"):
            candidate_bytes(raw, encoded_json(self.receipt), "SYNTHETIC_TEST")

    def test_each_review_dimension_must_be_approved(self):
        for field in REVIEW_FIELDS:
            for state in ("PENDING", "REJECTED", True, None):
                receipt = synthetic_receipt(self.raw)
                receipt["entries"][0][field] = state
                with self.assertRaisesRegex(PackError, "REVIEW_NOT_COMPLETE"):
                    candidate_bytes(self.raw, encoded_json(receipt), "SYNTHETIC_TEST")

    def test_all_entries_exactly_once(self):
        for edit in ("missing", "duplicate", "extra", "unknown"):
            receipt = synthetic_receipt(self.raw)
            if edit == "missing": receipt["entries"].pop()
            if edit == "duplicate": receipt["entries"][1] = receipt["entries"][0]
            if edit == "extra": receipt["entries"].append(receipt["entries"][0])
            if edit == "unknown": receipt["entries"][0]["reference_id"] = "unknown"
            with self.assertRaisesRegex(PackError, "REVIEW_COVERAGE_INVALID"):
                candidate_bytes(self.raw, encoded_json(receipt), "SYNTHETIC_TEST")

    def test_review_order_does_not_guess_photo_mapping(self):
        self.receipt["entries"].reverse()
        _, verified = validate_receipt(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST")
        self.assertEqual(self.receipt, verified)

    def test_receipt_unknown_fields_and_sensitive_locators_rejected(self):
        for field in ("reviewer_id", "source_evidence_id", "producer_qualification_evidence_id"):
            receipt = synthetic_receipt(self.raw)
            receipt[field] = "file:///private"
            with self.assertRaises(PackError):
                candidate_bytes(self.raw, encoded_json(receipt), "SYNTHETIC_TEST")
        self.receipt["image_uri"] = "forbidden"
        with self.assertRaisesRegex(PackError, "SCHEMA_INVALID"):
            candidate_bytes(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST")

    def test_receipt_duplicate_keys_rejected(self):
        raw = b'{"purpose":"SYNTHETIC_TEST",' + encoded_json(self.receipt)[1:]
        with self.assertRaisesRegex(PackError, "DUPLICATE_JSON_KEY"):
            candidate_bytes(self.raw, raw, "SYNTHETIC_TEST")

    def test_receipt_too_large_rejected(self):
        with self.assertRaisesRegex(PackError, "DOCUMENT_TOO_LARGE"):
            candidate_bytes(self.raw, b" " * (64 * 1024 + 1), "SYNTHETIC_TEST")

    def test_corrupted_truncated_or_trailing_archive_rejected(self):
        raw = candidate_bytes(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST")
        for broken in (b"not-zip", raw[:60], raw + b"trailing", raw[:-20]):
            with self.assertRaises(PackError): verify_candidate(broken, "SYNTHETIC_TEST")

    def test_extra_or_traversal_archive_member_rejected(self):
        for name in ("../bundle.json", "extra.txt", "/absolute"):
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, "w") as archive:
                archive.writestr(name, b"synthetic")
            with self.assertRaisesRegex(PackError, "ARCHIVE_MEMBERS_INVALID"):
                verify_candidate(stream.getvalue(), "SYNTHETIC_TEST")

    def test_compressed_member_rejected_before_read(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in ARCHIVE_NAMES:
                archive.writestr(name, b"x" * 100)
        with self.assertRaisesRegex(PackError, "ARCHIVE_MEMBER_INVALID"):
            verify_candidate(stream.getvalue(), "SYNTHETIC_TEST")

    def test_modified_manifest_cannot_grant_release(self):
        original = candidate_bytes(self.raw, encoded_json(self.receipt), "SYNTHETIC_TEST")
        stream = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(original)) as archive, zipfile.ZipFile(stream, "w") as changed:
            for info in archive.infolist():
                data = archive.read(info.filename)
                if info.filename == "candidate-manifest.json":
                    manifest = json.loads(data)
                    manifest["release_authorized"] = True
                    data = encoded_json(manifest)
                changed.writestr(info, data)
        with self.assertRaisesRegex(PackError, "ARCHIVE_NOT_CANONICAL"):
            verify_candidate(stream.getvalue(), "SYNTHETIC_TEST")

    def test_public_local_service_receipt_is_not_pipeline_evidence(self):
        from p23c_bundle_contract import canonical_payload
        bundle = synthetic_bundle(20)
        bundle["source"]["origin"] = "LOCAL_SERVICE"
        bundle["integrity"]["payload_sha256"] = sha256(canonical_payload(bundle))
        raw = encoded_json(bundle)
        with self.assertRaisesRegex(PackError, "REQUIRES_PIPELINE"):
            candidate_bytes(raw, encoded_json(synthetic_receipt(raw, "PUBLIC_CANDIDATE")), "PUBLIC_CANDIDATE")

    def test_archive_budget(self):
        with self.assertRaisesRegex(PackError, "ARCHIVE_TOO_LARGE"):
            verify_candidate(b" " * (MAX_ARCHIVE_BYTES + 1), "SYNTHETIC_TEST")


class FileAndCliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="p23c-tests-")
        self.root = Path(self.temp.name)
        self.bundle = self.root / "input.json"
        self.raw = encoded_json(synthetic_bundle())
        self.bundle.write_bytes(self.raw)

    def tearDown(self):
        self.temp.cleanup()

    def cli(self, *args):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = main(list(map(str, args)))
        return status, json.loads(output.getvalue())

    def test_cli_inspect_does_not_echo_content_or_path(self):
        status, result = self.cli("inspect", "--bundle", self.bundle)
        self.assertEqual(0, status)
        self.assertNotIn(str(self.root), json.dumps(result))
        self.assertNotIn("photography", result)
        self.assertFalse(result["app_import_authorized"])

    def test_cli_template_and_blocked_prepare_leave_no_archive(self):
        receipt = self.root / "pending.json"
        output = self.root / "candidate.zip"
        self.assertEqual(0, self.cli("review-template", "--bundle", self.bundle,
                                    "--purpose", "SYNTHETIC_TEST", "--output", receipt)[0])
        self.assertEqual(2, self.cli("prepare", "--bundle", self.bundle, "--receipt", receipt,
                                    "--purpose", "SYNTHETIC_TEST", "--output", output)[0])
        self.assertFalse(output.exists())

    def test_cli_prepare_verify_and_no_overwrite(self):
        receipt = self.root / "receipt.json"
        receipt.write_bytes(encoded_json(synthetic_receipt(self.raw)))
        output = self.root / "candidate.zip"
        args = ("prepare", "--bundle", self.bundle, "--receipt", receipt,
                "--purpose", "SYNTHETIC_TEST", "--output", output)
        self.assertEqual(0, self.cli(*args)[0])
        first = output.read_bytes()
        self.assertEqual(0, self.cli("verify-candidate", "--archive", output,
                                    "--purpose", "SYNTHETIC_TEST")[0])
        self.assertEqual("OUTPUT_EXISTS", self.cli(*args)[1]["code"])
        self.assertEqual(first, output.read_bytes())
        self.assertEqual(self.raw, self.bundle.read_bytes())

    def test_reject_output_inside_repo(self):
        repo = self.root / "repo"
        repo.mkdir()
        (repo / ".git").write_text("synthetic worktree marker")
        with self.assertRaisesRegex(PackError, "OUTPUT_INSIDE_REPOSITORY"):
            publish_new(repo / "candidate.zip", b"test")

    def test_reject_explicit_repository_without_git_marker(self):
        with self.assertRaisesRegex(PackError, "OUTPUT_INSIDE_REPOSITORY"):
            publish_new(self.root / "candidate.zip", b"test", repo_root=self.root)

    def test_wrong_extension_and_missing_file_sanitized(self):
        self.assertEqual("INPUT_TYPE_INVALID", self.cli("inspect", "--bundle", self.root / "private.jpg")[1]["code"])
        self.assertEqual("LOCAL_IO_ERROR", self.cli("inspect", "--bundle", self.root / "missing.json")[1]["code"])

    def test_parent_must_exist(self):
        with self.assertRaisesRegex(PackError, "OUTPUT_PARENT_MISSING"):
            publish_new(self.root / "missing" / "x.zip", b"test")

    def test_link_failure_does_not_publish_partial_output(self):
        target = self.root / "x.zip"
        with patch("p23c_prepare_review_pack.os.link", side_effect=OSError("synthetic")):
            with self.assertRaisesRegex(PackError, "ATOMIC_PUBLISH_UNAVAILABLE"):
                publish_new(target, b"test")
        self.assertFalse(target.exists())
        self.assertEqual([], list(self.root.glob(".photoai-candidate-*")))

    def test_concurrent_existing_target_is_not_replaced(self):
        target = self.root / "x.zip"
        real_link = os.link
        def competitor(source, destination):
            Path(destination).write_bytes(b"other-writer")
            return real_link(source, destination)
        with patch("p23c_prepare_review_pack.os.link", side_effect=competitor):
            with self.assertRaisesRegex(PackError, "OUTPUT_EXISTS"):
                publish_new(target, b"our-output")
        self.assertEqual(b"other-writer", target.read_bytes())

    def test_symlink_and_parent_symlink_rejected(self):
        link = self.root / "link.json"
        parent = self.root / "alias"
        try:
            link.symlink_to(self.bundle)
            parent.symlink_to(self.root, target_is_directory=True)
        except OSError:
            self.skipTest("Symlink privilege unavailable; do not count as PASS")
        for path in (link, parent / "input.json"):
            with self.assertRaisesRegex(PackError, "SYMLINK_OR_REPARSE_POINT"):
                read_selected(path, ".json", 512 * 1024)
        with self.assertRaisesRegex(PackError, "SYMLINK_OR_REPARSE_POINT"):
            publish_new(parent / "x.zip", b"test")

    def test_hardlink_input_rejected(self):
        link = self.root / "linked.json"
        try: os.link(self.bundle, link)
        except OSError: self.skipTest("Hardlinks unavailable")
        with self.assertRaisesRegex(PackError, "INPUT_NOT_ORDINARY_FILE"):
            read_selected(link, ".json", 512 * 1024)

    def test_input_size_limit(self):
        with self.assertRaisesRegex(PackError, "DOCUMENT_TOO_LARGE"):
            read_selected(self.bundle, ".json", 10)

    def test_path_traversal_rejected(self):
        with self.assertRaisesRegex(PackError, "PATH_TRAVERSAL"):
            publish_new(self.root / ".." / "x.zip", b"test")

    def test_fsync_failure_leaves_no_final_file(self):
        target = self.root / "x.zip"
        with patch("p23c_prepare_review_pack.os.fsync", side_effect=OSError("synthetic")):
            with self.assertRaises(OSError): publish_new(target, b"test")
        self.assertFalse(target.exists())
        self.assertEqual([], list(self.root.glob(".photoai-candidate-*")))


if __name__ == "__main__":
    unittest.main()
