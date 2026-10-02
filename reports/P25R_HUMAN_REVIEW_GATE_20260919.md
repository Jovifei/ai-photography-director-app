# P25R 真人审核 Gate 远端实现｜2026-09-19

状态：P25R_IMPLEMENTED_LOCAL_VALIDATION_AND_HUMAN_REVIEW_PENDING。

## 基线

- main：1b776ba9932a7fdc96112c8cb85c7258f3f7d6af
- 原 P25 编辑底稿：e06fd01d1282689601b17d1dfecb7cab1cc88773
- 本分支：codex/p25-human-review-gate-20260919
- source boundary：f45618add0a265a5e576eb4c89368a7e0467e371

## 审核结论

P25 原提交的诚实边界正确：20 条内容仍为 PENDING，未伪造真人审核、PIPELINE provenance 或 App 导入授权。
但有两个需要补强的点：

1. 原 editorial validator 未完全复用 P23C 的传输/路径和 Unicode fail-closed 边界。
2. 原文档的下一步容易让执行者直接把原创 editorial 变成 source.origin=PIPELINE；这会把“内容作者/来源”与“Bundle producer”混淆。当前 v1 只允许 PIPELINE/LOCAL_SERVICE，故必须先通过真人审核，再以中间 handoff 交给真实 Pipeline release 产出 Bundle。

## 本轮远端修改

- validator 增加固定 corpus_id、BOM、孤立 surrogate、U+2028/U+2029、URL/URI/path/storage traversal 拒绝。
- 新增 scripts/p25_human_review_gate.py。
- 新增真人 review-template / validate-review / export-pipeline-input 三步 Gate。
- PENDING 模板不能通过 validate-review。
- 真人审核必须具有 review_id、reviewer_id、总体 rights evidence 和 20 条独立 evidence ID。
- 20/20 content/rights/privacy 均 APPROVED 后，才返回 P25_REAL_20_CONTENT_REVIEWED。
- 导出格式固定为 photoai.curated-editorial-input.v1，并明确不是 Android Bundle，app_import_authorized=false。
- 输出复用 P23C no-overwrite / reparse-safe publish 机制，review/export 文件要求写到仓库外。\n- P23C 安全 I/O 的 PackError 已显式映射为 P25R CLI 的 BLOCKED/exit 2，避免 traceback 绕过稳定错误语义。

## 云端验证边界

- 使用与正式脚本相同逻辑的合成 20 条 fixture，21 个 Python unittest 方法通过。
- 对远端实际 P25 corpus 做只读结构扫描：20 条，未发现 URL/URI/path 或被禁止 Unicode 控制字符。
- 没有在完整 Windows clone 上运行本分支。
- 没有真人审核；没有 APPROVED review receipt；没有 Pipeline 执行。
- 没有生成 App 可导入 Bundle、签名知识包或发布授权。

## 下一 Gate

本地 Codex 先在独立 worktree 对真实仓库运行 P25/P23C 回归和 review-template 负路径。
之后由真人逐条审阅 20 条内容；Codex 不得自行把 PENDING 批量改为 APPROVED。
真人审查完成后才允许生成 curated-editorial Pipeline handoff。

P26 trust/signature 仍在这一步之后。
