# AI Photography Director App

AI 摄影现场导演 App。**首发 Android，iOS 第二阶段。**

## 仓库绑定

- 本地：`E:\project\ai-photography-director-app`
- 远端：`https://github.com/Jovifei/ai-photography-director-app.git`
- 默认分支：`main`
- Bundle 角色：consumer

## 推荐结构

```text
android/          # AH0 批准后创建
# ios/            # Android Gate 后才允许创建
shared-contract/
docs/
scripts/
```

## 当前 Android 产品状态

当前 P22 候选已经可以作为诚实的离线摄影工具使用：

`新建项目 → 最多 20 张私有导入 → 筛选/删除/选择主参考 → 无 AI 指导拍摄 → 系统另存为`

同时提供受控的离线 Photo Knowledge Bundle v1 导入：用户通过系统文件选择器选择无图片 JSON，校验 PKB1 摘要后逐条明确绑定项目照片。未完成真实 `READY` 分析的照片绝不会带着示例指导进入 AI Camera Director。

真实 AI 路径仍保持独立激活边界：

`连接本机 Qwen 服务 → 逐张 READY 分析 → READY-only 汇总 → AI Camera Director`

Qwen Private LAN 和 Nightly Pipeline artifact 尚未在本候选中启动或接入；Pose、Cloud、iOS 和公开发布也不是当前交付内容。

## Android 技术栈

Kotlin、Jetpack Compose、CameraX、系统 Photo Picker、Room、应用私有 JPEG 和 fail-closed 状态/来源校验。

## 核心体验

> 左边看环境，右边看人物，中间负责拍照。

当前可用的拍摄入口支持有可信来源的指导拍摄和明确标注的无 AI 指导拍摄；未来的实时 Pose、骨架/轮廓 Overlay 和 Pipeline 连接必须经过独立 Gate。

## 使用入口

- [Android Beta 手机配置与首次使用说明](docs/ANDROID_BETA_PHONE_SETUP.md)
- [摄影导演 Android Beta 用户指南](docs/BETA_USER_GUIDE.md)
- [Photo Knowledge Bundle Consumer v1](docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md)
- [P22 离线产品资格报告](reports/P22_OFFLINE_PRODUCT_CLOSURE_QUALIFICATION.md)
- Windows Private 网络只读诊断：`scripts/qualify_windows_private_network.ps1`

## 测试与参考仓库

- 运行测试：AH0 批准后在 `android/` 目录执行 `./gradlew test`；`shared-contract/` 的 Schema/Fixture 由 App 与 Pipeline 两侧各自实现并互验。
- 获取参考仓库（只本地、不入 Git）：`python scripts/fetch_reference_repos.py --profile core`，浅克隆到 `docs/references/repos/` 并写入 `docs/references/REFERENCE_LOCK.json`。只提交 `REFERENCE_LOCK.json`、来源、Commit 与研究结论，不提交第三方源码。

## 不入 Git 的内容

见 `.gitignore`。关键禁提交项：私人照片与用户素材（`private-data/`、`reference-images-private/`）、RAW/HEIC、`.env` 与 Token/密钥、模型权重（`*.pt/*.onnx/*.safetensors` 等）、SQLite 与运行数据库（`*.db/*.sqlite`）、日志（`*.log`）、Android `local.properties`、`build/`、`.gradle/`、`.idea/`、`docs/references/repos/` 第三方克隆、未来 iOS 签名与 `DerivedData/`。

## 当前候选与下一步

- 候选分支：`codex/p22-bundle-import-product-landing`
- 当前状态：`ANDROID_LOCAL_BUNDLE_IMPORT_PRODUCT_READY_AWAITING_APPROVED_PIPELINE_ARTIFACT`
- 当前候选已完成 Android 静态、API 35、D2D、签名和隐私资格；独立审查按当前执行边界未运行。
- 继续 Qwen 前，必须使用受控私有 Wi‑Fi，并通过 Windows Private 网络预检；禁止绕过脚本或使用 FlClash/WSL/VPN 地址。
