# P23D — READY / Provider / Summary 持久化进程恢复资格

状态：`P23D_IMPLEMENTED_LOCAL_ANDROID_VALIDATION_PENDING`

P23D 只补齐 P23C 明确保留的一个证据缺口：**既有 Provider-backed READY、来源信息、主参考和项目 summary 在真正的 OS 进程终止后是否仍可由 App 启动路径安全恢复**。

它不是新的分析算法、不是 Qwen/LAN 激活，也不产生真实照片或公共知识内容。

## 基线

- 已合入 `main` 的 P23C 精确 SHA：`9db98bad24347d286ded251aef76108ebe8872ba`
- P23D 分支：`codex/p23d-durable-ready-recovery-20260916`
- P23D source boundary：`2e02bbb7be32cd00f96961d12e4b77c36cba9c3c`

## 新增测试链

`P23DReadyStateProcessDeathAndroidTest` 使用程序生成 JPEG 和本地合成 Provider 结果：

1. 清理专用测试 fixture；
2. 导入一张程序生成参考图；
3. 通过生产 `AnalysisAttempt -> QUEUED -> RUNNING -> persistAnalysis` 写入 Provider READY；
4. 持久化 provider-only 项目 summary；
5. 设置主参考和 `lastActiveReferenceId`；
6. 外部脚本启动真实 `MainActivity` 并确认进程存在；
7. 使用 `adb shell am force-stop com.jovi.photoai`；
8. 明确确认旧进程 PID 已消失；
9. 通过新的 instrumentation invocation 启动 `MainActivity`，走真实 startup reconciliation；
10. 验证 READY、Provider provenance、bundle/source、primary reference、summary digest、summary Flow 和 last-active reference；
11. 清理测试 fixture。

prepare / verify 是两个独立 instrumentation invocation；Activity recreation 不会被当作 OS process death。

## 安全边界

脚本只接受 `emulator-<number>`，并再次验证 `ro.kernel.qemu=1` 与 API 35。所有 adb 命令都绑定显式 serial，不枚举、不安装到实体设备。

证据目录必须位于仓库外且必须预先不存在。脚本不会 wipe AVD、不会卸载用户设备、不会执行 Qwen、网络、签名或发布动作。

`pidof` 在进程不存在时通常返回 exit 1；P23D-02 已将这个“预期不存在”状态从严格 ADB 错误中分离，防止 force-stop 验证误阻断。

## 本地必须验证

网页端没有 Android SDK / emulator，因此以下均为 `NOT_RUN`：

- Debug / AndroidTest APK 编译；
- 3 个 P23D instrumentation 方法；
- `qualify_p23d_ready_recovery.ps1` 的真正 force-stop 流程；
- R1/P23C/既有 Android 回归、JVM、lint、合同、service、privacy、diff。

本地通过后仍不能推导出 Release signing、真实公共内容审核、Pipeline、Qwen/LAN 或发布已完成。
