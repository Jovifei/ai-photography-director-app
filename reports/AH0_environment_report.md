# AH0 Environment & CameraX Baseline Report - ai-photography-director-app

| 项 | 值 |
|---|---|
| 工程 | A - AI 摄影现场导演 App |
| 阶段 | AH0(环境盘点 + CameraX 基线) |
| 执行者 | Claude Code(按负责人 Jovi 分配) |
| 日期 | 2026-07-14 |
| 前置 | G0 已完成(commit `2140f3e` / `dbc07e6` 已推送 origin/main) |

## 1. 环境盘点(AH0 Step 1-2)

| 组件 | 状态 | 说明 |
|---|---|---|
| OS | ✅ | Microsoft Windows 11(10.0.22631.4460) |
| Android SDK | ✅ | `C:\Users\Admin\AppData\Local\Android\Sdk`;platform-tools/adb 37.0.0;build-tools 34.0.0/36.1.0/37.0.0;platforms android-34/35/36/36.1;emulator |
| JDK | ✅(本轮安装) | Eclipse Temurin 17.0.19+10,`C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`(winget 安装) |
| Gradle | ✅(wrapper) | 8.9.0 via gradlew(wrapper jar/脚本取自 Gradle 8.9.0 仓库) |
| cmdline-tools/sdkmanager | ❌ 缺失 | 现有 build-tools/platforms 足够 AH0;后续按需补 |
| Android Studio | ❌ 未安装 | 本轮用命令行 Gradle 编译;AS 为可选开发工具 |
| 连接的 Android 设备 | ❌ 无 | adb devices 为空;真机验证待 O-05(Jovi 连接两台设备) |

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
├── settings.gradle.kts          # AGP/Kotlin repo + include :app
├── build.gradle.kts             # 顶层 plugins(apply false)
├── gradle.properties            # AndroidX / JVM args
├── local.properties             # sdk.dir(gitignored)
├── gradle/wrapper/
│   ├── gradle-wrapper.jar       # Gradle 8.9.0
│   └── gradle-wrapper.properties
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts         # AGP + Compose + CameraX 依赖
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml        # CAMERA 权限 + FileProvider + MainActivity
        │   ├── java/com/jovi/photoai/
        │   │   ├── MainActivity.kt        # ComponentActivity + Compose
        │   │   ├── camera/CameraXManager.kt   # Preview+ImageCapture+ImageAnalysis 绑定
        │   │   └── ui/CameraScreen.kt     # Compose: PreviewView + 权限 + 拍照按钮
        │   └── res/{values,drawable,xml}
        └── test/java/com/jovi/photoai/ExampleUnitTest.kt
```

## 4. CameraX 基线实现(AH0 Step 4)

- **Preview**:`Preview.Builder().build()` + `setSurfaceProvider(previewView.surfaceProvider)`。
- **ImageCapture**:`CAPTURE_MODE_MINIMIZE_LATENCY`,输出到 `cacheDir/captures/`(FileProvider)。
- **ImageAnalysis**:`STRATEGY_KEEP_ONLY_LATEST`(背压,重推理不阻塞预览/快门);Analyzer 立即 `imageProxy.close()`(保证不泄漏)。
- **生命周期**:`ProcessCameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, imageAnalysis)`;`unbindAll()` on dispose。
- **权限**:`Manifest.permission.CAMERA` + `rememberLauncherForActivityResult`;权限拒绝时显示授权按钮,不崩溃。
- **降级**:CameraX 初始化失败时 try/catch 记录日志(后续 AA0 补完整降级)。
- **源安全**:仅 ImageCapture 写 cache,无对源照片的 write/move/delete。

## 5. 编译验证

✅ **BUILD SUCCESSFUL** in 1m 43s(`GRADLE_EXIT=0`,35 actionable tasks executed)。

- `compileDebugKotlin` 通过(Kotlin 代码 + Compose + CameraX API 正确,无 LocalLifecycleOwner/CameraX API 错误)。
- APK 产物:`app/build/outputs/apk/debug/app-debug.apk`(~11 MB)。
- 唯一警告:`stripDebugDebugSymbols` 无法 strip CameraX native 库(`libandroidx.graphics.path.so` / `libimage_processing_util_jni.so`),按原样打包,不影响功能。
- 编译环境:Temurin JDK 17.0.19 + Gradle 8.9.0(wrapper)+ AGP 8.7.2 + compileSdk 35。
- 单元测试:`./gradlew testDebugUnitTest` **BUILD SUCCESSFUL** in 14s(`compileDebugUnitTestKotlin` 通过,`ExampleUnitTest` 通过,`TEST_EXIT=0`)。

## 6. 真机验证

> ⏳ 待补:adb 当前无设备。待 Jovi 连接两台 Android 真机(O-05,主力 + 较旧)后,验证 Preview/ImageCapture/ImageAnalysis + 旋转 + 前后台 + 权限拒绝 + 相机占用降级。

## 7. 未解决 / 下一步

1. **编译验证**:等 assembleDebug 结果;若失败,修编译错误后重跑。
2. **真机验证**:待 O-05 设备连接。
3. **未覆盖(AAH0 后续)**:旋转/前后台/相机占用降级的完整测试;坐标映射测试点;与系统相机方向/裁切对比;10/30 分钟稳定性。
4. **cmdline-tools**:如需 sdkmanager 管理组件,后续补装。
5. **Android Studio**:Jovi 可选安装以提升开发体验(非阻断)。
