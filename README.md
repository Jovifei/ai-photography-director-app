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

`main` 当前已包含 P22 离线产品能力和 P23D 持久化恢复验证；P22 的产品边界仍然是诚实的离线摄影工具：

`新建项目 → 最多 20 张私有导入 → 筛选/删除/选择主参考 → 无 AI 指导拍摄 → 系统另存为`

同时提供受控的离线 Photo Knowledge Bundle v1 导入：用户通过系统文件选择器选择无图片 JSON，校验 PKB1 摘要后逐条明确绑定项目照片。未完成真实 `READY` 分析的照片绝不会带着示例指导进入 AI Camera Director。

真实 AI 路径仍保持独立激活边界：

`连接本机 Qwen 服务 → 逐张 READY 分析 → READY-only 汇总 → AI Camera Director`

Qwen Private LAN 和 Nightly Pipeline artifact 尚未在本候选中启动或接入；Pose、Cloud、iOS 和公开发布也不是当前交付内容。

## P23D 主线状态（2026-09-18）

- `main` 精确 HEAD：`6a42feced043297aac9eabe677bb4ba23b9154fd`。
- P23D 独立审查结论为 `PASS`，并已从通过审查的候选 fast-forward 合入 `main`；没有 force push、额外 merge commit 或生产 Android/Room/Provider 漂移。
- P23D 使用合成参考图和合成 Provider metadata，在专用 API 35 emulator 上验证了真实 OS `force-stop` 后的新进程启动、Room 中 Provider-backed `READY`、来源/模型 provenance、项目 summary、primary reference 与 last-active reference 恢复。
- Release signing 仍受外部 `PHOTOAI_RELEASE_STORE_FILE` 输入约束，缺少该材料时保持 `BLOCKED`；本状态不表示签名发布、真实照片、Qwen/LAN、Pipeline 或公开发布已授权。

## Android 技术栈

Kotlin、Jetpack Compose、CameraX、系统 Photo Picker、Room、应用私有 JPEG 和 fail-closed 状态/来源校验。

## 核心体验

> 左边看环境，右边看人物，中间负责拍照。

当前可用的拍摄入口支持有可信来源的指导拍摄和明确标注的无 AI 指导拍摄；未来的实时 Pose、骨架/轮廓 Overlay 和 Pipeline 连接必须经过独立 Gate。

## 使用入口

- [Android Beta 手机配置与首次使用说明](docs/ANDROID_BETA_PHONE_SETUP.md)
- [摄影导演 Android Beta 用户指南](docs/BETA_USER_GUIDE.md)
- [Photo Knowledge Bundle Consumer v1](docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md)
- [P23D 持久化恢复与安全合入经验](docs/90-RPT-P23D持久化恢复与安全合入经验-20260918.md)
- [P22 离线产品资格报告](reports/P22_OFFLINE_PRODUCT_CLOSURE_QUALIFICATION.md)
- Windows Private 网络只读诊断：`scripts/qualify_windows_private_network.ps1`
- 实体手机安全安装：`scripts/install_android_beta_phone.ps1`（只执行同签名 `adb install -r`）

## 测试与参考仓库

- 运行测试：AH0 批准后在 `android/` 目录执行 `./gradlew test`；`shared-contract/` 的 Schema/Fixture 由 App 与 Pipeline 两侧各自实现并互验。
- 获取参考仓库（只本地、不入 Git）：`python scripts/fetch_reference_repos.py --profile core`，浅克隆到 `docs/references/repos/` 并写入 `docs/references/REFERENCE_LOCK.json`。只提交 `REFERENCE_LOCK.json`、来源、Commit 与研究结论，不提交第三方源码。

## 不入 Git 的内容

见 `.gitignore`。关键禁提交项：私人照片与用户素材（`private-data/`、`reference-images-private/`）、RAW/HEIC、`.env` 与 Token/密钥、模型权重（`*.pt/*.onnx/*.safetensors` 等）、SQLite 与运行数据库（`*.db/*.sqlite`）、日志（`*.log`）、Android `local.properties`、`build/`、`.gradle/`、`.idea/`、`docs/references/repos/` 第三方克隆、未来 iOS 签名与 `DerivedData/`。

## 当前候选与下一步

- 当前交付基线是 `main@6a42feced043297aac9eabe677bb4ba23b9154fd`，P23D 独立审查已通过并完成安全合入。
- 下一阶段入口固定为 `P24_RELEASE_SIGNING_AND_DISTRIBUTION`：先检查 Owner 是否已有既有签名身份；没有 Owner 明确决定前，不生成新的生产 keystore。
- 下一阶段仍需单独授权；不要把 P23D 恢复证据外推为 Release signing、真实 Provider、公共知识包、Pipeline、Cloud、iOS 或公开发布完成。
- 继续 Qwen 前，必须使用受控私有 Wi‑Fi，并通过 Windows Private 网络预检；禁止绕过脚本或使用 FlClash/WSL/VPN 地址。
