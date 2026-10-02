# AI Photography Director App

AI 摄影现场导演 App。首发 Android，iOS 第二阶段。

## 仓库绑定

- 本地 Owner 工作区：`E:\project\ai-photography-director-app`
- 远端：`https://github.com/Jovifei/ai-photography-director-app.git`
- 默认分支：`main`
- Bundle 角色：consumer

## 当前 Android 产品状态

截至 2026-10-02，`origin/main` 已同步到 `1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`。最新离线 Android 工作已在独立候选分支 `codex/mainline-consolidation-20261002` 整合 T3–T17，并已推送为 [Draft PR #22](https://github.com/Jovifei/ai-photography-director-app/pull/22)。整体 exact-SHA ChatGPT 复核仍待 C2C 安全连接恢复；候选尚未合并到 GitHub `main`。分支、PR、Owner 工作区和验证状态见 [`docs/LOCAL_MAINLINE_RECONCILIATION_20261002.md`](docs/LOCAL_MAINLINE_RECONCILIATION_20261002.md)。

本机对整合候选的验证：JVM 159/159、Debug APK 与 AndroidTest APK 构建、Debug lint（0 errors / 17 warnings）均通过；P1.5 合同语义 75/75、PKB1 消费端合同 12/12 通过。完整 Python unittest 有一个外部 Qwen 许可证据目录缺失、一个 Windows 符号链接权限跳过。API 35 本机仪器测试未运行：本机没有已配置 AVD。Release lint 受缺少 `PHOTOAI_RELEASE_STORE_FILE` 门禁阻塞。T17 远端记录的 96/96 API 35 结果是独立远端证据，不能当成本机结果。

## 当前离线产品流程

`新建项目 → 私有参考图导入 → PKB1 无图 JSON 明确绑定 → 来源可信的指导拍摄或无指导拍摄 → 私有成片 → 拍摄库查看/安全重拍 → 系统导出`

T3–T17 候选为 CameraX 手动控制、拍摄成片库、项目/参考图关联、离线知识包替换、进程恢复后的当前指导重拍补齐了分阶段实现与合成/API 35 验证。真实照片的构图质量、物理手机、TalkBack 的实际可用性、Qwen/Private LAN、Pipeline、签名发布和人工审查仍未通过。

未来真实 AI 路径保持独立授权边界：

`连接本机 Qwen 服务 → 逐张 READY 分析 → READY-only 汇总 → AI Camera Director`

Pose、Cloud、iOS 和公开发布不是当前本地候选的能力声明。

## 当前状态与交接

- [本地整合状态、分支盘点与验证结果](docs/LOCAL_MAINLINE_RECONCILIATION_20261002.md)
- [项目门禁状态](docs/CURRENT_PROGRAM_STATUS.md)
- [下一 Gate 矩阵](docs/NEXT_GATE_MATRIX.md)
- T17 的远端接受证据、T14 依赖与本机复验边界见本地整合报告。
- [PKB1 生产端黄金向量交接要求](docs/PKB1_PRODUCER_GOLDEN_VECTOR_HANDOFF_REQUEST.md)

当前离线 Android 机器端没有已批准的新产品实现范围。后续 T14 需等待独立生产端提供原始单图、多图和 A/B 替换向量、原始字节摘要与 canonical digest 期望值；不得用消费者自造 fixture 代替生产端证据。物理设备、真实摄影/无障碍体验、人工内容审核、签名和公开发布仍按各自门禁办理。

## 历史使用入口

- [历史试点手机配置说明](docs/ANDROID_BETA_PHONE_SETUP.md)（不代表当前授权）
- [历史 Beta 用户指南](docs/BETA_USER_GUIDE.md)（不代表当前授权）

## Android 技术栈

Kotlin、Jetpack Compose、CameraX、系统 Photo Picker、Room、应用私有 JPEG 和 fail-closed 状态/来源校验。

## 开发与本地验证

从仓库根目录进入 `android/`，使用项目 Wrapper：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

API 35 仪器测试仅对计划中指定的专用 AVD 执行；测试状态必须标为本机或远端，不以云端 CI、历史报告或编译成功代替本机设备结果。每次推送前运行 `python scripts/prepush_privacy_audit.py`。

## 获取参考仓库

`python scripts/fetch_reference_repos.py --profile core` 只在本地浅克隆到 `docs/references/repos/` 并写入 `docs/references/REFERENCE_LOCK.json`。只提交锁文件、来源、Commit 与研究结论，不提交第三方源码；许可不兼容的仓库只研究。

## 不入 Git 的内容

见 `.gitignore`。关键禁提交项：私人照片与用户素材、RAW/HEIC、`.env` 与 Token/密钥、模型权重、数据库、日志、Android `local.properties`、`build/`、`.gradle/`、`.idea/`、`docs/references/repos/` 第三方克隆以及未来 iOS 签名与 `DerivedData/`。

Owner 工作区的未提交代码和本地草稿文档保持原位；当前 PR 候选不自动包含这些草稿。
