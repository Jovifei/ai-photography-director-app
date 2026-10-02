"""Render the milestone overview from its single progress ledger."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/project_progress.json"
OUTPUT = ROOT / "docs/PROJECT_PROGRESS.md"
STATUSES = {"done", "pending", "blocked", "not_run"}


def render(data: dict) -> str:
    phases = data["phases"]
    if len({p["id"] for p in phases}) != len(phases):
        raise ValueError("duplicate phase id")
    rows = ["# 项目进度总览", "", f"更新时间：{data['updated_at']}",
            f"总体规划状态：**{data['planning_status']}**", "",
            "由 project_progress.json 自动生成。路线图尚待远端审定；每条进度表示已有证据的检查点完成数，不代表摄影质量或整体产品完成百分比。", "",
            f"候选：[PR #22]({data['candidate_pr']})；证据基准 `{data['source_revision']}`。", "",
            "| 大阶段 | 进度条 | 检查点 | 状态 | 首发要求 |", "|---|---|---|---|---|"]
    for phase in phases:
        checks = phase["checks"]
        if not checks or any(c["status"] not in STATUSES or not c["evidence"] for c in checks):
            raise ValueError(f"invalid checkpoint evidence: {phase['id']}")
        done = sum(c["status"] == "done" for c in checks)
        fill = 10 * done // len(checks)
        bar = "█" * fill + "░" * (10 - fill)
        rows.append(f"| {phase['id']} {phase['name']} | {bar} | {done}/{len(checks)} | {phase['state']} | {phase['required']} |")
    rows += ["", f"**下一动作：** {data['next_action']}", "", "[大阶段实施步骤与完成标准](PROJECT_MASTER_ROADMAP.md)"]
    for phase in phases:
        rows += ["", f"## {phase['id']} {phase['name']}", "", "| 检查点 | 状态 | 证据或阻塞 |", "|---|---|---|"]
        for check in phase["checks"]:
            cells = [check["name"], check["status"].upper(), check["evidence"]]
            if any("\n" in cell or "|" in cell for cell in cells):
                raise ValueError("checkpoint text must be a single table cell")
            rows.append("| " + " | ".join(cells) + " |")
    rows += ["", "更新命令：`python scripts/render_project_progress.py`；一致性检查：`python scripts/render_project_progress.py --check`。"]
    return "\n".join(rows) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = render(json.loads(SOURCE.read_text(encoding="utf-8")))
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != expected:
            print("PROGRESS_OUT_OF_DATE: run python scripts/render_project_progress.py")
            return 1
        print("PASS: progress overview agrees with the milestone ledger")
    else:
        OUTPUT.write_text(expected, encoding="utf-8", newline="\n")
        print("Updated docs/PROJECT_PROGRESS.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
