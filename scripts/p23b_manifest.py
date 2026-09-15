#!/usr/bin/env python3
"""Generate/verify text manifests from committed Git blobs, never checkout bytes."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from p23b_git import GateError, Git, emit, relative_path, strict_json


def hashes(raw: bytes, oid: str) -> dict:
    try:
        raw.decode("utf-8")
    except UnicodeError:
        raise GateError("MANIFEST_TEXT_ONLY") from None
    if b"\0" in raw:
        raise GateError("MANIFEST_TEXT_ONLY")
    lf = raw.replace(b"\r\n", b"\n")
    return {"git_blob": oid, "git_blob_bytes": len(raw),
            "git_blob_sha256": hashlib.sha256(raw).hexdigest(),
            "bytes": len(lf), "sha256": hashlib.sha256(lf).hexdigest()}


def build(git: Git, source: str, paths: list[str]) -> dict:
    tree = git.tree(source)
    if not paths or len(paths) != len(set(paths)):
        raise GateError("NONEMPTY_UNIQUE_PATHS_REQUIRED")
    files = {}
    for path in sorted(paths):
        relative_path(path)
        if path not in tree:
            raise GateError("MANIFEST_SOURCE_MISSING")
        files[path] = hashes(git.blob(tree[path]), tree[path][2])
    return {"schema_version": "p23b.git-text-manifest.v1", "source_commit": source,
            "hash_domains": {"git_blob": "SHA-1 of unfiltered committed Git blob",
                             "git_blob_sha256": "SHA-256 of raw committed blob bytes",
                             "git_blob_bytes": "Raw committed blob byte length",
                             "bytes": "UTF-8 bytes after CRLF-to-LF only",
                             "sha256": "SHA-256 after CRLF-to-LF only",
                             "windows_checkout": "NOT_READ; never an approval hash domain"},
            "files": files}


def verify(git: Git, candidate: str, path: str, expected_count: int) -> dict:
    if expected_count <= 0:
        raise GateError("POSITIVE_MANIFEST_COUNT_REQUIRED")
    tree = git.tree(candidate)
    if relative_path(path) not in tree:
        raise GateError("MANIFEST_MISSING")
    manifest = strict_json(git.blob(tree[path]))
    source = manifest.get("source_commit")
    if not git.ancestor(source, candidate):
        raise GateError("MANIFEST_SOURCE_NOT_ANCESTOR")
    files = manifest.get("files")
    if not isinstance(files, dict) or len(files) != expected_count:
        raise GateError("MANIFEST_COUNT_MISMATCH")
    source_tree = git.tree(source)
    for rel, expected in files.items():
        relative_path(rel)
        if not isinstance(expected, dict) or not {"git_blob", "bytes", "sha256"} <= expected.keys():
            raise GateError("MANIFEST_ENTRY_INVALID")
        if rel not in tree or rel not in source_tree or tree[rel] != source_tree[rel]:
            raise GateError("MANIFEST_SOURCE_DRIFT")
        actual = hashes(git.blob(tree[rel]), tree[rel][2])
        for field in ("git_blob", "bytes", "sha256", "git_blob_bytes", "git_blob_sha256"):
            if field in expected and (type(expected[field]) is not type(actual[field]) or expected[field] != actual[field]):
                raise GateError("MANIFEST_HASH_MISMATCH")
    return {"status": "PASS", "candidate": candidate, "source_commit": source,
            "manifest_path": path, "manifest_git_blob": tree[path][2], "verified_files": len(files),
            "scope": "LISTED_COMMITTED_TEXT_ONLY_NOT_RUNTIME_OR_REVIEW_APPROVAL"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, help="New JSON outside every worktree; otherwise stdout")
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("build")
    create.add_argument("--source", required=True, help="Full existing commit SHA")
    create.add_argument("--path", action="append", required=True)
    check = commands.add_parser("verify")
    check.add_argument("--candidate", required=True)
    check.add_argument("--manifest", required=True)
    check.add_argument("--expected-count", type=int, required=True)
    args = parser.parse_args()
    try:
        git = Git(args.repo)
        report = (build(git, args.source, args.path) if args.command == "build"
                  else verify(git, args.candidate, args.manifest, args.expected_count))
        emit(report, args.output, git)
        return 0
    except (GateError, OSError, UnicodeError, KeyError, TypeError, ValueError) as error:
        code = str(error) if isinstance(error, GateError) else "LOCAL_READ_OR_FORMAT_FAILED"
        print(json.dumps({"status": "BLOCKED", "code": code}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
