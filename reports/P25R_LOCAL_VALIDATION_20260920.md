# P25R Windows 本地验证报告

状态：`P25R_LOCAL_VALIDATED_AWAITING_HUMAN_REVIEW`。

## 精确绑定

- 基线 main：`1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`
- 远端 P25R 输入：`759e1a9099c82097d566e8b387518683d55f3ad4`
- 本地验证分支：`codex/p25r-local-validation-20260920`
- 本地最终候选：`2ff8afe294a3e0a36df7d545ca903a53adcdecc5`
- 本地 source manifest 绑定的 source commit：`2ff8afe294a3e0a36df7d545ca903a53adcdecc5`
- 逻辑证据 ID：`p25r-20260920`

## 本轮最小修复

Windows 首次本地编译暴露两处 P25R 实际缺陷：

1. `p25_human_review_gate.py` 的 import 行包含字面量 `\\n`，造成 CLI `SyntaxError`。
2. `PackError` 未被 CLI 捕获，安全 I/O 拒绝会泄漏 traceback；测试文件也缺少对应 mock、gate 和异常导入。

已在 `2ff8afe` 修正；没有修改 Bundle v1、Android、Room、Provider、签名配置或 Owner 文件。

## 实际验证结果

| Gate | 实际结果 |
|---|---|
| P25 validator | PASS，20 条；PENDING；content/rights/privacy/public_candidate 全 false |
| P25R unittest | PASS，21/21 |
| P23C unittest | 55 PASS、1 SKIPPED、0 failure/error，共 56；skip 为 Windows symlink privilege unavailable |
| `compileall -q scripts` | PASS |
| `prepush_privacy_audit.py` | PASS |
| `git diff --check` | PASS |
| 负路径 | 16/16 BLOCKED：URL、URI、file/content、Windows path、sdcard/storage、traversal、BOM、surrogate、U+2028/U+2029、duplicate key、错误 ID、重复 source evidence、缺字段 |
| Windows 安全 I/O | hardlink、junction/reparse、existing output、repo-inside、UNC、文件中途变化均 BLOCKED；symlink 因权限 SKIPPED |
| PENDING 模板 | 仓库外模板生成 PASS；20 条三项 PENDING、20 evidence null；直接 validate-review exit 2，无 traceback |
| Git object manifest | 6/6 OID、byte count、SHA-256 PASS |

## 未完成边界

- 真人内容/权利/隐私审核：`NOT_RUN`，实际完成数量 `0/20`。
- 未生成 APPROVED review receipt；未运行 `validate-review` 成功路径。
- 未导出 curated editorial Pipeline handoff；未运行真实 Pipeline release。
- 未声明 `source.origin=PIPELINE`，未生成 Android 可导入 Bundle。
- P26 trust/signature、Qwen、真实照片、LAN、防火墙/TLS、实体设备、Cloud、iOS、公开发布：`NOT_RUN`。

## 下一步

真人逐条审阅 `PKB-PORTRAIT-001` 至 `020`，提供真实 `review_id`、`reviewer_id`、总体 rights evidence 和 20 个唯一 evidence ID。任何正文修改都必须重新提交、重新计算 corpus SHA 并重新审核。只有 20/20 三项 APPROVED 后，才可运行 `validate-review` 并导出 `photoai.curated-editorial-input.v1`；该导出仍不是 Android Bundle，也不授予导入或发布权。
