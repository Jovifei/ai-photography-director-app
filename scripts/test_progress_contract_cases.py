#!/usr/bin/env python3
"""Executable stdlib regression tests for progress contract failures."""
from __future__ import annotations

import copy
import importlib.util
import unittest

spec = importlib.util.spec_from_file_location("contract", "scripts/validate_progress_contract.py")
contract = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(contract)

BASE = {
    "checkpoint_contract": "progress-contract-v1",
    "source_revision": "a" * 40,
    "planning_status": "PENDING",
    "phases": [
        {"id": key, "checks": [{"name": name, "status": "pending", "evidence": "fixture"} for name in names]}
        for key, names in contract.EXPECTED_PHASES.items()
    ],
}


class ProgressContractTest(unittest.TestCase):
    def test_valid_contract(self):
        contract.validate(BASE)

    def test_wrong_sha_rejected(self):
        data = copy.deepcopy(BASE)
        data["source_revision"] = "bad"
        with self.assertRaises(ValueError):
            contract.validate(data)

    def test_removed_checkpoint_rejected(self):
        data = copy.deepcopy(BASE)
        data["phases"][0]["checks"].pop()
        with self.assertRaises(ValueError):
            contract.validate(data)

    def test_renamed_checkpoint_rejected(self):
        data = copy.deepcopy(BASE)
        data["phases"][0]["checks"][0]["name"] = "替换历史身份"
        with self.assertRaises(ValueError):
            contract.validate(data)

    def test_duplicate_phase_rejected(self):
        data = copy.deepcopy(BASE)
        data["phases"].append(copy.deepcopy(data["phases"][0]))
        with self.assertRaises(ValueError):
            contract.validate(data)

    def test_false_approval_rejected(self):
        data = copy.deepcopy(BASE)
        data["planning_status"] = "APPROVED"
        with self.assertRaises(ValueError):
            contract.validate(data)


if __name__ == "__main__":
    unittest.main()
