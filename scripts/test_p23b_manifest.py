"""Real local Git fixtures only. No network, Android, Owner files or production data."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

from p23b_git import GateError, Git, MAX_BYTES, emit, full_oid, overlaps, relative_path, strict_json
from p23b_manifest import build, hashes, verify


class RepoCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="photoai-p23b-test-")
        self.addCleanup(self.temporary.cleanup)
        env = mock.patch.dict(os.environ, {"HOME": self.temporary.name, "USERPROFILE": self.temporary.name,
                                            "XDG_CONFIG_HOME": self.temporary.name})
        env.start()
        self.addCleanup(env.stop)
        self.root = Path(self.temporary.name) / "repo"
        self.root.mkdir()
        self.command("init", "-b", "main")
        (self.root / ".git" / "info").mkdir(exist_ok=True)
        self.command("config", "user.email", "fixture@example.invalid")
        self.command("config", "user.name", "Synthetic fixture")
        self.command("config", "core.autocrlf", "false")
        self.put("source.txt", b"synthetic\n")
        self.base = self.commit()
        self.git = Git(self.root)

    def command(self, *args):
        env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        env.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull, GIT_TERMINAL_PROMPT="0")
        empty = str(Path(self.temporary.name) / "empty-hooks-and-templates")
        Path(empty).mkdir(exist_ok=True)
        return subprocess.run(["git", "-c", "core.hooksPath=" + empty, "-c", "init.templateDir=" + empty,
                               "-c", "commit.gpgsign=false", "-C", str(self.root), *args],
                              check=True, env=env, capture_output=True).stdout.decode().strip()

    def put(self, path, data):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data if isinstance(data, bytes) else data.encode())

    def commit(self):
        self.command("add", "--all")
        self.command("commit", "-m", "synthetic fixture")
        return self.command("rev-parse", "HEAD")

    def manifest(self, transform=lambda value: value):
        value = transform(build(self.git, self.base, ["source.txt"]))
        self.put("manifest.json", json.dumps(value))
        return self.commit()


class ManifestTests(RepoCase):
    def test_build_and_verify_exact_committed_source(self):
        candidate = self.manifest()
        self.assertEqual(1, verify(self.git, candidate, "manifest.json", 1)["verified_files"])

    def test_crlf_checkout_does_not_change_manifest(self):
        before = build(self.git, self.base, ["source.txt"])
        self.command("config", "core.autocrlf", "true")
        self.put("source.txt", b"synthetic\r\n")
        self.assertEqual(before, build(self.git, self.base, ["source.txt"]))
        self.assertNotEqual(before["files"]["source.txt"]["sha256"], hashlib.sha256((self.root / "source.txt").read_bytes()).hexdigest())

    def test_dirty_source_is_not_read(self):
        before = build(self.git, self.base, ["source.txt"])
        self.put("source.txt", b"uncommitted bytes must not be approved\n")
        self.assertEqual(before, build(self.git, self.base, ["source.txt"]))

    def test_raw_crlf_blob_has_distinct_explicit_domains(self):
        self.put(".gitattributes", "*.txt -text\n")
        self.put("source.txt", b"synthetic\r\n")
        self.base = self.commit()
        fields = build(self.git, self.base, ["source.txt"])["files"]["source.txt"]
        self.assertEqual(11, fields["git_blob_bytes"])
        self.assertEqual(10, fields["bytes"])
        self.assertNotEqual(fields["sha256"], fields["git_blob_sha256"])
        self.assertEqual("PASS", verify(self.git, self.manifest(), "manifest.json", 1)["status"])

    def test_legacy_r1_shape_supported_without_rewriting(self):
        def legacy(value):
            value.pop("schema_version")
            value["status"] = "HISTORICAL_REPORT_NOT_AUTHORIZATION"
            value["files"]["source.txt"].pop("git_blob_bytes")
            value["files"]["source.txt"].pop("git_blob_sha256")
            return value
        self.assertEqual("PASS", verify(self.git, self.manifest(legacy), "manifest.json", 1)["status"])

    def test_manifest_count_must_match(self):
        candidate = self.manifest()
        for count in [0, 2]:
            with self.assertRaises(GateError):
                verify(self.git, candidate, "manifest.json", count)

    def test_hash_or_size_tampering_fails(self):
        for field, value in [("sha256", "0" * 64), ("git_blob", "0" * 40), ("bytes", True),
                             ("git_blob_bytes", 9), ("git_blob_sha256", "0" * 64)]:
            with self.subTest(field=field):
                manifest = build(self.git, self.base, ["source.txt"])
                manifest["files"]["source.txt"][field] = value
                self.put("manifest.json", json.dumps(manifest))
                candidate = self.commit()
                with self.assertRaisesRegex(GateError, "HASH_MISMATCH"):
                    verify(self.git, candidate, "manifest.json", 1)

    def test_listed_source_drift_fails_even_when_old_manifest_hash_is_valid(self):
        self.manifest()
        self.put("source.txt", b"new implementation\n")
        candidate = self.commit()
        with self.assertRaisesRegex(GateError, "SOURCE_DRIFT"):
            verify(self.git, candidate, "manifest.json", 1)

    def test_deleted_source_fails(self):
        self.manifest()
        (self.root / "source.txt").unlink()
        candidate = self.commit()
        with self.assertRaisesRegex(GateError, "SOURCE_DRIFT"):
            verify(self.git, candidate, "manifest.json", 1)

    def test_unrelated_manifest_source_fails(self):
        self.command("checkout", "--orphan", "unrelated")
        self.put("other.txt", "different root\n")
        unrelated = self.commit()
        self.command("checkout", "main")
        candidate = self.manifest(lambda m: dict(m, source_commit=unrelated))
        with self.assertRaisesRegex(GateError, "NOT_ANCESTOR"):
            verify(self.git, candidate, "manifest.json", 1)

    def test_manifest_missing_and_entry_invalid_fail(self):
        with self.assertRaisesRegex(GateError, "MANIFEST_MISSING"):
            verify(self.git, self.base, "manifest.json", 1)
        candidate = self.manifest(lambda m: dict(m, files={"source.txt": {"bytes": 10}}))
        with self.assertRaisesRegex(GateError, "ENTRY_INVALID"):
            verify(self.git, candidate, "manifest.json", 1)

    def test_no_filter_or_index_writes(self):
        candidate = self.manifest()
        self.put(".gitattributes", "*.txt filter=never\n")
        self.command("config", "filter.never.smudge", "exit 99")
        self.command("config", "filter.never.clean", "exit 99")
        self.command("config", "filter.never.required", "true")
        index = self.root / ".git" / "index"
        before = index.read_bytes()
        self.assertEqual("PASS", verify(self.git, candidate, "manifest.json", 1)["status"])
        self.assertEqual(before, index.read_bytes())

    def test_build_requires_nonempty_unique_existing_regular_paths(self):
        for paths in [[], ["source.txt", "source.txt"], ["missing.txt"]]:
            with self.assertRaises(GateError):
                build(self.git, self.base, paths)
        link = self.root / "link.txt"
        try:
            link.symlink_to("source.txt")
        except OSError:
            self.skipTest("OS does not permit symlink fixture")
        candidate = self.commit()
        with self.assertRaisesRegex(GateError, "REGULAR_BLOB"):
            build(self.git, candidate, ["link.txt"])

    def test_partial_clone_is_rejected_before_implicit_network_access(self):
        self.command("config", "remote.origin.promisor", "true")
        with self.assertRaisesRegex(GateError, "PARTIAL_CLONE"):
            Git(self.root)

    def test_subdirectory_is_not_accepted_as_repo_root(self):
        (self.root / "child").mkdir()
        with self.assertRaisesRegex(GateError, "REPOSITORY_ROOT"):
            Git(self.root / "child")

    def test_output_is_external_and_never_overwrites(self):
        target = Path(self.temporary.name) / "result.json"
        emit({"status": "synthetic"}, target, self.git)
        with self.assertRaisesRegex(GateError, "OUTPUT_EXISTS"):
            emit({}, target, self.git)
        with self.assertRaisesRegex(GateError, "OUTSIDE_ALL"):
            emit({}, self.root / "result.json", self.git)
        other = Path(self.temporary.name) / "other"
        self.command("worktree", "add", "--detach", str(other), self.base)
        with self.assertRaisesRegex(GateError, "OUTSIDE_ALL"):
            emit({}, other / "result.json", self.git)
        self.command("worktree", "remove", str(other))

    def test_cli_verification_and_safe_error(self):
        import sys
        script = Path(__file__).with_name("p23b_manifest.py")
        candidate = self.manifest()
        proc = subprocess.run([sys.executable, str(script), "--repo", str(self.root), "verify",
                               "--candidate", candidate, "--manifest", "manifest.json", "--expected-count", "1"],
                              capture_output=True, text=True)
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        self.assertEqual("PASS", json.loads(proc.stdout)["status"])
        proc = subprocess.run([sys.executable, str(script), "--repo", str(self.root), "verify",
                               "--candidate", "main", "--manifest", "manifest.json", "--expected-count", "1"],
                              capture_output=True, text=True)
        self.assertEqual(2, proc.returncode)
        self.assertNotIn(str(self.root), proc.stdout)


class InputTests(unittest.TestCase):
    def test_sha_requires_exact_lowercase_sha1(self):
        for value in ["main", "--help", "a" * 39, "A" * 40, None]:
            with self.assertRaises(GateError):
                full_oid(value)

    def test_json_duplicate_and_nonfinite_and_invalid_unicode_rejected(self):
        for raw in [b'{"a":1,"a":2}', b'{"a":NaN}', b'[]', b'\xff', b'{}{}', b' ' * (MAX_BYTES + 1)]:
            with self.assertRaises(GateError):
                strict_json(raw)

    def test_paths_and_prefixes_are_not_shell_or_windows_aliases(self):
        for value in ["../x", "/x", "a//b", ".git/config", "C:x", "a\\b", "a\n.txt"]:
            with self.assertRaises(GateError):
                relative_path(value)
        self.assertTrue(overlaps("TASKS/todo.md", "tasks/TODO.md"))
        self.assertTrue(overlaps("tasks", "tasks/todo.md"))
        self.assertFalse(overlaps("tasks", "tasks2/todo.md"))
        self.assertTrue(overlaps("caf\u00e9.md", "cafe\u0301.md"))

    def test_binary_and_invalid_utf8_are_not_text_manifest_sources(self):
        for raw in [b'\0binary', b'\xff']:
            with self.assertRaises(GateError):
                hashes(raw, "a" * 40)


if __name__ == "__main__":
    unittest.main()
