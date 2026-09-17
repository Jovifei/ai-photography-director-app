# P23D 本地 Android 资格验证

状态：`P23D_READY_RECOVERY_VALIDATED_AWAITING_INDEPENDENT_REVIEW`

## 绑定

- 基线 main：`9db98bad24347d286ded251aef76108ebe8872ba`
- P23D 远端实现起点：`b84f14b1c45e5d38d8081fdcbf98a18cfc134a89`
- 本地验证分支：`codex/p23d-local-validation-20260916`
- 测试源码提交：`5495d375994653b6ae6a2c739254bf237fad2668`
- 清单提交：`d36fade37ac2bf11988f31def8810b130149a13b`
- 逻辑证据 ID：`p23d-20260917`

本轮只修复了 P23D 测试源码与现有 `ProjectSummaryRequest` API 的命名参数失配：两处 `readyInputs` 改为实际参数 `readyItems`。没有修改生产 Android、Room、Provider、签名配置或 Owner 文件。

## 源码清单

P23D manifest 以 Git object 为权威域，绑定 source commit `5495d375994653b6ae6a2c739254bf237fad2668`，两项均精确复核通过：

- `P23DReadyStateProcessDeathAndroidTest.kt`：Git blob `33d9f42929ca119e955597557f4d7f38086d882d`，9043 bytes，SHA-256 `5aa7dde4caee21a7237102655e8da53d32faad35d63d9fa71cc91a8dd7196e34`。
- `qualify_p23d_ready_recovery.ps1`：Git blob `c9d935deebc855eea3532043628fe4c237f79d0f`，5961 bytes，SHA-256 `45e4e27c0b61078a56930ac406eb08eace730cee27b02e1f4492aa45534b4993`。

未使用 Windows CRLF 工作树哈希作为 canonical source hash。

## P23D OS 进程恢复

仅使用专用 API 35 emulator，设备 guard 实际通过：emulator serial、`ro.kernel.qemu=1`、SDK 35。所有 ADB 命令显式绑定目标串口，未触碰实体设备。

真实 runner summary：

- cleanup before：PASS；
- prepare Provider-backed READY + summary：PASS；
- MainActivity 启动并确认进程存在：PASS；
- `am force-stop` 后确认旧 PID 消失：`PASS_OS_PROCESS_TERMINATED`；
- 新 instrumentation invocation 启动 MainActivity 并执行 startup reconciliation：PASS；
- verify：PASS；
- cleanup after：PASS；
- summary result：`PASS`，failure code：`NONE`。

3 个 P23D 测试方法均实际通过；外部 force-stop 与 verify 是不同 instrumentation invocation。验证覆盖 READY、`本机 VLM` 来源、Provider provenance、无 knowledge-bundle provenance、scene/director prompt、summary digest/count/model/recommended primary、summary Flow、项目 primary reference 和 last-active reference。

原始日志和模拟器证据留在仓库外的逻辑证据目录 `p23d-20260917`，没有提交仓库。

## 实际验证结果

- 标准 Gradle 命令：`BLOCKED`，`verifyReleaseSigning` 报缺少 `PHOTOAI_RELEASE_STORE_FILE`；没有生成密钥。
- 排除 signing guard 的 Debug APK、AndroidTest APK、JVM、`lintDebug`、`lintRelease`：PASS；本次 no-sign 构建 110 actionable tasks 中 19 executed、91 up-to-date。
- lint：Debug 24 warnings、Release 24 warnings，0 errors。
- JVM：130/130，failure/error/skip 均 0。
- P23D instrumentation：3/3 方法通过。
- P23C Parser/Room：6/6 方法通过。
- R1 findings/regression：28/28 方法通过。
- 既有 Android 回归：38/38 方法通过。
- Bundle contract：12/12；Phase 1.5 contract：75/75；service unittest：5/5。
- Phase 1.5 两个语义 fixture：均 `valid=true`。
- Python compileall：PASS。
- `prepush_privacy_audit.py`：PASS。
- `git diff --check`：PASS。

## Owner 安全

Owner 主树前后均为 `main`、HEAD `bdb04f344241149b32f9412dc23807d6c47a2179`，18 项既有修改/未跟踪文件的相对路径、大小和 SHA-256 均无差异（mismatches 0）。没有修改 Owner 的 `tasks/todo.md`、`tasks/lessons.md` 或其他文件。

## BLOCKED / NOT_RUN

- Release signing、signed APK/AAB：`BLOCKED`，外部 `PHOTOAI_RELEASE_STORE_FILE` 未提供；未生成生产密钥。
- P23D Python unittest pattern：`NO TESTS RAN`，仓库没有 `test_p23d_*.py`，不能计为 PASS。
- 系统字体 100%/200% 截图、host `kotlinc`、备份传输、真实照片/Provider/LAN/TLS、防火墙、Qwen、Pipeline、公共生产知识包、Cloud、iOS、实体设备、OS 进程以外的发布流程：`NOT_RUN`。
- 独立人工审查尚未执行；本报告不构成 main 合入、发布或公共内容授权。

下一步是由独立 Reviewer 审查本地验证分支最终精确 SHA，并由 Owner 决定是否进入后续落地流程。
