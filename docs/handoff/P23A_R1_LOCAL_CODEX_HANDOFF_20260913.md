# 本地 Codex 执行提示：P23A-R1 修复、Android 复验与远端提交

继续现有 AI 摄影导演 App，不重新规划、不从 Demo 开始。
当前唯一任务：`P23A_R1_REVIEW_FINDINGS_REMEDIATION_AND_ANDROID_REQUALIFICATION`。

## 入口和基线

仓库：`https://github.com/Jovifei/ai-photography-director-app.git`
本地验证分支：`codex/p23a-local-validation-20260913`
审查失败候选：`be040188c93d7613d9433a23e0d1f5d640e317f1`
P23A 实现祖先：`43b1dcd23ca0b2ad621c9dfff960ea3e18ac7d3b`
P22 基线：`902072065efe5bc2da143a26ad05119f46e6ed8c`

新的材料是用户提供的 `P23A_R1_20260913.zip`。
网页端已编写修复、测试与应用脚本，但本轮 GitHub 只有读接口，因此没有新提交或新远端 SHA。
不要误以为 git fetch 就能获取本轮修复；必须读取附件内的源码/规则并应用。

最高事实是：独立审查 REQUEST_CHANGES 尚未解除，main 仍禁止合入。
修复两个 P1（分析覆盖 Bundle、提交异常被当作确定回滚）和两个 P2（同帧导航、测试覆盖缺口）。

## 安全准备

先只读检查 remote、status、worktree 列表，确认 origin 后 fetch。
在 Owner 主工作树以外保存改动/未跟踪文件的相对路径、大小和 SHA-256；不读/上传私人媒体正文。
保护 `tasks/todo.md`、`tasks/lessons.md` 及其他 Owner 文件，不整包 stash、不 clean、不 reset --hard。

核对远端验证分支仍精确等于 be040188... 后，才从它建立一个新的隔离 linked worktree：
建议分支 `codex/p23a-r1-review-remediation-20260913`，路径自行选择不存在的目录。
先检查本地/远端同名分支和目录；存在则读取状态，不删除、重置或强推。
若远端有新提交，先读差异、继续最新成果；不得为了通过应用脚本回退远端或用户分支。

阅读 AGENTS、项目绑定、P23A 本地报告、用户给出的独立审查 findings，以及修复包：
1. `00_READ_ME_FIRST.md`
2. `REVIEW_AND_REPAIR_REPORT.md`
3. `edits.json` 与 `payload/`
4. `apply_repair.py`
5. `SHA256SUMS.json` 与 `verification/`

附带脚本只用于匹配精确基线。包放仓库外，先检查源码和校验清单，再 dry-run：

```powershell
python <解压包目录>\apply_repair.py --repo <新工作树>
```

取得 DRY_RUN_PASS 后输出仓库外完整 diff，审阅后应用：

```powershell
python <解压包目录>\apply_repair.py --repo <新工作树> --write --diff-out <仓库外且尚不存在的补丁文件>
```

任一 Git blob、锚点或路径检查不符就停止自动应用，阅读实际差异再移植，不关闭校验来“成功”。
应用不会暂存、提交或联网；成功标记仅 APPLIED_NOT_COMMITTED，不表示 Android 已通过。

## 修复说明和必须审查的关键点

- Room 从 v5 到 v6，添加 nullable analysisAttemptId，旧 v4→v5 测试相应验证到 v6。
- Coordinator 在挂起排队前持有 token，排队/运行/持久化均在 Room 事务内重新读取和检查状态、来源与 token。
- 旧任务不得覆盖 Bundle、更新新重试、取消新重试或复活删除记录。
- Bundle 必须完整预校验后再写入，数据库异常返回 OutcomeUnknown；CancellationException 传播。
- ViewModel 对未知结果不展示“保持原状”，不保留可盲目重试的预览。
- 父路由与所有返回回调使用同步 tryLeave；子 BackHandler 不等待重组。
- ProjectSummary 写回检查最新输入摘要，Bundle-only 来源不伪造 Provider 语义汇总。

请先 `git grep` 查找 markAnalysisQueued、markAnalysisRunning、persistAnalysis 所有调用者。
这些是内部 API，有签名变化。若当前分支其他测试调用了旧签名，请使用真实 token 生命周期适配，
不能为了通过编译保留无 token 的绕过入口。检查 sealed OutcomeUnknown 的全部 when 分支。

## 本地验证

网页实际运行：156 个生产策略/异常分类核心断言、12 个补丁应用安全测试。
网页未运行 Android，20 个新增 Android 方法只是已编写，不能沿用旧 128/128 做本轮结论。

1. 完整 Debug/Test APK 构建、JVM、lintDebug/lintRelease，记录实际数量。
2. 签名输入缺失应单独 BLOCKED，绝不创建新生产密钥、不修改 signing guard。
   若 Debug/Lint 命令错误地被 release signing guard 挡住，先记录标准命令与错误；
   可沿用已审查的仅 Debug/Lint 排除该任务方法，必须在报告中注明，不得宣称签名验证通过。
3. 只绑定已确认的专用 API35 emulator；禁止无设备限定的 instrumentation 误触实体机。
4. 新增六个 Android 测试类共 20 个方法：
   - P23RRepositoryAndroidTest：9
   - P23RCoordinatorAndroidTest：3
   - P23RMigrationAndroidTest：1
   - P23RViewModelRoomAndroidTest：3
   - P23RBundleUiAndroidTest：3
   - P23RUtf8AndroidTest：1
   包名均为 com.jovi.photoai.p23r。
5. 回归既有 P21/P22/P23A、系统 OpenDocument/Photo Picker、Room 旧库升级、拍摄导出、官方 Local/D2D。
6. 明确验证 Bundle 先赢、分析先赢、真正并发、第二行触发 SQL ABORT、endTransaction 已提交后抛异常、
   取消回执、旧响应对新 token、删除后迟到结果、排队回执丢失后的释放。
   必须是实际 Repository/Room；测试包装器未命中时修正测试，不替换为 fake apply。
7. 同一个主线程回调内 apply→系统 Back→旧按钮回调，不给 Compose 空闲帧；检查真实屏幕与父路由。
8. 1×/2× 导入页长 producer/release ID、64 字符摘要的换行、可达性、无障碍。
   包内是 LocalDensity 测试；额外跑实际系统 100%/200% 字体，保留合成界面截图供审核。
9. Activity recreation 不等于 OS process death：额外在合成数据上验证提交中进程销毁、重启、恢复，
   要求无假成功、无盲目重试、无损坏来源、无永久锁定。无法自动验证就准确保留 NOT_RUN。
10. 项目官方合同、service、Python compileall、完整仓库 prepush_privacy_audit.py、git diff --check。
    PKB1 与 shared-contract 未变，不得添加跨仓库自动运行。

本轮 Room/Coordinator 已变化，旧 P20 实测照片的 canonical 证据不再等价；
用合成 Provider 进行集成测试即可，不得为刷新旧证据启动被禁止的真实 Qwen/LAN。

## 分阶段提交

在独立修复分支完成以上实际检查并修复失败后，逐段非 force 提交：
A. 事务内 token、Coordinator、Room 迁移和数据库并发测试；
B. OutcomeUnknown、同步导航、ViewModel/界面测试与非法 UTF-8 回归；
C. 脱敏验收报告、完整源码 manifest、接力提示。
可以调整提交边界以确保每个提交可编译，不得拆出暂时失配的内部 API。
仅暂存明确文件，不 git add 全工作区，不动 Owner tasks 文件。
包生成的 R1 manifest 是应用时快照；本地若继续改动，另生成与最终源码一致的清单，保留历史快照。

允许把本轮代码/测试/脱敏报告推送至新的修复分支，并创建指向验证分支的 draft PR。
不要覆盖旧验证分支或旧实现分支，不直接合并 main，不把自己的测试当成独立审查 PASS。
推送前再 fetch 确认远端未被其他 Agent 推进，运行隐私扫描和 diff 检查。

结束时输出：远端分支、每个 SHA、精确审查候选、改动内容、测试分层、NOT_RUN/BLOCKED、
Owner 文件哈希复核、新的独立 Reviewer 提示。没有实际 push 成功就不要编造远端 SHA。

合格终点：`P23A_R1_ANDROID_REQUALIFIED_AWAITING_INDEPENDENT_REVIEW`。
测试失败或环境不齐则：`P23A_R1_BLOCKED_WITH_EXACT_GATES`。
最终仍需要独立 Reviewer 针对新的精确 SHA 审查，不自动进入主线落地。

禁止：Qwen/真实照片、LAN/防火墙、Pipeline、真实生产 Bundle、模型下载/替换、签名密钥生成、
实体设备、Cloud、iOS、main 合并和公开发布。
