# P25U 本地 Android 成片资格验证｜2026-09-24

状态：P25U_ANDROID_INDEPENDENT_REVIEW_PASS_AWAITING_OWNER_LANDING。

## 精确绑定

- 原分支：codex/p25s-internal-handoff-validation-20260920
- 本地隔离分支：codex/p25u-local-android-qualification-20260924
- 分支初始远端输入：9df16203512f91c79150058afd02858c9a292bb3
- 本轮交付目标父提交：832a87af933157741bb01ce77a5c54ef8940369c
- P25T 基线：11342a3a441cb03826b85f46f3932a98bd98930f
- P25U 源码最终提交：d26f3cf81c57936da7d144dd17c5258f2d70d66a
- Git-object 清单：docs/handoff/P25U_LOCAL_SOURCE_MANIFEST_20260924.json，24/24 OID、原始字节和 SHA-256 匹配
- 观测 main：1b776ba9932a7fdc96112c8cb85c7258f3f7d6af，本轮未修改

## 本轮实现与最小整改

- 新增默认 App 根路由的真实成片恢复回归：prepare → 外部启动并确认 PID → force-stop → 独立 verify → cleanup。
- 新增真实 CameraX 成片通过 Android CreateDocument 的成功/取消回归。
- 修复导出时序：持久化 reserve 后立即 arm，再通过预注册的 Activity Result launcher 发起选择；回调使用同一 capture token。
- 相机页的保存入口回到成片预览/成片库，不再直接创建未绑定 launcher 的导出请求。
- 原片仍位于 noBackupFilesDir/capture-library；卸载、清除数据、换机不自动保留；外部副本不由 App 跟踪或删除。

## 独立审查整改

首次独立审查为 BLOCKED，指出以下问题；均已整改并等待对新 SHA 复审：

- P1：Activity 重建可能丢失临时导出 token。改为 `rememberSaveable` 保存 token，Activity Result 消费时先清空；新增正式 App 路径的真实 CameraX/系统保存器重建回归。旧实现测试失败，修复后导出测试类 3/3 通过。
- P2：恢复 verify 未确认 prepare 的同一成片。verify 现在读取同一 run marker，核对对应 capture ID、AVAILABLE 状态、原片哈希，并在重开的成片库选择该 ID。
- P2：force-stop/PID 证据此前未在仓库报告中定位。以下摘要绑定仓库外证据；原始 instrumentation 日志保留在逻辑证据目录 `p25u-local-android-qualification-20260924`，不提交运行输出。

最终恢复 run：`2e704f18fd914180b27ee96f1d5c3ab1`；capture ID：`ec62826d26964fa480ad234a827f242e`；目标 `emulator-5554` / API 35 / `ro.kernel.qemu=1`；外部 force-stop 前后 PID 为 `5994 → 空`。prepare、verify、cleanup 各 1/1 PASS；verify 精确打开该 capture 并验证原片 digest。

外部证据 SHA-256：

- `P25UDefaultAppCaptureRecovery.final.prepare.log`: `932c1975a9f799d0d9898b2139874de82b62a5bcfbdc0c215438a989a43e5a39`
- `P25UDefaultAppCaptureRecovery.final.marker.json`: `bd99d21800bfe9e121a92272bec4ef5f4bd205aca23074775317e3e18404ab6e`
- `P25UDefaultAppCaptureRecovery.final.force-stop.json`: `e2cf734c5719078165e0c36d111bc27158f33c75691db8f24423a4212bfad5d3`
- `P25UDefaultAppCaptureVerify.final.verify.log`: `100cd6fe8bc3c0aa104945b321630da4d7c449a372351fd4b9c6c9812a749abf`
- `P25UDefaultAppCaptureRecovery.final.cleanup.log`: `5acdcec251cc5f960d595e499d0ad25e1e110c7438eca0b07c830024d666ea5e`
- 旧实现红测：`P25UCaptureExport-recreation-old-behavior-red.log`, `7514821b2b6d0d5640b5c427cc276c4c7560f087bc29806f8fbba73ad1424070`
- 修复后导出 3/3：`P25UCaptureExport-final-remediation.log`, `42e2f1afbcd02b980f137baf1f02bf2cd3aacdd44bf92ca1c56988cbe1f93839`

最终独立审查：delivery `753163efddfd8e28a6b267ac70af95cdd86c282e` 为 PASS，FINDINGS=NONE，OWNER_LANDING=ALLOWED。该结论允许进入 Owner landing 决策，不代表 PR 自动合并。

## 验证结果

| Gate | 结果 |
|---|---|
| P25U host core | NOT_RUN：本机无现有 kotlinc，未安装 |
| P25 Python | 43/43 PASS（上轮已通过；本次 Android-only 整改未重跑） |
| P23C Python | 55 PASS / 1 SKIP（上轮结果；skip 为 Windows symlink privilege，本次未重跑） |
| JVM | 137/137 PASS，0 failure/error/skip（整改后 fresh rerun） |
| Debug + AndroidTest APK | PASS（整改后 fresh rerun） |
| lintDebug | 0 errors / 25 warnings |
| lintRelease / signed Release | BLOCKED：P20_BLOCKED_RELEASE_SIGNING_INPUT，未找到可注入的既有 DPAPI identity root；未生成新密钥 |
| P25U Repository/UI/CameraX | 7/7、3/3、1/1 PASS |
| P25U DocumentsUI 导出（含 Activity 重建） | 3/3 PASS；旧 token 实现的回归反例失败符合预期 |
| 磁盘进程恢复 | prepare / verify / cleanup 各 1/1 PASS，确认 PID 消失后再 verify |
| 默认 App 成片恢复 | prepare / verify / cleanup 各 1/1 PASS，verify 绑定 prepare capture ID 与文件 digest |
| DocumentsUI 另存成功/取消 | 各 1/1 PASS |
| P25S/P23C/P25T/P23R/系统 Picker 回归 | 已在 API 35 专用 AVD 以精确 class filter 通过 |
| privacy / diff | PASS |

Android 目标始终是 emulator-5554、SDK 35、ro.kernel.qemu=1；物理设备未触碰。

## 未完成边界

- 首次独立 Reviewer 的 P1/P2 与证据 hash 问题均已整改；最终独立审查通过，等待 Owner 决定是否 landing。
- 未执行真实照片、Qwen、LAN、Pipeline、知识包签名、实体设备、Cloud、iOS、公开发布或 main 合入。
- P25R 真人内容/权利/隐私审核仍是独立的 0/20 Gate。
