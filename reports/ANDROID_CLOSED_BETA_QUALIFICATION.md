# Android Closed Beta Qualification

状态：`ANDROID_CLOSED_BETA_CANDIDATE_AWAITING_INDEPENDENT_REVIEW`

本报告绑定本轮 Beta 候选执行。它不表示已合并、公开发布或完成 T01–T05 试点。

## 固定来源

- `origin/main`：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`
- 本轮 Release 运行源 SHA：`e87e937d0de48c2f909c7bc71b732ed9ee46596f`
- Beta 分支：`codex/android-beta-release-candidate`
- package：`com.jovi.photoai`
- version：`0.2.0-beta.1` / `versionCode 2`
- P20 复用证据：20 READY、0 FAILED、0 CANCELLED、READY-only Summary SUCCESS
- P20 Provider/Coordinator/Room/项目汇总 canonical 内容未改变
- 外部证据根：`E:\project\_benchmark_evidence\android-closed-beta\20260811T115749Z\`

## Gate ledger

| Gate | Result | 说明 |
|---|---|---|
| main ancestry/fast-forward | PASS | `origin/main` 已精确到 `61a9b26` |
| Debug build | PASS | `clean assembleDebug` |
| AndroidTest APK | PASS | `assembleDebugAndroidTest` |
| JVM | PASS | 116 tests，0 failure/error/skip |
| lintDebug | PASS | 0 errors |
| lintRelease | PASS | 0 errors |
| service unittest | PASS | 5/5 |
| Python compileall | PASS | `local_analysis_service` |
| privacy audit | PASS | 未发现敏感产物或广泛媒体权限 |
| diff check | PASS | `git diff --check` |
| API 35 Photo Picker | PASS | 合成媒体，2/2 |
| API 35 当前 P20 flow | PASS | 项目首页→项目看板→基础拍摄，3/3 |
| API 35 responsive/accessibility | PASS | 100% 与 200% 字体语义 Gate 均通过；合法双按钮已处理 |
| API 35 pairing/error surface | PASS | 私有 LAN HTTPS、一次性配对码、证书 pin 和错误文案 |
| API 35 capture/Save As | PASS | CameraX 合成拍摄；系统 CreateDocument 成功与取消均通过，取消保留缓存 |
| API 35 write-failure contract | PASS | test-only 不可写 Provider；失败后缓存保留/可重试契约通过 |
| API 35 official D2D | PASS | `PASS_D2D_TRANSPORT`；官方单设备自动恢复，allowlist/禁止项/reconciliation 通过 |
| Release signing identity | PASS | 永久非 Debug RSA 4096 身份；公开证书 SHA-256 已 pin |
| APK/AAB signature | PASS | APK `apksigner`、AAB `jarsigner -verify`、AAB `keytool` 均通过；三方指纹一致 |
| signed Release runtime | PASS | API 35 直接启动签名 APK；空项目、创建项目、Camera 页面可达；版本/包名正确 |
| Beta branch push | NOT_RUN | 待本报告提交后执行非 force push |
| independent review | NOT_RUN | 待固定远端 SHA 的 detached worktree 审查 |
| T01–T05 pilot | NOT_RUN | 需独立审查 PASS 和 Jovi 后续分发授权 |

## Release artifacts

产物只存在外部 evidence，不进入 Git：

- APK：`photo-director-0.2.0-beta.1-e87e937d.apk`，9,056,280 bytes，SHA-256 `0041cab8470fd09f1a0ec46f4d4d6a2a85d85dc055c4d89d6ef148f001e6982b`
- AAB：`photo-director-0.2.0-beta.1-e87e937d.aab`，8,570,196 bytes，SHA-256 `91f352a204b8e5047ed34a9adbdec48f709deaa00b31b2c06c41def7264ab072`
- APK/AAB/public identity certificate SHA-256：`623C7DB70A8AA8552BD59B20BC103152326062F7FEC3D6D9313F8BBB94469CD0`
- Android build-tools：`37.0.0`

`jarsigner -strict` 对 Android App Bundle 的标准 JarInputStream 条目警告返回非零；本轮按计划要求的 `jarsigner -verify -verbose -certs` 成功，并另行提取 AAB 证书与 APK/public pin 比对。

## Runtime boundary

- 所有 instrumentation 仅使用 API 35 `emulator-*`；检测到的物理设备未安装、未清除、未 instrumentation。
- D2D 先在 wipe-data/no-snapshot AVD 上通过 clean 顺序完成；Picker 残留导致的首轮失败未被升级为 PASS。
- Debug 测试包不能驱动签名 Release 包，Android 系统按签名匹配规则拒绝该组合；签名 APK 的产品行为改用直接 UI smoke 验证，不放宽签名边界。
- 不包含照片、URI、来源文件名、数据库、模型权重、原始 AI 输出、设备 serial、密钥、密码或绝对私人媒体路径。

## Next gate

提交本报告后，按精确文件清单完成四个线性提交，运行最终隐私审计并非 force push Beta 分支；随后由新的 detached Reviewer 重跑静态 Gate、签名证据、D2D 证据和 P20 canonical binding。独立 PASS 后状态才可升级为 `ANDROID_CLOSED_BETA_CANDIDATE_READY`。
