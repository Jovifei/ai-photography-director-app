# 本地 Codex 接力：P23A Android 复验与修复

你接手的是现有代码，不是重新规划 AI 相机。当前任务仅为：
`P23A_LOCAL_ANDROID_QUALIFICATION_AND_REMEDIATION`。

## 固定入口

- App：`Jovifei/ai-photography-director-app`
- remote：`https://github.com/Jovifei/ai-photography-director-app.git`
- 实现分支：`codex/p23a-bundle-import-reliability-20260913`
- P22 基线：`902072065efe5bc2da143a26ad05119f46e6ed8c`
- 两条代码提交：`38fc1601f58a48fc78d88951779b66a732bda2da`、
  `43b1dcd23ca0b2ad621c9dfff960ea3e18ac7d3b`
- 分支随后包含本接力文档等 docs-only 提交；先 fetch，读取实际 HEAD。
- 开始本轮开发时 `main` 仍为 `61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`。

## 先核对，不破坏本地成果

只读查看 `git remote -v`、`git status --porcelain=v2`、`git worktree list --porcelain`；
确认 origin 后 `git fetch origin --prune`，核对远端 HEAD 和祖先关系。
若实现分支或 main 出现未在本交接记录中的变化，先报告精确 SHA、阅读差异，不得回退。
禁止在 Owner 主 checkout 开发。新建独立 worktree 和本地验证分支，例如：

```powershell
# 只在确认目标路径和分支尚不存在后执行；已有路径绝不删除或重置。
git worktree add -b codex/p23a-local-validation-20260913 ../photoai-p23a-validation origin/codex/p23a-bundle-import-reliability-20260913
```

对主工作树改动/未跟踪文件仅记录相对路径、大小、SHA-256，放仓库外，不复制文件正文。
特别检查 `tasks/todo.md` 和 `tasks/lessons.md`；本轮没有消除旧候选的路径冲突风险。
禁止 `git clean`、整包 stash、reset --hard、force push、rebase 或覆盖用户文件。

先读 AGENTS、P20/Beta/P21/P22 资格报告，以及：
- `reports/P23A_BUNDLE_IMPORT_RELIABILITY_20260913.md`
- `docs/handoff/P23A_SOURCE_MANIFEST_20260913.json`
- `docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md`

旧 AH0/G0 kickoff 文档不能重置当前阶段。本轮不修改 Pipeline，旧 Pipeline SHA 不能作为当前授权。

## 本轮远端已经实现什么

严格 JSON 预检；有界/可取消读取；异步读取 ticket 隔离；Picker 取消保留预览；
一对一映射及显式解绑；提交防重入和不确定结果提示；来源/版本/摘要显示及认证边界说明。
四组纯 Kotlin 核心共有 125 个断言已经实际运行通过。
11 个新增 Android 集成测试只已提交，没有运行。完整 Android、签名和独立审查均未完成。

## 在本地执行的检查

可选 SDK-free 核心复验（需要已有 kotlinc 和 Java，不为此自动安装）：

```powershell
python scripts/test_p23_bundle_core.py
```

已有 Android 环境下执行：

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

记录实际测试数量/失败，不要沿用旧报告的 124 或本轮的 125 当全量 JUnit 数量。
仅对已确认的专用 API35 `emulator-*` 运行 instrumentation；禁止不带设备限定的
connected 测试误触实体手机。先检查设备清单，沿用项目安全脚本并显式绑定模拟器，
不得清空、卸载或安装到 Owner 实体设备。

新增测试类：
- `com.jovi.photoai.p23.P23BundleParserAndroidTest`（6 个方法）
- `com.jovi.photoai.p23.P23BundleImportViewModelAndroidTest`（5 个方法）

同时回归既有 P21/P22、Room 4→5、事务/删除、系统 OpenDocument、Photo Picker、
capture/export、100%/200% 字体语义和官方 Local/D2D 流程。

重点复核与修复：
1. 标准 v1 包可以导入，PKB1 摘要保持兼容；宽松 JSON、尾随内容、重复键、孤立代理字符、
   超深嵌套、超大文件、无效 UTF-8 均拒绝；不修改损坏包来制造成功。
2. A 读取慢于 B、读取中放弃、系统 Picker 取消、ViewModel 清理，不得出现旧结果回写。
3. 点击已选目标可以解绑，不能抢占另一条映射；照片删除/进入分析后不得覆盖。
4. 提交中连续点击、立即系统 Back、屏幕返回、旋转、进程销毁；专门检查父级导航与子级
   BackHandler 的同帧窗口。必要时在 App 导航层增加同步 busy guard，并添加测试。
5. 注入异常时不得永久转圈；无法确认提交结果时不能写“项目保持原状”或盲目重试。
   审查 repository 层取消异常与事务回滚语义，使用真实 Room 测试而不是只测 fake。
6. 来源/版本/长摘要换行、绑定进度、TalkBack 标签、返回按钮状态；保持现有视觉主题。
7. 不把 digest 匹配、synthetic fixture 或自测当成生产来源授权/发布者签名/独立审查。

仓库根目录执行既有合同、service 和隐私检查；具体脚本以仓库当前清单为准：

```powershell
python scripts/test_photo_knowledge_bundle_consumer_contract.py
python scripts/prepush_privacy_audit.py
git diff --check
```

签名 APK/AAB 只使用 Owner 已有的外部签名流程，在明确授权后执行；缺材料则 BLOCKED，
不得生成新生产密钥。不得上传照片、数据库、模型、APK/AAB、原始输出、设备序列号或密钥。

## 提交与停止条件

只修复本轮范围内的实际问题，逐段非 force 提交；不关闭失败测试来“通过”。
可推送独立验证分支并报告与原实现分支的关系，不要覆盖未核对的远端推进。
输出代码 SHA、构建/测试/设备结果、隐私审计范围、尚未完成项、审查候选 SHA。

成功状态：`P23A_ANDROID_VALIDATED_AWAITING_INDEPENDENT_REVIEW`。
无法完整验证则：`P23A_BLOCKED_WITH_EXACT_LOCAL_GATES`，附已通过与失败证据。

本任务结束时不直接合并 main。由独立 reviewer 审查精确候选；Beta/P22 栈的落地还需
Owner 文件冲突检查、完整资格和合并决策。之后再单独进入受批准的公共知识包生产/导入。

严禁自动启动 Qwen、真实照片、LAN/防火墙改动、Pipeline、Pose、4B、RTMW、SAM2、Cloud、iOS 或公开发布。
