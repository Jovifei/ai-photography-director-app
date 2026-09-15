"""P23B Owner-safety tests use isolated local Git worktrees, never the user's tree."""
from __future__ import annotations

import hashlib
from pathlib import Path
from unittest import mock
import unittest

from p23b_git import GateError, Git
from p23b_landing_preflight import ORIGINS, audit, checked_local_file, owner_snapshot, portable_tree
from test_p23b_manifest import RepoCase


class LandingTests(RepoCase):
    def setUp(self):
        super().setUp()
        self.command("remote", "add", "origin", sorted(ORIGINS)[0])
        self.command("checkout", "-b", "candidate")
        self.put("tasks/todo.md", "candidate-authored notes\n")
        self.candidate = self.manifest()
        self.command("checkout", "main")
        self.command("update-ref", "refs/remotes/origin/main", self.base)
        self.command("update-ref", "refs/remotes/origin/candidate", self.candidate)
        self.runner = Path(self.temporary.name) / "runner"
        self.command("worktree", "add", "--detach", str(self.runner), self.candidate)
        self.git = Git(self.runner)
        self.owner = Git(self.root)

    def check(self, **kwargs):
        return audit(self.git, self.owner, self.candidate, self.base,
                     "refs/remotes/origin/candidate", "manifest.json", 1, **kwargs)

    def codes(self, **kwargs):
        return {item["code"] for item in self.check(**kwargs)["blockers"]}

    def test_clean_ancestor_is_not_merge_authorization(self):
        result = self.check()
        self.assertEqual("LOCAL_PREFLIGHT_PASS_NOT_MERGE_AUTHORIZATION", result["status"])
        self.assertFalse(result["merge_authorized"])
        self.assertFalse(result["release_authorized"])
        self.assertIn("OS_PROCESS_DEATH", result["manual_gates_not_verified"])

    def test_untracked_todo_collision_is_blocking_and_untouched(self):
        self.put("tasks/todo.md", "Owner private task text\r\n")
        before = (self.root / "tasks/todo.md").read_bytes()
        self.assertIn("OWNER_PATH_OVERLAP", self.codes())
        self.assertEqual(before, (self.root / "tasks/todo.md").read_bytes())
        self.assertNotIn("Owner private task text", str(self.check()))

    def test_ignored_file_collision_is_also_blocking(self):
        (self.root / ".git/info/exclude").write_text("tasks/todo.md\n")
        self.put("tasks/todo.md", "ignored Owner notes\n")
        result = self.check()
        self.assertTrue(any(b["status"] == "!!" for b in result["blockers"] if b["code"] == "OWNER_PATH_OVERLAP"))
        entry = result["owner_snapshot"]["entries"][0]
        self.assertEqual("IGNORED_NOT_READ", entry["kind"])
        self.assertNotIn("worktree_raw_sha256", entry)

    def test_ignored_directory_prefix_collision_is_blocking(self):
        (self.root / ".git/info/exclude").write_text("tasks/\n")
        self.put("tasks/other.md", "ignored subtree\n")
        self.assertIn("OWNER_PATH_OVERLAP", self.codes())

    def test_noncolliding_ignored_directory_is_not_read(self):
        (self.root / ".git/info/exclude").write_text("private-data/\n")
        self.put("private-data/image.jpg", b"not an image; never read\n")
        with mock.patch("p23b_landing_preflight.checked_local_file", side_effect=AssertionError("must not read")):
            self.assertEqual([], self.check()["blockers"])

    def test_untracked_file_directory_overlap_blocks(self):
        self.put("tasks", "file occupying candidate directory\n")
        self.assertIn("OWNER_PATH_OVERLAP", self.codes())

    def test_case_alias_overlap_blocks_on_linux_too(self):
        self.put("TASKS/TODO.md", "Owner case alias\n")
        self.assertIn("OWNER_PATH_OVERLAP", self.codes())

    def test_noncolliding_owner_text_is_raw_hashed(self):
        self.put("notes space中.md", b"Owner notes\r\n")
        result = self.check()
        self.assertEqual([], result["blockers"])
        entry = result["owner_snapshot"]["entries"][0]
        self.assertEqual(hashlib.sha256(b"Owner notes\r\n").hexdigest(), entry["worktree_raw_sha256"])

    def test_tracked_dirty_noncolliding_file_is_preserved(self):
        self.put("source.txt", "Owner source edits\n")
        self.assertEqual([], self.check()["blockers"])
        self.assertEqual("Owner source edits\n", (self.root / "source.txt").read_text())

    def test_binary_untracked_file_blocks_without_reading(self):
        self.put("photo.jpg", b"synthetic binary marker")
        with mock.patch.object(Path, "open", side_effect=AssertionError("content must not be opened")):
            detail = checked_local_file(self.root, "photo.jpg")
        self.assertEqual("UNHASHED", detail["kind"])
        self.assertIn("OWNER_CONTENT_NOT_SAFELY_SNAPSHOTTED", self.codes())

    def test_sensitive_text_path_is_not_hashed(self):
        self.put("secrets/settings.json", "synthetic only\n")
        self.assertEqual("UNHASHED", checked_local_file(self.root, "secrets/settings.json")["kind"])

    def test_symbolic_link_is_not_followed(self):
        link = self.root / "notes.md"
        try:
            link.symlink_to("source.txt")
        except OSError:
            self.skipTest("OS does not permit symlink fixture")
        self.assertEqual("SYMLINK_OR_REPARSE_POINT", checked_local_file(self.root, "notes.md")["reason"])
        self.assertIn("OWNER_CONTENT_NOT_SAFELY_SNAPSHOTTED", self.codes())

    def test_previous_snapshot_detects_content_change_with_same_status(self):
        self.put("notes.md", "first\n")
        first = self.check()
        self.put("notes.md", "later\n")
        self.assertIn("OWNER_SNAPSHOT_CHANGED_SINCE_PREVIOUS_CHECK", self.codes(previous=first))

    def test_identical_previous_snapshot_passes(self):
        self.put("notes.md", "unchanged\n")
        self.assertEqual([], self.check(previous=self.check())["blockers"])

    def test_wrong_previous_scope_fails(self):
        previous = self.check()
        previous["candidate"] = self.base
        with self.assertRaisesRegex(GateError, "WRONG_SCOPE"):
            self.check(previous=previous)

    def test_snapshot_changes_during_check_block(self):
        first = owner_snapshot(self.owner)
        second = dict(first, index_sha256="changed")
        with mock.patch("p23b_landing_preflight.owner_snapshot", side_effect=[first, second]):
            self.assertIn("OWNER_CHANGED_DURING_PREFLIGHT", self.codes())

    def test_main_remote_movement_blocks(self):
        self.command("update-ref", "refs/remotes/origin/main", self.candidate)
        with self.assertRaisesRegex(GateError, "REFS_MOVED"):
            self.check()

    def test_candidate_remote_movement_blocks(self):
        self.command("update-ref", "refs/remotes/origin/candidate", self.base)
        with self.assertRaisesRegex(GateError, "REFS_MOVED"):
            self.check()

    def test_owner_on_other_branch_is_blocking(self):
        self.command("checkout", "-b", "owner-other")
        self.assertIn("OWNER_NOT_ON_MAIN", self.codes())

    def test_owner_head_divergence_is_blocking(self):
        self.put("owner.txt", "Owner new commit\n")
        self.commit()
        self.assertIn("OWNER_HEAD_NOT_EXPECTED_MAIN", self.codes())

    def test_wrong_origin_fails_without_disclosing_url(self):
        self.command("remote", "set-url", "origin", "https://example.invalid/private")
        with self.assertRaisesRegex(GateError, "^ORIGIN_MISMATCH$"):
            self.check()

    def test_same_worktree_is_not_allowed(self):
        with self.assertRaisesRegex(GateError, "SEPARATE_LINKED"):
            audit(self.owner, self.owner, self.candidate, self.base, "refs/remotes/origin/candidate", "manifest.json", 1)

    def test_hidden_index_flags_block(self):
        self.command("update-index", "--assume-unchanged", "source.txt")
        self.put("source.txt", "hidden edits\n")
        self.assertIn("OWNER_HIDDEN_INDEX_FLAGS", self.codes())
        self.command("update-index", "--no-assume-unchanged", "source.txt")
        self.command("update-index", "--skip-worktree", "source.txt")
        self.assertIn("OWNER_HIDDEN_INDEX_FLAGS", self.codes())

    def test_merge_in_progress_blocks(self):
        (self.root / ".git/MERGE_HEAD").write_text(self.candidate + "\n")
        self.assertIn("OWNER_GIT_OPERATION_IN_PROGRESS", self.codes())

    def test_filter_is_refused_before_execution(self):
        self.command("config", "filter.external.clean", "exit 99")
        with self.assertRaisesRegex(GateError, "CUSTOM_GIT_FILTER"):
            self.check()

    def test_filter_gate_reads_repository_scope_not_system_scope(self):
        with mock.patch.object(self.owner, "run", wraps=self.owner.run) as run:
            owner_snapshot(self.owner)
        config_calls = [call.args for call in run.call_args_list if call.args[:1] == ("config",)]
        self.assertTrue(any(args[1:2] == ("--local",) for args in config_calls))

    def test_preflight_never_updates_owner_index_or_refs(self):
        index = self.root / ".git/index"
        self.put("notes.md", "Owner change\n")
        before_index, before_head = index.read_bytes(), self.command("rev-parse", "HEAD")
        self.check()
        self.assertEqual(before_index, index.read_bytes())
        self.assertEqual(before_head, self.command("rev-parse", "HEAD"))

    def test_missing_owner_file_is_a_snapshot_state_not_a_read_failure(self):
        (self.root / "source.txt").unlink()
        result = self.check()
        self.assertEqual("MISSING", result["owner_snapshot"]["entries"][0]["kind"])

    def test_snapshot_oversized_file_is_not_hashed(self):
        self.put("notes.md", b"x" * 2_000_001)
        self.assertEqual("TEXT_TOO_LARGE", checked_local_file(self.root, "notes.md")["reason"])


class PortableTests(unittest.TestCase):
    def test_tree_aliases_and_windows_unsafe_names(self):
        for paths in [["a.md", "A.md"], ["A", "a/child.md"], ["notes.md."], ["CON.txt"], ["dir/LPT1"]]:
            with self.assertRaises(GateError):
                portable_tree(dict.fromkeys(paths))
        portable_tree(dict.fromkeys(["docs/a.md", "docs/b.md"]))


if __name__ == "__main__":
    unittest.main()
