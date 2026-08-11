from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import ipaddress
import json
import os
import stat
import subprocess
import sys
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


DEFAULT_VALID_DAYS = 7
MAX_VALID_DAYS = 30
CERTIFICATE_NAME = "cert.pem"
PRIVATE_KEY_NAME = "key.pem"
SUMMARY_NAME = "tls-summary.json"
RFC1918_NETWORKS = (
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
)
SAFE_USER_ERRORS = {
    "P20_PILOT_TLS_INVALID_LAN_IP",
    "P20_PILOT_TLS_INVALID_VALIDITY",
}


def parse_private_lan_ipv4(value: str) -> ipaddress.IPv4Address:
    """Return a routable RFC1918-style IPv4 address without echoing user input."""
    try:
        address = ipaddress.ip_address(value)
    except ValueError as exc:
        raise ValueError("P20_PILOT_TLS_INVALID_LAN_IP") from exc
    if not isinstance(address, ipaddress.IPv4Address) or not any(address in network for network in RFC1918_NETWORKS):
        raise ValueError("P20_PILOT_TLS_INVALID_LAN_IP")
    return address


def _utc_timestamp(value: datetime) -> str:
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")


def _current_windows_sid() -> str:
    result = subprocess.run(
        ["whoami", "/user", "/fo", "csv", "/nh"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    rows = list(csv.reader(result.stdout.splitlines()))
    if result.returncode != 0 or len(rows) != 1 or len(rows[0]) != 2 or not rows[0][1].startswith("S-1-"):
        raise OSError("P20_PILOT_TLS_PERMISSION_HARDENING_FAILED")
    return rows[0][1]


def _harden_private_path(path: Path, *, directory: bool) -> None:
    if os.name != "nt":
        mode = stat.S_IRUSR | stat.S_IWUSR | (stat.S_IXUSR if directory else 0)
        os.chmod(path, mode)
        if stat.S_IMODE(path.stat().st_mode) & (stat.S_IRWXG | stat.S_IRWXO):
            raise OSError("P20_PILOT_TLS_PERMISSION_HARDENING_FAILED")
        return

    sid = _current_windows_sid()
    inheritance = "(OI)(CI)F" if directory else "F"
    result = subprocess.run(
        [
            "icacls",
            str(path),
            "/inheritance:r",
            "/grant:r",
            f"*{sid}:{inheritance}",
            "/grant:r",
            f"*S-1-5-18:{inheritance}",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        raise OSError("P20_PILOT_TLS_PERMISSION_HARDENING_FAILED")


def _write_exclusive(path: Path, content: bytes, created: list[Path]) -> None:
    handle = path.open("xb")
    created.append(path)
    with handle:
        handle.write(content)
        handle.flush()
        os.fsync(handle.fileno())


def create_tls_material(output_dir: Path, lan_ip: str, valid_days: int = DEFAULT_VALID_DAYS) -> dict[str, Any]:
    """Create one short-lived, pinned TLS identity for a private LAN pilot."""
    if valid_days <= 0 or valid_days > MAX_VALID_DAYS:
        raise ValueError("P20_PILOT_TLS_INVALID_VALIDITY")
    lan_address = parse_private_lan_ipv4(lan_ip)
    if not output_dir.parent.is_dir():
        raise OSError("P20_PILOT_TLS_OUTPUT_PARENT_UNAVAILABLE")
    try:
        output_dir.mkdir()
    except FileExistsError as exc:
        raise FileExistsError("P20_PILOT_TLS_OUTPUT_EXISTS") from exc
    cert_path = output_dir / CERTIFICATE_NAME
    key_path = output_dir / PRIVATE_KEY_NAME
    summary_path = output_dir / SUMMARY_NAME

    created: list[Path] = []
    try:
        _harden_private_path(output_dir, directory=True)
        now = datetime.now(UTC)
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = issuer = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "photoai-local-pilot")])
        certificate = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(issuer)
            .public_key(private_key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5))
            .not_valid_after(now + timedelta(days=valid_days))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(
                x509.KeyUsage(
                    digital_signature=True,
                    content_commitment=False,
                    key_encipherment=True,
                    data_encipherment=False,
                    key_agreement=False,
                    key_cert_sign=False,
                    crl_sign=False,
                    encipher_only=False,
                    decipher_only=False,
                ),
                critical=True,
            )
            .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
            .add_extension(
                x509.SubjectAlternativeName(
                    [
                        x509.DNSName("localhost"),
                        x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
                        x509.IPAddress(lan_address),
                    ]
                ),
                critical=False,
            )
            .sign(private_key=private_key, algorithm=hashes.SHA256())
        )
        certificate_der = certificate.public_bytes(serialization.Encoding.DER)
        spki_der = private_key.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        summary = {
            "status": "PASS",
            "certificate_sha256": hashlib.sha256(certificate_der).hexdigest(),
            "spki_pin": "sha256/" + base64.b64encode(hashlib.sha256(spki_der).digest()).decode("ascii"),
            "san_count": 3,
            "not_valid_before_utc": _utc_timestamp(certificate.not_valid_before_utc),
            "not_valid_after_utc": _utc_timestamp(certificate.not_valid_after_utc),
        }
        _write_exclusive(cert_path, certificate.public_bytes(serialization.Encoding.PEM), created)
        _write_exclusive(
            key_path,
            private_key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            ),
            created,
        )
        _harden_private_path(key_path, directory=False)
        _write_exclusive(summary_path, (json.dumps(summary, sort_keys=True, indent=2) + "\n").encode("utf-8"), created)
        return summary
    except Exception:
        for path in reversed(created):
            try:
                path.unlink()
            except OSError:
                pass
        try:
            output_dir.rmdir()
        except OSError:
            pass
        raise


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Prepare short-lived PhotoAI private-LAN TLS material.")
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--lan-ip", required=True)
    parser.add_argument("--valid-days", type=int, default=DEFAULT_VALID_DAYS)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        create_tls_material(args.output_dir, args.lan_ip, args.valid_days)
    except ValueError as exc:
        code = str(exc)
        print(code if code in SAFE_USER_ERRORS else "P20_PILOT_TLS_PREPARATION_FAILED", file=sys.stderr)
        return 2
    except FileExistsError:
        print("P20_PILOT_TLS_OUTPUT_EXISTS", file=sys.stderr)
        return 2
    except OSError:
        print("P20_PILOT_TLS_PREPARATION_FAILED", file=sys.stderr)
        return 3
    except Exception:
        print("P20_PILOT_TLS_PREPARATION_FAILED", file=sys.stderr)
        return 3
    print("P20_PILOT_TLS_PREPARATION_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
