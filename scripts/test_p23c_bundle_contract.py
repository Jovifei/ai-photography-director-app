"""Synthetic data only; the pinned P22 fixture is an independent golden vector."""
from __future__ import annotations
import copy
import json
from pathlib import Path
import unittest
from p23c_bundle_contract import (
    MAX_BUNDLE_BYTES, PHOTO_FIELDS, PackError, canonical_payload,
    encoded_json, sha256, strict_json, validate_bundle,
)


def synthetic_bundle(count: int = 2) -> dict:
    bundle = {
        "contract_version": "1.0", "bundle_id": "synthetic_p23c",
        "source": {"origin": "PIPELINE", "producer_id": "synthetic_test", "release_id": "test_v1"},
        "integrity": {"algorithm": "SHA-256", "payload_sha256": "0" * 64},
        "references": [{"reference_id": f"synthetic_{i:02d}", "photography": {
            name: f"合成测试 {name} {i} 📷 e\u0301" for name in PHOTO_FIELDS
        }} for i in range(count)],
    }
    bundle["integrity"]["payload_sha256"] = sha256(canonical_payload(bundle))
    return bundle


class ContractTests(unittest.TestCase):
    def bad(self, raw: bytes, code: str | None = None) -> None:
        with self.assertRaises(PackError) as raised:
            validate_bundle(raw)
        if code:
            self.assertEqual(code, str(raised.exception))

    def test_existing_android_golden_fixture(self):
        path = Path(__file__).resolve().parents[1] / "docs/reference/fixtures/photo_knowledge_bundle_consumer.v1.json"
        self.assertEqual("9bd85c8b1be3e3544d9a74b4140e59fc57f85e6b97a343d7c6fba2018048bb69",
                         validate_bundle(path.read_bytes())["integrity"]["payload_sha256"])

    def test_one_and_twenty_entries(self):
        for count in (1, 20):
            self.assertEqual(count, len(validate_bundle(encoded_json(synthetic_bundle(count)))["references"]))

    def test_codepoints_not_utf8_bytes(self):
        bundle = synthetic_bundle()
        bundle["references"][0]["photography"]["scene"] = "📷" * 800
        bundle["integrity"]["payload_sha256"] = sha256(canonical_payload(bundle))
        validate_bundle(encoded_json(bundle))

    def test_whitespace_and_key_order_preserve_digest(self):
        bundle = synthetic_bundle()
        raw = json.dumps(bundle, ensure_ascii=True).encode()
        self.assertEqual(bundle, validate_bundle(b"\r\n\t" + raw + b"\n"))

    def test_uppercase_digest_is_accepted_without_mutating_bytes(self):
        bundle = synthetic_bundle()
        bundle["integrity"]["payload_sha256"] = bundle["integrity"]["payload_sha256"].upper()
        raw = encoded_json(bundle)
        self.assertEqual(bundle, validate_bundle(raw))
        self.assertEqual(raw, encoded_json(bundle))

    def test_all_fields_bound(self):
        for field in PHOTO_FIELDS:
            bundle = synthetic_bundle()
            bundle["references"][0]["photography"][field] += "改变"
            self.bad(encoded_json(bundle), "DIGEST_MISMATCH")

    def test_source_and_order_bound(self):
        original = synthetic_bundle()
        changes = []
        for field in ("producer_id", "release_id"):
            bundle = copy.deepcopy(original)
            bundle["source"][field] += "_new"
            changes.append(bundle)
        bundle = copy.deepcopy(original)
        bundle["references"].reverse()
        changes.append(bundle)
        for value in changes:
            self.bad(encoded_json(value), "DIGEST_MISMATCH")

    def test_unknown_fields_at_every_level(self):
        for key in ("root", "source", "integrity", "reference", "photography"):
            bundle = synthetic_bundle()
            target = {"root": bundle, "source": bundle["source"], "integrity": bundle["integrity"],
                      "reference": bundle["references"][0], "photography": bundle["references"][0]["photography"]}[key]
            target["unknown"] = "forbidden"
            self.bad(encoded_json(bundle), "SCHEMA_INVALID")

    def test_wrong_shapes_and_types(self):
        for key in ("source", "integrity", "references", "bundle_id"):
            bundle = synthetic_bundle()
            bundle[key] = []
            self.bad(encoded_json(bundle))
        bundle = synthetic_bundle()
        bundle["references"][0]["photography"]["scene"] = 17
        self.bad(encoded_json(bundle), "TEXT_INVALID")

    def test_duplicate_object_keys_including_escaped(self):
        raw = encoded_json(synthetic_bundle())
        for duplicate in (b'"bundle_id":"x",', b'"bundle_\\u0069d":"x",'):
            self.bad(b"{" + duplicate + raw[1:], "DUPLICATE_JSON_KEY")

    def test_malformed_json_extensions(self):
        raw = encoded_json(synthetic_bundle())
        for value in (raw + b"{}", raw + b"x", raw.replace(b'"', b"'"),
                      b"{/*test*/" + raw[1:], raw.rstrip()[:-1] + b",}"):
            self.bad(value, "MALFORMED_JSON")

    def test_invalid_utf8_and_bom(self):
        for raw in (b"\xef\xbb\xbf{}", b'{"x":"\xc3\x28"}', b'{"x":"\xed\xa0\x80"}', b"\xff"):
            self.bad(raw, "INVALID_UTF8")

    def test_unpaired_unicode_surrogates(self):
        self.bad(b'{"x":"\\ud800"}', "INVALID_UNICODE")

    def test_deep_json_bounded_before_parser(self):
        self.bad(b'{"x":' + b"[" * 10000 + b"0" + b"]" * 10000 + b"}", "NESTING_TOO_DEEP")

    def test_nonfinite_and_float_rejected(self):
        for token in (b"NaN", b"Infinity", b"-Infinity", b"1e999"):
            self.bad(b'{"x":' + token + b"}", "NUMBER_NOT_ALLOWED")

    def test_empty_and_oversize(self):
        self.bad(b"", "DOCUMENT_EMPTY")
        self.bad(b" " * (MAX_BUNDLE_BYTES + 1), "DOCUMENT_TOO_LARGE")

    def test_cardinality_and_duplicates(self):
        for count in (0, 21):
            self.bad(encoded_json(synthetic_bundle(count)), "REFERENCE_COUNT_INVALID")
        bundle = synthetic_bundle()
        bundle["references"][1]["reference_id"] = bundle["references"][0]["reference_id"]
        self.bad(encoded_json(bundle), "DUPLICATE_REFERENCE_ID")

    def test_text_safety(self):
        for text in ("", " ", "x\n", "x\u0085", "x\u2028", "a" * 801,
                     "content://private", "C:\\private", "../private", "https://example.invalid"):
            bundle = synthetic_bundle()
            bundle["references"][0]["photography"]["scene"] = text
            self.bad(encoded_json(bundle))

    def test_identifier_version_and_digest_errors(self):
        for key, value in (("bundle_id", "../x"), ("contract_version", "2.0")):
            bundle = synthetic_bundle()
            bundle[key] = value
            self.bad(encoded_json(bundle))
        bundle = synthetic_bundle()
        bundle["integrity"]["payload_sha256"] = "g" * 64
        self.bad(encoded_json(bundle), "DIGEST_INVALID")

    def test_unicode_normalization_is_not_silent(self):
        bundle = synthetic_bundle()
        raw = encoded_json(bundle).replace("e\u0301".encode(), "é".encode())
        self.bad(raw, "DIGEST_MISMATCH")

    def test_pinned_p23c_android_asset(self):
        path = Path(__file__).resolve().parents[1] / "android/app/src/androidTest/assets/p23c/roundtrip.bundle.json"
        raw = path.read_bytes()
        # Source checkout CRLF is not the runtime receipt hash domain.
        self.assertEqual(encoded_json(synthetic_bundle()), raw.replace(b"\r\n", b"\n"))
        validate_bundle(raw)

    def test_windows_crlf_vector_keeps_pkb1_but_changes_file_hash(self):
        raw = encoded_json(synthetic_bundle())
        crlf = raw.replace(b"\n", b"\r\n")
        self.assertEqual(validate_bundle(raw), validate_bundle(crlf))
        self.assertNotEqual(sha256(raw), sha256(crlf))


if __name__ == "__main__":
    unittest.main()
