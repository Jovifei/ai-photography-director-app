"""Read-only Git primitives for P23B. No network, filters, hooks or index refresh."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import unicodedata

MAX_BYTES = 2_000_000
OID = re.compile(r"[0-9a-f]{40}\Z")


class GateError(Exception):
    """Safe machine code only: never forward Git stderr or file content."""


def full_oid(value: str) -> str:
    if not isinstance(value, str) or not OID.fullmatch(value):
        raise GateError("EXACT_SHA_REQUIRED")
    return value


def relative_path(value: str) -> str:
    if (not isinstance(value, str) or not value or "\\" in value or ":" in value
            or any(ord(c) < 32 or 0xD800 <= ord(c) <= 0xDFFF for c in value)):
        raise GateError("UNSAFE_RELATIVE_PATH")
    parts = value.split("/")
    if any(p in {"", ".", ".."} or p.casefold() == ".git" for p in parts):
        raise GateError("UNSAFE_RELATIVE_PATH")
    return value


def path_key(value: str) -> str:
    # Conservative cross-platform overlap, even when this check runs on Linux.
    return unicodedata.normalize("NFC", relative_path(value)).casefold()


def overlaps(left: str, right: str) -> bool:
    a, b = path_key(left), path_key(right)
    return a == b or a.startswith(b + "/") or b.startswith(a + "/")


def strict_json(raw: bytes) -> dict:
    def pairs(items: list) -> dict:
        result = {}
        for key, value in items:
            if key in result:
                raise GateError("DUPLICATE_JSON_KEY")
            result[key] = value
        return result
    if len(raw) > MAX_BYTES:
        raise GateError("JSON_TOO_LARGE")
    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                           parse_constant=lambda _: (_ for _ in ()).throw(GateError("INVALID_JSON")))
    except (ValueError, UnicodeError, RecursionError):
        raise GateError("INVALID_JSON") from None
    if not isinstance(value, dict):
        raise GateError("JSON_OBJECT_REQUIRED")
    return value


class Git:
    def __init__(self, root: Path):
        self.root = root.resolve(strict=True)
        actual = Path(self.run("rev-parse", "--show-toplevel").decode().strip()).resolve()
        if actual != self.root:
            raise GateError("REPOSITORY_ROOT_REQUIRED")

    def run(self, *args: str, allow_one: bool = False) -> bytes:
        env = os.environ.copy()
        # Inherited Git overrides must not redirect reads to another index/object store.
        for name in list(env):
            if name.startswith("GIT_"):
                del env[name]
        env.update(GIT_OPTIONAL_LOCKS="0", GIT_TERMINAL_PROMPT="0", GIT_NO_REPLACE_OBJECTS="1")
        try:
            result = subprocess.run(
                ["git", "--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false",
                 "-C", str(self.root), *args], env=env, capture_output=True, timeout=30,
            )
        except (OSError, subprocess.SubprocessError):
            raise GateError("GIT_EXECUTION_FAILED") from None
        if result.returncode not in ({0, 1} if allow_one else {0}):
            raise GateError("GIT_READ_FAILED")
        if len(result.stdout) > 10 * MAX_BYTES:
            raise GateError("GIT_OUTPUT_TOO_LARGE")
        return result.stdout if result.returncode == 0 else b"NOT_ANCESTOR"

    def commit(self, sha: str) -> str:
        full_oid(sha)
        if self.run("cat-file", "-t", sha).strip() != b"commit":
            raise GateError("COMMIT_REQUIRED")
        return sha

    def ref(self, name: str) -> str:
        return full_oid(self.run("rev-parse", "--verify", name).decode().strip())

    def ancestor(self, base: str, head: str) -> bool:
        return self.run("merge-base", "--is-ancestor", self.commit(base), self.commit(head), allow_one=True) == b""

    def tree(self, sha: str) -> dict[str, tuple[str, str, str]]:
        result = {}
        for row in self.run("ls-tree", "-r", "-z", "--full-tree", self.commit(sha)).split(b"\0"):
            if not row:
                continue
            header, path = row.split(b"\t", 1)
            mode, kind, oid = header.decode("ascii").split()
            result[relative_path(path.decode("utf-8"))] = (mode, kind, full_oid(oid))
        return result

    def blob(self, entry: tuple[str, str, str]) -> bytes:
        mode, kind, oid = entry
        if mode not in {"100644", "100755"} or kind != "blob":
            raise GateError("REGULAR_BLOB_REQUIRED")
        size = int(self.run("cat-file", "-s", full_oid(oid)))
        if size > MAX_BYTES:
            raise GateError("BLOB_TOO_LARGE")
        raw = self.run("cat-file", "blob", oid)
        actual = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        if len(raw) != size or actual != oid:
            raise GateError("BLOB_ID_MISMATCH")
        return raw

    def common_dir(self) -> Path:
        raw = self.run("rev-parse", "--git-common-dir").decode().strip()
        return (self.root / raw).resolve()

    def worktrees(self) -> list[Path]:
        result = []
        for part in self.run("worktree", "list", "--porcelain", "-z").split(b"\0"):
            if part.startswith(b"worktree "):
                result.append(Path(part[9:].decode("utf-8")).resolve())
        return result


def emit(report: dict, output: Path | None, git: Git) -> None:
    text = json.dumps(report, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    if output is None:
        print(text, end="")
        return
    destination = output.resolve()
    protected = git.worktrees() + [git.common_dir()]
    if any(destination == root or root in destination.parents for root in protected):
        raise GateError("OUTPUT_MUST_BE_OUTSIDE_ALL_WORKTREES")
    if output.is_symlink() or output.exists():
        raise GateError("OUTPUT_EXISTS")
    # Existing external parent required. Never create/remove a user directory.
    try:
        with output.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(text)
    except FileExistsError:
        raise GateError("OUTPUT_EXISTS") from None
