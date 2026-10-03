"""Real typed inputs, exact failure codes and side-effect-free CLI regression."""
from __future__ import annotations
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from scripts.t14_bundle_intake_preflight_contract import preflight

RAW = (ROOT / "docs/reference/fixtures/photo_knowledge_bundle_consumer.v1.json").read_bytes()
DIGEST = hashlib.sha256(RAW).hexdigest()
BASE = {"contract_version": "T14_PREFLIGHT_MANIFEST_V1", "mode": "SYNTHETIC_CONTRACT_TEST_ONLY",
        "producer_revision": "a" * 40, "files": [{"path": "vector.json", "bytes": len(RAW), "sha256": DIGEST}]}
CHECKSUM = (DIGEST + "  vector.json\n").encode("ascii")

def encoded(value):
    return json.dumps(value, ensure_ascii=False).encode("utf-8")

class IntakeContractTests(unittest.TestCase):
    def call(self, raw=RAW, manifest=None, checksum=CHECKSUM, expected_name=None):
        return preflight(raw, encoded(BASE if manifest is None else manifest), checksum, expected_name)

    def reject(self, error, **kwargs):
        result = self.call(**kwargs)
        self.assertEqual(error, result["error"])
        self.assertFalse(result["compatible_preflight"])
        self.boundaries(result)

    def boundaries(self, result):
        for key in ("ready", "db_import", "t14_authority", "producer_origin_verified"):
            self.assertIs(result[key], False)
        self.assertEqual("NOT_RUN", result["consumer_validation"])

    def test_frozen_document_raw_integrity(self):
        result = self.call(expected_name="vector.json")
        self.assertTrue(result["compatible_preflight"])
        self.assertEqual(DIGEST, result["raw_sha256"])
        self.assertEqual(len(RAW), result["raw_bytes"])
        self.boundaries(result)

    def test_document_mutation_rejected(self):
        self.reject("DIGEST_MISMATCH", raw=RAW.replace(b'1.0', b'1.1', 1))

    def test_manifest_hash_mismatch(self):
        data = copy.deepcopy(BASE); data["files"][0]["sha256"] = "0" * 64
        self.reject("DIGEST_MISMATCH", manifest=data)

    def test_checksum_hash_mismatch(self):
        self.reject("DIGEST_MISMATCH", checksum=("0" * 64 + " vector.json\n").encode())

    def test_byte_length_and_bool_rejected(self):
        for value in (len(RAW) + 1, True, "1"):
            data = copy.deepcopy(BASE); data["files"][0]["bytes"] = value
            with self.subTest(value=value): self.reject("BYTE_LENGTH", manifest=data)

    def test_empty_and_multiple_files_rejected(self):
        for value in ([], [BASE["files"][0], BASE["files"][0]], None, {}):
            data = copy.deepcopy(BASE); data["files"] = value
            with self.subTest(value=value): self.reject("SINGLE_DOCUMENT_REQUIRED", manifest=data)

    def test_manifest_entry_type_and_unknown_key(self):
        for value in (None, [], {**BASE["files"][0], "unexpected": True}):
            data = copy.deepcopy(BASE); data["files"] = [value]
            with self.subTest(value=value): self.reject("MANIFEST_ENTRY", manifest=data)

    def test_unsafe_paths_rejected_at_path_guard(self):
        for name in ("../x", "C:\\outside\\x", "//host/x", "a/b.json", "x\\y", ".", "..", "CON.json", "x.", "x ", "x\x00"):
            data = copy.deepcopy(BASE); data["files"][0]["path"] = name
            with self.subTest(name=name): self.reject("PATH", manifest=data)

    def test_expected_filename_bound(self):
        self.reject("PATH_BINDING", expected_name="different.json")

    def test_digest_format(self):
        data = copy.deepcopy(BASE); data["files"][0]["sha256"] = "not-a-digest"
        self.reject("DIGEST_FORMAT", manifest=data)

    def test_uppercase_digest_accepted(self):
        data = copy.deepcopy(BASE); data["files"][0]["sha256"] = DIGEST.upper()
        self.assertTrue(self.call(manifest=data, checksum=(DIGEST.upper()+" vector.json\n").encode())["compatible_preflight"])

    def test_wrong_mode_or_version(self):
        for key,value in (("mode", "PRODUCER_GOLDEN_VECTOR"), ("contract_version", "unknown")):
            data = copy.deepcopy(BASE); data[key] = value
            self.reject("MANIFEST_MODE", manifest=data)

    def test_revision_format(self):
        data = copy.deepcopy(BASE); data["producer_revision"] = "short"
        self.reject("REVISION", manifest=data)

    def test_manifest_root_and_extra_key(self):
        self.reject("MANIFEST", manifest=[])
        self.reject("MANIFEST", manifest={**BASE, "extra": True})

    def test_bom_utf8_and_malformed_json(self):
        for raw,error in ((b"\xef\xbb\xbf{}", "BOM"), (b"\xff", "UTF8"), (b"{", "JSON")):
            with self.subTest(error=error): self.reject(error, raw=raw)

    def test_duplicate_keys_and_nonfinite_numbers(self):
        self.reject("DUPLICATE_KEY", raw=b'{"x":1,"x":2}')
        self.reject("NONFINITE_JSON", raw=b'{"x":NaN}')

    def test_deep_json_rejected_without_traceback(self):
        self.reject("JSON_DEPTH", raw=b"[" * 1500 + b"]" * 1500)

    def test_wrong_raw_root(self):
        self.reject("RAW_ROOT_TYPE", raw=b'[]')

    def test_input_resource_bounds(self):
        self.reject("SIZE", raw=b" " * (512 * 1024 + 1))
        result = preflight(RAW, b" " * (16 * 1024 + 1), CHECKSUM)
        self.assertEqual("SIZE", result["error"])
        self.reject("CHECKSUM_SIZE", checksum=b" " * 4097)

    def test_typed_manifest_not_fake_negative(self):
        result = preflight(RAW, BASE, CHECKSUM)
        self.assertEqual("INPUT_TYPE", result["error"])
        self.boundaries(result)

    def test_checksum_records_and_binding(self):
        for value,error in ((b"", "CHECKSUM_RECORDS"), (CHECKSUM+CHECKSUM, "CHECKSUM_RECORDS"),
                            (b"invalid vector.json\n", "CHECKSUM_FORMAT"),
                            ((DIGEST+" other.json\n").encode(), "CHECKSUM_FORMAT")):
            with self.subTest(error=error): self.reject(error, checksum=value)

class IntakeCliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="photoai-intake-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.raw = self.root / "vector.json"; self.raw.write_bytes(RAW)
        self.manifest = self.root / "manifest.json"; self.manifest.write_bytes(encoded(BASE))
        self.checksum = self.root / "CHECKSUMS.sha256"; self.checksum.write_bytes(CHECKSUM)

    def run_cli(self):
        return subprocess.run([sys.executable, str(ROOT / "scripts/t14_bundle_intake_preflight.py"),
                               str(self.raw), str(self.manifest), str(self.checksum)], cwd=self.root,
                              text=True, capture_output=True)

    def test_cli_success_is_read_only(self):
        before = {p.name: p.read_bytes() for p in self.root.iterdir()}
        result = self.run_cli(); self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(json.loads(result.stdout)["t14_authority"])
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.root.iterdir()})

    def test_cli_rejection_exit_and_no_traceback(self):
        self.raw.write_bytes(b'{}')
        result = self.run_cli(); self.assertEqual(1, result.returncode)
        self.assertEqual("BYTE_LENGTH", json.loads(result.stdout)["error"])
        self.assertNotIn("Traceback", result.stderr)

    def test_cli_missing_input(self):
        self.checksum.unlink()
        result = self.run_cli(); self.assertEqual(1, result.returncode)
        self.assertEqual("INPUT_FILE", json.loads(result.stdout)["error"])

    def test_cli_oversize_is_bounded(self):
        self.raw.write_bytes(b" " * (512 * 1024 + 1))
        result = self.run_cli(); self.assertEqual(1, result.returncode)
        self.assertEqual("SIZE", json.loads(result.stdout)["error"])

if __name__ == "__main__":
    unittest.main()
