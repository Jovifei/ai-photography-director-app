# P25S 本地接力

当前边界：内部交接工程实现已完成，但 API35 emulator ADB 注册失败使新增 Android runtime 测试保持 `BLOCKED`。正式 20/20 内容、权利、隐私审核仍为 `NOT_RUN`，Pipeline N5/N7 仍 `LOCKED`。

接力顺序：

1. 从本分支最新远端 SHA 建立隔离工作树，验证 P25S source manifest。
2. 修复或更换明确专用的 API35 emulator；核对 serial 前缀、SDK=35、`ro.kernel.qemu=1`。
3. 仅对该 serial 运行 `com.jovi.photoai.p25s.P25SInternalHandoffAndroidTest`（2 个方法）与 `com.jovi.photoai.p23c.P23CPythonBundleRoomAndroidTest`（2 个阶段，含真实 force-stop/new instrumentation）。
4. fresh 重跑 P25/P23C Python、JVM、Debug/Test APK、lint、privacy 和 diff。
5. 对最终精确 SHA 做独立审查；不得把模拟回执、测试 fixture 或 App 侧映射器写成真实 Pipeline provenance。

Pipeline 的 producer 缺口与验收条件见 `reports/P25S_INTERNAL_HANDOFF_AND_PIPELINE_PREFLIGHT_20260920.md`。真实 release 必须由获得授权后的 Pipeline 实现；随后才进入使用独立知识包 signing identity 的 P26。
