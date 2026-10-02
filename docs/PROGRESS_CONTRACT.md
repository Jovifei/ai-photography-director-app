# Progress checkpoint contract

## Purpose

Protect the milestone ledger from silent evidence loss.

## Required identity

`docs/project_progress.json` is the only progress source.

`source_revision` must be an exact 40 lowercase hexadecimal Git object SHA.

## Frozen checkpoint identity

The accepted phase contract is:

- M1: 4 checkpoints
- M2: 6 checkpoints
- M3: 5 checkpoints
- M4: 7 checkpoints
- M5: 6 checkpoints
- M6: 6 checkpoints

Total: 34 checkpoints.

Checkpoint names are identities. Removing, renaming, duplicating, or silently replacing checkpoints requires an explicit migration decision, not a normal progress edit.

## Approval rule

`planning_status=APPROVED` requires the M1 roadmap approval checkpoint to be `done` with evidence.

Technical approval does not imply product, legal, device, human, producer, or release approval.

## Local commands

```bash
python scripts/validate_progress_contract.py
python scripts/render_project_progress.py --check
python scripts/prepush_privacy_audit.py
```

## 本地集成与执行合同

进度必须绑定progress-contract-v1，保持M1–M6顺序及34个原始检查点身份。APPROVED要求M1审定done、roadmap_approval.reviewed_source_revision等于source_revision、40位review_commit被M1证据引用。待规划状态为PENDING_REMOTE_PLANNING。证据为非空单行Markdown单元格。

生成器在输出前validate；隐私门通过renderer --check检查进度。原有私人资产/图片/模型/数据库/密钥/参考clone扫描完整保留。运行`python scripts/test_progress_contract_cases.py`执行20项行为测试；用真实账本及隔离CLI进行验证。不得用测试案例清单或内存fixture通过替代当前账本集成结果。

详见reports/PROGRESS_INTEGRITY_20261002.md，保留bc9d3de空账本失败证据。
