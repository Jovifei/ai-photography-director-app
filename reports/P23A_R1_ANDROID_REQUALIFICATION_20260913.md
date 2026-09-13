# P23A-R1 Android 本地复验与修复验收

状态：`P23A_R1_ANDROID_REQUALIFIED_AWAITING_INDEPENDENT_REVIEW`

## 候选与边界

- 起始审查候选：`be040188c93d7613d9433a23e0d1f5d640e317f1`
- P23A 实现祖先：`43b1dcd23ca0b2ad621c9dfff960ea3e18ac7d3b`
- P22 基线：`902072065efe5bc2da143a26ad05119f46e6ed8c`
- 本地修复分支：`codex/p23a-r1-review-remediation-20260913`
- A 提交：`7968ef3a0314120e60d4ad0c5116fc49f449df7b`
- B 提交：`a874ec266341db67203892f69096f37a57866446`
- C 文档提交后的最终审查 SHA 以 Git 实际 HEAD 为准。

本轮未修改 main、旧验证分支或 Pipeline；未启动 Qwen、真实照片、LAN、防火墙、生产 Bundle、实体设备、Cloud、iOS 或公开发布。

## 修复范围

- Room v5→v6 nullable `analysisAttemptId` 迁移，旧 v4→v5 迁移测试扩展到 v6。
- 分析排队、运行、持久化、取消均以真实 Room 事务和 attempt token 重新读取并校验归属。
- Bundle 全量预校验后再写入；数据库未知异常返回 `OutcomeUnknown`，取消异常继续传播。
- Bundle 来源不能覆盖分析任务；旧任务不能覆盖新 token、删除记录或新的 Bundle 结果。
- ViewModel unknown outcome 不展示确定回滚或盲目重试；父级返回和子级 BackHandler 使用同步 `tryLeave()`。
- 项目 summary 写回校验最新 Provider 输入摘要，Bundle-only 结果不冒充 Provider summary。
- 新增 20 个真实 Android 测试方法，使用实际 Repository/Room/Coordinator/ViewModel/Composable；不使用 fake apply 替代。

## 新鲜验证结果

| Gate | 结果 |
|---|---|
| 标准 Gradle 命令 | `BLOCKED`：缺少 Owner 外部 `PHOTOAI_RELEASE_*` 签名输入 |
| Debug/Test APK、JVM、lintDebug/lintRelease（排除 release-only signing guard） | `PASS`；JVM `130/130`，0 failure/error/skip |
| P23R Android 新增测试 | `PASS`；6 类、20/20 |
| 既有 Android 回归 | `PASS`；14 类、36 项 |
| Room migration v5→v6 | `PASS` |
| System OpenDocument / Photo Picker | `PASS` |
| Capture/export | `PASS` |
| 官方字体 100%/200% | `PASS_SEMANTICS_ONLY` |
| 官方 LocalTransport | `PASS_LOCAL_TRANSPORT` |
| 官方 D2D | `PASS_D2D_TRANSPORT` |
| Bundle contract | `PASS` |
| Phase 1.5 contract | `PASS` |
| Service unittest | `PASS 5/5` |
| Python compileall | `PASS` |
| Privacy audit | `PASS` |
| `git diff --check` | `PASS` |

外部证据目录：

`E:\project\_benchmark_evidence\p23a-r1-20260913\`

关键证据：

- `r1-static-final2.log`
- `r1-instrumentation-final2/summary.json`
- `regression-final/summary.json`
- `official-local-d2d-final2/backup-restore-summary.json`
- `P23A_R1_SOURCE_MANIFEST_20260913.json`

## NOT_RUN / BLOCKED

- 签名 APK/AAB：`BLOCKED`，缺少 Owner 外部 keystore；未生成新密钥。
- P20 Qwen/LAN/真实照片分析：`NOT_RUN`，属于明确禁止范围。
- OS 级进程杀死后恢复：`NOT_RUN`；Activity recreation 已有合成测试，但不冒充 OS process death。
- 100%/200% 系统截图：`NOT_RUN`；语义 Gate 已通过，未保存截图。
- 独立 Reviewer：`NOT_RUN`。

## Owner 安全

Owner 主工作树仍保持原始状态；仓库外快照：

`E:\project\_benchmark_evidence\p23a-r1-20260913\owner-worktree-status-hashes.json`

本轮不合并 main。下一步是对最终精确 SHA 做独立 Review，之后再进行 Owner 文件检查和合入决策。
