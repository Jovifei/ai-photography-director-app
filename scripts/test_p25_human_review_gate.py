from __future__ import annotations

import json
from pathlib import Path
import unittest

from p25_human_review_gate import (
    EXPORT_FORMAT,
    ReviewError,
    encoded,
    pipeline_input,
    review_template,
    validate_review,
)


FIXTURE = Path(__file__).parents[1] / "docs" / "reference" / "p25_real_20_editorial_draft.v1.json"


class P25HumanReviewGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.corpus_raw = FIXTURE.read_bytes()

    def approved(self):
        review = review_template(self.corpus_raw)
        review["review_id"] = "P25-REVIEW-001"
        review["reviewer_id"] = "P25-REVIEWER-001"
        review["rights_basis_evidence_id"] = "P25-RIGHTS-001"
        for index, entry in enumerate(review["entries"], 1):
            entry["evidence_id"] = f"P25-REV-EVID-{index:03d}"
            entry["content_review"] = "APPROVED"
            entry["rights_review"] = "APPROVED"
            entry["privacy_review"] = "APPROVED"
        return review

    def test_template_is_pending_and_cannot_validate(self):
        review = review_template(self.corpus_raw)
        self.assertIsNone(review["reviewer_id"])
        self.assertEqual("PENDING", review["entries"][0]["content_review"])
        with self.assertRaises(ReviewError):
            validate_review(self.corpus_raw, encoded(review))

    def test_valid_complete_human_review_is_bound_but_not_import_authorized(self):
        result = validate_review(self.corpus_raw, encoded(self.approved()))
        self.assertEqual("P25_REAL_20_CONTENT_REVIEWED", result["status"])
        self.assertEqual(20, result["reference_count"])
        self.assertTrue(result["content_approved"])
        self.assertFalse(result["app_import_authorized"])

    def test_corpus_hash_mismatch_is_rejected(self):
        review = self.approved()
        review["corpus_sha256"] = "0" * 64
        with self.assertRaisesRegex(ReviewError, "REVIEW_BINDING_MISMATCH"):
            validate_review(self.corpus_raw, encoded(review))

    def test_missing_approval_is_rejected(self):
        review = self.approved()
        review["entries"][4]["rights_review"] = "REJECTED"
        with self.assertRaisesRegex(ReviewError, "REVIEW_NOT_COMPLETE"):
            validate_review(self.corpus_raw, encoded(review))

    def test_missing_reviewer_identity_is_rejected(self):
        review = self.approved()
        review["reviewer_id"] = None
        with self.assertRaisesRegex(ReviewError, "REVIEWER_ID_INVALID"):
            validate_review(self.corpus_raw, encoded(review))

    def test_source_binding_mismatch_is_rejected(self):
        review = self.approved()
        review["entries"][0]["source_evidence_id"] = "P25-SRC-020"
        with self.assertRaisesRegex(ReviewError, "REVIEW_SOURCE_BINDING_MISMATCH"):
            validate_review(self.corpus_raw, encoded(review))

    def test_duplicate_review_evidence_is_rejected(self):
        review = self.approved()
        review["entries"][1]["evidence_id"] = review["entries"][0]["evidence_id"]
        with self.assertRaisesRegex(ReviewError, "REVIEW_EVIDENCE_ID_DUPLICATE"):
            validate_review(self.corpus_raw, encoded(review))

    def test_cli_maps_safe_io_rejection_to_blocked_exit_2(self):
        with mock.patch.object(gate, "read_selected", side_effect=PackError("SYMLINK_OR_REPARSE_POINT")):
            with mock.patch("builtins.print") as output:
                result = gate.main(["review-template", "input.json", "output.json"])
        self.assertEqual(2, result)
        self.assertIn("SYMLINK_OR_REPARSE_POINT", output.call_args.args[0])

    def test_pipeline_export_is_explicitly_not_app_bundle(self):
        review_raw = encoded(self.approved())
        exported = pipeline_input(self.corpus_raw, review_raw)
        self.assertEqual(EXPORT_FORMAT, exported["format"])
        self.assertEqual("HUMAN_REVIEW_APPROVED_NOT_APP_IMPORTABLE", exported["status"])
        self.assertFalse(exported["app_import_authorized"])
        self.assertNotIn("contract_version", exported)
        self.assertNotIn("source", exported)
        self.assertNotIn("integrity", exported)
        self.assertNotIn("references", exported)


if __name__ == "__main__":
    unittest.main()
