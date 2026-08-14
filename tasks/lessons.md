# P21 Lessons

- 系统 Picker 的“唯一合成缩略图”安全断言必须在恢复出厂的专用 AVD 上先于会保存测试图片的另存为用例运行；不要为了通过测试而选择任意媒体。
- `adb shell am instrument` 的 shell 退出码不能单独证明成功；资格脚本必须同时解析预期的 `OK (N tests)` 结果。
- Release 产物只有在运行时代码已提交后，`source_sha` 才能成为可审查绑定；预提交构建只能作为非最终预检。
- 通过嵌套 PowerShell 调用 Gradle 时要显式继承 `ANDROID_HOME`；缺少 SDK 前置不应被误判为签名或产品失败。
- 独立审查必须在固定远端 SHA 的干净 detached worktree 中执行；其 `PASS` 只能覆盖实际复跑的静态、签名和 API 35 层，不得外推为 Qwen/LAN、夜间 Pipeline、五人试点、main 合并或发布完成。
