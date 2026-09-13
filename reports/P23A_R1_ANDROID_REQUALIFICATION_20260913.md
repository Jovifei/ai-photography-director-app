# P23A-R1 Android 本地复验与修复验收

状态：`P23A_R1_ANDROID_REQUALIFIED_AWAITING_INDEPENDENT_REVIEW`

## 候选与边界

- 起始审查候选：`be040188c93d7613d9433a23e0d1f5d640e317f1`
- P23A 实现祖先：`43b1dcd23ca0b2ad621c9dfff960ea3e18ac7d3b`
- P22 基线：`902072065efe5bc2da143a26ad05119f46e6ed8c`
- 本地修复分支：`codex/p23a-r1-review-remediation-20260913`
- A 提交：`7968ef3a0314120e60d4ad0c5116fc49f449df7b`
- B 提交：`a874ec266341db67203892f69096f37a57866446`
- findings 修复提交：`c0a2cc387d5ce95858b4c98bb7cb23828f3a3de0`。
- 最终 docs 提交后的审查 SHA 以 Git 实际 HEAD 为准。

本轮未修改 main、旧验证分支或 Pipeline；未启动 Qwen、真实照片、LAN、防火墙、生产 Bundle、实体设备、Cloud、iOS 或公开发布。

## 修复范围

- Room v5→v6 nullable `analysisAttemptId` 迁移，旧 v4→v5 迁移测试扩展到 v6。
- 分析排队、运行、持久化、取消均以真实 Room 事务和 attempt token 重新读取并校验归属。
- Bundle 全量预校验后再写入；数据库未知异常返回 `OutcomeUnknown`，取消异常继续传播。
- Bundle 来源不能覆盖分析任务；旧任务不能覆盖新 token、删除记录或新的 Bundle 结果。
- ViewModel unknown outcome 不展示确定回滚或盲目重试；父级返回和子级 BackHandler 使用同步 `tryLeave()`。
- 项目 summary 写回校验最新 Provider 输入摘要，Bundle-only 结果不冒充 Provider summary。
- 新增/复验 25 个真实 Android 测试方法，使用实际 Repository/Room/Coordinator/ViewModel/Composable；不使用 fake apply 替代。新增 summary 失效、Local cancellation、真实 OpenDocument apply 三条审查缺口链路。

## 新鲜验证结果

| Gate | 结果 |
|---|---|
| 标准 Gradle 命令 | `BLOCKED`：缺少 Owner 外部 `PHOTOAI_RELEASE_*` 签名输入 |
| Debug/Test APK、JVM、lintDebug/lintRelease（排除 release-only signing guard） | `PASS`；JVM `130/130`，0 failure/error/skip |
| P23R/R1 与审查缺口 Android 测试 | `PASS`；10 类、25/25（8 个 P23R 类 + 真实 OpenDocument/Picker 集成） |
| 既有 Android 回归 | `PASS`；15 类、38/38 |
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

- `static-final3/standard-gradle.log`、`static-final3/no-signing-gradle.log`
- `findings-final3/gradle-findings-25.log`、`findings-final3/TEST-findings-25.xml`
- `regression-final4/TEST-regression-38.xml`
- `official-final-findings3/backup-restore-summary.json`
- `P23A_R1_SOURCE_MANIFEST_20260913.json`

## NOT_RUN / BLOCKED

- 签名 APK/AAB：`BLOCKED`，缺少 Owner 外部 keystore；未生成新密钥。
- P20 Qwen/LAN/真实照片分析：`NOT_RUN`，属于明确禁止范围。
- OS 级进程杀死后恢复：`NOT_RUN`；Activity recreation 已有合成测试，但不冒充 OS process death。
- 100%/200% 系统截图：`NOT_RUN`；语义 Gate 已通过，未保存截图。
- 对新候选 SHA 的独立 Reviewer：`NOT_RUN`；上一审查 SHA `724b6227370ba3e71437c2e50e1957abb536a38a` 的 REQUEST_CHANGES 已逐项处理。

## 修复包自检边界

- ZIP 内 23 个文件 SHA-256：全部匹配。
- ZIP 内 `tests/test_apply_repair.py`：当前 Windows 环境 `12` 项中 `9` 项通过；2 项因临时文件 CRLF 与 LF 硬编码断言失败，1 项因创建符号链接需要当前进程特权而失败。未用这些包内测试结果冒充 Android 资格。
- ZIP 内 host Kotlin policy check：`NOT_RUN`，当前环境没有 `kotlinc`；真实 Gradle JVM 已通过 `130/130`。
- 应用脚本 dry-run/write：`DRY_RUN_PASS`、`APPLIED_NOT_COMMITTED` 均已取得；应用没有联网、暂存或覆盖 Owner 文件。

## Owner 安全

Owner 主工作树仍保持原始状态；仓库外快照：

`E:\project\_benchmark_evidence\p23a-r1-20260913\owner-worktree-status-hashes.json`

本轮不合并 main。下一步是对最终精确 SHA 做独立 Review，之后再进行 Owner 文件检查和合入决策。

## Independent review findings follow-up

针对审查候选 `724b6227370ba3e71437c2e50e1957abb536a38a` 的 REQUEST_CHANGES 已在本分支继续处理：

- P1 summary：v5→v6 migration 清除旧 `project_summaries`；Repository 读取时只接受当前 Provider READY 集合、failed count 和 `inputDigest` 均匹配的 summary；新增真实 migration/digest 测试。
- P2 cancellation：Local LAN pair/analyze/summary 明确重新抛出 `CancellationException`；新增真实 Repository 读取私有合成 JPEG + OkHttp cancellation 测试。
- P2 OpenDocument/Repository bypass：P22 OpenDocument 测试调用真实 isolated Room `applyKnowledgeBundle`；迟到分析测试调用真实 `ReferenceRepository.delete`；新增 25 项 findings/R1 Android coverage。
- P2 Photo Picker：fallback 不再要求设备只有一个 thumbnail，并以合成 fixture aspect ratio 验证实际选择了测试媒体；在专用 API35 emulator 上通过。
- P3 manifest：最终 manifest 共 27 个文件，同时记录 Git blob domain 与当前 Windows checkout SHA-256；不把 CRLF 工作树哈希冒充 canonical blob。

审查后续新鲜结果：代码提交 `c0a2cc387d5ce95858b4c98bb7cb23828f3a3de0` 上 R1/审查缺口 Android `25/25`，既有回归 `38/38`；官方 Local/D2D summary 为 PASS。最终候选 SHA 以本轮最后 docs commit 的 Git HEAD 为准。
