# AH0 Environment & CameraX Baseline Report - ai-photography-director-app

| 项 | 值 |
|---|---|
| 工程 | A - AI 摄影现场导演 App |
| 阶段 | AH0(环境盘点 + CameraX 基线)+ AH0_REVIEW_REMEDIATION |
| 执行者 | Claude Code(按负责人 Jovi 分配) |
| 日期 | 2026-07-14 |
| 前置 | G0 已完成(commit `2140f3e` / `dbc07e6` 已推送 origin/main) |
| AH0 基线提交 | `f2d1fb2`(已推送 origin/main) |
| 整改分支 | `fix/ah0-review-remediation`(从 `f2d1fb2` 创建) |
| Reviewer 结论(基线) | REQUEST_CHANGES_BEFORE_DEVICE_GATE |

## 1. 环境盘点(AH0 Step 1-2)

| 组件 | 状态 | 说明 |
|---|---|---|
| OS | ✅ | Microsoft Windows 11(10.0.22631.4460) |
| Android SDK | ✅ | `C:\Users\Admin\AppData\Local\Android\Sdk`;platform-tools/adb 37.0.0;build-tools 34.0.0/36.1.0/37.0.0;platforms android-34/35/36/36.1;emulator |
| JDK | ✅(本轮安装) | Eclipse Temurin 17.0.19+10,`C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`(winget 安装) |
| Gradle | ✅(wrapper) | 8.9.0 via `gradlew.bat`(wrapper jar/脚本取自 Gradle 8.9.0 仓库) |
| cmdline-tools/sdkmanager | ❌ 缺失 | 现有 build-tools/platforms 足够 AH0;后续按需补 |
| Android Studio | ❌ 未安装 | 本轮用命令行 Gradle 编译;AS 为可选开发工具 |
| 连接的 Android 设备 | ❌ 无 | adb devices 为空;真机验证待 O-05 |

## 2. 版本决策(Jovi 授权"自己决定最佳")

| 项 | 值 | 理由 |
|---|---|---|
| minSdk | 24(Android 7.0) | 覆盖较旧/中端设备矩阵,兼容广 |
| compileSdk | 35 | 用已装 platform android-35 |
| targetSdk | 35 | 与 compileSdk 一致 |
| AGP | 8.7.2 | 稳定,需 Gradle 8.9+/JDK 17(已满足) |
| Kotlin | 2.0.21 | Compose Compiler 2.0.21 匹配 |
| Compose BOM | 2024.10.01 | 稳定组合 |
| CameraX | 1.4.0 | Preview/ImageCapture/ImageAnalysis 稳定版 |
| namespace / applicationId | com.jovi.photoai | 占位,待 O-01 正式 namespace 确认 |

## 3. 工程结构(`android/`)

```text
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties             # sdk.dir(gitignored)
├── gradle/wrapper/{jar,properties}
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml        # CAMERA 权限 + FileProvider + MainActivity + allowBackup=false
        │   ├── java/com/jovi/photoai/
        │   │   ├── MainActivity.kt
        │   │   ├── camera/CameraXManager.kt
        │   │   └── ui/CameraScreen.kt     # B1: CameraScreen/PermissionContent/CameraContent + ON_RESUME
        │   └── res/
        │       ├── values/themes.xml      # B2: 基础主题(无 cutout 属性,API 24-26 安全)
        │       ├── values-v27/themes.xml  # B2: API 27+ shortEdges cutout
        │       ├── values/strings.xml
        │       └── xml/file_paths.xml
        └── test/java/com/jovi/photoai/ExampleUnitTest.kt
```

## 4. CameraX 基线实现 + B1 权限流程整改

**CameraX 用例(AH0 Step 4)**:
- **Preview**:`Preview.Builder().build()` + `setSurfaceProvider(previewView.surfaceProvider)`。
- **ImageCapture**:`CAPTURE_MODE_MINIMIZE_LATENCY`,输出到 `cacheDir/captures/`(FileProvider)。
- **ImageAnalysis**:`STRATEGY_KEEP_ONLY_LATEST`;Analyzer 立即 `imageProxy.close()`(不阻塞预览/快门)。
- **生命周期**:`bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, imageAnalysis)`;`unbindAll()` on dispose。
- **源安全**:仅 ImageCapture 写 cache,无对源照片的 write/move/delete。

**B1 整改(首次授权后 CameraX 重新绑定)**:`CameraScreen.kt` 重构为三层:
- `CameraScreen`:持有权限状态;首次 `LaunchedEffect` 请求一次;`DisposableEffect(lifecycleOwner)` 注册 `LifecycleEventObserver`,在 `ON_RESUME` 重新 `checkSelfPermission`(用户从系统设置返回后不依赖首次 launcher 回调)。
- 仅当 `hasCameraPermission == true` 才组合 `CameraContent`;否则组合 `PermissionContent`。无权限时不初始化/不绑定 CameraX。
- `CameraContent` 内 `remember { CameraXManager(context.applicationContext) }` + `DisposableEffect` 负责 `initialize/bindToLifecycle/setAnalyzer/onDispose shutdown`。权限 `false->true` 时 `CameraContent` 重新进入组合,创建**新的** `CameraXManager`;`true->false` 时 `onDispose` 关闭旧实例。

> ⚠️ CameraX 首次权限流程为**代码已整改**,尚未真机验证(见 §9)。

## 5. 代码编译(已验证)

整改后在 `android/` 真实运行(退出码):

| 命令 | 结果 | 退出码 |
|---|---|---|
| `gradlew.bat clean` | BUILD SUCCESSFUL in 9s | 0 |
| `gradlew.bat assembleDebug` | BUILD SUCCESSFUL in 23s(35 tasks) | 0 |
| `gradlew.bat testDebugUnitTest` | BUILD SUCCESSFUL in 13s(见 §6) | 0 |
| `gradlew.bat lintDebug` | BUILD SUCCESSFUL in 27s(见 §7) | 0 |

- `compileDebugKotlin` 通过(含 B1 重构后的 CameraScreen:LocalLifecycleOwner/LifecycleEventObserver/CameraContent 重组语义正确)。
- APK 产物:`app/build/outputs/apk/debug/app-debug.apk`,**11485684 字节**(~11 MB)。
- 编译环境:Temurin JDK 17.0.19 + Gradle 8.9.0(wrapper)+ AGP 8.7.2 + compileSdk 35。

## 6. 单元测试框架(已验证)

`gradlew.bat testDebugUnitTest` BUILD SUCCESSFUL in 13s(`TEST_EXIT=0`);`compileDebugUnitTestKotlin` 通过;`ExampleUnitTest` 通过。

> 仅 JVM 单元测试框架已验证。CameraX 行为需 instrumentation/真机(未验证)。

## 7. Android Lint(整改后真实结果)

`gradlew.bat lintDebug` BUILD SUCCESSFUL in 27s(`LINT_EXIT=0`)。报告:`app/build/reports/lint-results-debug.xml`。

**整改前(f2d1fb2)**:`values/themes.xml` 含 `android:windowLayoutInDisplayCutoutMode`,该属性要求 API 27,但 minSdk 24 -> **1 个 lint error**(B2)。

**整改后(fix/ah0-review-remediation)**:基础 `values/themes.xml` 移除该属性,新增 `values-v27/themes.xml` 在 API 27+ 设 `shortEdges` -> **0 lint error**。

**lint error 数:0**;**lint warning 数:12**(全部 non-blocking,如实保留):

| # | id | 概要 | 处置 |
|---|---|---|---|
| 1 | OldTargetApi | targetSdk 35 非最新(可升 36) | non-blocking,后续 |
| 2-10 | GradleDependency(×9) | core-ktx/activity-compose/lifecycle-*/camera-* 有更新版 | non-blocking,依赖升级保留后续 |
| 11 | DataExtractionRules | `allowBackup` 自 Android 12 deprecated,建议 `dataExtractionRules` | B4 引入;本轮不设计 rules(保留) |
| 12 | MissingApplicationIcon | 未设 `android:icon` | non-blocking,正式图标保留后续 |

> 未通过忽略 lint / baseline / 降严格解决;cutout error 已用 values-v27 限定符正确消除。

## 8. 备份与隐私决定(B4)

- `AndroidManifest.xml`:`android:allowBackup="true"` -> `"false"`。
- 决定:私人摄影数据默认**不允许 Android Auto Backup**。
- AH0 拍摄结果当前仅写入 App `cacheDir/captures/`(临时,系统可清理)。
- 后续正式照片存储策略另行设计(本轮不扩大为完整隐私系统)。
- 附带 warning `DataExtractionRules`(§7 #11):本轮不设计 `dataExtractionRules`,保留为后续 AH0 Device Hardening 项。

## 9. 验证状态(严格区分,无真机证据不写"已验证")

| # | 项 | 状态 |
|---|---|---|
| 1 | 代码编译 | ✅ 已验证(`assembleDebug` exit 0) |
| 2 | 单元测试框架 | ✅ 已验证(`testDebugUnitTest` exit 0) |
| 3 | Android Lint | ✅ 整改后真实结果(0 error, 12 warning) |
| 4 | CameraX 首次权限流程 | ⚠️ 代码已整改(B1),**尚未真机验证** |
| 5 | 真机 Preview/ImageCapture | ❌ 尚未验证(无设备) |
| 6 | 生命周期和进程重建 | ❌ 尚未验证 |
| 7 | 两台设备矩阵 | ❌ 尚未完成(O-05) |
| 8 | AH0_CODE_BASELINE | ⏳ 等待独立 Reviewer |
| 9 | AH0_FULL_GATE | ❌ 未通过 |
| 10 | AA0 | ❌ 未授权 |

## 10. Reviewer Blocking Findings 整改(B1-B5)

- **B1(首次授权后不重绑)**:`CameraScreen.kt` 重构为 CameraScreen(权限+ON_RESUME)/PermissionContent/CameraContent(remember manager + DisposableEffect)。详见 §4。
- **B2(API 24-26 cutout lint error)**:`values/themes.xml` 移除 `windowLayoutInDisplayCutoutMode`;新增 `values-v27/themes.xml` 设 `shortEdges`。lint error 1->0。
- **B3(报告自相矛盾)**:删除 §7 旧"等 assembleDebug 结果;若失败"矛盾文案;报告与真实结果一致(§5/§7)。
- **B4(备份风险)**:`AndroidManifest.xml` `allowBackup` -> `false`;决定记录于 §8。
- **B5(报告遗漏 Lint)**:新增 §7 Android Lint 部分,基于 `lintDebug` 真实输出,区分整改前/后。

## 11. 未解决 non-blocking findings(保留,不顺手修)

- 12 个 lint warning(§7:依赖升级×9、OldTargetApi、DataExtractionRules、MissingApplicationIcon)。
- Executor 可重建问题(CameraXManager.analysisExecutor)。
- 拍照按钮并发保护。
- mkdirs 错误提示。
- 拍摄失败 Snackbar。
- 正式 App 图标。
- 依赖升级。
- CameraX instrumentation test。
- cmdline-tools/sdkmanager 缺失。
- 坐标映射测试点;旋转/前后台/相机占用降级完整测试;与系统相机对比;10/30min 稳定性。

以上保留至后续 AH0 Device Hardening / AA0,不在本轮 diff 内。

## 12. 下一步

- AH0 真机 Gate 待 O-05(Jovi 连接两台 Android 真机)。
- 整改分支 `fix/ah0-review-remediation` 待独立 Reviewer 复核;通过后由 Owner 决定 merge main 时机(本轮未 merge main)。
- AA0 未授权;Android Gate 前不创建 `ios/`。
