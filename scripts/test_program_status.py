#!/usr/bin/env python3
"""Small standard-library tests for the T5 status validator."""

from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from validate_program_status import StatusError, read_status, validate


ROOT = Path(__file__).resolve().parents[1]


class ProgramStatusTests(unittest.TestCase):
    def make_fixture(self) -> tuple[Path, dict]:
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        for relative in (
            "README.md",
            "tasks/todo.md",
            "docs/CURRENT_PROGRAM_STATUS.md",
            "docs/current_program_status.v1.json",
            "docs/NEXT_GATE_MATRIX.md",
            "docs/PROGRAM_HANDOFF_REFERENCE.md",
            "docs/00_ANDROID_FIRST_EXECUTION_PLAN.md",
            "docs/01_CAMERAX_AND_POSE_SPIKE.md",
            "docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md",
            "docs/UI1_P1B_ENGINEERING_COMPLETION_PLAN.md",
            "docs/P25R_OWNER_FINAL_REVIEW_GUIDE.md",
            "docs/ANDROID_BETA_PHONE_SETUP.md",
            "docs/BETA_USER_GUIDE.md",
        ):
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        return root, {"temp": temp}

    def test_repository_status_is_valid(self) -> None:
        validate(ROOT)

    def test_rejects_delivery_head_drift(self) -> None:
        root, holder = self.make_fixture()
        try:
            status_path = root / "docs" / "current_program_status.v1.json"
            status = read_status(ROOT)
            status["delivery_head"] = "0" * 40
            status_path.write_text(json.dumps(status), encoding="utf-8")
            with self.assertRaises(StatusError):
                validate(root)
        finally:
            holder["temp"].cleanup()

    def test_rejects_promoted_external_gate(self) -> None:
        root, holder = self.make_fixture()
        try:
            status_path = root / "docs" / "current_program_status.v1.json"
            status = read_status(ROOT)
            status["external_gates"]["qwen_private_lan"] = "PASS"
            status_path.write_text(json.dumps(status), encoding="utf-8")
            with self.assertRaises(StatusError):
                validate(root)
        finally:
            holder["temp"].cleanup()

    def test_rejects_missing_review_limit(self) -> None:
        root, holder = self.make_fixture()
        try:
            path = root / "docs" / "CURRENT_PROGRAM_STATUS.md"
            text = path.read_text(encoding="utf-8").replace("C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE", "REVIEW_UNSPECIFIED")
            path.write_text(text, encoding="utf-8")
            with self.assertRaises(StatusError):
                validate(root)
        finally:
            holder["temp"].cleanup()

    def test_accepts_historical_text_with_current_pointer(self) -> None:
        root, holder = self.make_fixture()
        try:
            path = root / "docs" / "P25R_OWNER_FINAL_REVIEW_GUIDE.md"
            text = path.read_text(encoding="utf-8") + "\nHistorical P24 note: Qwen was not connected.\n"
            path.write_text(text, encoding="utf-8")
            validate(root)
        finally:
            holder["temp"].cleanup()

    def test_rejects_false_main_merge_claim(self) -> None:
        self.assert_rejects_forward_claim("README.md", "主线已合并")

    def test_rejects_false_pipeline_claim(self) -> None:
        self.assert_rejects_forward_claim("docs/CURRENT_PROGRAM_STATUS.md", "Pipeline 已集成")

    def test_rejects_false_physical_device_claim(self) -> None:
        self.assert_rejects_forward_claim("docs/NEXT_GATE_MATRIX.md", "实体设备 PASS")

    def assert_rejects_forward_claim(self, relative: str, claim: str) -> None:
        root, holder = self.make_fixture()
        try:
            path = root / relative
            path.write_text(path.read_text(encoding="utf-8") + f"\n{claim}\n", encoding="utf-8")
            with self.assertRaises(StatusError):
                validate(root)
        finally:
            holder["temp"].cleanup()


if __name__ == "__main__":
    unittest.main()
