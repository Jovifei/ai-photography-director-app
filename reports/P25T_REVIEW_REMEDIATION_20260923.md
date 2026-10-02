# P25T 独立审查 finding 修复与复验

## 绑定与结论

- 初审对象：`9fad15ea2916b6ec4a24191f0e8884747b658dfe`，独立 verdict `REQUEST_CHANGES`（P1 证据未绑定候选；P2 字体测试没有精确核对系统值）。
- 本轮代码提交：`78fdcd4baefbe779c989f4d02ef87b35c2b5748f`；此提交之后仅有本报告、handoff 状态及 Git-object manifest 文档变更。
- 交付目标仍为原远端 P25S 分支，不合入 main。
- 外部脱敏证据 ID：`p25t-review-remediation-20260923`；完整 SHA-256 清单在仓库外 `review-evidence-index-final.json`，清单摘要 `9fe30172bb260cc1bb6397ff7d35f08b0772e2895d545a54f96eec1a4ef6ca3a`。简明验收汇总为 `verification-summary-78fdcd4.json`。

## P2：字体比例断言

原测试只按当前值 `>=1.5` 选择 200% 截图标签，没有断言当前系统值就是所请求值。本轮修复要求 instrumentation 参数 `expectedFontScale`，仅接受 `1.0` 或 `2.0`，并以 0.01 容差断言 Android System `font_scale` 等于该参数；截图标签只在断言后生成。

使用明确绑定的 API 35 专用模拟器，class filter 指向 `P25TFontScaleScreenshotAndroidTest`：`expectedFontScale=1.0` 为 `1/1 PASS`，`2.0` 为 `1/1 PASS`；负向控制 `1.5` 被预期断言拒绝。100%/200% 完整页与底部操作区合成截图均已保存到外部证据目录。最终 `font_scale=1.0`。新源文件 Git-object 信息见 `docs/handoff/P25T_REVIEW_REMEDIATION_SOURCE_MANIFEST_20260923.json`。

同一代码提交上的 Debug 与 AndroidTest APK 构建 PASS；Debug JVM 单测使用 `--rerun-tasks` 实跑 `131/131`，0 failure/error/skip；`lintDebug` 与签名构建流程中的 `lintRelease` 均 PASS，分别 0 errors / 24 warnings。

## P1：签名、进程恢复和备份证据

在代码提交 `78fdcd4` 上，通过既有 P24 DPAPI 身份重新执行 `verifyReleaseSigning`、`assembleRelease`、`bundleRelease`、`lintRelease`，均 PASS；未创建新身份。APK 为非 debuggable、包名 `com.jovi.photoai`，APK 与 AAB 证书指纹均匹配既有固定指纹 `c62a1ee1…52b457b`。APK SHA-256：`714940593858473445c0a7e4b36e400cb6504f969b3cbefe8e254738f77bc563`；AAB SHA-256：`cbc95508c6402bd1e2e7ca27a5431dfd81061a0a0a3d11bc5f26f7ed2201405d`。`jarsigner` 报 `jar verified`、退出码 0；保留它关于自签证书信任链及 AAB JarFile/JarInputStream 条目顺序的 warning，不将此本地构建说成商店上传或公开发布验证。

同一代码提交上重新运行 P23D：Provider-backed READY/Summary 合成状态 prepare PASS，外部 `am force-stop` 后 PID 确认消失，重启恢复验证及 cleanup PASS。官方模拟器流程再运行 LocalTransport 和单设备 D2D：均 PASS，脚本恢复原 transport、备份设置和字体设置。此证据仅为专用模拟器合成数据。

## 运行中发生的 runner 误调用

首次字体复验命令漏传 AndroidJUnitRunner 的 `-e class`，意外启动了完整 92 项 instrumentation。结果为 92 项、5 failures；原始日志保留并在证据索引中标为 `NOT_ACCEPTED_AS_QUALIFICATION_GATE`，之后已通过 class filter 单独重跑目标字体类。失败为：Phase1 导航等待超时；P20 配对参数缺失；两项 P20 导入因合成图片素材/项目缺失未到 Provider 分析；以及把依赖 `prepare → verify` 的备份 fixture 当成无序全量套件运行。源码显示这几项未触达真实照片或 Provider。相关失败不被隐藏，也不冒充全量 Android PASS；原 P25T/P22/P23 指定回归在此前 9fad 资格批次中的历史结果与本轮目标类结果保持分开记录。

## 证据边界与后续

- P25T Android 生产 UI、Room、Provider、签名配置和人审状态没有被本轮代码更改；新增改动仅限 Android instrumentation test。
- Owner 基线复核：对照 2026-09-22 外部快照的 18 项修改/未跟踪文件，当前 18/18 SHA-256 匹配、0 缺失；Owner HEAD 未变，`tasks/todo.md`、`tasks/lessons.md` 均在快照范围内。明细路径保留于仓库外，不写入 Git。
- 真人审核仍 `0/20`；Pipeline 生产输出/发布、真实照片、Qwen、真实 LAN、实体设备、Cloud、iOS、main 合入均未执行。
- APK/AAB、截图、日志和设备数据库均未提交到 Git。Owner 主工作树和 tasks 文件未改。
- 当前状态：finding 已修复并补齐候选绑定证据，等待独立 Reviewer 对最终完整 delivery SHA 重新给出 verdict；此报告不表示 main 合并或 release 授权。
