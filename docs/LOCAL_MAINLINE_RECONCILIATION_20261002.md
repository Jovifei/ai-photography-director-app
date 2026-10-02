# 本地 main 同步、分支整合与验证记录

**记录日期：** 2026-10-02
**项目：** `ai-photography-director-app`
**远端：** `https://github.com/Jovifei/ai-photography-director-app.git`

## 当前结论

本地 Owner 工作区的 `main` 已同步到最新取得的 `origin/main@1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`，当前与该远端跟踪分支 0 ahead / 0 behind。此前它落后 86 个提交；快进前比对 179 个远端变更路径与 Owner 修改/草稿路径，没有发现重叠。六个已修改的跟踪文件及 Owner 未跟踪文档均保留。

T3–T17 已在隔离候选分支 `codex/mainline-consolidation-20261002` 中以 merge 保留完整历史。候选 merge anchor 为 `a045b4b36488fe975ae1caca63d2d70886070d31`，父节点是 `origin/main@1b776ba...` 与逐阶段远端审查的 T17 头 `96ec9b3f0270636000acbea163a7b64e5ca5dffc`。T17 相对最新 `origin/main` 有 121 个提交；候选还包含这次同步的本地代码和文档更新。

这只是本地整合候选。候选整体还没有推送、创建 PR 或由远端 ChatGPT 对整体精确 SHA 复核；也没有合并 GitHub `main`。T17 原有远端 DONE 只覆盖其接受源码，不覆盖这次合并和本地六文件补丁。

## Owner 工作区保护

六个 Owner 已修改跟踪文件：`AGENTS.md`、`scripts/fetch_reference_repos.py`、`scripts/generate_g0_report.py`、`scripts/phase1_5_contract_semantics.py`、`scripts/prepush_privacy_audit.py`、`scripts/test_phase1_5_contracts.py`。补丁在 T3–T17 候选上的三方预检和应用均干净；Owner E 盘原件没有被覆盖。

Owner `docs/` 下十份未跟踪草稿（`01`–`07`、`AA0` 两份和 `P20_PHOTOGRAPHY_PROJECT_EXECUTION_SPEC.md`）及原有 `tasks/` 目录没有复制进公开候选、没有改写。Obsidian 映射规则仅将 allowlist 中的 P20 执行规格镜像到个人工程文档目录；其余 Owner 草稿和 `tasks/` 均未同步。镜像不改写 Owner 源文件。

## 分支与 PR 盘点

| 分支 / PR | 当前身份 | 本次处理 |
|---|---|---|
| `main` | 本地与 `origin/main` 同为 `1b776ba...` | 已快进同步；Owner 未提交状态保留 |
| `codex/mainline-consolidation-20261002` | `origin/main` + T3–T17 + 本地补丁 | 本地整合候选；整体远端复核待做 |
| PR #3 / #4 | GitHub 已关闭并合并 | 已在 `origin/main` 历史中，不重复合入 |
| PR #1 | Draft 开放；head `1d13b8e...` 已可从 `origin/main` 到达 | 标记为重复候选；本次未关闭 PR |
| PR #2 / P23B | Draft 开放，head `bd8d66b...`，base 为 P23A R1 分支 | 保持独立；其固定候选和预期 main 已过时，不混入 Android 主候选 |
| PR #5 / #6 | Draft 开放，真人审核或产品接受仍待办 | 代码提交祖先已在 T3–T17 候选；人工审核状态仍 Pending |
| PR #7–#21 | T3–T17 阶段 Draft 堆栈 | 完整代码/测试/计划历史在本地候选；旧 PR 均保持打开，等待整体复核与单独状态处理 |
| `codex/project-docs-refresh-20260918` | 7 个不在当前 `main`/T17 的 P24 文档提交 | 保留，先逐份确认历史发布/签名文字是否适合当前知识库 |
| `codex/ui0-runtime-fix` | 本地独有 `e4862f9`，旧 UI0 Home/import 代码 | 保留；当前 T3–T17 屏幕与流程已演进，未盲目 cherry-pick |
| `codex/t11-artifact-acquisition-preflight-20260928` | 本地独有 `251242a` 计划提交 | 保留；新 T11 便携身份方案另有 12 个后续提交，需按历史证据而非代码合入处理 |

P23B 本地验证分支 `codex/p23b-local-validation-20260915@34949f8` 的测试在另一隔离工作树执行：manifest 21 tests 和 landing-preflight 30 tests 均无失败；各有 1 个 symlink fixture 因 Windows 符号链接权限不可用而跳过，compileall 与该分支隐私审计 PASS。它的脚本和文档仍绑在 P23A R1 预检目标，不适合作为当前 T3–T17 App 候选的无条件 merge。

## 本机验证结果

| 检查 | 结果 | 说明 |
|---|---|---|
| `scripts/test_phase1_5_contracts.py` | `PASS 75/75` | P1.5 schema 与语义边界 |
| `scripts/test_photo_knowledge_bundle_consumer_contract.py` | `PASS 12/12` | PKB1 消费端合同 |
| Python `unittest discover` | `247 pass / 1 skip / 1 fail`（249 total） | 唯一失败需外部 P1A/Qwen 许可证据目录；该目录缺失且未设置 `P1A_EXTERNAL_EVIDENCE_BASE`。skip 是 Windows 符号链接权限，不计作 PASS |
| JVM `:app:testDebugUnitTest` | `PASS 159/159` | 本机 API 35 AVD 没有配置 |
| `:app:assembleDebug` / `:app:assembleDebugAndroidTest` | `PASS` | Debug app 与仪器测试 APK 可构建 |
| `:app:lintDebug` | `PASS` | 0 errors，17 warnings |
| Debug APK 元数据与签名校验 | `PASS` | `com.jovi.photoai`，version `0.2.0-beta.1`；仅验证 APK 元数据，不代表 Release |
| `:app:lintRelease` | `BLOCKED` | 项目门禁报缺少 `PHOTOAI_RELEASE_STORE_FILE`；没有生成、读取或配置签名材料 |
| API 35 仪器矩阵 | `NOT_RUN`（本机） | 本机 AVD 列表为空。ADB 默认端口服务无法启动；备用本地端口服务启动成功但无设备连接。T17 的远端 96/96 报告保留为远端证据 |
| candidate pre-push privacy audit | `PASS` | 最终文档树的 `python scripts/prepush_privacy_audit.py` 与 `git diff --check` 均通过 |

本机 Debug APK SHA-256：`3DF2816F635E4479A9E6B42F266E76FD348AA62D7653971EA922611BF8491D4B`。运行日志保存在 OS 临时目录，不入 Git。

## 产品与授权状态

- 本地整合后的 Android 离线机器端阶段为 T3–T17；T17 的远端阶段审查和 API 35 资格仍有独立历史证据。
- 下一机器接口门为 T14：等待生产端提供原始单图、多图及 A/B 替换 golden vectors、文件原始字节摘要、canonical digest 和映射期望。不能以 App 自造 fixture 代替生产端向量。
- P1B 仍为 `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`；T11 当前阻塞 `BLOCKED_LEGAL_REVIEW`。没有模型下载、运行、推理、真实照片、Pipeline、Private LAN 或网络权限变化。
- 人工摄影质量、真实设备、TalkBack/无障碍实用性、20 项真人内容审核、签名、Beta 和公开发布仍未通过。

## 远端复核状态与恢复点

固定域名登录记录正常，但该工作区的安全连接 Doctor 未变绿：连接服务访问 Cloudflare API 时收到本机 socket access-denied；已刷新登录并重试一次受限权限运行，状态仍失败。没有重建连接器、换域名或改 DNS/代理。因为 Doctor 未绿，没有向 ChatGPT 网页发送本次控制消息。

恢复后使用同一 `Ai相机` Project/同一已绑定聊天，对整合分支最终 tip 做 exact-SHA 复核；要求远端检查 T17 交接约束、本地六文件补丁、文档状态、验证结果和分支差异。只有整体复核后再确定 GitHub landing；不得把本地整合候选直接标成 `main` 或发布 PASS。

## 本地知识库镜像

`sync-project-docs.ps1 -ProjectRoot E:\project\ai-photography-director-app -DryRun` 返回 `DRY_RUN`，copy_count 16、skip_count 0。随后按映射运行无参数 `invoke-mirror.ps1`，返回 `MEMORY_UPDATED`，依据同一轮 DryRun 同步 16 份 allowlist 文档。任务记录、构建/运行输出、九份未列入 allowlist 的 Owner 草稿和 `tasks/` 未镜像；P20 执行规格按现有 allowlist 镜像到个人 Obsidian，E 盘原件仍未改。
