# P23B 本地预检与落地证据

状态：`P23B_BLOCKED_WITH_EXACT_GATES`

## 绑定

- 工具分支：`codex/p23b-safe-landing-preflight-20260915`
- 本地验证分支：`codex/p23b-local-validation-20260915`
- 工具修复 source commit：`9db9b1fb41a8b109b427d98fa23d9777562a8ffb`
- 被预检 App 候选：`e4a954b313320944fe751a2388366ee72d3e9083`
- 预期 main：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`
- 逻辑 evidence ID：`p23b-20260915`

P23B 只增加安全预检工具、工具测试和接力文档；未修改 e4a 的 Android/Room/Provider、签名配置、R1 manifest、shared-contract 或 tasks。

## 工具验证

- Windows unittest：`49/49` executed PASS，`0` failure/error，`2` symlink fixture SKIP（当前进程无 symlink 权限）。
- 新增 repository-local filter scope 回归通过；系统 Git LFS 全局定义不再误阻断，仓库本地 filter 仍会阻断。
- Python compileall：PASS。
- Windows junction/reparse 实测：PASS，返回 `UNHASHED / SYMLINK_OR_REPARSE_POINT`，未读取目标正文。
- e4 R1 manifest：`27/27 PASS`。
- P23B manifest：`5/5 PASS`，source commit 绑定 `9db9b1f...`。

## Owner 预检

前后两次报告均为 `BLOCKED`，且 owner snapshot 相同、检查期间未变化。

精确 blocker：

- `OWNER_HEAD_NOT_EXPECTED_MAIN`：Owner 本地 HEAD 为 `bdb04f...`，不是预期 `61a9b26...`。
- `OWNER_PATH_OVERLAP`：候选变更包含 `tasks/lessons.md`、`tasks/todo.md`，与 Owner 未跟踪文件重叠。

此前 18 项 Owner SHA 快照逐项复核：`0 mismatch`。没有修改、清理、暂存或移动 Owner 文件。

## e4 App 有限复验

- 标准 Gradle：`BLOCKED`，缺少 `PHOTOAI_RELEASE_STORE_FILE`；未生成密钥。
- 排除 release signing guard：Debug/Test APK、JVM、Debug/Release lint PASS；JVM `130/130`。
- 官方 LocalTransport：`PASS_LOCAL_TRANSPORT`。
- 官方 D2D：`PASS_D2D_TRANSPORT`，官方单设备自动恢复。
- 系统 `font_scale=1.0/2.0`：导入知识包页面真实截图和按钮可达性 PASS；结束恢复为 `1.0`。
- OS `force-stop`/重启：合成 imported 私有记录的来源、Room、派生文件恢复 PASS。
- READY/provider/source/summary 在 OS process death 后的持久化场景：`NOT_RUN`，现有安全 harness 无法在不改 App 测试边界的情况下提供该场景。
- R1 `28/28`、既有 `38/38` 为 e4 候选的既有独立审查 fresh 结果；本轮未重复宣称为 P23B 新 Android 测试，因工具修复未改变 Android/test harness。

## 未完成与禁止项

- Release signing：`BLOCKED`，外部 `PHOTOAI_RELEASE_STORE_FILE` 缺失。
- READY/source/summary OS process death、host `kotlinc`、系统截图之外的完整人工视觉审查：`NOT_RUN`。
- Qwen、真实照片、真实 LAN/TLS、防火墙、Pipeline、生产 Bundle、Cloud、iOS、实体设备、main merge、公开发布：未运行。

P23B 预检 PASS（工具本身）不等于 merge/release authorization。当前 Owner HEAD/tasks overlap gates 未解除，因此阶段状态保持 `P23B_BLOCKED_WITH_EXACT_GATES`。
