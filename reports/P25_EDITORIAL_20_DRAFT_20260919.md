# P25 20 条摄影知识编辑底稿报告

状态：`P25_EDITORIAL_20_DRAFT_READY_FOR_HUMAN_REVIEW`。

## 基线与提交

- P24 已独立审查并安全落地：`main=1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`。
- P25 分支：`codex/p25-real-20-knowledge-20260919`。
- 内容/校验源码提交：`a7c5376ff084486dadcdcfaa92ae53621ab381a8`。
- 内容 Git blob SHA-256：`1670f7101120261f88759bb390d896c34b194d07cbfdcf0eb4defce2c95a275e`。

## 已完成

- 编写 20 条原创中文人像摄影知识底稿，稳定 ID 为 `PKB-PORTRAIT-001` 至 `020`。
- 每条具备 v1 消费合同要求的九个摄影字段和独立脱敏 `source_evidence_id`。
- 新增 fail-closed 校验器；它只接受精确 20 条、稳定顺序、完整字段和 PENDING 状态。
- P25 测试 `7/7` PASS；P23C 回归 56 个方法中 `55 PASS / 1 SKIPPED`，skip 原因为 Windows 当前无符号链接权限，未计作 PASS。
- Python compileall、privacy audit、diff check 通过。

## 未完成与边界

- 内容、权利、隐私三项真人审核均 `NOT_RUN`；当前不是 `P25_REAL_20_CONTENT_REVIEWED`。
- 没有声称或伪造 `PIPELINE` provenance，没有生成 APPROVED 回执或候选 ZIP。
- P23C `PUBLIC_CANDIDATE`、P26 App 信任、Qwen/LAN、真实照片、实体设备、Pilot 和发布均 `NOT_RUN`。
- 此分支可以接受内容审查意见；未经真人逐条批准，不得合入生产知识或授权 App 导入。
