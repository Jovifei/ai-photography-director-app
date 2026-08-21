# P21 Lessons

- Bundle 完整性字段必须先冻结跨语言 canonical bytes 再接入 Android；只有 64 位哈希格式校验不构成内容完整性证明。
- 夜间知识条目的 opaque ID 与 App 随机照片 ID 没有天然对应关系；没有获批 ID 交接时必须由用户一对一确认，禁止按顺序、文件名或路径静默匹配。
- `READY` 只是状态，不是可信来源证明；进入 Camera Director 还必须持有有效 Provider provenance 或 Knowledge Bundle provenance。
- Jovi 明确要求“本轮不独立审查”时，仍需完成实现层测试和资格门禁，但最终状态必须标注为未独立审查，不能自行扩大证明层级。

- 系统 Picker 的“唯一合成缩略图”安全断言必须在恢复出厂的专用 AVD 上先于会保存测试图片的另存为用例运行；不要为了通过测试而选择任意媒体。
- `adb shell am instrument` 的 shell 退出码不能单独证明成功；资格脚本必须同时解析预期的 `OK (N tests)` 结果。
- Release 产物只有在运行时代码已提交后，`source_sha` 才能成为可审查绑定；预提交构建只能作为非最终预检。
- 通过嵌套 PowerShell 调用 Gradle 时要显式继承 `ANDROID_HOME`；缺少 SDK 前置不应被误判为签名或产品失败。
- 独立审查必须在固定远端 SHA 的干净 detached worktree 中执行；其 `PASS` 只能覆盖实际复跑的静态、签名和 API 35 层，不得外推为 Qwen/LAN、夜间 Pipeline、五人试点、main 合并或发布完成。
- P22 implementation qualification 通过不等于 Pipeline artifact 已接入：离线 Bundle 只能提供逐张、带 provenance 的 READY，项目级汇总和夜间管线仍保持独立授权边界。
- 手机配置指南中的命令必须从当前候选仓库目录解析脚本，不能硬编码已经替代的旧 worktree 路径。
- Qwen 激活前置要区分模型可用与网络可用：`P20_PILOT_SESSION_BLOCKED_NETWORK_PROFILE` 时保持服务/TLS/防火墙全未启动，不用模型检查结果冒充 LAN 可用。
