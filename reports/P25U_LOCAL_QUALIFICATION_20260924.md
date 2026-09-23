# P25U 本地 Android 成片资格验证｜2026-09-24

状态：P25U_ANDROID_VALIDATED_AWAITING_INDEPENDENT_REVIEW。

## 精确绑定

- 原分支：codex/p25s-internal-handoff-validation-20260920
- 本地隔离分支：codex/p25u-local-android-qualification-20260924
- 实际远端输入：9df16203512f91c79150058afd02858c9a292bb3
- P25T 基线：11342a3a441cb03826b85f46f3932a98bd98930f
- P25U 源码最终提交：525725115f595bf53b795109928d691854b04d6b
- Git-object 清单：docs/handoff/P25U_LOCAL_SOURCE_MANIFEST_20260924.json，24/24 OID、原始字节和 SHA-256 匹配
- 观测 main：1b776ba9932a7fdc96112c8cb85c7258f3f7d6af，本轮未修改

## 本轮实现与最小整改

- 新增默认 App 根路由的真实成片恢复回归：prepare → 外部启动并确认 PID → force-stop → 独立 verify → cleanup。
- 新增真实 CameraX 成片通过 Android CreateDocument 的成功/取消回归。
- 修复导出时序：持久化 reserve 后立即 arm，再通过预注册的 Activity Result launcher 发起选择；回调使用同一 capture token。
- 相机页的保存入口回到成片预览/成片库，不再直接创建未绑定 launcher 的导出请求。
- 原片仍位于 noBackupFilesDir/capture-library；卸载、清除数据、换机不自动保留；外部副本不由 App 跟踪或删除。

## 验证结果

| Gate | 结果 |
|---|---|
| P25U host core | NOT_RUN：本机无现有 kotlinc，未安装 |
| P25 Python | 43/43 PASS |
| P23C Python | 55 PASS / 1 SKIP，skip 为 Windows symlink privilege |
| JVM | 137/137 PASS，0 failure/error/skip |
| Debug + AndroidTest APK | PASS |
| lintDebug | 0 errors / 25 warnings |
| lintRelease / signed Release | BLOCKED：P20_BLOCKED_RELEASE_SIGNING_INPUT，未找到可注入的既有 DPAPI identity root；未生成新密钥 |
| P25U Repository/UI/CameraX | 7/7、3/3、1/1 PASS |
| 磁盘进程恢复 | prepare / verify / cleanup 各 1/1 PASS，确认 PID 消失后再 verify |
| 默认 App 成片恢复 | prepare / verify / cleanup 各 1/1 PASS |
| DocumentsUI 另存成功/取消 | 各 1/1 PASS |
| P25S/P23C/P25T/P23R/系统 Picker 回归 | 已在 API 35 专用 AVD 以精确 class filter 通过 |
| privacy / diff | PASS |

Android 目标始终是 emulator-5554、SDK 35、ro.kernel.qemu=1；物理设备未触碰。

## 未完成边界

- P25U 独立 Reviewer 尚未执行；不能把本地资格写成独立审查 PASS。
- 未执行真实照片、Qwen、LAN、Pipeline、知识包签名、实体设备、Cloud、iOS、公开发布或 main 合入。
- P25R 真人内容/权利/隐私审核仍是独立的 0/20 Gate。
