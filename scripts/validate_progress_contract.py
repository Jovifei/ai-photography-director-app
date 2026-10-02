#!/usr/bin/env python3
"""Validate milestone progress identity and approval contracts."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEDGER = ROOT / "docs/project_progress.json"
EXPECTED_SOURCE_RE = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_PHASES = {
    "M1": ("产品目标与 Android 首发边界", "PKB1 消费端合同", "App/生产端责任及交接要求", "远端总路线图审定"),
    "M2": ("项目/参考图/指导界面", "拍摄/成片库/重拍/导出", "阶段级合成/API35远端资格", "当前候选本地JVM/构建/Debug lint", "当前候选本机API35复验", "整合PR远端复核与主线接收"),
    "M3": ("消费者交接要求", "生产端原始向量/版本/摘要", "双端canonical摘要兼容", "App明确绑定/替换/拒绝无写入兼容", "端到端来源与逐图状态交接"),
    "M4": ("Provider/READY权限边界", "评价协议与合成工具", "首发AI路由决定", "具体路线权利/运输/资源条件", "最小运行与单图真实结果", "实测质量/延迟/失败行为", "可信READY接线与降级"),
    "M5": ("人工审核工具与说明", "当前候选物理手机验证", "真实照片与摄影指导实用性", "TalkBack/大字体/设备相机差异", "实测问题修复闭环", "受控试点与观察回执"),
    "M6": ("已有交付工具和流程", "当前候选外部签名身份资格", "已审查主线与发布决定", "签名包安装升级/数据保存", "回滚与版本支持追溯", "发布范围与上线回执"),
}
EXPECTED_SOURCE_VERSION = "progress-contract-v1"


def fail(message: str) -> None:
    raise ValueError(message)


def validate(data: dict) -> None:
    if not isinstance(data, dict) or type(data.get("version")) is not int or data["version"] != 1:
        fail("progress ledger version must be 1")
    if data.get("checkpoint_contract") != EXPECTED_SOURCE_VERSION:
        fail("checkpoint contract version mismatch")
    source = data.get("source_revision")
    if not isinstance(source, str) or not EXPECTED_SOURCE_RE.fullmatch(source):
        fail("source_revision must be exact 40 lowercase hex characters")
    if data.get("planning_status") not in {"APPROVED", "PENDING_REMOTE_PLANNING"}:
        fail("unknown planning status")
    phases = data.get("phases")
    if not isinstance(phases, list) or len(phases) != 6:
        fail("phase count mismatch")
    if any(not isinstance(p, dict) for p in phases):
        fail("phase must be an object")
    if tuple(p.get("id") for p in phases) != tuple(EXPECTED_PHASES):
        fail("phase identity/order mismatch")
    seen = set()
    for phase in phases:
        pid = phase.get("id")
        if pid in seen or pid not in EXPECTED_PHASES:
            fail("phase identity mismatch")
        seen.add(pid)
        checks = phase.get("checks")
        if not isinstance(checks, list) or any(not isinstance(c, dict) for c in checks):
            fail(f"{pid} checkpoints must be objects")
        names = tuple(c.get("name") for c in checks)
        if names != EXPECTED_PHASES[pid]:
            fail(f"{pid} checkpoint identity changed")
        for check in phase["checks"]:
            if check.get("status") not in {"done", "pending", "blocked", "not_run"}:
                fail(f"invalid checkpoint status {pid}/{check.get('name')}")
            evidence = check.get("evidence")
            if not isinstance(evidence, str) or not evidence.strip():
                fail(f"missing evidence {pid}/{check.get('name')}")
            if any(char in evidence for char in ("\n", "\r", "|")):
                fail(f"evidence must fit a single Markdown table cell: {pid}/{check['name']}")
    if data.get("planning_status") == "APPROVED":
        m1 = next(p for p in phases if p["id"] == "M1")
        approval = next((c for c in m1["checks"] if c["name"] == "远端总路线图审定"), None)
        if not approval or approval.get("status") != "done":
            fail("APPROVED requires M1 roadmap approval evidence")
        receipt = data.get("roadmap_approval")
        if not isinstance(receipt, dict) or receipt.get("reviewed_source_revision") != source:
            fail("APPROVED requires a receipt bound to source_revision")
        review = receipt.get("review_commit")
        if not isinstance(review, str) or not EXPECTED_SOURCE_RE.fullmatch(review):
            fail("approval review_commit must be exact 40 lowercase hex characters")
        if review not in approval["evidence"]:
            fail("approval checkpoint must cite its review_commit")


if __name__ == "__main__":
    try:
        validate(json.loads(LEDGER.read_text(encoding="utf-8")))
    except Exception as exc:
        print(f"PROGRESS CONTRACT FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
    print("PASS: progress checkpoint contract")
