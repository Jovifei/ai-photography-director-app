# P25U / T2：成片预览、项目归档与导出恢复

日期：2026-09-23。状态：`P25U_IMPLEMENTED_HOST_VERIFIED_ANDROID_VALIDATION_PENDING`。

## 目标与交付边界

从 Owner 已报告独立审查 PASS 的 P25T `11342a3a441cb03826b85f46f3932a98bd98930f` 继续，
在原分支实现真实产品代码，不再以未填写内容审核 JSON 阻断内部工程。
本轮没有读取该外部 Reviewer 任务的完整日志，没有把用户报告的 PASS 当成本轮代码的审查结果。

目标流程：CameraX 拍照 → 应用内原片落盘 → 成片预览 → 全部/项目/未归类列表 → 保存外部副本 → 取消/异常/重启后仍能找到原片。
实现已提交；Android/Room/Compose/系统文件选择器/设备验证必须由本地执行。

## 借鉴的开源实现

### 1. Google Jetpack Camera App

- 仓库：https://github.com/google/jetpack-camera-app
- 实际读取提交：`7905b757725c11d95fe8fdac7a5ab771f86c7397`
- 文件：`feature/postcapture/src/main/java/com/google/jetpackcamera/feature/postcapture/PostCaptureViewModel.kt`
- Git blob：`d82122ddeff1feb5fe99da1a3cb31185677320a4`
- 借鉴：独立 post-capture 页面、由 ViewModel 管理媒体操作和可观察状态、相机与媒体浏览职责分离。
- 没有直接复用它的 cache-and-review 存储策略：本项目要求退出之后仍能找到原片，因此采用持久私有原片而非缓存为唯一来源。
- 没有导入整套模块、Hilt、视频/HDR，也没有升级本项目 Gradle/CameraX 版本。

### 2. Android Architecture Samples

- 仓库：https://github.com/android/architecture-samples
- 实际读取提交：`ee66e1526b84c026615df032c705842b7d2a521f`
- 文件：`app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/DefaultTaskRepository.kt`
- Git blob：`7d61755a656ae1d366662c50bc2eceb6b2783100`
- 借鉴：Repository 作为本地数据入口、DAO Flow 驱动界面、明确 IO dispatcher、需独立于页面结束的短持久化工作使用应用生命周期作用域。
- 没有采用示例中的网络同步或 delete-all 刷新模式。

### 3. Android Camera Samples

- 仓库：https://github.com/android/camera-samples
- 本轮查看官方 README；仅作为后续点按对焦、变焦、曝光补偿、旋转和能力降级的目录级参考，未声称读取/锁定其全部实现。
- 这些相机控制不是本轮已交付功能，不把样例支持的功能写成本 App 已支持。

前两份实际读取的源码具有 Apache-2.0 头部。上述是设计模式借鉴，新增本项目代码自行实现，没有复制第三方源码。
本轮没有改写旧 `REFERENCE_LOCK.json` 或把第三方 clone 放进仓库。以后直接移植代码须单独核对许可、保留通知。

## 本轮数据模型

原参考图库仍为既有 Room v6。新增独立 `CaptureLibraryDatabase` v1，包含：

- `captured_photos`：不透明照片 ID、快门时项目/参考图 ID、时间、私有原片状态、大小/摘要、最近导出状态与成功时间。
- `capture_export_slot`：一次全局外部导出请求的 token、captureId、processId 和 phase。

没有把成片存入参考分析表；AVAILABLE 只表示成片可用，不表示 AI READY。
不复制 Provider/Bundle 分析来源给成片。引用参考图用于拍摄关联，不声称对成片做了 AI 分析。

## 存储取舍（需要准确向用户说明）

JPEG 和成片数据库均放在 `noBackupFilesDir/capture-library`。本轮选择“本机私有、明确另存副本”：

- 不依赖系统可回收 cache；普通退出/进程重建时可以继续读取。
- 不自动进入云备份或设备迁移；卸载/清除数据仍会删除。
- 界面已明确提醒重要照片另存外部副本。
- 原参考图库已有 Local/D2D 配置没有修改；其历史备份 PASS 不可外推为新成片自动迁移 PASS。
- 不扫描旧缓存或源图库来猜测归属；也不自动清理旧缓存原片。

参考官方说明：
https://developer.android.com/training/data-storage/app-specific
https://developer.android.com/identity/data/autobackup

后续若选择默认保存 MediaStore 或允许成片设备迁移，应作为单独产品决策和数据迁移任务，不能只改文案。

## 文件与数据库恢复协议

1. 快门回调前固定 captureId/projectId/referenceId，并记录 CAPTURING。
2. CameraX 写入该 ID 的 `.partial.jpg`；成功后校验 JPEG、采样解码、大小和摘要。
3. 同目录同步并重命名为 `.jpg`，再提交 AVAILABLE。
4. 文件已完成但数据库回执中断：启动按最终文件重新检查，幂等补齐。
5. 只有 partial 而没有完成标记：保留为 INTERRUPTED，不冒充完整成片、不自动删除。
6. 删除先写 DELETING，再删除该 ID 私有文件，最后删记录；失败留下可重试记录。
7. 删除项目只解绑成片，不删除原片。跨库通知中断后，启动检查与界面归类补偿，不虚构跨库原子事务。

该协议针对进程中断与应用 IO 错误；不声称文件系统与 Room 是一个事务，也不承诺任意断电下零损失。

## 导出协议

- 固定 captureId + 唯一 export token；ActivityResultRegistry key 包含该 token。
- RESERVED → SELECTING 在 launch 前持久化，避免重组/旋转重复打开。
- 返回结果只交给对应 token，旧回调不能写新照片。
- 前一进程留下的请求按 UNKNOWN 处理，不自动重放旧 URI 写入。
- 输入/输出流在失败路径同样关闭；检查复制长度/摘要，flush/close 成功后才记录 SAVED。
- 外部文件无法与本地数据库一起回滚。open/write/flush/close/取消/回执异常均保守处理，原片保留。
- 不持久化外部 URI，不自动删除用户外部文件；明确再次操作可能产生重复副本。

官方 Activity Result 说明：https://developer.android.com/training/basics/intents/result

## 可见功能

- 拍摄完成打开成片预览，支持继续操作。
- 首页“查看全部成片”、项目“查看本项目成片”，无项目或已删除项目显示未归类。
- 显示私有原片/外部副本状态，提供保存副本和删除确认。
- 使用采样和 EXIF 方向修正预览，不改写原片。
- CameraX 绑定失败不再发出 CameraReady；销毁后的初始化回调不重新绑定；只解绑本 manager 的 use cases。

## 后续顺序

U4：本地完整 Android 验证与最小整改 → 精确候选独立审查。
通过之后，再做取景器参考图对照、点按对焦/曝光等有限相机操作，接着进入真实 Provider/设备与用户试用。
Pipeline exporter/合同投影/知识信任是独立供给线，不作为本轮成片内部开发的前置条件。
内容真人审核事实、生产签名、主线合入和发布授权继续分别记录。
