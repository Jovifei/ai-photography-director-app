# P25U 云端实现报告

状态：`P25U_IMPLEMENTED_HOST_VERIFIED_ANDROID_VALIDATION_PENDING`。

## 固定范围

- 仓库：`Jovifei/ai-photography-director-app`
- 继续原分支：`codex/p25s-internal-handoff-validation-20260920`
- 输入候选：`11342a3a441cb03826b85f46f3932a98bd98930f`
- 输入 P25T 独立 PASS：Owner 提供，未在本轮重复派发或冒充新审查。
- 原 main：`1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`，本轮不更新。
- 最终源码提交：`5a6968635df89f99e44ea15896d0dd6898df6f38`
- 最终源码树：`aaf17f15525e93e6afa8e521c4037a4d18416242`
- 后续为文档提交；交付 HEAD 从实际分支读取，不为自引用 SHA 不断追加提交。

## 分段提交

| 步骤 | 提交 | 内容 |
|---|---|---|
| U1 | `8367f233120e4cad8ddeb2c6ade14b925ad980b0` | 独立成片 Room、私有文件、恢复与导出 token |
| U2 | `b657b8cbcf965c0cd2940d63a3c0be7f25d94bff` | 拍后预览、ViewModel、EXIF、CameraX 与导出错误修复 |
| U3 | `6defd95c499f7e068f35af102fa4b94893f2186f` | 首页/项目入口、项目删除保留原片、根级导出回调 |
| U4 | `5a6968635df89f99e44ea15896d0dd6898df6f38` | 核心测试、14 项 Android 测试、JPEG 采样验证与回执故障补偿 |

本轮新增/修改 21 个源码、测试与执行脚本。没有修改参考 DB v6、P25 内容、人审回执、Provider、签名身份、Gradle 依赖或 Pipeline。

## 实际运行证据

环境：Linux、Python 3.13.5、OpenJDK 21.0.11、Kotlin compiler 1.9.0。
执行 `python scripts/test_p25u_capture_core.py`，最终重新运行结果：

    PASS capture-core assertions=98 scope=PRODUCTION_ENGINE_REAL_FILES_TEST_LEDGER_NO_ANDROID_CODEC

被实际编译运行的是生产 `CaptureModels/CaptureFiles/CaptureEngine`，使用真实临时文件、事务测试 ledger 和合成 JPEG inspector。
它证明宿主环境下的核心状态/文件行为，不证明 Room、Android 解码器、CameraX 或 Compose 通过。
6 个 JUnit 方法复用同一组断言；98 不是全 App JVM 测试数量。

覆盖：快门身份、重复/迟到完成、最终文件与数据库确认故障、部分文件保留、缺失文件、导出 token、旧进程不重放、取消及 open/write/flush/close 故障、删除 tombstone、项目解绑、导出与拍摄并发、文件路径拒绝。

Python compileall 通过；原仓库隐私扫描脚本和 `git diff --cached --check` 在物化变更快照通过。
该快照不是完整 clone，不能称完整仓库或 Owner 磁盘审计通过。源码树使用与快照完全相同的 Git blob 身份，清单记录原始 bytes/SHA-256。

## 已提交但未运行的 Android 测试

| 类别 | 方法数 | 实际边界 |
|---|---:|---|
| P25UCaptureRepositoryAndroidTest | 7 | 真实隔离磁盘 Room/ledger/engine、生成 JPEG、流异常 |
| P25UCaptureUiAndroidTest | 3 | 实际画廊/解码；UI 回调观察与 Room 测试分别执行 |
| P25UCameraCaptureAndroidTest | 1 | 实际 CameraX→生产 VM/Repository→预览、Activity 重建 |
| P25UDiskProcessRecoveryAndroidTest | 3 | 外部按 phase 分别运行的磁盘 Room/engine prepare/verify/cleanup |

14 项均为本轮 `NOT_COMPILED / NOT_RUN`。最后三项不是无序全套测试；缺 phase 会跳过，不能计入 PASS。
Activity 重建、隔离数据库重开、外部 force-stop、默认 App 端到端恢复是不同证据，不相互冒充。

## 本地必须完成

1. 完整 Android/JVM/Lint 与既有指定回归，核对 CameraScreen 可见性、CameraX bind 返回值和全部调用者。
2. 真正 CameraX 输出、连续拍摄、项目 A/B、离页/旋转/迟到回调、项目删除与未归类结果。
3. 系统 CreateDocument 成功/取消/旋转/进程中断；root ActivityResultRegistry 每 token 绑定是否正确。
4. 默认 App 的成片 force-stop/重启/再次打开画廊；独立磁盘测试不可替代。
5. 源文件/数据库崩溃窗口、低空间、损坏 JPEG、异常 close 与确认丢失；不得以假 ledger 代替所有 Room 故障。
6. 系统 font_scale=1.0/2.0、长文案、真实缩略图、八种 EXIF 方向；现有 Android 测试仅编写了90度验证。
7. 新成片明确不备份/不 D2D；回归原参考图库恢复而非声称成片迁移成功。
8. 完整隐私扫描、21 项 Git-object manifest、Owner 文件前后复核、独立审查。

尚未实现/未承诺：图库分页与容量配额、媒体编辑/分享、自动导入旧缓存、成片设备迁移、外部副本位置追踪、完整断电安全。
外部 provider 单次阻塞 IO 不保证立即可取消；未知结果保留原片，不能盲目重复导出。

## 开源与授权

详见 `docs/P25U_CAPTURE_RESULTS_AND_OPEN_SOURCE_20260923.md` 的精确参考提交和模式取舍。
没有复制第三方源码、增加库或下载权重，没有触碰真实照片、实体设备、Qwen/LAN、Pipeline、生产密钥和 main。
正式人审仍与内部工程分别记录，不因空审核 JSON 停止本轮验证，也不伪造审核通过。
