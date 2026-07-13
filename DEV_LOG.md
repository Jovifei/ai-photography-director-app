# AI 摄影项目群 · 开发过程记录 (DEV_LOG)

| 项 | 值 |
|---|---|
| 文档定位 | 跨工程开发过程、错误处置与后续计划的工作日志 |
| 维护者 | Claude Code(默认独立 Reviewer;本次按负责人 Jovi 分配转为受限实现者) |
| 负责人 | Jovi |
| 创建日期 | 2026-07-14 |
| 关联工程 | 工程A `ai-photography-director-app`(Android 首发消费端);工程B `nightly-photo-intelligence-pipeline`(Bundle 生产端) |
| 当前阶段 | G0(工程A 待初始化)/ N0 关闭整改(工程B,已提交待复核) |

---

## 一、背景与角色边界

- 默认角色是独立 Reviewer;只有 Jovi 明确分配不重叠工作包时才作为实现者。
- 两个工程独立 Git 仓库,只通过 `shared-contract/` 版本化 Bundle 通信,不共享 DB/源码。
- 平台顺序:**Android First**;iOS 在 Android MVP Gate 通过后才启动,Android Gate 前禁止创建 `ios/`、Xcode 工程、Apple 签名。
- 本日志记录两次被分配的工作包:① G0 仓库初始化探查(被中断);② 工程B N0 关闭记录整改(已提交,待复核)。

---

## 二、开发过程

### 阶段 1:G0 仓库初始化探查(工程A + 工程B)— 只读,被中断

1. 定位交接包根 = 工程A 本地目录(`E:\project\ai-photography-director-app`),该目录同时承载项目群 Source of Truth 文档(`docs/00–40`、`config`、`tasks`、`project-templates`、`shared-contract`、`scripts`、`templates`)。
2. 只读核查两个工程的 git 状态:
   - **工程A**:未 git init,无 `.git`,是干净待初始化目标。
   - **工程B**:已 git init,分支 `master`(非 main),无 remote,1 个 N0 scaffold 提交,211 跟踪文件,约 35 个未提交修改。
3. 读完 19 份必读战略文档(master PRD、两工程关系、路线图 Gate、仓库绑定、Android 修正、Phase 0 runbook、设备矩阵、iOS Gate、G0 yaml、`repositories.json`、`reference_repositories.json` 等)。
4. 产出执行前摘要(产品初衷 / 职责 / 数据关系 / Android First 顺序 / G0 允许与禁止 / 预计文件 / 阻断项)。
5. 识别 3 个阻断问题,向 Jovi 提交结构化决策问询 → 被中断,切换到 N0 关闭任务。

### 阶段 2:工程B N0 关闭记录整改 — 已提交,质量门禁为红,待复核

1. 核查发现关闭提交 `6ce5cac docs(n0): record independent review and reconcile test evidence` **已由前序执行创建**,工作树干净(阶段1 的 35 个未提交修改已被前序智能体提交)。
2. 逐条核验 `6ce5cac` 是否满足 10 条要求:
   - 父提交 `229be08` 保留(未 amend/reset/rebase)✓
   - `reports/N0_independent_review.md` 已新增跟踪 ✓
   - `reports/N0_test_evidence.md` 第 23 行已由 "32 passed" 修正为 "33 passed" ✓
   - Reviewer 报告 8 项要素齐全(审查 commit、22 条实跑命令、`PASS_FOR_OWNER_REVIEW`、blocking=None、5 项 non-blocking、未批准 N0、未改 PROJECT_STATE、未启动 N1)✓
   - 4 项 non-blocking(verify_handoff/.venv 发散、Python 3.11、catalog_version=unknown、source_runtime_separation SKIPPED)均保留未修 ✓
   - diff 仅 2 个报告文件,禁改区零改动 ✓
3. 本轮实测重跑 6 个质量命令(pytest / ruff check / ruff format --check / mypy / `run_quality.py` / `sensitive_file_scan.py`)。
4. 发现 pytest FAIL,定位根因,如实上报,发出停止消息(附质量门禁为红的提示)。

---

## 三、遇到的错误与处理

### 错误 1:工程B 已被前序智能体推进到 N0,与"G0 only"边界冲突
- **现象**:G0 任务要求工程B 创建 `docs: initialize...` 初始化提交,但工程B 已有 N0 提交 + 35 个未提交修改 + master 分支 + 无 remote。
- **根因**:前序智能体已执行 N0 scaffold,越过了 G0。
- **处理**:按 AGENTS.md"不覆盖未提交修改"、按任务"禁止 force push/删历史",识别为阻断,向 Jovi 提供 3 个选项(保留现状补绑定 / 本轮只做A / 回退B)。
- **状态**:**未解决,被中断,待 Jovi 决策。**

### 错误 2:工程A 模板同名不同内容冲突
- **现象**:复制 `project-templates/ai-photography-director-app/` 到工程A 根时,`README.md`/`AGENTS.md`/`CLAUDE.md`/`OPENCLAW.md`/`OWNER_INPUTS_REQUIRED.md` 与交接包根已有版本同名但内容不同(项目群级 vs 项目级)。
- **根因**:交接包根 = 工程A 目录,双重身份导致模板复制必然撞名。
- **处理**:按任务"同名不同内容必须停报,不得静默覆盖",识别为阻断,向 Jovi 提供 3 个选项(项目版入库+包级改名 / 项目版覆盖丢弃包级 / 包级保留+项目版改名)。
- **状态**:**未解决,被中断,待 Jovi 决策。**

### 错误 3(关键):pytest FAIL — AT-N0-GIT-01 与关闭流程固有冲突
- **现象**:在 `6ce5cac`(HEAD)重跑 pytest,`tests/test_git.py::test_at_n0_git_01_one_isolated_commit_no_sensitive_files` FAIL,`1 failed, 32 passed`;`tools/run_quality.py` 报 `QUALITY_GATE_FAILED`。
- **根因**:`AT-N0-GIT-01` 断言 `git rev-list --count HEAD == 1`(N0 实现必须是单个孤立提交)。在 `229be08`(1 commit)时该测试 PASS、pytest=33 passed;关闭提交 `6ce5cac` 是第 2 个提交,断言必然失败。这是关闭流程与 N0 门禁测试的**固有冲突,非代码回归**(关闭提交只动了 2 个报告文件)。
- **处理**:本轮第 7 条禁止修改 `tests/test_git.py`,故**无权在本轮修复**。如实上报根因 + 处置建议,发出停止消息交 delta Reviewer 裁决。
- **状态**:**未解决,等待裁决。**
- **建议处置(非本轮)**:在单独授权轮次把 `AT-N0-GIT-01` 改为"N0 实现提交 scoped"或放宽为">=1 commit 且无敏感文件",而非断言恰好 1 个提交。

### 已解决的部分(诊断与核验层面)
- 通过 `git diff --name-status 229be08 6ce5cac` 确认关闭提交范围合规(仅 2 报告文件)。
- 通过 `git diff 229be08 HEAD -- PROJECT_STATE.json` 为空 + `cli.py` 仅 3 个 `@app.command()` 确认 PROJECT_STATE 未改、N1 未启动。
- 通过 `git ls-files` 排查确认工程B 无 `.venv`/缓存/DB/模型/`.env` 被跟踪(隐私面干净)。
- 定位 pytest FAIL 根因为 commit 计数断言,排除代码回归。

---

## 四、当前状态快照

| 工程 | 状态 | 阻断/待裁决 |
|---|---|---|
| 工程A | 未 git init;交接包根已就绪 | G0 阻断:模板同名冲突(错误2)+ 入库范围,待 Jovi 决策 |
| 工程B | HEAD=`6ce5cac`(N0 关闭提交);`229be08` 保留;工作树干净;PROJECT_STATE 未改;N1 未启动;分支 `master` | 质量门禁为红:`AT-N0-GIT-01` FAIL(错误3),待 delta Reviewer 裁决 |

---

## 五、接下来的计划

### 短期(等待 Jovi / Reviewer 决策)
1. **工程B N0 关闭裁决**:等 Jovi 裁定 `AT-N0-GIT-01` 的 FAIL 是否可作为"关闭后预期产物"接受。
   - 接受 → delta Reviewer 复核 `6ce5cac` 文档增量提交,N0 关闭收尾。
   - 不接受 → 单独授权轮次修测试(scoped / 放宽),再重跑质量。
2. **工程A G0 阻断决策**:等 Jovi 回答 3 个问询(工程B 处置、A 模板冲突版本、A 入库范围)。
   - 决策后执行:模板安全复制(dry-run → apply)→ 新建 `.gitignore` → `git init` + `remote add origin` + `branch -M main` → 隐私审计(git add 前后各一次)→ 首个 commit + push → 生成 G0 报告。

### 中期(N0 关闭 + G0 完成后)
3. **Pipeline N1**(工程B):仅在 Owner 更新 PROJECT_STATE 解锁 N1 后;配置 `NPI_SOURCE_ROOT`/`NPI_RUNTIME_ROOT`(解决 N5)、3.11 验证决策(解决 N3)、`verify_handoff` 排除 `.git`/`.venv`(解决 N2)。
4. **App AH0**(工程A):Windows Android Studio/JDK/SDK/Gradle/ADB 环境盘点;`android/` 创建 Kotlin/Compose 工程壳;CameraX Preview+ImageCapture+ImageAnalysis+权限;两台设备矩阵。

### 长期(严格遵守 Android First)
5. AA0:Pose Engine Spike(MediaPipe vs ML Kit,必要时 MoveNet)→ ADR 选唯一引擎。
6. AA1:参考图复刻 MVP(Bundle Importer + Overlay + 单条指令 + AI 失败仍可拍照 + 真机+用户任务)。
7. Android MVP Gate(两台真机 10/30min + 5–10 用户 + 成片对比)。
8. Gate 通过后才:跑 500–600 图库 / 场景导演原型 / 受限 OpenClaw。
9. iOS0:Android MVP + 用户Gate + Bundle v1 + 100图Pilot + Jovi书面批准后才启动。

---

## 六、经验教训(本轮)

- **范围边界优先**:本轮被反复要求"只动报告文件/不得扩大 diff"。任何顺手修复(如错误3的测试)都必须忍住,交由授权轮次处理。
- **如实上报优于自作主张**:错误3发现质量门禁为红时,选择如实上报+停止,而非擅自改测试或假装通过。
- **前序智能体遗留状态需先核查再动手**:工程B 的 N0 状态、`6ce5cac` 的已存在,都是在动手前通过只读核查发现的,避免了重复提交或覆盖未提交修改。
- **测试不变量与流程冲突**:N0 门禁测试断言"恰好1个commit"与后续任何关闭/文档提交天然冲突,设计门禁测试时应使其 scoped 到具体提交而非 HEAD 计数。
