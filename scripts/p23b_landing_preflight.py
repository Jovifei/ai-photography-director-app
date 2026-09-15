#!/usr/bin/env python3
"""Read-only Owner-file preflight. A PASS never authorizes merge or release."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat

from p23b_git import GateError, Git, MAX_BYTES, emit, full_oid, overlaps, path_key, relative_path, strict_json
from p23b_manifest import verify

CANDIDATE = "e4a954b313320944fe751a2388366ee72d3e9083"
MAIN = "61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6"
CANDIDATE_REF = "refs/remotes/origin/codex/p23a-r1-review-remediation-20260913"
MANIFEST = "docs/handoff/P23A_R1_SOURCE_MANIFEST_20260913.json"
ORIGINS = {"https://github.com/Jovifei/ai-photography-director-app.git",
           "git@github.com:Jovifei/ai-photography-director-app.git"}
TEXT_SUFFIXES = {".md", ".txt", ".py", ".kt", ".kts", ".java", ".json", ".yaml", ".yml",
                 ".xml", ".toml", ".properties", ".ps1", ".gradle", ".sh", ".bat"}
SENSITIVE_PARTS = {"private-data", "photos", "private", "models", "model-cache", "secrets", "credentials"}
MANUAL_GATES = ["OWNER_EXACT_SHA_MERGE_DECISION", "INDEPENDENT_REVIEW_RECORD_BINDING",
                "EXISTING_RELEASE_SIGNING_IDENTITY", "V6_LOCAL_AND_D2D_REQUALIFICATION",
                "OS_PROCESS_DEATH", "SYSTEM_FONT_SCREENSHOTS_100_200"]


def checked_local_file(root: Path, rel: str) -> dict:
    """Hash only bounded ordinary text files; never traverse symlinks/junctions."""
    path = root
    for component in relative_path(rel).split("/"):
        path = path / component
        try:
            info = path.lstat()
        except FileNotFoundError:
            return {"kind": "MISSING"}
        if stat.S_ISLNK(info.st_mode) or (getattr(info, "st_file_attributes", 0) & 0x400):
            return {"kind": "UNHASHED", "reason": "SYMLINK_OR_REPARSE_POINT"}
    if not stat.S_ISREG(info.st_mode):
        return {"kind": "UNHASHED", "reason": "NOT_REGULAR_TEXT"}
    parts = {p.casefold() for p in Path(rel).parts}
    name = path.name.casefold()
    if (path.suffix.casefold() not in TEXT_SUFFIXES and name not in {".gitignore", ".gitattributes"}
            or name.startswith(".env") or parts & SENSITIVE_PARTS):
        return {"kind": "UNHASHED", "reason": "NON_TEXT_OR_PRIVATE_PATH"}
    if info.st_size > MAX_BYTES:
        return {"kind": "UNHASHED", "reason": "TEXT_TOO_LARGE"}
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_BINARY", 0))
    with os.fdopen(fd, "rb") as stream:
        opened = os.fstat(stream.fileno())
        if not stat.S_ISREG(opened.st_mode) or (opened.st_dev, opened.st_ino) != (info.st_dev, info.st_ino):
            raise GateError("OWNER_FILE_CHANGED_DURING_READ")
        raw = stream.read(MAX_BYTES + 1)
        after = os.fstat(stream.fileno())
    final = path.lstat()
    signature = lambda value: (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_mode)
    if len(raw) > MAX_BYTES or signature(info) != signature(after) or signature(after) != signature(final):
        raise GateError("OWNER_FILE_CHANGED_DURING_READ")
    return {"kind": "TEXT_HASH_ONLY", "bytes": len(raw), "mode": stat.S_IMODE(info.st_mode),
            "worktree_raw_sha256": hashlib.sha256(raw).hexdigest()}


def owner_snapshot(owner: Git) -> dict:
    # A configured clean/process filter could run an external program during git status.
    configured = owner.run("config", "--name-only", "--get-regexp", r"^filter\..*\.(clean|process)$", allow_one=True)
    if configured not in {b"", b"NOT_ANCESTOR"}:
        raise GateError("CUSTOM_GIT_FILTER_REQUIRES_REVIEW")
    raw = owner.run("status", "--porcelain=v1", "-z", "--no-renames", "--untracked-files=all",
                    "--ignored=matching", "--ignore-submodules=all")
    entries = []
    total_bytes = 0
    for row in raw.split(b"\0"):
        if not row:
            continue
        if len(entries) >= 5000:
            raise GateError("OWNER_SNAPSHOT_TOO_LARGE")
        if len(row) < 4 or row[2:3] != b" ":
            raise GateError("UNEXPECTED_GIT_STATUS")
        code = row[:2].decode("ascii")
        rel = relative_path(row[3:].decode("utf-8").rstrip("/"))
        detail = {"kind": "IGNORED_NOT_READ"} if code == "!!" else checked_local_file(owner.root, rel)
        total_bytes += detail.get("bytes", 0)
        if total_bytes > 10 * MAX_BYTES:
            raise GateError("OWNER_SNAPSHOT_TOO_LARGE")
        entries.append({"status": code, "path": rel, **detail})
    index_path = owner.root / owner.run("rev-parse", "--git-path", "index").decode().strip()
    index_hash = hashlib.sha256(index_path.read_bytes()).hexdigest() if index_path.is_file() else None
    return {"head": owner.ref("HEAD"), "index_sha256": index_hash,
            "entries": sorted(entries, key=lambda entry: (entry["path"], entry["status"])),
            "hash_domain": "OWNER_RAW_WORKTREE_BYTES_NOT_GIT_APPROVAL_BYTES"}


def portable_tree(tree: dict) -> None:
    keys = sorted(path_key(path) for path in tree)
    for a, b in zip(keys, keys[1:]):
        if a == b or b.startswith(a + "/"):
            raise GateError("WINDOWS_TREE_ALIAS")
    reserved = {"con", "prn", "aux", "nul"} | {f"{p}{n}" for p in ["com", "lpt"] for n in range(1, 10)}
    for path in tree:
        for part in path.split("/"):
            if part.endswith((".", " ")) or part.split(".")[0].casefold() in reserved:
                raise GateError("WINDOWS_UNSAFE_TREE_PATH")


def audit(git: Git, owner: Git, candidate: str, expected_main: str, candidate_ref: str,
          manifest: str = MANIFEST, count: int = 27, previous: dict | None = None) -> dict:
    full_oid(candidate)
    full_oid(expected_main)
    if not candidate_ref.startswith("refs/remotes/origin/") or ".." in candidate_ref or "@{" in candidate_ref:
        raise GateError("REMOTE_TRACKING_REF_REQUIRED")
    if git.root == owner.root or git.common_dir() != owner.common_dir():
        raise GateError("SEPARATE_LINKED_WORKTREE_REQUIRED")
    if git.run("remote", "get-url", "origin").decode().strip() not in ORIGINS:
        raise GateError("ORIGIN_MISMATCH")
    before_refs = (git.ref("refs/remotes/origin/main"), git.ref(candidate_ref))
    if before_refs != (expected_main, candidate):
        raise GateError("REMOTE_TRACKING_REFS_MOVED")
    if not git.ancestor(expected_main, candidate):
        raise GateError("NOT_FAST_FORWARD")
    report = {"schema_version": "p23b.owner-preflight.v1", "candidate": candidate,
              "expected_main": expected_main, "candidate_ref": candidate_ref,
              "merge_authorized": False, "release_authorized": False,
              "remote_freshness": "LOCAL_TRACKING_REFS_ONLY_FETCH_SEPARATELY",
              "manual_gates_not_verified": MANUAL_GATES.copy(), "blockers": []}
    manifest_result = verify(git, candidate, manifest, count)
    report["source_manifest"] = manifest_result
    base_tree, target_tree = git.tree(expected_main), git.tree(candidate)
    portable_tree(target_tree)
    if any(entry[0] == "160000" for entry in base_tree.values()):
        raise GateError("SUBMODULE_OWNER_AUDIT_UNSUPPORTED")
    changed = sorted(p for p in base_tree.keys() | target_tree.keys() if base_tree.get(p) != target_tree.get(p))
    before = owner_snapshot(owner)
    report["owner_snapshot"] = before
    blockers = report["blockers"]
    if before["head"] != expected_main:
        blockers.append({"code": "OWNER_HEAD_NOT_EXPECTED_MAIN"})
    if owner.run("symbolic-ref", "--quiet", "HEAD", allow_one=True).strip() != b"refs/heads/main":
        blockers.append({"code": "OWNER_NOT_ON_MAIN"})
    flags = owner.run("ls-files", "-v", "-z").split(b"\0")
    if any(row and (row[:1] == b"S" or chr(row[0]).islower()) for row in flags):
        blockers.append({"code": "OWNER_HIDDEN_INDEX_FLAGS"})
    git_dir = owner.root / owner.run("rev-parse", "--git-dir").decode().strip()
    if any((git_dir / name).exists() for name in ["MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "rebase-merge", "rebase-apply", "sequencer"]):
        blockers.append({"code": "OWNER_GIT_OPERATION_IN_PROGRESS"})
    for entry in before["entries"]:
        collisions = [path for path in changed if overlaps(entry["path"], path)]
        if collisions:
            blockers.append({"code": "OWNER_PATH_OVERLAP", "path": entry["path"], "status": entry["status"],
                             "candidate_paths": collisions})
        if entry["kind"] == "UNHASHED":
            blockers.append({"code": "OWNER_CONTENT_NOT_SAFELY_SNAPSHOTTED", "path": entry["path"]})
        if "U" in entry["status"] or entry["status"] in {"AA", "DD"}:
            blockers.append({"code": "OWNER_UNMERGED_INDEX", "path": entry["path"]})
    if any(target_tree.get(path, ("100644",))[0] not in {"100644", "100755"} for path in changed):
        blockers.append({"code": "CANDIDATE_SPECIAL_FILE_REQUIRES_REVIEW"})
    if previous is not None:
        if (previous.get("schema_version") != report["schema_version"] or previous.get("candidate") != candidate
                or previous.get("expected_main") != expected_main or not isinstance(previous.get("owner_snapshot"), dict)):
            raise GateError("PREVIOUS_SNAPSHOT_WRONG_SCOPE")
        if previous["owner_snapshot"] != before:
            blockers.append({"code": "OWNER_SNAPSHOT_CHANGED_SINCE_PREVIOUS_CHECK"})
    after = owner_snapshot(owner)
    if before != after:
        blockers.append({"code": "OWNER_CHANGED_DURING_PREFLIGHT"})
    if before_refs != (git.ref("refs/remotes/origin/main"), git.ref(candidate_ref)):
        blockers.append({"code": "REFS_CHANGED_DURING_PREFLIGHT"})
    report["changed_path_count"] = len(changed)
    report["owner_unchanged_during_check"] = before == after
    report["status"] = "BLOCKED" if blockers else "LOCAL_PREFLIGHT_PASS_NOT_MERGE_AUTHORIZATION"
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path, help="Independent linked tooling worktree")
    parser.add_argument("--owner-worktree", required=True, type=Path)
    parser.add_argument("--candidate", default=CANDIDATE)
    parser.add_argument("--expected-main", default=MAIN)
    parser.add_argument("--candidate-ref", default=CANDIDATE_REF)
    parser.add_argument("--previous", type=Path, help="Previous external report for unchanged-file comparison")
    parser.add_argument("--output", type=Path, help="New external JSON; never commit this private snapshot")
    args = parser.parse_args()
    try:
        git, owner = Git(args.repo), Git(args.owner_worktree)
        previous = None
        if args.previous is not None:
            with args.previous.open("rb") as stream:
                previous = strict_json(stream.read(MAX_BYTES + 1))
        report = audit(git, owner, args.candidate, args.expected_main, args.candidate_ref, previous=previous)
        emit(report, args.output, git)
        return 2 if report["status"] == "BLOCKED" else 0
    except (GateError, OSError, UnicodeError, KeyError, TypeError, ValueError) as error:
        code = str(error) if isinstance(error, GateError) else "LOCAL_READ_OR_FORMAT_FAILED"
        print(json.dumps({"status": "BLOCKED", "code": code, "merge_authorized": False}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
