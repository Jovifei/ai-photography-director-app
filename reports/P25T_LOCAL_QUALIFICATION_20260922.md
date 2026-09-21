# P25T 本地 Android 产品资格验证报告

状态：`P25T_ANDROID_VALIDATED_AWAITING_INDEPENDENT_REVIEW`

## 候选与范围

- 远端分支：`codex/p25s-internal-handoff-validation-20260920`
- 最终交接候选：`69db7bf9b1015d045861a7e8b142539a1614af36`
- 本轮源码提交：`e17d7fe79a4c8e3d9178a249ccf31e61eafc4250`
- 源码 manifest：17 项，绑定 `e17d7fe` Git objects
- Android 设备：fresh 专用 `emulator-5560`，SDK 35，`ro.kernel.qemu=1`，系统启动完成
- Owner 主树：保持开始时状态；未修改 tasks、main 或旧 worktree

## 实际验证

| Gate | 结果 |
|---|---|
| P25 Python | `43/43 PASS` |
| P23C Python | `55 PASS / 1 SKIPPED`（Windows symlink privilege） |
| Python compileall | PASS |
| JVM | `131/131 PASS`，0 failure/error/skip |
| Debug APK / AndroidTest APK | PASS |
| lintDebug | PASS，0 errors / 23 warnings |
| lintRelease | PASS，0 errors / 24 warnings |
| P25T/P25S/P23C/P23R/P22 相关 Android 回归 | `49/49 PASS` |
| P25T 真实 JPEG/缩略图/绑定新增测试 | `2/2 PASS` |
| P25D OS force-stop/restart runner | PASS：prepare、PID start、force-stop PID disappearance、restart verify、cleanup |
| 系统 font_scale 1.0/2.0 | `2/2 PASS`，Compose root screenshots generated and visually checked |
| Git-object source manifest | `17/17 PASS` |
| Privacy / diff check | PASS |

## 真实产品链覆盖

- 程序生成 20 张不同尺寸/颜色 JPEG；生产 `ReferenceRepository.importIntoProject` 导入真实项目。
- 每张源 JPEG 与 app-private derivative 均通过 Bitmap decode。
- 20 条知识以反向顺序绑定，逐条核对 scene、lighting、composition 和 Bundle provenance。
- Compose 看图绑定实际显示 private thumbnails；已绑定目标在另一条目中禁用；解绑后状态移除。
- P25T 原有 9 方法覆盖可选 0/5 preparation、saved state、内容变化重置、完整九项详情、busy guard、外来项目过滤、光线/构图映射。
- P23D runner 证明 OS force-stop 后真实 Room/Repository READY、Provider provenance、ProjectSummary 和 primary/last-active 恢复。
- 100%/200% 截图为真实系统 font_scale 下的 Compose root capture；长标题、来源、准备进度、长说明和卡片布局可见，200% 通过垂直滚动可达。截图只存仓库外，不提交 Git。

## 未完成项

- Release signing：需运行既有外部 signing 流程；本轮未生成新密钥。
- 正式真人内容/权利/隐私审核仍为 `0/20 NOT_RUN`；内部验证/AI 预审不升级为真人批准。
- Pipeline 生产 importer/exporter、正式知识 release、P26 签名与公开发布未执行；N5/N7 仍按前序记录锁定。
- 独立 Reviewer 尚未绑定本轮最终源码 SHA；main 未合入。

外部逻辑证据 ID：`p25t-local-qualification-20260922`。APK、截图、设备日志和 Owner 路径哈希不提交仓库。
