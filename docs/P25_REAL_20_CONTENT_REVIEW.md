# P25：第一份 20 条摄影知识人工审核

状态：P25_HUMAN_REVIEW_GATE_IMPLEMENTED_AWAITING_HUMAN_DECISIONS。

本阶段把闭测需要的第一组 20 条摄影知识整理为原创中文编辑底稿，并增加严格的真人审核 Gate。底稿不是照片分析结果，没有伪造 PIPELINE provenance，也没有生成 P23C PUBLIC_CANDIDATE 回执或 ZIP。

## 审核对象

- 文件：docs/reference/p25_real_20_editorial_draft.v1.json
- 条目：PKB-PORTRAIT-001 至 PKB-PORTRAIT-020
- 权利基础声明：PROJECT_ORIGINAL_EDITORIAL_DRAFT
- 当前状态：PENDING_HUMAN_CONTENT_RIGHTS_PRIVACY_REVIEW
- 每条包含 App v1 所需九个摄影字段和独立 source_evidence_id。

source_evidence_id 只是稳定的脱敏证据句柄，不等于来源、版权或真实性已经被证明。真人审核仍必须提供独立 review_id、reviewer_id、总体 rights evidence 以及每条独立 evidence ID。

## 自动校验边界

scripts/p25_validate_editorial_corpus.py 只证明：精确 20 条、稳定 corpus/reference/source ID、九个摄影字段完整、UTF-8/控制字符/孤立 surrogate/U+2028/U+2029 合法，以及不包含 URL、URI、文件路径、Android storage path 或 ../ 传输文本；同时状态必须保持 PENDING。

它不会批准内容、判断摄影建议质量、认证审核人、授予权利或授权 App 导入。

验证入口：

    python scripts/p25_validate_editorial_corpus.py docs/reference/p25_real_20_editorial_draft.v1.json
    python -m unittest discover -s scripts -p "test_p25_*.py" -v

## 真人审核 Gate

生成 PENDING 模板：

    python scripts/p25_human_review_gate.py review-template docs/reference/p25_real_20_editorial_draft.v1.json E:\\review\\p25-review.json

模板必须全部是 PENDING，审核身份与 evidence 必须为空，且模板本身必须无法通过 validate-review。

真人逐条完成后：

    python scripts/p25_human_review_gate.py validate-review docs/reference/p25_real_20_editorial_draft.v1.json E:\\review\\p25-review.json

只有 20/20 同时满足 content_review=APPROVED、rights_review=APPROVED、privacy_review=APPROVED，具有非空合法 reviewer/review/evidence ID，且 corpus SHA-256 与源证据绑定完全一致，才可得到状态 P25_REAL_20_CONTENT_REVIEWED。

## 人工审核表

20 条 PKB-PORTRAIT-001 至 020 当前 content_review / rights_review / privacy_review 全部保持 PENDING。

审核重点：摄影建议是否准确、可执行；场景和动作是否安全；文字是否原创、无品牌或受限素材复制；不存在姓名、账号、地址、设备、EXIF、文件路径、媒体哈希等个人或来源定位信息。007 城市行走、008 楼梯、015 低机位、016 高机位、019 夜间城市场景要特别检查安全站位，不能暗示进入车流、楼梯边缘、危险高位或使用不稳定支撑。

## Provenance 关键约束

当前 Photo Knowledge Bundle v1 的 source.origin 只允许 PIPELINE 或 LOCAL_SERVICE。这 20 条是项目原创编辑内容，因此不能直接把它们改写成 source.origin=PIPELINE。

审核通过后，P25R 只能生成 photoai.curated-editorial-input.v1。它是给后续 Pipeline release Gate 的中间交接，不是 Android 可导入 Bundle，并且 app_import_authorized=false、public_distribution_authorized=false，也不包含 Bundle v1 的 contract_version/source/integrity/references 根结构。

只有后续真实 Pipeline release 接收并产出新的 v1 Bundle 后，才可以诚实声明 source.origin=PIPELINE，再进入 P26 trust / signed release Gate。

## 后续 Gate

1. 真人完成 20/20 内容、权利、隐私审核。
2. 被拒绝条目必须修改后重新绑定新的 corpus hash 并重新审核。
3. 通过 p25_human_review_gate.py validate-review。
4. 生成 photoai.curated-editorial-input.v1，交给后续 Pipeline release Gate。
5. Pipeline 必须真正产出 Bundle，不能仅重新命名 editorial JSON。
6. P23C ZIP / App trust / 批准导入仍属于后续独立 Gate。

当前未运行 Qwen、Pipeline、真实照片、真实 LAN、实体设备、Cloud 或公开发布。
