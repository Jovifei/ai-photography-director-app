#!/usr/bin/env python3
"""Prepare an UNSIGNED review candidate, never a production release.

Only explicitly selected bounded local JSON/ZIP files are read. No source scan,
network, signing, inference or App mutation. Review receipts are unauthenticated
attestations; neither the tool nor its archive grants import/distribution rights.
Run only in Owner-controlled directories, without concurrent filesystem changes.
"""
from __future__ import annotations

import argparse
import io
import json
import os
from pathlib import Path
import stat
import tempfile
from typing import Any
import zipfile

from p23c_bundle_contract import (
    MAX_BUNDLE_BYTES, PackError, encoded_json, exact_keys, opaque,
    require, sha256, strict_json, validate_bundle,
)

MAX_RECEIPT_BYTES = 64 * 1024
MAX_ARCHIVE_BYTES = 1024 * 1024
PURPOSES = ("SYNTHETIC_TEST", "PUBLIC_CANDIDATE")
REVIEW_FIELDS = ("content_review", "rights_review", "privacy_review")
ARCHIVE_NAMES = ("bundle.json", "review-receipt.json", "candidate-manifest.json")


def review_template(bundle_raw: bytes, purpose: str) -> dict[str, Any]:
    bundle = validate_bundle(bundle_raw)
    require(purpose in PURPOSES, "PURPOSE_INVALID")
    # Do not prefill approval, identity or evidence. A template cannot pass prepare.
    return {
        "receipt_version": "1.0", "purpose": purpose,
        "bundle_id": bundle["bundle_id"],
        "payload_sha256": bundle["integrity"]["payload_sha256"].lower(),
        "bundle_file_sha256": sha256(bundle_raw),
        "review_id": None, "reviewer_id": None,
        "source_evidence_id": None, "producer_qualification_evidence_id": None,
        "entries": [{"reference_id": item["reference_id"], "evidence_id": None,
                     **{field: "PENDING" for field in REVIEW_FIELDS}}
                    for item in bundle["references"]],
    }


def validate_receipt(bundle_raw: bytes, receipt_raw: bytes, purpose: str) -> tuple[dict, dict]:
    bundle = validate_bundle(bundle_raw)
    receipt = strict_json(receipt_raw, MAX_RECEIPT_BYTES)
    exact_keys(receipt, {"receipt_version", "purpose", "bundle_id", "payload_sha256",
                         "bundle_file_sha256", "review_id", "reviewer_id", "source_evidence_id",
                         "producer_qualification_evidence_id", "entries"})
    require(purpose in PURPOSES and receipt["purpose"] == purpose, "PURPOSE_MISMATCH")
    require(receipt["receipt_version"] == "1.0", "RECEIPT_VERSION_UNSUPPORTED")
    require(receipt["bundle_id"] == bundle["bundle_id"]
            and receipt["payload_sha256"] == bundle["integrity"]["payload_sha256"].lower()
            and receipt["bundle_file_sha256"] == sha256(bundle_raw), "REVIEW_BINDING_MISMATCH")
    if purpose == "PUBLIC_CANDIDATE":
        require(len(bundle["references"]) == 20, "PUBLIC_CANDIDATE_REQUIRES_20")
        require(bundle["source"]["origin"] == "PIPELINE", "PUBLIC_CANDIDATE_REQUIRES_PIPELINE")
    for field in ("review_id", "reviewer_id", "source_evidence_id", "producer_qualification_evidence_id"):
        opaque(receipt[field])
    entries = receipt["entries"]
    require(type(entries) is list and len(entries) == len(bundle["references"]), "REVIEW_COVERAGE_INVALID")
    expected = {item["reference_id"] for item in bundle["references"]}
    seen: set[str] = set()
    for entry in entries:
        exact_keys(entry, {"reference_id", "evidence_id", *REVIEW_FIELDS})
        opaque(entry["reference_id"])
        require(entry["reference_id"] in expected and entry["reference_id"] not in seen,
                "REVIEW_COVERAGE_INVALID")
        seen.add(entry["reference_id"])
        opaque(entry["evidence_id"])
        require(all(entry[field] == "APPROVED" for field in REVIEW_FIELDS), "REVIEW_NOT_COMPLETE")
    return bundle, receipt


def candidate_bytes(bundle_raw: bytes, receipt_raw: bytes, purpose: str) -> bytes:
    bundle, _ = validate_receipt(bundle_raw, receipt_raw, purpose)
    manifest = {
        "format": "photoai.review-candidate.v1", "purpose": purpose,
        "status": "UNSIGNED_NOT_FOR_DISTRIBUTION", "signature_status": "NOT_SIGNED",
        "review_validation": "STRUCTURE_AND_HASH_ONLY",
        "publisher_authenticated": False, "app_import_authorized": False,
        "release_authorized": False, "reference_count": len(bundle["references"]),
        "payload_sha256": bundle["integrity"]["payload_sha256"].lower(),
        "files": {name: {"bytes": len(raw), "sha256": sha256(raw)} for name, raw in
                  ((ARCHIVE_NAMES[0], bundle_raw), (ARCHIVE_NAMES[1], receipt_raw))},
    }
    stream = io.BytesIO()
    # Fixed names, time, ordering and ZIP_STORED make byte-for-byte reproducibility
    # independent of zlib versions. Raw bundle/receipt bytes are never rewritten.
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_STORED) as archive:
        for name, raw in zip(ARCHIVE_NAMES, (bundle_raw, receipt_raw, encoded_json(manifest))):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | 0o600) << 16
            archive.writestr(info, raw)
    result = stream.getvalue()
    require(len(result) <= MAX_ARCHIVE_BYTES, "ARCHIVE_TOO_LARGE")
    return result


def verify_candidate(raw: bytes, purpose: str) -> dict[str, Any]:
    require(len(raw) <= MAX_ARCHIVE_BYTES, "ARCHIVE_TOO_LARGE")
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            require(tuple(archive.namelist()) == ARCHIVE_NAMES, "ARCHIVE_MEMBERS_INVALID")
            for info, limit in zip(archive.infolist(), (MAX_BUNDLE_BYTES, MAX_RECEIPT_BYTES, 8192)):
                require(not info.flag_bits & 1 and info.compress_type == zipfile.ZIP_STORED
                        and 0 < info.file_size <= limit, "ARCHIVE_MEMBER_INVALID")
            # No extraction; every member is size-bounded before any read.
            bundle_raw, receipt_raw, manifest_raw = (archive.read(name) for name in ARCHIVE_NAMES)
    except PackError:
        raise
    except (ValueError, OSError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as error:
        raise PackError("ARCHIVE_INVALID") from error
    expected = candidate_bytes(bundle_raw, receipt_raw, purpose)
    require(raw == expected, "ARCHIVE_NOT_CANONICAL")
    manifest = strict_json(manifest_raw, 8192)
    return {"status": manifest["status"], "purpose": purpose,
            "reference_count": manifest["reference_count"], "archive_sha256": sha256(raw),
            "publisher_authenticated": False, "app_import_authorized": False, "release_authorized": False}


def _safe_path(path: Path) -> Path:
    require(".." not in path.parts, "PATH_TRAVERSAL")
    path = path.absolute()
    require(not str(path).startswith(("//", "\\\\")), "NETWORK_PATH_FORBIDDEN")
    # Reject symlinks AND Windows junction/reparse points without following them.
    for part in reversed((path, *path.parents)):
        try:
            info = part.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info, "st_file_attributes", 0) & 0x400,
                "SYMLINK_OR_REPARSE_POINT")
    return path


def read_selected(path: Path, suffix: str, limit: int) -> bytes:
    path = _safe_path(path)
    require(path.suffix.lower() == suffix, "INPUT_TYPE_INVALID")
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
                 | getattr(os, "O_BINARY", 0))
    with os.fdopen(fd, "rb") as stream:
        before = os.fstat(stream.fileno())
        require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1, "INPUT_NOT_ORDINARY_FILE")
        require(before.st_size <= limit, "DOCUMENT_TOO_LARGE")
        raw = stream.read(limit + 1)
        after = os.fstat(stream.fileno())
    current = path.lstat()
    require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
            == (current.st_dev, current.st_ino, current.st_size, current.st_mtime_ns), "INPUT_CHANGED")
    require(len(raw) <= limit, "DOCUMENT_TOO_LARGE")
    return raw


def publish_new(path: Path, raw: bytes, repo_root: Path | None = None) -> None:
    path = _safe_path(path)
    root = (repo_root or Path(__file__).absolute().parents[1]).resolve()
    require(root != path and root not in path.parents, "OUTPUT_INSIDE_REPOSITORY")
    require(not any((parent / ".git").exists() for parent in path.parents), "OUTPUT_INSIDE_REPOSITORY")
    require(path.parent.is_dir(), "OUTPUT_PARENT_MISSING")
    require(not path.exists(), "OUTPUT_EXISTS")
    fd, temporary = tempfile.mkstemp(prefix=".photoai-candidate-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        try:
            # Atomic no-replace publication on supported filesystems (including NTFS).
            # No os.replace/rename fallback that might overwrite another writer's file.
            os.link(temporary, path)
        except FileExistsError as error:
            raise PackError("OUTPUT_EXISTS") from error
        except OSError as error:
            raise PackError("ATOMIC_PUBLISH_UNAVAILABLE") from error
    finally:
        try:
            os.unlink(temporary)
        except OSError:
            pass  # A leftover private temp file is not a published candidate.


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("inspect").add_argument("--bundle", type=Path, required=True)
    for command in ("review-template", "prepare"):
        child = commands.add_parser(command)
        child.add_argument("--bundle", type=Path, required=True)
        child.add_argument("--purpose", choices=PURPOSES, required=True)
        child.add_argument("--output", type=Path, required=True)
        if command == "prepare":
            child.add_argument("--receipt", type=Path, required=True)
    child = commands.add_parser("verify-candidate")
    child.add_argument("--archive", type=Path, required=True)
    child.add_argument("--purpose", choices=PURPOSES, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "verify-candidate":
            result = verify_candidate(read_selected(args.archive, ".zip", MAX_ARCHIVE_BYTES), args.purpose)
        else:
            bundle_raw = read_selected(args.bundle, ".json", MAX_BUNDLE_BYTES)
            bundle = validate_bundle(bundle_raw)
            result = {"status": "BUNDLE_FORMAT_AND_DIGEST_VALID", "reference_count": len(bundle["references"]),
                      "payload_sha256": bundle["integrity"]["payload_sha256"].lower(),
                      "bundle_file_sha256": sha256(bundle_raw), "app_import_authorized": False}
            if args.command == "review-template":
                require(args.output.suffix.lower() == ".json", "OUTPUT_TYPE_INVALID")
                raw = encoded_json(review_template(bundle_raw, args.purpose))
                publish_new(args.output, raw)
                result = {"status": "PENDING_REVIEW_TEMPLATE_WRITTEN", "receipt_sha256": sha256(raw)}
            elif args.command == "prepare":
                require(args.output.suffix.lower() == ".zip", "OUTPUT_TYPE_INVALID")
                receipt_raw = read_selected(args.receipt, ".json", MAX_RECEIPT_BYTES)
                raw = candidate_bytes(bundle_raw, receipt_raw, args.purpose)
                result = verify_candidate(raw, args.purpose)
                publish_new(args.output, raw)
        print(json.dumps(result, sort_keys=True))
        return 0
    except PackError as error:
        print(json.dumps({"status": "BLOCKED", "code": str(error)}, sort_keys=True))
        return 2
    except OSError:
        print(json.dumps({"status": "BLOCKED", "code": "LOCAL_IO_ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
