from __future__ import annotations

import copy
import json
from pathlib import Path
import unittest

from p25_validate_editorial_corpus import CorpusError, EXPECTED_STATUS, validate


FIXTURE = Path(__file__).parents[1] / "docs" / "reference" / "p25_real_20_editorial_draft.v1.json"


class P25EditorialCorpusTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = FIXTURE.read_bytes()
        cls.document = json.loads(cls.raw)

    def encoded(self, document):
        return json.dumps(document, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")

    def test_repository_fixture_is_pending_and_complete(self):
        result = validate(self.raw)
        self.assertEqual(EXPECTED_STATUS, result["status"])
        self.assertEqual(20, result["reference_count"])
        self.assertFalse(result["public_candidate_authorized"])

    def test_requires_exactly_twenty_entries(self):
        document = copy.deepcopy(self.document)
        document["entries"].pop()
        with self.assertRaisesRegex(CorpusError, "REQUIRES_20"):
            validate(self.encoded(document))

    def test_rejects_fake_approval_state(self):
        document = copy.deepcopy(self.document)
        document["status"] = "APPROVED"
        with self.assertRaisesRegex(CorpusError, "STATUS_INVALID"):
            validate(self.encoded(document))

    def test_requires_stable_ordered_ids(self):
        document = copy.deepcopy(self.document)
        document["entries"][0]["reference_id"] = "PKB-PORTRAIT-020"
        with self.assertRaisesRegex(CorpusError, "REFERENCE_ID_INVALID"):
            validate(self.encoded(document))

    def test_requires_unique_opaque_source_evidence_ids(self):
        document = copy.deepcopy(self.document)
        document["entries"][1]["source_evidence_id"] = document["entries"][0]["source_evidence_id"]
        with self.assertRaisesRegex(CorpusError, "SOURCE_EVIDENCE_ID_INVALID"):
            validate(self.encoded(document))

    def test_rejects_missing_photography_field(self):
        document = copy.deepcopy(self.document)
        del document["entries"][0]["photography"]["lighting"]
        with self.assertRaisesRegex(CorpusError, "PHOTOGRAPHY_SCHEMA_INVALID"):
            validate(self.encoded(document))

    def test_rejects_duplicate_json_keys(self):
        raw = self.raw.replace(b'"corpus_version": "1.0",', b'"corpus_version": "1.0", "corpus_version": "1.0",', 1)
        with self.assertRaisesRegex(CorpusError, "DUPLICATE_JSON_KEY"):
            validate(raw)


if __name__ == "__main__":
    unittest.main()
