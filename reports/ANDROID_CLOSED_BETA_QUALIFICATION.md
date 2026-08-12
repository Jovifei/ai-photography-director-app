# Android Closed Beta Qualification

状态：`ANDROID_CLOSED_BETA_CANDIDATE_READY`

本报告绑定本轮 Beta 候选执行。它不表示已合并、公开发布或完成 T01–T05 试点。

## 固定来源

- `origin/main`：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`
- 独立审查代码边界：`7094ac6de22f709d24f0ebdefe90803281ed2b0d`
- 本轮 Release 运行源 SHA：`eafcf30fce81982876f7adc6269190debcbd7093`
- Beta 分支：`codex/android-beta-release-candidate`
- package：`com.jovi.photoai`
- version：`0.2.0-beta.1` / `versionCode 2`
- P20 复用证据：20 READY、0 FAILED、0 CANCELLED、READY-only Summary SUCCESS
- P20 Provider/Coordinator/Room/项目汇总 canonical 内容未改变
- 外部证据根：`E:\project\_benchmark_evidence\android-closed-beta-pilot\20260812T120303Z\`

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
| API 35 pairing/error surface | PASS | 配对字段、证书 pin 校验和错误文案；真实 LAN 连接另受下方 Preflight 阻断 |
| API 35 capture/Save As | PASS | CameraX 合成拍摄；系统 CreateDocument 成功与取消均通过，取消保留缓存 |
| API 35 write-failure contract | PASS | test-only 不可写 Provider；失败后缓存保留/可重试契约通过 |
| API 35 official D2D | PASS | `PASS_D2D_TRANSPORT`；官方单设备自动恢复，allowlist/禁止项/reconciliation 通过 |
| Release signing identity | PASS | 永久非 Debug RSA 4096 身份；公开证书 SHA-256 已 pin |
| APK/AAB signature | PASS | APK `apksigner`、AAB `jarsigner -verify`、AAB `keytool` 均通过；三方指纹一致 |
| signed Release runtime | PASS | API 35 直接启动签名 APK；空项目、创建项目、Camera 页面可达；版本/包名正确 |
| Beta branch push | PASS | 非 force push 已完成；独立审查代码边界固定为 `7094ac6`，随后仅追加本报告 docs-only 跟进 |
| independent review | PASS | detached Reviewer 固定审查 `7094ac6`；静态、脚本、证据边界和 P20 canonical binding 均通过 |
| T01–T05 pilot | NOT_RUN | 需独立审查 PASS 和 Jovi 后续分发授权 |

## Release artifacts

产物只存在外部 evidence，不进入 Git：

- APK：`photo-director-0.2.0-beta.1-eafcf30f.apk`，9,056,280 bytes，SHA-256 `a4cd77c7949fd40eaee8c8e9909406c52a2c1eac962d3ffeb9f13b1660708bcb`
- AAB：`photo-director-0.2.0-beta.1-eafcf30f.aab`，8,570,196 bytes，SHA-256 `91f352a204b8e5047ed34a9adbdec48f709deaa00b31b2c06c41def7264ab072`
- APK/AAB/public identity certificate SHA-256：`623C7DB70A8AA8552BD59B20BC103152326062F7FEC3D6D9313F8BBB94469CD0`
- Android build-tools：`37.0.0`
- Final source-bound artifact summary：`release-current-summary.json` under the external evidence root，SHA-256 `5A7E80545319D7D08BD070B8AFC453E0DF5C5F7BF60FBE8E732D041767B1457C`；摘要标记 `api35_runtime=PASS_EMULATOR_ONLY`、`beta_smoke=PASS_3_OF_3`。
- Independent review record：`independent-review-7094ac6.md`，SHA-256 `D9B68BB61DD57FAC27FE317634CAD79DC92AE874D0FB4164E010FFE323681D7B`。

`jarsigner -strict` 对 Android App Bundle 的标准 JarInputStream 条目警告返回非零；本轮按计划要求的 `jarsigner -verify -verbose -certs` 成功，并另行提取 AAB 证书与 APK/public pin 比对。

## Runtime boundary

- 所有 instrumentation 仅使用 API 35 `emulator-*`；检测到的物理设备未安装、未清除、未 instrumentation。
- 当前真实 LAN Preflight：`BLOCKED_NO_RFC1918_INTERFACE`；本机没有可验证的 RFC1918 Private 网络接口，因此没有启动服务、创建防火墙规则或宣称 LAN 配对通过。
- D2D 先在 wipe-data/no-snapshot AVD 上通过 clean 顺序完成；Picker 残留导致的首轮失败未被升级为 PASS。
- Debug 测试包不能驱动签名 Release 包，Android 系统按签名匹配规则拒绝该组合；签名 APK 的产品行为改用直接 UI smoke 验证，不放宽签名边界。
- 不包含照片、URI、来源文件名、数据库、模型权重、原始 AI 输出、设备 serial、密钥、密码或绝对私人媒体路径。

## Next gate

候选已通过当前实现、签名、API 35、隐私和独立审查门禁。下一步只有在 Jovi 实际分发授权、Private LAN Preflight 通过、服务可达且五位测试者到位后执行 T01–T05；本机当前仍为 `BLOCKED_NO_RFC1918_INTERFACE`，因此本报告不宣称 LAN 或五人试点通过。Beta 合并、公开发布仍未执行。

## Phone setup documentation follow-up

- Final Beta docs-only follow-up: `51d41aa252ccf792e8a5c87db5f620343cca7568` (runtime product code unchanged).
- Repository guide: `docs/ANDROID_BETA_PHONE_SETUP.md`; `docs/BETA_USER_GUIDE.md` links to it.
- External pilot package regenerated at `E:\project\_benchmark_evidence\android-closed-beta-pilot\20260812T155333Z\`; package summary SHA-256 `073C6717F0A6C046CEE9917D3D5D9FD77B39937EC03D2D89C7AE8FFFEBA354AC`.
- The package contains the setup guide, user guide, privacy policy, signed APK and SHA256SUMS; it contains no pairing material, private key, photo or raw model output.
