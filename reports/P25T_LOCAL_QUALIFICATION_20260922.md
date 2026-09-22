# P25T 本地 Android 产品资格验证报告

状态：P25T_ANDROID_VALIDATED_AWAITING_INDEPENDENT_REVIEW。仅为内部合成产品资格；独立审查尚待进行。

## 绑定

- 交付分支：codex/p25s-internal-handoff-validation-20260920；交付 HEAD 由该报告所在提交及远端 ref 确定，避免自引用 SHA。
- 原始接收 HEAD：a6b4cbeff3ae6da5c98ed0ebff280c4d238c4fd2。
- 最新测试源码：4c8adfa5cffed9fbc40405a3ed0b4b1d57b55795。
- 历史 14 项清单恢复绑定 9ea075a；新增 P25T_LOCAL_SOURCE_MANIFEST_20260922.json 绑定最新源码，17 项。
- 仅使用 emulator-5560，API35、qemu=1、boot=1；结束 font_scale=1.0，模拟器已关闭。
- main 未修改；草稿 PR #6。

## 实际结果及计数范围

| 项目 | 结果 |
|---|---|
| P25 Python | 43/43 PASS |
| P23C Python | 55 PASS / 1 SKIPPED，Windows symlink privilege |
| JVM | 131/131 PASS；后续相同 JVM 源码 Gradle 校验为 UP-TO-DATE |
| Debug/Test APK、Debug/Release Lint | PASS；最后 test-only 修改重新编译并完成 Lint |
| 初轮 P25T/P25S/P23C/P23R/P22 | 49 个方法 PASS，已包含真实 JPEG 类的 2 个方法，不能再重复相加 |
| 当前真实 JPEG/系统 OpenDocument/ViewModel/Room | 2/2 PASS；100%/200% 系统字体各重跑通过 |
| 导演卡字体测试 | 1 个方法在 100%/200% 各执行一次通过；属于两次执行，不是两个独立方法 |
| 追加拍摄/导出/P21 | 7/7 PASS |
| Bundle / Phase1.5 合同 | 12/12、75/75 PASS |
| service | 5/5 PASS |
| host Kotlin helper | NOT_RUN，缺少已安装 kotlinc；不下载编译器 |
| P23D 外部 OS process-death runner | PASS，独立 prepare、PID 存在、force-stop、PID 消失、新 instrumentation verify、cleanup |
| 官方 LocalTransport / D2D | PASS_LOCAL_TRANSPORT / PASS_D2D_TRANSPORT；脚本 COMPLETE、failure_code=NONE |
| Release signing | 已使用既有 P24 DPAPI 身份加载；verifyReleaseSigning 通过，APK/AAB 生成；APK apksigner 验证通过且匹配固定证书 |
| 历史 / 本地源码清单 | 14/14、17/17 Git blob、原始字节数、SHA-256 PASS |
| 隐私扫描 / diff / compileall | PASS |

签名证书 SHA-256：C62A1EE1F293CB596571135D37C9D51E7523E1639FBFAA01A6AB34D2528B457B。
前序 shell 缺少 signing 环境变量的记录是历史事件；本次加载现有身份后已解除。不生成新密钥，私钥和密码没有提交或输出。

## 可见产品证据

- 二十张不同尺寸/颜色的程序生成 JPEG 经真实 importIntoProject 导入，每张私有派生图可解码。
- 系统 OpenDocument 选择测试 JSON，真实 ContentResolver/Parser/ViewModel 读取；UI 显式反向选择二十个目标，经真实 ViewModel/Room 提交。
- 提交后逐个目标对比全部九字段、producerReferenceId 和 READY；不按数组顺序配图。
- 真实缩略图、已占用不可抢、解绑验证通过。既有 P25T、P23R 测试继续覆盖九项详情、外来项目过滤、busy/same-frame Back 和事务语义。
- 已在真实 MainActivity 路由操作分析详情→导演卡→0/5 进入 Camera Director，看到合成 Provider 来源和对应指导。该路由检查使用 P23D 的合成 READY 记录。
- 实际系统字体 100%/200% 下二十张绑定流程、提交和导演卡入口通过。仓库外截图覆盖缩略图列表与导演卡底部按钮；截图是 Compose surface capture，不代表整台设备 framebuffer 的完整覆盖。
- 顶部/底部文字自然换行，列表能滚动至最后一张，200% 下拍摄入口可见并可点击。
- saved-state 测试仅证明页面状态恢复；OS force-stop 证据单独来自 P23D runner，不将前者计作后者。

## Owner 与证据边界

本次补验前在仓库外保存 Owner 18 个修改/未跟踪文件的相对路径、大小和 SHA-256，补验后比较：18 项、0 内容变化、0 路径差异。这证明本次补验区间；此前最初执行只保留 AGENTS/tasks 三项哈希，不能追溯宣称最初全过程的 18 项哈希已验证。

逻辑证据 ID：p25t-completion-20260922；初轮日志见 p25t-android-20260922，OS force-stop 见 p25t-p23d-process-death-20260922。截图、APK、日志、Owner 快照和签名材料均留仓库外。

## 后续

独立 Reviewer 审查交付分支的精确最新 HEAD，并核验两份清单、test-only 增量、系统 Picker→真实 ViewModel→Room 的二十张反向映射、字体截图与以上计数范围。不能把本报告当作独立审查 PASS。

正式人审、真实 Pipeline/照片/设备/模型、知识包签名与公开发布没有执行。它们不作为当前 UI 验证的阻塞。当前 Gate 结束后，下一产品任务为成片预览、项目关联、保存/导出及恢复闭环；本轮不合并 main。
