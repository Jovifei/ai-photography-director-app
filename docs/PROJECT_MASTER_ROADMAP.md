# 项目总路线图：AI 摄影现场导演

更新时间：2026-10-02。

规划状态：**PENDING_REMOTE_PLANNING**。

说明：当前文档完成远端源码证据整理和阶段实施包补充，但详细总路线图正式批准状态仍保持待审定。不得把源码审查、测试通过或文档整理等同于产品完成。

## 产品目标与边界

目标：用户选择可信来源参考图，获得来源明确、可执行的构图/光线/人物指导，在 Android 相机中完成拍摄、查看成片、重拍与导出。夜间照片分析工程通过版本化 Bundle 合同进入 App。

首发边界：Android 离线闭环优先。

不自动包含：实时 Pose、云服务、未经许可模型服务、生产端分析实现、iOS。

当前候选：PR #22

基准源码：`57081b4aebc5feffdfe7460b359c5e3e031b27b3`

## M1 产品范围与数据契约

目标：冻结产品责任和数据边界。

步骤：

1. 审定 Reference→Director 用户流程。
2. 冻结 Android consumer 责任。
3. 冻结 PKB1 字段、digest、错误语义。
4. 完成 producer/consumer 边界审定。

交付物：

- PROJECT_MASTER_ROADMAP.md
- project_progress.json
- PKB1 contract
- producer handoff request

验收：

- consumer 不生成 producer 证据。
- digest/provenance 可追溯。

当前：待正式路线批准。

## M2 Android 离线产品闭环

目标：完成无需真实 AI provider 的 Android 产品闭环。

源码范围：

- `android/app/src/main/java/com/jovi/photoai/camera/CameraCapability.kt`
- `android/app/src/main/java/com/jovi/photoai/camera/CameraControlFence.kt`
- `android/app/src/main/java/com/jovi/photoai/camera/CameraXManager.kt`
- `android/app/src/main/java/com/jovi/photoai/data/capture/CaptureEngine.kt`
- `android/app/src/main/java/com/jovi/photoai/data/capture/CaptureRepository.kt`
- `android/app/src/main/java/com/jovi/photoai/data/reference/PhotoKnowledgeBundle.kt`

步骤：

1. 项目/参考图管理。
2. PKB1 导入和绑定。
3. 指导卡展示。
4. CameraX 拍摄。
5. 成片保存、重拍、恢复。
6. 本地设备复验。

验收依赖：

- JVM/build/lint。
- API35 设备验证。
- 真人体验验证独立记录。

当前：源码审查未发现可复现缺陷；设备验证 NOT_RUN。

## M3 生产端与 App 接入

目标：生产分析结果可安全进入 App。

步骤：

1. 接收 producer golden vector。
2. 校验 revision/digest。
3. 双端验证 canonical bytes。
4. 验证导入、替换、拒绝无写入。
5. 验证来源和逐图状态。

交付物：

- T14 compatibility report。
- 原始向量清单。
- consumer receipt。

依赖：生产端独立提供证据。

当前阻塞：golden vector 未提供。

## M4 真实 AI 能力资格与路由

目标：确定真实分析能力是否进入产品。

步骤：

1. 审定 provider 路线。
2. 审核模型/服务权利。
3. 验证输入隐私边界。
4. 单图真实运行。
5. 按 rubric 测量质量、失败、延迟。
6. 验证 READY promotion。

交付物：

- provider version。
- evaluation report。
- privacy/legal evidence。

当前：路线决策待审定；不阻断 M2。

## M5 真实设备、摄影体验与试点

目标：验证真实用户拍摄价值。

步骤：

1. 准备审核包。
2. 设备安装。
3. 真实照片评价。
4. 无障碍验证。
5. 问题修复。
6. 试点观察。

验收：

- 人工回执。
- 设备记录。
- 缺陷关闭证据。

当前：NOT_RUN。

## M6 发布与维护

目标：形成可维护 Android 发布流程。

步骤：

1. 当前候选重新资格化。
2. 签名材料验证。
3. 权限/隐私审核。
4. 安装升级测试。
5. 回滚演练。
6. 发布决定。

依赖：M2-M5 门禁完成。

当前：签名和发布验证未完成。

## 当前源码审查证据

已读取：

PKB1：

- `PhotoKnowledgeBundleParser.parse`
- `KnowledgeBundleProvenance`
- `canonicalPayloadBytes`
- `canonicalPayloadSha256`

核对：schema、版本、UTF-8、digest、duplicate reference、provenance。

T17：

- `T17OfflineGuidedContinuityAndroidTest`
- `T17OwnershipPolicyAndroidTest`

核对：API35 gate、process identity、bundle replacement、deleted reference safety、capture identity。

结论：当前远端源码审查未发现可复现源码缺陷。

## 依赖关系

主路径：

M1 审定 → M2 收口 → M3 producer compatibility → M5 人机验收 → M6 发布。

M4 独立决策，不允许用未授权 AI 路线阻塞 Android 离线闭环。

## 进度规则

唯一进度来源：`docs/project_progress.json`。

Markdown 必须由 `scripts/render_project_progress.py` 生成。

每次状态变化必须同步 ledger；未执行项目保持 NOT_RUN。
