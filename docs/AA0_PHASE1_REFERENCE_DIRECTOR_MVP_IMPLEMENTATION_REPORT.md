# AA0 Phase 1：Reference → Director MVP 实现报告

**状态：** `IMPLEMENTED — AWAITING_INDEPENDENT_REVIEW`
**分支：** `codex/phase1-reference-director-mvp`
**起点基线：** `8a4006b0266d371b3e05d69fbec08642b5ad4518`

## 交付内容

Phase 1 实现了不依赖实时 Pose 的 `Reference → Director` 本地 Demo 闭环：

```text
Home → Import Reference → Reference Analysis → Director Card → Camera Director
```

- **Reference Library**：仅显示本次会话中已完成 Demo Analysis 的参考记录；不持久化 Photo Picker Uri 或图片。
- **Import Reference**：继续使用 Android 系统 `PickVisualMedia(ImageOnly)`；没有新增照片读取权限。
- **Reference Analysis**：展示场景、背景价值、光线、构图、人物姿态意图、情绪与拍摄建议；页面和结果均明确标注 `Demo Analysis`。
- **Director Card**：把结果整理为环境、人物、情绪、相机四组可执行建议。
- **Camera Director**：左侧环境面板、中央提示和右侧人物面板消费 `ReferenceBundle` 生成的展示模型；该路径不显示静态 Skeleton/Outline Demo，也不含实时 Pose。

## 架构与合同边界

新增 `com.jovi.photoai.reference` feature：

- `ReferenceAnalysis`：七项分析字段；
- `ReferenceBundle`：`reference_id`、`scene`、`background_story`、`lighting`、`composition`、`subject_intent`、`emotion`、`pose_template`、`camera_position`、`director_prompt`、`version`；
- `DirectorCard` 与 `CameraDirectorGuidance`：把 Bundle 变成展示和现有 `GuidanceItem`；
- `ReferenceFlowReducer`：`Import → Analysis → Director Card → Camera Director` 的纯导航流；
- `DemoReferenceAnalyzer`：只校验短生命周期 URI 输入并返回固定内容；不解码为 AI 输入、不上传、不记录、不持久化。

`docs/reference/reference_bundle.schema.json` 是 **app-local draft**。为遵守 `shared-contract/SOURCE_OF_TRUTH_NOTICE.md`，本阶段没有修改被冻结的跨仓库 Contract、正式 `pose_template.schema.json` 或 Pipeline 端合同。

## 隐私与禁止范围验证

- Manifest 仍只声明 `android.permission.CAMERA`；没有 `READ_MEDIA_IMAGES`、`READ_EXTERNAL_STORAGE` 或 `INTERNET`。
- 系统 Photo Picker 只交付用户主动选择的 URI；URI 仅在 Compose 会话内保存。
- 没有 MediaPipe、ML Kit、MoveNet、RTMPose、TensorFlow、模型下载、GPU、Docker、云端 API 或网络依赖。
- 没有修改 `CameraXManager`、`domain/pose`、`CameraUiState`、UI0 design tokens 或共享合同快照。

## 验证结果

| 验证 | 结果 |
| --- | --- |
| `./gradlew.bat assembleDebug lintDebug testDebugUnitTest` | PASS |
| JVM 单元测试 | **91 passed**, 0 failures, 0 errors, 0 skipped |
| `./gradlew.bat connectedDebugAndroidTest` | **3/3 passed**，真实 Android 设备（脱敏） |
| Debug APK | PASS，11,838,668 bytes; SHA-256 `FE773BF0B203E15181A9382EEB02DC81F38E820DCBA4DA4933764D4828F234D4` |
| Manifest / 源码禁止项扫描 | PASS |

真实用户照片的 Picker 选择没有在本轮自动化中执行：本实现不读取或测试私人图库。该限制不影响系统 Picker、纯导航、Demo Bundle、Director Card 和既有 Camera instrumentation 的编译/测试证明，但真实用户选择体验应在独立人工验收中观察。

## Reviewer 关注点

1. 确认“本次会话 Reference Library”是否满足产品对历史参考图的 MVP 表达；跨重启持久化需要单独隐私和 URI 失效策略。
2. 确认 `ReferenceBundle` 进入 Pipeline 前的 ADR、双仓库同步和 Producer/Consumer 版本策略。
3. 确认 Demo Analysis 标识、无实时 Pose 文案和 Camera Director 两侧指引符合产品表达。
