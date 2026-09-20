from __future__ import annotations

import json
from pathlib import Path
import unittest

from p23c_bundle_contract import PHOTO_FIELDS, encoded_json, validate_bundle
from p25_human_review_gate import encoded, pipeline_input, review_template
from p25_internal_handoff_fixture import TEST_PRODUCER, TEST_RELEASE, build_test_bundle


ROOT = Path(__file__).parents[1]
CORPUS = ROOT / "docs" / "reference" / "p25_real_20_editorial_draft.v1.json"
ANDROID_FIXTURE = ROOT / "android" / "app" / "src" / "androidTest" / "assets" / "p25s" / "internal-handoff-20.bundle.json"


def simulated_review(corpus_raw: bytes) -> dict:
    review = review_template(corpus_raw)
    review["review_id"] = "SIMULATED-INTERNAL-REVIEW"
    review["reviewer_id"] = "SIMULATED-INTERNAL-REVIEWER"
    review["rights_basis_evidence_id"] = "SIMULATED-RIGHTS-EVIDENCE"
    for index, entry in enumerate(review["entries"], 1):
        entry["evidence_id"] = f"SIMULATED-EVIDENCE-{index:03d}"
        entry["content_review"] = "APPROVED"
        entry["rights_review"] = "APPROVED"
        entry["privacy_review"] = "APPROVED"
    return review


class P25InternalHandoffFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.corpus_raw = CORPUS.read_bytes()
        cls.corpus = json.loads(cls.corpus_raw)
        cls.curated = pipeline_input(cls.corpus_raw, encoded(simulated_review(cls.corpus_raw)))

    def test_simulated_handoff_preserves_all_twenty_editorial_entries(self):
        bundle = build_test_bundle(self.curated)
        self.assertEqual([e["reference_id"] for e in self.corpus["entries"]], [r["reference_id"] for r in bundle["references"]])
        self.assertEqual([e["photography"] for e in self.corpus["entries"]], [r["photography"] for r in bundle["references"]])
        self.assertTrue(all(set(r["photography"]) == set(PHOTO_FIELDS) for r in bundle["references"]))
        self.assertEqual(TEST_PRODUCER, bundle["source"]["producer_id"])
        self.assertEqual(TEST_RELEASE, bundle["source"]["release_id"])
        validate_bundle(encoded_json(bundle))

    def test_committed_android_fixture_matches_current_corpus_and_generator(self):
        expected = encoded_json(build_test_bundle(self.curated))
        self.assertEqual(expected, ANDROID_FIXTURE.read_bytes())

    def test_non_handoff_or_authorized_input_is_rejected(self):
        wrong = dict(self.curated)
        wrong["format"] = "photoai.unknown.v1"
        with self.assertRaisesRegex(ValueError, "CURATED_HANDOFF_INVALID"):
            build_test_bundle(wrong)
        wrong = dict(self.curated)
        wrong["app_import_authorized"] = True
        with self.assertRaisesRegex(ValueError, "CURATED_AUTHORITY_INVALID"):
            build_test_bundle(wrong)
