# P23A-R1 最终本地接力

目标状态：`P23A_R1_ANDROID_REQUALIFIED_AWAITING_INDEPENDENT_REVIEW`

## 当前候选

- 分支：`codex/p23a-r1-review-remediation-20260913`
- 基线：`be040188c93d7613d9433a23e0d1f5d640e317f1`
- A：`7968ef3a0314120e60d4ad0c5116fc49f449df7b`
- B：`a874ec266341db67203892f69096f37a57866446`
- findings 修复代码：`c0a2cc387d5ce95858b4c98bb7cb23828f3a3de0`
- summary 读取失效测试：`0f7b42c9d052b1d745fa9add5ebc42568d1f26bc`
- 最终候选：本接力文档提交后的实际 branch HEAD；不得使用旧 `be040188` 作为最终 SHA。

## 已完成

- 两个 P1：分析 attempt 归属/Bundle 覆盖保护、提交异常 OutcomeUnknown 与取消传播。
- 两个 P2：同步父子返回 gate、真实 Room/Coordinator/ViewModel/UI/迁移/UTF-8 测试覆盖。
- R1 与审查缺口 Android 测试：28/28（10 类，含 Provider READY 集合、failed count、inputDigest 和 summary Flow 失效）。
- 既有 Android 回归：38/38（15 classes）。
- JVM：130/130；Debug/Test build、Debug/Release lint（排除签名 guard）通过。
- 专用 API35 emulator 官方 Picker、字体、Local/D2D 通过。

## 明确边界

- 标准 Gradle 任务被缺失的 `PHOTOAI_RELEASE_*` 外部签名输入阻断；未生成密钥。
- 未运行 Qwen、真实照片、LAN、防火墙、Pipeline、生产 Bundle、实体设备、Cloud、iOS。
- OS 级 process death、系统字体截图和独立 Reviewer 仍为 `NOT_RUN`。
- 不修改 main，不覆盖旧验证分支，不把本地验证写成独立审查 PASS。

## Reviewer 入口

Reviewer 必须在新的 clean detached worktree 审查最终实际 HEAD，重新读取：

- `AGENTS.md`
- `reports/P23A_R1_ANDROID_REQUALIFICATION_20260913.md`
- `docs/handoff/P23A_R1_SOURCE_MANIFEST_20260913.json`
- `docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md`

审查重点：真实 Room commit/rollback/unknown acknowledgement、attempt token 并发归属、旧结果迟到、同步 Back、Bundle-only summary 禁止伪造、28 个 R1/审查缺口测试是否命中真实实现。

独立审查结论只能基于新 SHA 的新鲜证据；不得复用 P22/P23A 历史 PASS。

## Review findings follow-up

Review SHA `724b6227370ba3e71437c2e50e1957abb536a38a` 的 P1/P2 findings 已继续修复：

- 旧 summary migration/read-time Provider source + digest 校验；Bundle-only summary 不再可见。
- Local LAN cancellation propagation；不再把取消压成 `UNAVAILABLE`。
- OpenDocument 使用真实 isolated Room apply；迟到 Provider 删除链路使用真实 Repository。
- Photo Picker fallback 不依赖唯一 thumbnail，并用合成 aspect ratio 防止误选残留媒体。
- Local LAN pair/analyze/summary 取消异常继续传播，并由真实 OkHttp cancellation 测试覆盖。
- summary 测试新增真实 Provider READY 集合、failed count、inputDigest 和 Flow 失效场景。
- 最终 manifest 共 27 个文件，区分 Git clean/index blob、LF SHA-256 与 Windows CRLF 工作树域。

新鲜 findings coverage（代码提交 `0f7b42c9d052b1d745fa9add5ebc42568d1f26bc`）：`28/28` Android tests；既有回归 `38/38`；官方 Picker、100%/200% 字体、LocalTransport、D2D 均 PASS。新 SHA 尚待独立 Reviewer 复审。
