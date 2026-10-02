"""Behavioral regression tests for real ledgers, CLI gates and privacy rules."""
from __future__ import annotations
import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("contract", ROOT / "scripts/validate_progress_contract.py")
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)
BASE = json.loads((ROOT / "docs/project_progress.json").read_text(encoding="utf-8"))

class ProgressContractTest(unittest.TestCase):
    def reject(self, mutate):
        data = copy.deepcopy(BASE)
        mutate(data)
        with self.assertRaises(ValueError):
            contract.validate(data)

    def test_real_ledger_accepted(self):
        contract.validate(BASE)

    def test_empty_phases_rejected(self):
        self.reject(lambda d: d.update(phases=[]))

    def test_wrong_sha_rejected(self):
        for value in ("bad", "A" * 40, 123, None):
            with self.subTest(value=value):
                self.reject(lambda d: d.update(source_revision=value))

    def test_removed_checkpoint_rejected(self):
        self.reject(lambda d: d["phases"][0]["checks"].pop())

    def test_renamed_checkpoint_rejected(self):
        self.reject(lambda d: d["phases"][0]["checks"][0].update(name="replacement identity"))

    def test_duplicate_checkpoint_rejected(self):
        self.reject(lambda d: d["phases"][0]["checks"].__setitem__(1, copy.deepcopy(d["phases"][0]["checks"][0])))

    def test_duplicate_phase_rejected(self):
        self.reject(lambda d: d["phases"].__setitem__(1, copy.deepcopy(d["phases"][0])))

    def test_reordered_phases_rejected(self):
        self.reject(lambda d: d["phases"].reverse())

    def test_unknown_version_rejected(self):
        self.reject(lambda d: d.update(checkpoint_contract="unknown"))

    def test_false_approval_rejected(self):
        self.reject(lambda d: d["phases"][0]["checks"][-1].update(status="pending"))

    def test_missing_approval_receipt_rejected(self):
        self.reject(lambda d: d.pop("roadmap_approval"))

    def test_approval_for_different_source_rejected(self):
        self.reject(lambda d: d["roadmap_approval"].update(reviewed_source_revision="0" * 40))

    def test_uncited_approval_commit_rejected(self):
        self.reject(lambda d: d["roadmap_approval"].update(review_commit="0" * 40))

    def test_pending_planning_without_receipt_accepted(self):
        data = copy.deepcopy(BASE)
        data["planning_status"] = "PENDING_REMOTE_PLANNING"
        data.pop("roadmap_approval")
        data["phases"][0]["checks"][-1]["status"] = "pending"
        contract.validate(data)

    def test_invalid_evidence_rejected(self):
        for value in ("", " ", 123, "row\nbreak", "cell|break"):
            with self.subTest(value=value):
                self.reject(lambda d: d["phases"][0]["checks"][0].update(evidence=value))

class ProgressCliTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="photoai-progress-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        (self.root / "docs").mkdir()
        for name in ("validate_progress_contract.py", "render_project_progress.py", "prepush_privacy_audit.py"):
            shutil.copyfile(ROOT / "scripts" / name, self.root / "scripts" / name)
        (self.root / "docs/project_progress.json").write_text(json.dumps(BASE, ensure_ascii=False), encoding="utf-8")
        env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        subprocess.run(["git", "init", "-q", str(self.root)], env=env, check=True, capture_output=True)
        self.assertEqual(0, self.run_script("render_project_progress.py").returncode)

    def run_script(self, name, *args):
        return subprocess.run([sys.executable, str(self.root / "scripts" / name), *args], cwd=self.root, text=True, capture_output=True)

    def test_fresh_render_and_privacy_pass(self):
        self.assertEqual(0, self.run_script("render_project_progress.py", "--check").returncode)
        self.assertEqual(0, self.run_script("prepush_privacy_audit.py").returncode)

    def test_stale_output_rejected(self):
        (self.root / "docs/PROJECT_PROGRESS.md").write_text("stale", encoding="utf-8")
        result = self.run_script("render_project_progress.py", "--check")
        self.assertEqual(1, result.returncode)
        self.assertIn("PROGRESS_OUT_OF_DATE", result.stdout)
        self.assertEqual(1, self.run_script("prepush_privacy_audit.py").returncode)

    def test_empty_ledger_blocked_by_cli_and_prepush(self):
        data = copy.deepcopy(BASE)
        data["phases"] = []
        (self.root / "docs/project_progress.json").write_text(json.dumps(data), encoding="utf-8")
        result = self.run_script("validate_progress_contract.py")
        self.assertEqual(1, result.returncode)
        self.assertIn("phase count mismatch", result.stderr)
        self.assertNotEqual(0, self.run_script("render_project_progress.py").returncode)
        self.assertEqual(1, self.run_script("prepush_privacy_audit.py").returncode)

    def test_existing_privacy_artifact_rules_retained(self):
        for relative in ("weights.onnx", "private.db", "photo.png", ".env.test", "docs/references/repos/fixture.txt"):
            with self.subTest(relative=relative):
                path = self.root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("synthetic rejection fixture", encoding="utf-8")
                try:
                    self.assertEqual(1, self.run_script("prepush_privacy_audit.py").returncode)
                finally:
                    path.unlink()

    def test_existing_secret_assignment_rule_retained(self):
        value = "service_api" + "_key = " + chr(34) + "synthetic_placeholder_12345" + chr(34)
        (self.root / "fixture.txt").write_text(value, encoding="utf-8")
        self.assertEqual(1, self.run_script("prepush_privacy_audit.py").returncode)

if __name__ == "__main__":
    unittest.main()
