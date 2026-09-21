# P25T 本地接手：验证可见产品功能，不再循环等待空的人审回执

## 本轮唯一任务

`P25T_LOCAL_ANDROID_PRODUCT_QUALIFICATION_AND_REMEDIATION`

仓库：`Jovifei/ai-photography-director-app`。
继续同一远端分支：`codex/p25s-internal-handoff-validation-20260920`。
起始审查 SHA：`61a7c1da92c51b791aac4ea094e6efa56adf474e`。
本轮源码候选：`9ea075a95891faa68db45cd436096ea9c94a21d8`；之后有 docs-only 交接提交，以 fetch 后实际 HEAD 为准。
预期 main：`1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`，本轮未更新。

已提交代码：
- `d720640ecc5d170f6867e1fff5912776cd3a9630`：交接 fixture 边界/反向绑定/九字段逐项核对。
- `6569ef623a38eae4f900315fb2deed45d5ce77df`：看图绑定、九项详情、导演卡光线/构图、可选准备清单。
- `9ea075a95891faa68db45cd436096ea9c94a21d8`：七项 Android 测试和 lazy-list 测试标记。

先读 AGENTS、PROJECT_BINDING、当前阶段文档：
- `reports/P25S_REVIEW_AND_P25T_PRODUCT_PROGRESS_20260921.md`
- `docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md`
- `docs/handoff/P25T_SOURCE_MANIFEST_20260921.json`
- `docs/handoff/P25T_STAGE_STATE_20260921.json`

## 1. 保护 Owner 与同步

只读检查 origin、status、worktree，再 fetch。Owner 主树不 checkout/pull/reset，先在仓库外记录修改/未跟踪文件原始哈希，结束时比较。
禁止 git clean、整包 stash、reset --hard、force push、修改全局 Git、覆盖 tasks 或旧 worktree。

在新的隔离 linked worktree 建立本地验证分支，例如 `codex/p25t-local-validation-20260921`，基于实际 `origin/codex/p25s-internal-handoff-validation-20260920`。同名路径已存在时先检查，不删除重建。

后续修复允许非 force 推进原远端 P25S 分支；推送前重新 fetch，确认没有他人新提交。出现推进先读差异，不覆盖。不得合入 main。

## 2. 字节与测试身份

新 manifest 14 项绑定 source commit `9ea075a...`。从 Git object 读取，而不是 Windows CRLF checkout，核对 Git blob/原始 bytes/SHA-256；再确认最终 candidate 中这些源码未漂移。
历史 P25/P25R/P25S 清单继续绑定各自历史提交，不能强行让它们匹配新代码或抹掉历史证据。

网页真实已运行：16 个 Python fixture 单元测试、34 个实际 Kotlin preparation 断言、物化源码快照的 compileall/privacy/diff。完整 Windows/Gradle/Compose/Android 尚未执行；不要照抄历史 130/130 或此处数字为新候选 PASS。

## 3. Python 和核心策略

运行：

    python -m unittest discover -s scripts -p "test_p25*.py" -v
    python -m unittest discover -s scripts -p "test_p23c*.py" -v
    python -m compileall -q scripts

可选（已有 kotlinc/Java 才运行，不为这个检查下载）：

    python scripts/test_p25t_director_core.py

没有 kotlinc 时记 NOT_RUN，实际策略还需 Gradle JVM 回归；不能把 zero tests 写成 PASS。

确认 fixture 拒绝非法根/错误 hash 格式/重复证据/错误类型/危险文本，仍固定 synthetic producer/release；只对 checked-in JSON fixture 规范化 CRLF。外部 corpus/review 文件字节和摘要不能自动改写。

## 4. Android 编译与运行

先完整构建 Debug/Test APK、全量 JVM、lintDebug/lintRelease。本轮改变了实际 UI 和 mapper，必须编译，不是仅文档审查。

P24 身份已存在；本进程无 PHOTOAI_RELEASE_* 时记录环境输入 BLOCKED，按已有外部签名流程加载，不能重新 bootstrap 密钥。排除 release guard 的 Debug 静态检查不得称为 signed-release PASS。

仅使用明确验证过的专用 API35 emulator（serial 前缀、SDK35、qemu=1）；所有 adb 绑定 serial。旧 AVD 曾失败不代表新 AVD 仍失败，按最新可用环境执行，不用实体手机替代。

先运行：
- `com.jovi.photoai.p25t.P25TDirectorPreparationAndroidTest`：3 方法。
- `com.jovi.photoai.p25t.P25TBundleMappingAndroidTest`：4 方法。
- `com.jovi.photoai.p25s.P25SInternalHandoffAndroidTest`：原 2 方法加强版。

然后回归 P22 系统 OpenDocument、P23R 同帧 Back/真实 ViewModel/Room、P23C Parser/Room，P23D 按已有外部 runner 分步执行（不能把依赖顺序的 prepare/verify 当无序独立测试批跑）。补全全量 JVM、Bundle/Phase1.5/service 与必要备份/导出流程，报告实际 pass/fail/skip。

## 5. 真正验可见功能

A. 用程序生成的不同颜色/比例 JPEG 导入一个二十张项目（不是私人照片）。新代码的元数据-only Room fixture 不证明缩略图成功。
B. 从完整系统选择文件链导入 synthetic Bundle；在“看图绑定”中故意打乱映射，确认预览、解绑、已占用不可抢、READY不可覆盖。
C. 完整九项详情逐项可见；中文/长字段不溢出；当前项目之外的 rows 不出现、不让提交启用。
D. 绑定后进入分析→导演卡→Camera Director，导演卡不再称当前真实参考为固定示例，光线/构图可见。
E. 五项准备可选；0/5 仍可进入拍摄，勾选不改 READY/provenance；重置、重组、saved state、内容切换不串进度。离页不承诺项目持久清单。
F. 提交期间选择/详情/解绑全部禁用；同帧 Back 和旧回调继续由原 ViewModel gate 防护，不以按钮变灰代替验证。
G. 100%和200%系统字体真实截图检查长字段、缩略图、对话框滚动、底部按钮可达；结束恢复 font_scale。StateRestorationTester 不是 OS force-stop 的替代证据。

若旧 mapper 单测对 environment 有精确旧文案预期，更新到包含 scene/background/light/composition 的真实新要求并保留字段哨兵断言，不删除失败测试。

## 6. 不扩大本轮证明

这些是参考指导/手动准备功能，不是实时 Pose、现场图像理解、完整作品库或已接通 Pipeline。相机 live analyzer 仍未接模型。当前 v1 Bundle 仍无图片，也没有来源签名认证。

Pipeline 此次读取 main=`ffc4130823c1308f089b835c766e341ec2173e82`、Real20 R0=`50d9ffac597570ceba3bfea44ff9dc6448ba85c0`。它的目录式 Photo Intelligence Bundle 和 App PKB1 不是同格式。不得直接拷贝 sibling DB、冒充 exporter 或把 editorial 改 origin 假装照片模型输出。

不要再把人审模板为空当所有工程的总阻塞。用户已授权内部验证/AI预审；本轮保持真实人审0/20，不伪造批准。需要真实素材/设备/密钥决策时集中询问一次，代理整理可读材料和JSON，不要求 Owner 手工编码。

## 7. 修复、提交、交接

本轮范围内实际错误可直接最小修复、补测试、非 force 提交，无需再申请同一范围授权。将复现→原因→修改→测试记录清楚。独立审查必须绑定最终源码及文档候选；不能宣称调用了一个实际不存在的独立 agent。

提交分组：UI/生产修复，测试，Git-object manifest与报告。push 前完整仓库 privacy 和 git diff --check；不上传照片、数据库、截图日志、APK/AAB、签名材料或Owner哈希路径清单。报告使用逻辑证据ID，不暴露私人目录/实体设备标识。

成功状态：`P25T_ANDROID_VALIDATED_AWAITING_INDEPENDENT_REVIEW`。
失败/部分完成：`P25T_BLOCKED_WITH_EXACT_GATES`，逐项列出，不把模型/人审后续未启动混成UI失败。

输出：原分支/新HEAD、每段提交、manifest14或更新后的实际数、Python/JVM/Android/截图/签名结果、Owner前后差异、未完成项及独立Reviewer prompt。完成本轮后默认下一任务为成片预览/项目关联/导出与恢复闭环（T2），不是再生成一份人审模板。
