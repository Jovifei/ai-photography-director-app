# 本地 Codex：P23C 离线交付准备复验

任务：`P23C_LOCAL_PACK_PREPARATION_QUALIFICATION`。
直接使用远端提交，不再应用旧 P23A-R1 ZIP，不重新规划，不继续无必要地修改已审查生产代码。

## 0. 远端状态与授权边界

- App 仓库：`Jovifei/ai-photography-director-app`
- main 已由网页端按 Owner 本轮委托非 force 更新为：
  `f2d6a87f85ba3b2ddd9f83544a6d912890019e7c`。
- 两个父提交：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`、
  `e4a954b313320944fe751a2388366ee72d3e9083`。
- 相对 e4a 已审查树只有 tasks/todo.md、tasks/lessons.md 两项删除，未清理旧分支/历史。
- 本轮功能分支：`codex/p23c-public-pack-preparation-20260916`。
- P23C-01：`b9e52345e5127f587ffe977d85c02ba9b5fe6d95`。
- P23C-02：`787bbde15b9beaa9f45da761006ab56fb04ae5a5`。
- P23C-02b（最终源码绑定/CRLF测试修正）：`ae29bc32c122830cd6d03b06ec647ca73ae51759`。
- 本文位于后续 docs-only 提交。最终候选使用 fetch 后实际分支 HEAD。
- P23B 工具验证分支 `34949f81...` 没有并入当前 main 或 P23C，不要混合旧工具栈。

这是代码合入，不是签名/发布或公共素材授权。此次不再把旧 `61a9b26` 当作应恢复的 main。
Owner 本地旧 HEAD 和18项文件未被网页端修改，继续保持安全快照，不做主树 reset/pull/clean。

## 1. 起步

只读核对 remote/status/worktree，保护 Owner 修改及未跟踪文件，然后 fetch。
发现远端已推进先读差异，不回退到本文 SHA，不覆盖已有分支。
从实际 P23C HEAD 建立新隔离 linked worktree，可用分支
`codex/p23c-local-validation-20260916`；先检查路径/分支未占用。

先读：AGENTS.md、docs/P23C_PUBLIC_KNOWLEDGE_PACK_PREPARATION.md、
reports/P23B_LANDING_AND_P23C_IMPLEMENTATION_20260916.md、
docs/handoff/P23C_SOURCE_MANIFEST_20260916.json、
docs/handoff/P23C_STAGE_STATE_20260916.json。
旧 G0/AH0/P22 文档是历史记录，不重新开启模型选型。

## 2. 核对代码与哈希

从 Git 对象读取 manifest 的 source_commit:path，而非 Windows 工作树原始字节，验证6项
Git blob OID、byte count、SHA-256；再检查当前候选各路径与 source_commit 相同。
参考提交对象命令为 `git cat-file blob <source_commit>:<path>`；不要使用 clean filter 生成“审批字节”。
Windows CRLF 工作树哈希用于 Owner 本地快照，不能拿来比源码 manifest。

检查 f2d6a87..P23C 只新增 P23C 代码、测试、test-only JSON 与文档；
android/app/src/main、Room、Provider、shared-contract、签名配置、旧manifest和tasks不应变化。

## 3. Windows 工具验证（本阶段必做）

```powershell
python -m unittest discover -s scripts -p "test_p23c*.py" -v
python -m compileall -q scripts/p23c_bundle_contract.py scripts/p23c_prepare_review_pack.py scripts/test_p23c_bundle_contract.py scripts/test_p23c_prepare_review_pack.py
python scripts/p23c_prepare_review_pack.py inspect --bundle android/app/src/androidTest/assets/p23c/roundtrip.bundle.json
```

Linux 原结果56/56不是 Windows 结果；记录实际 executed/pass/fail/skip。
权限导致 symlink/hardlink skip 必须保留，不能记为 PASS。Windows junction/reparse、
NTFS 硬链接发布、网络路径、已存在输出、跨仓库输出、输出中断需补实际平台测试。
不支持原子硬链接发布时精确记录 BLOCKED，不改成覆盖式 rename/replace。

在新的仓库外 evidence 目录执行 review-template，确认 null 身份+所有PENDING；
将它传给 prepare 应返回2、且没有最终 ZIP。
成功路径仅复用 test_p23c_prepare_review_pack 中的合成 fixture/模拟回执机制：
1条、20条都 prepare/verify；同字节输入重复生成到不同新路径应得到相同SHA；
改内容但不改digest、改digest但不改receipt、改用途、缺项/重复项、改archive manifest都应拒绝。
测试中的 APPROVED 只是合成断言，不记录为人工审核，不包装成真实公共素材。

任何 ZIP 输出都应 `UNSIGNED_NOT_FOR_DISTRIBUTION`，且 publisher_authenticated、
app_import_authorized、release_authorized 全部false。verify-candidate不解压、不安装、不导入。

## 4. Android 对接验证（新增4个方法）

在已有 Android SDK/JDK 环境构建 Debug 与 AndroidTest APK，运行完整 JVM 和 lint。
只能使用确认SDK35、ro.kernel.qemu=1且serial为emulator-*的专用模拟器；所有adb命令显式-s。
不操作实体设备。不为通过检查修改签名guard；签名材料缺失单独列BLOCKED。

新增类：`com.jovi.photoai.p23c.P23CPythonBundleCompatibilityAndroidTest`（4个方法）。
命中真实 Android Parser，验证 Python golden PKB1、中文/emoji/组合字符、JSON CRLF与篡改拒绝。
本轮网页端没有编译/执行这4项。Test APK 中的JSON不能移到 main/assets 冒充公共内容。

随后回归原 P23R 28项、既有38项、JVM、Bundle12、Phase1.5、service5、compileall、privacy、diff，
记录实际数量与原因；不要直接填写旧130/130为本轮结果。
端到端合成导入仍走现有系统OpenDocument和真实Room显式映射；ZIP不能交给App直接打开。
若现有测试不覆盖新Python文件生成的20条输入，增加test-only Android测试，
对20张程序生成的参考图显式绑定、持久化、重启读取；不得读取Owner真实照片。

OS process death 的 READY/provider/source/summary 是继承未完成项，不要将之前IMPORTED
force-stop通过外推为这些状态通过；本阶段有条件补合成验收，无条件则明确NOT_RUN。

## 5. 内容、证据与签名边界

此工具只核验回执结构和声明与文件的绑定，不证明审核人身份、权利证据或原始AI推理。
无真实公共语料和生产资格时只能说明准备能力可用；不得自行把PENDING改为真实APPROVED。
PUBLIC_CANDIDATE模式通过也不是导入/分发授权。

本轮不调用 Qwen、真实LAN、防火墙、Pipeline、真实照片、模型、Cloud、iOS、签名密钥或发布。
APK签名缺少PHOTOAI_RELEASE_STORE_FILE维持BLOCKED；知识包签名/信任根是不同的后续事项，
不能重用/创建密钥绕过，也不能将SHA-256称作签名。

## 6. 提交与阶段终点

允许本阶段真实工具/测试问题的最小修复，逐段非force提交到独立验证分支；无需再次请求同范围授权。
不改Owner tasks，报告写P23C专属report/handoff。只有源码变化才生成新的source_commit清单，
保留历史manifest，先提交源码再从真实Git对象生成清单。每次push前全仓库privacy+diff检查。
不上传外部输入/回执/候选ZIP、原图、模型、数据库、设备serial或私有路径日志。

交付实际远端SHA、逐提交目的、6项哈希、56项平台测试、新增4项Android及真实集成结果、
Owner前后差异、BLOCKED/NOT_RUN和独立Reviewer的精确候选。
成功：`P23C_LOCAL_VALIDATED_AWAITING_INDEPENDENT_REVIEW`。
失败/部分完成：`P23C_BLOCKED_WITH_EXACT_GATES`。

不要自动合并P23C到main；既有main=f2d6a87的合入不撤销。
后续内容任务是独立批准公共语料和生产栈后生产真实20条、人工审查、确定知识包签名信任和批准交付，
再用本工具校验/准备并经单独授权进入App。Pipeline的当前SHA须在该任务中重新读取，不能沿用旧交接值。
