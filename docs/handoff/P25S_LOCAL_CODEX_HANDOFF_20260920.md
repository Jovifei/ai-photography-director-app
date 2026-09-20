# P25S 本地接力

当前边界：P25S 内部交接与 Android 本地验证已完成，最终候选等待独立审查。正式 20/20 内容、权利、隐私审核仍为 `NOT_RUN`，Pipeline N5/N7 仍 `LOCKED`。

接力顺序：

1. 从本分支最新远端 SHA 建立隔离工作树，验证 P25S source manifest。
2. 已使用 fresh 专用 API35 emulator `emulator-5560`，核对 serial、SDK=35、`ro.kernel.qemu=1`。
3. 已对该 serial 运行 P25S `2/2`、P23C Parser `4/4`、P23C Room/重启 `2/2`。
4. 已 fresh 重跑 P25/P23C Python、JVM、Debug/Test APK、lint、privacy 和 diff。
5. 对最终精确 SHA 做独立审查；不得把模拟回执、测试 fixture 或 App 侧映射器写成真实 Pipeline provenance。

2026-09-21 先前旧 AVD 的 ADB bridge 阻塞已通过创建 fresh AVD `P25S_API35_Fresh_20260921` 解除。最终 Android 证据绑定 `emulator-5560`；未触碰实体设备。

Pipeline 的 producer 缺口与验收条件见 `reports/P25S_INTERNAL_HANDOFF_AND_PIPELINE_PREFLIGHT_20260920.md`。真实 release 必须由获得授权后的 Pipeline 实现；随后才进入使用独立知识包 signing identity 的 P26。
