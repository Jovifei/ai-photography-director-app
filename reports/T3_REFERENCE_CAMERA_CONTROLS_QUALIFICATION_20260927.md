# T3 参考图对照与相机控制资格验证｜2026-09-27

状态：`T3_EMULATOR_VALIDATED_AWAITING_INDEPENDENT_REVIEW`。

## 源码与证据绑定

- 隔离分支：`codex/t3-reference-camera-controls`
- P25U 基线：`7b2c09d189b598e1517af784f06d89eb4ea62320`
- 验收源码：`7ddfdb66fc0d6725b1af67db2a9bf53a35615ee9`
- Git 原始对象清单：`docs/handoff/T3_LOCAL_SOURCE_MANIFEST_20260927.json`，10/10 文件绑定 blob OID、字节数和 SHA-256。
- 专用证据：`E:\project_benchmark_evidence\t3-reference-camera-controls-20260926\0871703be1de4a7cb895b6eefd397e4b`；资格摘要和各原始日志的哈希记录在 `docs/handoff/T3_STAGE_STATE_20260927.json`。
- 专用目标：`emulator-5554`、API 35、`ro.kernel.qemu=1`。物理设备未触碰。

## 已实现

- Director 相机页只显示当前主参考图的私有文件缩略入口，可展开查看、关闭/返回；大图按比例适配，缺图时可重试。基础拍摄模式不出现对照入口。
- 预览画面支持点按对焦/测光；异步结果受生命周期 fence 保护，过期回调不覆盖较新的控制状态。
- 曝光补偿范围与步进来自当前相机能力；界面区分待应用和已确认值，新会话从 0 EV 开始。
- 未引入 Picker URI 持久化、前摄、实时 Pose 或 AI 场景分析。

## 验证结果

| Gate | 结果 |
|---|---|
| JVM | 138/138 PASS；0 failure / 0 error / 0 skip |
| Debug / AndroidTest 构建 | PASS |
| lintDebug | 0 errors / 25 warnings |
| T3 参考图、布局/旋转、缺图重试、展开、曝光/复位、点按对焦、基础模式边界 | 7/7 PASS |
| Director 根路由 | 2/2 PASS |
| P25U CameraX 成片 | 1/1 PASS |
| P25U 成片导出（空库首次拍摄路径） | 3/3 PASS |
| 默认 App 恢复 prepare / verify / cleanup | 各 1/1 PASS；恢复记录绑定 capture ID |
| 外部 force-stop | PID `6309 → 空` |

资格脚本：`scripts/qualify_t3_reference_camera_controls.ps1`。最终摘要为 `T3_EMULATOR_VALIDATED_AWAITING_INDEPENDENT_REVIEW`；debug APK SHA-256：`1cc39f492d4153b05f4a66a6a99b684d3de42f1688f19419692b37f5dcad62f7`。

## 未完成边界

- 独立 Reviewer：`PENDING`；代码已验证但尚不能称为最终完成。
- 本轮未做真实照片、光学精度/实体相机人工验收、Qwen/LAN、Pipeline、实体设备、DPAPI release signing 或公开发布。
- 不更新 main，不合并 Draft PR；P25U 的 release signing 输入仍缺失。
- GitHub Draft PR/远端状态须以创建并回读后的结果为准。
