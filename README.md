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

## Android 首版

Kotlin、Jetpack Compose、CameraX、系统 Photo Picker、Room；MediaPipe Pose Landmarker 与 ML Kit Pose 做 Spike，必要时才评估 MoveNet。

## 核心体验

> 左边看环境，右边看人物，中间负责拍照。

本仓库消费 Nightly Pipeline 生成并经人工审核的 Bundle，负责参考图详情、实时 Pose、骨架/轮廓/参考图 Overlay、构图与人物导演、拍照和复盘。

## 测试与参考仓库

- 运行测试：AH0 批准后在 `android/` 目录执行 `./gradlew test`；`shared-contract/` 的 Schema/Fixture 由 App 与 Pipeline 两侧各自实现并互验。
- 获取参考仓库（只本地、不入 Git）：`python scripts/fetch_reference_repos.py --profile core`，浅克隆到 `docs/references/repos/` 并写入 `docs/references/REFERENCE_LOCK.json`。只提交 `REFERENCE_LOCK.json`、来源、Commit 与研究结论，不提交第三方源码。

## 不入 Git 的内容

见 `.gitignore`。关键禁提交项：私人照片与用户素材（`private-data/`、`reference-images-private/`）、RAW/HEIC、`.env` 与 Token/密钥、模型权重（`*.pt/*.onnx/*.safetensors` 等）、SQLite 与运行数据库（`*.db/*.sqlite`）、日志（`*.log`）、Android `local.properties`、`build/`、`.gradle/`、`.idea/`、`docs/references/repos/` 第三方克隆、未来 iOS 签名与 `DerivedData/`。

## 第一步

完成安全首次 push 后停止。批准后只进入 AH0/AA0，不得先开发 iOS。
