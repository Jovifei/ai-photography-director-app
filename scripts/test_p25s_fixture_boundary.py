"""Synthetic inputs exercise the real adapter and PKB1 checker, without human claims."""
from __future__ import annotations

import copy
import unittest

from p23c_bundle_contract import PHOTO_FIELDS, encoded_json, validate_bundle
from p25_internal_handoff_fixture import (
    CURATED_FORMAT, CURATED_STATUS, TEST_PRODUCER, TEST_RELEASE, build_test_bundle,
)


def synthetic_handoff():
    return {
        "format": CURATED_FORMAT, "status": CURATED_STATUS,
        "corpus_id": "synthetic-corpus", "corpus_sha256": "a" * 64,
        "review_sha256": "b" * 64, "rights_basis": "PROJECT_ORIGINAL_EDITORIAL_DRAFT",
        "review_id": "SIMULATED-REVIEW", "rights_basis_evidence_id": "SIMULATED-RIGHTS",
        "app_import_authorized": False, "public_distribution_authorized": False,
        "entries": [{"reference_id": f"r{i}", "source_evidence_id": f"s{i}",
                     "review_evidence_id": f"SIMULATED-e{i}",
                     "photography": {key: f"合成 {key} {i} 📷 e\u0301" for key in PHOTO_FIELDS}}
                    for i in range(20)],
    }


class P25SFixtureBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.input = synthetic_handoff()

    def test_all_fields_preserved_without_mutating_input(self):
        before = copy.deepcopy(self.input)
        result = build_test_bundle(self.input)
        self.assertEqual(before, self.input)
        self.assertEqual([x["photography"] for x in before["entries"]],
                         [x["photography"] for x in result["references"]])
        validate_bundle(encoded_json(result))

    def test_synthetic_source_cannot_be_supplied_by_input(self):
        bundle = build_test_bundle(self.input)
        self.assertEqual(TEST_PRODUCER, bundle["source"]["producer_id"])
        self.assertEqual(TEST_RELEASE, bundle["source"]["release_id"])
        self.input["source"] = {"producer_id": "production"}
        with self.assertRaises(ValueError):
            build_test_bundle(self.input)

    def test_unknown_root_rejected(self):
        self.input["unknown"] = True
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_non_object_rejected_without_attribute_error(self):
        for value in (None, [], "text", True):
            with self.subTest(value=value), self.assertRaises(ValueError): build_test_bundle(value)

    def test_hashes_must_be_valid(self):
        for field in ("corpus_sha256", "review_sha256"):
            data = copy.deepcopy(self.input)
            data[field] = "not-a-hash"
            with self.subTest(field=field), self.assertRaises(ValueError): build_test_bundle(data)

    def test_authority_not_coerced_from_zero(self):
        self.input["app_import_authorized"] = 0
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_duplicate_references_rejected(self):
        self.input["entries"][1]["reference_id"] = "r0"
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_duplicate_evidence_rejected(self):
        for key in ("source_evidence_id", "review_evidence_id"):
            data = copy.deepcopy(self.input)
            data["entries"][1][key] = data["entries"][0][key]
            with self.subTest(key=key), self.assertRaises(ValueError): build_test_bundle(data)

    def test_reference_transport_rejected(self):
        self.input["entries"][0]["reference_id"] = "file:///private"
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_invalid_text_type_rejected_without_attribute_error(self):
        for value in (1, None, [], {}):
            data = copy.deepcopy(self.input)
            data["entries"][0]["photography"]["scene"] = value
            with self.subTest(value=value), self.assertRaises(ValueError): build_test_bundle(data)

    def test_surrogate_rejected_without_encoding_error(self):
        self.input["entries"][0]["photography"]["scene"] = "\ud800"
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_transport_or_controls_in_any_field_rejected(self):
        for key in PHOTO_FIELDS:
            for text in ("file:///private.jpg", "a\nb", "a\u2028b", "../private", " "):
                data = copy.deepcopy(self.input)
                data["entries"][0]["photography"][key] = text
                with self.subTest(key=key, text=text), self.assertRaises(ValueError): build_test_bundle(data)

    def test_field_limits_rejected(self):
        for key in PHOTO_FIELDS:
            data = copy.deepcopy(self.input)
            data["entries"][0]["photography"][key] = "x" * (1201 if key == "director_prompt" else 801)
            with self.subTest(key=key), self.assertRaises(ValueError): build_test_bundle(data)

    def test_wrong_count_rejected(self):
        self.input["entries"].pop()
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_missing_or_extra_field_rejected(self):
        self.input["entries"][0]["photography"]["filename"] = "private.jpg"
        with self.assertRaises(ValueError): build_test_bundle(self.input)

    def test_digest_preserves_order_and_unicode(self):
        first = build_test_bundle(self.input)
        self.input["entries"].reverse()
        second = build_test_bundle(self.input)
        self.assertNotEqual(first["integrity"], second["integrity"])
        self.assertIn("e\u0301", second["references"][0]["photography"]["scene"])
        self.assertEqual(second, build_test_bundle(self.input))


if __name__ == "__main__":
    unittest.main()
