"""Render the milestone overview from the single progress ledger."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/project_progress.json"
OUTPUT = ROOT / "docs/PROJECT_PROGRESS.md"
STATUSES = {"done", "pending", "blocked", "not_run"}

sys.path.insert(0, str(ROOT / "scripts"))
from validate_progress_contract import validate  # noqa: E402


def render(data: dict) -> str:
    validate(data)
    rows = ["# 项目进度总览", "", f"更新时间：{data['updated_at']}", f"总体规划状态：**{data['planning_status']}**", "",
            "由 project_progress.json 自动生成。每条进度表示已有证据的检查点完成数，不代表摄影质量或整体产品完成百分比。", "",
            f"候选：[PR #22]({data['candidate_pr']})；证据基准 `{data['source_revision']}`。", "",
            "| 大阶段 | 进度条 | 检查点 | 状态 | 首发要求 |", "|---|---|---|---|---|"]
    for phase in data["phases"]:
        checks = phase["checks"]
        done = sum(c["status"] == "done" for c in checks)
        fill = 10 * done // len(checks)
        rows.append(f"| {phase['id']} {phase['name']} | {'█'*fill}{'░'*(10-fill)} | {done}/{len(checks)} | {phase['state']} | {phase['required']} |")
    rows += ["", f"**下一动作：** {data['next_action']}", "", "[大阶段实施步骤与完成标准](PROJECT_MASTER_ROADMAP.md)"]
    for phase in data["phases"]:
        rows += ["", f"## {phase['id']} {phase['name']}", "", "| 检查点 | 状态 | 证据或阻塞 |", "|---|---|---|"]
        for check in phase["checks"]:
            cells = [check["name"], check["status"].upper(), check["evidence"]]
            rows.append("| " + " | ".join(cells) + " |")
    rows += ["", "更新命令：`python scripts/render_project_progress.py`；一致性检查：`python scripts/render_project_progress.py --check`。"]
    return "\n".join(rows) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = render(json.loads(SOURCE.read_text(encoding="utf-8")))
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != expected:
            print("PROGRESS_OUT_OF_DATE: run render_project_progress.py")
            return 1
        print("PASS: progress overview agrees with milestone ledger")
    else:
        OUTPUT.write_text(expected, encoding="utf-8", newline="\n")
        print("Updated docs/PROJECT_PROGRESS.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
