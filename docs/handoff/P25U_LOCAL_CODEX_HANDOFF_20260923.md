# P25U 本地 Codex：接收已有成片代码，完成 Android 资格

唯一任务：`P25U_LOCAL_ANDROID_CAPTURE_QUALIFICATION_AND_REMEDIATION`。
不要从 T2 方案重新开始；云端已在原分支实现产品代码。

## 1. 远端与保护

仓库：`Jovifei/ai-photography-director-app`。
继续原分支：`codex/p25s-internal-handoff-validation-20260920`。
本轮输入：`11342a3a441cb03826b85f46f3932a98bd98930f`，P25T PASS 为 Owner 已提供结果。
P25U 源码：`5a6968635df89f99e44ea15896d0dd6898df6f38`。
后面有 docs-only 提交；先 fetch，读取实际最终 HEAD，不回退后续成果。
预期 main：`1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`，本任务不自动修改。

只读核对 origin/status/worktree；主工作树 dirty 不阻止在隔离 linked worktree 执行。
保存 Owner 当前改动/未跟踪文件的路径、大小、哈希到仓库外；不抄私人正文。
禁止 git clean、整包 stash、reset --hard、force push、覆盖 tasks 或更改全局 Git 配置。
可建立唯一的本地验证分支/worktree，从实际原远端分支起步；检查后续并发推进后，非 force 推送回同一原分支。

## 2. 必读与源码绑定

- AGENTS.md、PROJECT_BINDING.json（旧 kickoff 不重置当前阶段）
- docs/P25U_CAPTURE_RESULTS_AND_OPEN_SOURCE_20260923.md
- reports/P25U_REMOTE_IMPLEMENTATION_20260923.md
- docs/handoff/P25U_SOURCE_MANIFEST_20260923.json
- docs/handoff/P25U_STAGE_STATE_20260923.json
- 本次新增 data/capture/、ui/capture/、CameraScreen、CameraXManager、CaptureExporter、根导航。

从 source_commit 的 Git 对象验证清单 21 项 OID、原始 bytes、SHA-256；再核对最终候选同一路径未漂移。
不要使用 Windows CRLF checkout hash 替代源码 Git bytes。保留历史清单，不改写旧审查记录。
已通过的 P25T 不需重新从零整改，但 P25U 修改了生产代码，需要独立的新候选审查。

## 3. 理解本轮架构，不能套用错误迁移

- 新增独立 CaptureLibraryDatabase v1；旧参考库仍 v6，本轮没有 v6→v7 migration。
- 原片和新数据库在 noBackupFilesDir/capture-library；不是 cache、不是 MediaStore。
- 退出/进程恢复可继续读，卸载/clear-data/换机不自动保留，界面已提示另存副本。
- 不修改原参考图库备份/D2D策略，不宣称新原片可自动迁移。
- 删除项目只解绑成片，删除参考图不删成片，删除私有成片不删外部副本。
- 文件 rename 与 DB 是可恢复协议，不是跨资源原子事务。
- 导出 token 绑定 captureId/processId；前一进程的外部回调不自动重放写入。

## 4. 构建与宿主测试

已有 kotlinc/Java 才运行：

    python scripts/test_p25u_capture_core.py

本轮云端真实结果是98个核心断言，不是全 App JVM。缺 kotlinc 可记录 NOT_RUN，由 Gradle 的6个新增 JUnit 方法复验逻辑，不自动安装编译器。

运行 Debug/Test APK、完整 JVM、lintDebug/lintRelease；核对 CameraXManager.bindToLifecycle 返回 Boolean 和 CameraScreen internal 可见性对所有调用者的影响。
P24 长期签名身份已存在，只使用已授权的外部/DPAPI流程。缺环境输入单列 signing BLOCKED，不生成新密钥，不永久跳过 guard。
旧131项JVM不是本轮结果；记录新鲜测试数量、执行/缓存情况、skip和warning。

## 5. 专用模拟器与精确 class filter

任何安装或 instrumentation 前必须确认目标为已批准专用 AVD：emulator-*、SDK35、ro.kernel.qemu=1、boot_completed。
所有adb显式-s。不得触碰实体设备或wipe旧AVD。没有模拟器时保留实现并记录精确环境问题。
之前误跑92项的5个failure不能隐瞒或变为全量PASS；本次每次调用都必须有非空 class/method filter。

新增通常可独立运行：

- com.jovi.photoai.p25u.P25UCaptureRepositoryAndroidTest：7方法。
- com.jovi.photoai.p25u.P25UCaptureUiAndroidTest：3方法。
- com.jovi.photoai.p25u.P25UCameraCaptureAndroidTest：1方法，另传 `-e p25uDedicatedEmulator true`。

CameraX测试必须真实调用相机并生成JPEG，不能改为直接seed成功结果；它只证明Activity recreation，不是OS process death。
测试只清理本次创建的ID/目录，不能全库clear或删除Owner媒体。

磁盘进程测试必须单独有序执行，使用同一个全新32位小写hex run ID：

    -e class com.jovi.photoai.p25u.P25UDiskProcessRecoveryAndroidTest#prepare -e p25uPhase prepare -e p25uRun <run>

随后启动 com.jovi.photoai/.MainActivity，确认PID存在；外部 am force-stop，确认PID消失（pidof exit1可表示不存在，不等于ADB断开）。
再以新的instrumentation invocation执行：

    -e class com.jovi.photoai.p25u.P25UDiskProcessRecoveryAndroidTest#verifyAfterExternalForceStop -e p25uPhase verify -e p25uRun <same-run>

最后以同一run执行：

    -e class com.jovi.photoai.p25u.P25UDiskProcessRecoveryAndroidTest#cleanup -e p25uPhase cleanup -e p25uRun <same-run>

三者使用 `com.jovi.photoai.test/androidx.test.runner.AndroidJUnitRunner`。保存每步原始日志至仓库外；未运行/assumption skip不计PASS。
这个夹具验证真实磁盘Room与生产Engine，不证明默认App、系统选择器和SavedState已闭环。必须继续第6节。

## 6. 用户链与故障验证（可补最小测试）

A. 默认App建立项目A/B，各拍成片；核对快门时归属，故意切项目/退出/旋转/重复回调，不串图。
B. 从真实CameraX输出打开预览→继续拍摄→项目成片→全部/未归类；实际Bitmap可解码。
C. 默认成片库外部force-stop→重新打开App→画廊仍有正确ID、摘要、项目和导出状态。不得用isolated DB重开替代。
D. 实际系统CreateDocument成功、取消、旋转、离开相机后返回；结果属于原token，而不是当前最近照片。
E. 选择器期间、复制期间、文件完成/DB回执之间分别中断；未知结果不报成功、不自动再次写入；源文件保留。
F. open/write/flush/close失败，删除与导出竞争、数据库提交异常、缺失/损坏文件、磁盘不足；真实Room测试与fake ledger明确区分。
G. 参考图删除不删成片；项目删除后原片保留且未归类；只删除App内成片；误触删除有确认。
H. 八类EXIF方向、竖/横图、长项目名/文案、系统font_scale=1.0/2.0，精确断言系统值后再命名截图。
I. 留意 CameraX disposed callback、快速进入退出、真正bind失败不得CameraReady，CameraControl未新增不要求额外模型功能。
J. 核对预览内存、大量成片列表和满盘失败的可操作性；本轮没有分页或配额，不把它写成已实现。
K. 画廊详情中SAVED/UNKNOWN重复导出有确认，检查相机快捷保存入口是否需要同样的确认；只做最小一致性修复。
L. 复验旧参考库迁移和P23D/Local/D2D；新成片noBackup应验证“未自动传输”的设计，不以旧PASS冒充成功迁移。

## 7. 回归、提交与审查

执行当前指定 P25/P23C Python、P25T/P25S/Room/系统Picker/拍摄回归、合同、service、compileall、完整仓库privacy和diff。
不裸跑会触发P20真实Provider或有序夹具的全套instrumentation。每个过滤集、源码SHA、APK哈希和结果分别绑定。
允许在本任务范围修复实际编译/数据/生命周期/测试问题，补测试，逐段非force提交；不需要再次请求相同范围授权。
修改源码后先提交源码，再从Git对象生成新的清单与报告。不要为了文档引用自身HEAD循环生成提交。
推送前fetch，发现其他提交先读差异，不能覆盖。继续原远端分支并更新已有draft PR #6，不自动合main。
不要把本分支main之前所有未合入历史都当作本次小增量已审查。

截图、JPEG、数据库、日志、APK/AAB、keystore、设备ID和Owner路径哈希均留仓库外，禁止上传Git。
Owner前后快照一致必须实际核对；云端没有读取Owner磁盘。

## 8. 输出和停止条件

完整通过：`P25U_ANDROID_VALIDATED_AWAITING_INDEPENDENT_REVIEW`。
未完整通过：`P25U_BLOCKED_WITH_EXACT_LOCAL_GATES`。
输出实际分支、逐个提交、候选SHA、每个测试集合数量、截图结论、存储/备份限制、BLOCKED/NOT_RUN与独立Reviewer Prompt。
独立审查对象必须为最终P25U，不重复消费旧P25T PASS。没有独立agent能力就记录不可用，不能编造Reviewer。

不因人审0/20而停止这轮内部开发，也不把模拟回执升级为真人批准。
本轮不启用真实照片/Qwen/LAN/Pipeline/知识包签名/实时Pose/实体设备/Cloud/iOS/发布。
下一产品候选方向为取景器参考图对照、基本对焦曝光和一次真实受控拍摄试用，先完成P25U证据再扩展。
