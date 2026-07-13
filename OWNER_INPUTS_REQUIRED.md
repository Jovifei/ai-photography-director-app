# Android 首发 App｜产品负责人必须补齐的输入

这些输入不能由智能体猜测。缺失时只能完成 Mock、Fixture、纯 Domain 和文档工作，不得宣称 Android 真机 Gate 或发布已通过。

## AH0 前必须确认

| ID | 输入 | 推荐默认 | 影响 |
|---|---|---|---|
| A-01 | Android Studio、JDK、SDK、ADB 状态 | 在 Windows 本机真实记录 | 构建与设备安装 |
| A-02 | Android applicationId | 暂用 `com.jovi.photoai`，提交前由负责人确认 | 签名、存储、发布 |
| A-03 | minSdk 候选 | 先评估 API 26；最终以设备覆盖和依赖为准 | 兼容性 |
| A-04 | compileSdk/targetSdk | 实施时核对最新稳定和 Play 要求 | 构建与发布 |
| A-05 | 两台基准 Android 手机 | 主力新机 + 较旧/中端机 | FPS、热状态、OEM 差异 |
| A-06 | 首版摄像头 | 默认仅后置摄像头 | 镜像和 UI 复杂度 |
| A-07 | 首个必须成功的任务 | 单人站姿参考图复刻 | MVP Gate |
| A-08 | 20 张权利明确参考图 | 自制或仅限私人测试 | 测试合法性 |
| A-09 | 5–10 名目标测试用户 | Android 手机人像拍摄者 | 用户 Gate |
| A-10 | 签名策略 | Debug/内部测试 keystore 与正式 keystore 分离 | 安全与发布 |

## AA0 Pose Spike 前必须确认

- 是否允许 MediaPipe 进入 Spike；
- ML Kit Pose 仅作为 Beta 对照还是允许生产候选；
- 是否仅在前两者都失败时评估 MoveNet/TFLite；
- 允许的 APK 包体增量；
- CPU/GPU delegate 的测试设备；
- 轮廓/分割 mask 是否是 MVP Stretch。

## Android MVP 前必须确认

- 系统 Photo Picker 的本地保存和删除策略；
- 是否允许性能遥测以及 opt-out；
- 相机成片质量对比场景和最低标准；
- Google Play Internal Testing 是否启用；
- 危险场景和动作禁用规则；
- TalkBack、动态字体与行动能力适配范围。

## iOS 第二阶段

只有 Android MVP、100 图 Pipeline Pilot、用户 Gate 和产品负责人书面批准全部通过，才确认 Mac/Xcode、Apple Developer、Bundle ID、iPhone 设备和 iOS 架构。Android 阶段不得把 Apple 输入作为阻断项。
