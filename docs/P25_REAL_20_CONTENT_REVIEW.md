# P25：第一份 20 条摄影知识人工审核

状态：`P25_EDITORIAL_20_DRAFT_READY_FOR_HUMAN_REVIEW`。

本阶段把闭测需要的第一组 20 条摄影知识整理为原创中文编辑底稿。底稿不是照片分析结果，
没有伪造 `PIPELINE` provenance，也没有生成 P23C `PUBLIC_CANDIDATE` 回执或 ZIP。

## 审核对象

- 文件：`docs/reference/p25_real_20_editorial_draft.v1.json`
- 条目：`PKB-PORTRAIT-001` 至 `PKB-PORTRAIT-020`
- 权利基础声明：`PROJECT_ORIGINAL_EDITORIAL_DRAFT`
- 当前状态：`PENDING_HUMAN_CONTENT_RIGHTS_PRIVACY_REVIEW`
- 每条包含 App v1 所需九个摄影字段和独立 `source_evidence_id`。

自动校验只证明格式、数量、稳定 ID、字段完整性和 PENDING 边界。它不会批准内容、判断摄影建议质量、
认证审核人、授予权利或授权 App 导入。验证入口：

```powershell
python scripts/p25_validate_editorial_corpus.py docs/reference/p25_real_20_editorial_draft.v1.json
python -m unittest discover -s scripts -p "test_p25_*.py" -v
```

## 人工审核表

三列都由真人逐条判断。任何一列不是 `APPROVED`，该条不得进入后续 Bundle。

| reference_id | content_review | rights_review | privacy_review |
|---|---|---|---|
| PKB-PORTRAIT-001 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-002 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-003 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-004 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-005 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-006 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-007 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-008 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-009 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-010 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-011 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-012 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-013 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-014 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-015 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-016 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-017 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-018 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-019 | PENDING | PENDING | PENDING |
| PKB-PORTRAIT-020 | PENDING | PENDING | PENDING |

审核重点：摄影建议是否准确可执行；场景和动作是否安全且不暗示危险站位；文字是否原创、无品牌或受限素材复制；
是否不存在姓名、账号、地址、设备、EXIF、文件路径、媒体哈希等个人或来源定位信息。

## 后续 Gate

1. 真人给出 `review_id`、`reviewer_id` 以及每条对应的脱敏证据 ID，并完成三项审核。
2. 被拒绝条目必须修改后重新审核，不能把本文件中的 PENDING 批量替换为模拟 APPROVED。
3. 只有审核完成且生产来源真实可绑定时，才转换为 `source.origin=PIPELINE` 的 v1 Bundle，并运行 P23C
   `review-template → prepare → verify-candidate`。
4. P23C ZIP 始终是 `UNSIGNED_NOT_FOR_DISTRIBUTION`；App 信任和批准导入属于 P26。

当前未运行 Qwen、Pipeline、真实照片、真实 LAN、实体设备、Cloud 或公开发布。
