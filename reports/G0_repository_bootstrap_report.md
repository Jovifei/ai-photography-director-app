# G0 Repository Bootstrap Report — ai-photography-director-app

| 项 | 值 |
|---|---|
| 工程 | A — AI 摄影现场导演 App(Android 首发消费端) |
| 本地目录 | `E:\project\ai-photography-director-app` |
| origin | `https://github.com/Jovifei/ai-photography-director-app.git` |
| 分支 | `main` |
| 初始化提交 SHA | `2140f3e0258bc2ee04bac83dd3899155f94f1ac6` |
| 提交信息 | `docs: initialize Android-first photography director project` |
| 推送结果 | 成功(`main -> origin/main`,新分支,`PUSH_EXIT=0`) |
| git status | 干净(working tree clean) |
| 报告日期 | 2026-07-14 |
| 执行者 | Claude Code(按负责人 Jovi 分配的 G0 实现者角色) |

## 1. 执行摘要

工程A 完成 G0 仓库初始化与首次推送。前序进程已将工程A 目录从"项目群交接包根"重组为"工程A 项目目录"(交接包 Source of Truth 已安全归档,见 §7)。本轮完成:`.gitignore` 与 `README.md` 的 G0 验收缺口补齐、`git init -b main`、`origin` 绑定、两次隐私审计、首个 commit 与 `push -u origin main`。

## 2. README 检查(G0 第8条)

- ✅ 项目为何存在(参考图分析 -> 现场机位 -> 人物导演 -> 拍摄闭环)
- ✅ Android First("首发 Android,iOS 第二阶段")
- ✅ iOS 第二阶段
- ✅ 本地目录、GitHub 远端
- ✅ 与另一工程关系(消费 Pipeline 的 Bundle)
- ✅ Bundle Consumer 身份
- ✅ 当前阶段与下一阶段(完成首推后停止;批准后进 AH0/AA0)
- ✅ 如何运行测试(本轮补:AH0 后 `./gradlew test`;shared-contract 互验)
- ✅ 如何获取参考仓库(本轮补:`python scripts/fetch_reference_repos.py --profile core`)
- ✅ 哪些文件不入 Git(本轮补:指向 `.gitignore` 关键禁提交项)

## 3. .gitignore 检查(G0 第10条)

- ✅ 私人照片(`private-data/`、`reference-images-private/`)
- ✅ RAW/HEIC(`*.heic`、`*.dng`、`*.raw`)
- ✅ `.env` 与 Token/密钥(`.env`、`*.pem`、`*.key`、`*.p12`)
- ✅ 模型权重(`*.safetensors`、`*.ckpt`、`*.pth`、`*.pt`、`*.onnx`、`*.tflite`、`*.gguf`、`models/`)
- ✅ SQLite 与运行数据库(本轮补:`*.db`、`*.sqlite`、`*.sqlite3`、`*.db-wal`、`*.db-shm`)
- ✅ Docker 私有配置(本轮补:`docker-compose.override.yml`、`*.local.yml`)
- ✅ 构建产物(`build/`、`**/build/`)
- ✅ IDE 用户目录(`.idea/`、`.vscode/`、`*.iml`)
- ✅ 日志(本轮补:`*.log`)
- ✅ 缩略图/Mask/Cutout(本轮补:`thumbnails/`、`masks/`、`cutouts/`)
- ✅ 私人 Bundle(`reports/private/`、`artifacts/private/`)
- ✅ `docs/references/repos/`(第三方克隆)
- ✅ Android `local.properties`、`.gradle/`、`build/`、`.idea/`
- ✅ 未来 iOS 签名/DerivedData/xcuserdata(`DerivedData/`、`xcuserdata/`、`*.xcuserstate`、`Pods/` 等)

## 4. 隐私审计

- 审计 #1(git add 前):**PASS**(0 敏感物)
- 审计 #2(git add 后):**PASS**(0 敏感物)
- staged 自检:37 文件,全为文档/配置/脚本/合同,无图片/DB/密钥/模型/`.env`/`.venv`/`build`
- 脚本:`scripts/prepush_privacy_audit.py`(Python 3.14.2)

## 5. staged 文件清单(37,按类别)

- 顶层 agent/项目文件:`README.md`、`AGENTS.md`、`AGENT_START_HERE.md`、`CLAUDE.md`、`CODEX_START_HERE.md`、`OPENCLAW.md`、`OWNER_INPUTS_REQUIRED.md`、`PROGRAM_CONTRACT_NOTICE.md`、`PROJECT_BINDING.json`、`.gitignore`、`DEV_LOG.md`
- `config/`:`reference_repositories.json`
- `docs/`:`00_ANDROID_FIRST_EXECUTION_PLAN.md`、`01_CAMERAX_AND_POSE_SPIKE.md`、`03_REFERENCE_REPOSITORY_MAP.md`、`04_IOS_SECOND_PLATFORM_GATE.md`、`PROGRAM_HANDOFF_REFERENCE.md`、`PROJECT_RELATIONSHIP.md`、`references/`(README、REFERENCE_LOCK.json)
- `scripts/`:`fetch_reference_repos.py`、`first_push.ps1`、`generate_g0_report.py`、`prepush_privacy_audit.py`
- `shared-contract/`:`README.md`、`SOURCE_OF_TRUTH_NOTICE.md`、`contract_version.json`、4 fixtures、5 schemas
- `reports/`:`README.md`(占位)

## 6. 工程B(Pipeline)状态说明

工程B 未在本轮执行 G0 模板复制/push,原因:

- 工程B 已被前序智能体推进到 N0 阶段(HEAD=`6ce5cac` N0 关闭提交;`229be08` 为 N0 实现)。
- N0 关闭记录整改已完成核验,但质量重跑发现 pytest FAIL(`AT-N0-GIT-01` 断言恰好 1 个 commit,关闭提交使其变 2 个),`QUALITY_GATE_FAILED`,待 Jovi/delta Reviewer 裁决。
- 工程B 分支 `master`、未设 remote、未 push(G0 对工程B 已不适用,等 N0 裁决后由 Owner 决定工程B 的 G0 补齐方式)。
- 本轮严守边界:**未碰工程B 任何文件**。

## 7. 交接包 Source of Truth 去向

前序进程将原交接包根内容移至:
`E:\project\_agent-handoff\AI_Photography_Program_Agent_Handoff_v1.2.1_Android_First`

完整保留:`manifest.sha256`、`PROGRAM_START_HERE.md`、`docs`(42)、`tasks`(6)、`templates`(5)、`project-templates`(2)、`research`(6)、`config`(2)、`scripts`(11)、`shared-contract`(4)及全部 agent 规范文件。工程A 仓库不再承载交接包,仅含工程A 项目文件 + `shared-contract` 副本。

## 8. 未解决问题

1. 工程B N0 关闭质量门禁为红(`AT-N0-GIT-01` FAIL),待裁决。
2. 工程B 未设 remote、未 push、分支 `master`(待 N0 裁决后处理)。
3. 工程A 的 README"如何运行测试"目前指向未来 AH0(尚无 `android/` 代码与测试),属预期。

## 9. 下一阶段建议

- **工程A**:G0 完成,等待 Owner 批准进入 **AH0**(Windows Android Studio/JDK/SDK/Gradle/ADB 环境 + CameraX Preview/ImageCapture/ImageAnalysis + 两台设备矩阵)。
- **工程B**:等 N0 裁决;通过后 Owner 决定是否补 G0(remote/main/push)或直接继续 N1。
- 严格遵守 Android First,Android Gate 前不创建 `ios/`、Xcode 工程、Apple 签名。
