from __future__ import annotations

import base64
import concurrent.futures
import contextlib
import hashlib
import io
import importlib.util
import ipaddress
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from cryptography import x509
from cryptography.hazmat.primitives import serialization


MODULE_PATH = Path(__file__).with_name("prepare_photoai_pilot_tls.py")
SPEC = importlib.util.spec_from_file_location("prepare_photoai_pilot_tls", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("pilot TLS helper import unavailable")
TLS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TLS)


class PreparePhotoAiPilotTlsTests(unittest.TestCase):
    def test_private_lan_certificate_has_redacted_summary_and_expected_sans(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "tls"
            summary = TLS.create_tls_material(root, "192.168.20.10")
            stored = json.loads((root / "tls-summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary, stored)
            self.assertEqual(stored["status"], "PASS")
            self.assertEqual(stored["san_count"], 3)
            self.assertRegex(stored["certificate_sha256"], r"^[0-9a-f]{64}$")
            self.assertRegex(stored["spki_pin"], r"^sha256/[A-Za-z0-9+/]{43}=$")
            self.assertNotIn("192.168.20.10", (root / "tls-summary.json").read_text(encoding="utf-8"))
            base64.b64decode(stored["spki_pin"].removeprefix("sha256/"), validate=True)

            certificate = x509.load_pem_x509_certificate((root / "cert.pem").read_bytes())
            sans = certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
            self.assertEqual(
                list(sans),
                [
                    x509.DNSName("localhost"),
                    x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
                    x509.IPAddress(ipaddress.ip_address("192.168.20.10")),
                ],
            )
            self.assertEqual(
                hashlib.sha256(certificate.public_bytes(serialization.Encoding.DER)).hexdigest(),
                stored["certificate_sha256"],
            )
            certificate_spki = certificate.public_key().public_bytes(
                serialization.Encoding.DER,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            )
            self.assertEqual(
                stored["spki_pin"],
                "sha256/" + base64.b64encode(hashlib.sha256(certificate_spki).digest()).decode("ascii"),
            )
            self.assertEqual(stored["not_valid_before_utc"], TLS._utc_timestamp(certificate.not_valid_before_utc))
            self.assertEqual(stored["not_valid_after_utc"], TLS._utc_timestamp(certificate.not_valid_after_utc))
            private_key = serialization.load_pem_private_key((root / "key.pem").read_bytes(), password=None)
            self.assertEqual(
                private_key.public_key().public_bytes(
                    serialization.Encoding.DER,
                    serialization.PublicFormat.SubjectPublicKeyInfo,
                ),
                certificate.public_key().public_bytes(
                    serialization.Encoding.DER,
                    serialization.PublicFormat.SubjectPublicKeyInfo,
                ),
            )

    def test_public_and_loopback_addresses_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            for address in ("8.8.8.8", "127.0.0.1", "169.254.1.1", "192.0.2.1", "240.0.0.1"):
                with self.subTest(address=address):
                    with self.assertRaisesRegex(ValueError, "P20_PILOT_TLS_INVALID_LAN_IP"):
                        TLS.create_tls_material(Path(temporary) / address.replace(".", "_"), address)

    def test_invalid_validity_and_existing_output_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(ValueError, "P20_PILOT_TLS_INVALID_VALIDITY"):
                TLS.create_tls_material(root / "invalid", "192.168.20.10", 31)
            TLS.create_tls_material(root / "existing", "192.168.20.10")
            with self.assertRaisesRegex(FileExistsError, "P20_PILOT_TLS_OUTPUT_EXISTS"):
                TLS.create_tls_material(root / "existing", "192.168.20.10")

    def test_cli_collision_does_not_echo_output_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            occupied = Path(temporary) / "occupied-output"
            occupied.write_text("not a directory", encoding="utf-8")
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr):
                result = TLS.main(["--output-dir", str(occupied), "--lan-ip", "192.168.20.10"])
            self.assertEqual(result, 2)
            self.assertEqual(stderr.getvalue().strip(), "P20_PILOT_TLS_OUTPUT_EXISTS")
            self.assertNotIn(str(occupied), stderr.getvalue())

    def test_concurrent_processes_publish_one_complete_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "race"
            command = [
                sys.executable,
                str(MODULE_PATH),
                "--output-dir",
                str(output),
                "--lan-ip",
                "192.168.20.10",
            ]
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
                results = list(executor.map(lambda _: subprocess.run(command, capture_output=True, text=True, check=False), range(2)))
            self.assertEqual(sorted(result.returncode for result in results), [0, 2])
            self.assertTrue((output / "cert.pem").is_file())
            self.assertTrue((output / "key.pem").is_file())
            self.assertTrue((output / "tls-summary.json").is_file())
            summary = json.loads((output / "tls-summary.json").read_text(encoding="utf-8"))
            certificate = x509.load_pem_x509_certificate((output / "cert.pem").read_bytes())
            private_key = serialization.load_pem_private_key((output / "key.pem").read_bytes(), password=None)
            self.assertEqual(summary["status"], "PASS")
            self.assertEqual(
                hashlib.sha256(certificate.public_bytes(serialization.Encoding.DER)).hexdigest(),
                summary["certificate_sha256"],
            )
            self.assertEqual(certificate.public_key().public_numbers(), private_key.public_key().public_numbers())

    def test_permission_or_write_failure_leaves_no_material(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "permission-failure"
            with mock.patch.object(TLS, "_harden_private_path", side_effect=OSError("permission failure")):
                with self.assertRaises(OSError):
                    TLS.create_tls_material(root, "192.168.20.10")
            self.assertFalse(root.exists())

            write_failure = Path(temporary) / "write-failure"
            with mock.patch.object(TLS, "_write_exclusive", side_effect=OSError("write failure")):
                with self.assertRaises(OSError):
                    TLS.create_tls_material(write_failure, "192.168.20.10")
            self.assertFalse(write_failure.exists())

            fsync_failure = Path(temporary) / "fsync-failure"
            with mock.patch.object(TLS.os, "fsync", side_effect=[None, OSError("fsync failure")]):
                with self.assertRaises(OSError):
                    TLS.create_tls_material(fsync_failure, "192.168.20.10")
            self.assertFalse(fsync_failure.exists())

    def test_exclusive_write_registers_created_path_before_each_io_stage(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "key.pem"
            for stage in ("write", "flush", "fsync"):
                with self.subTest(stage=stage):
                    created: list[Path] = []
                    handle = mock.MagicMock()
                    handle.__enter__.return_value = handle
                    handle.__exit__.return_value = False
                    if stage == "write":
                        handle.write.side_effect = OSError("write failure")
                    elif stage == "flush":
                        handle.flush.side_effect = OSError("flush failure")
                    else:
                        handle.fileno.return_value = 1
                    with mock.patch.object(Path, "open", return_value=handle):
                        if stage == "fsync":
                            context = mock.patch.object(TLS.os, "fsync", side_effect=OSError("fsync failure"))
                        else:
                            context = contextlib.nullcontext()
                        with context:
                            with self.assertRaises(OSError):
                                TLS._write_exclusive(path, b"private", created)
                    self.assertEqual(created, [path])


if __name__ == "__main__":
    unittest.main()
