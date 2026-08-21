# P22：Android 离线知识包导入与产品落地

状态：`ANDROID_LOCAL_BUNDLE_IMPORT_PRODUCT_READY_AWAITING_APPROVED_PIPELINE_ARTIFACT`
基线：`origin/codex/p21-offline-product-closure` @ `a7fc499b4835ad95d4af55a2c74cfc0983f747ab`

## 目标

在不启动 Qwen、不访问夜间 Pipeline、不读取真实照片的前提下，把 P21 的静态 Photo Knowledge Bundle 契约落成 Android 可实际使用的离线导入路径：

`系统选择 JSON → 严格校验与 SHA-256 → 用户逐项绑定项目照片 → 事务性 READY → 逐张详情/主参考/Camera Director`

目标状态：`ANDROID_LOCAL_BUNDLE_IMPORT_PRODUCT_READY_AWAITING_APPROVED_PIPELINE_ARTIFACT`

## 执行清单

- [x] 从 P21 最终候选建立独立 P22 worktree/分支；主工作树 Owner 修改保持不变。
- [x] 冻结 Photo Knowledge Bundle v1 的跨语言 canonical digest，并让合成 fixture 使用真实摘要。
- [x] 新增 Android 严格 JSON 解析、大小/UTF-8/字段/隐私/重复 ID/摘要校验。
- [x] 使用系统 `OpenDocument` 读取一次性 JSON；不新增存储权限、不持久化 URI 或原始正文。
- [x] 增加脱敏预览和用户逐项一对一照片绑定；禁止按顺序、文件名、路径或媒体哈希猜测。
- [x] 新增独立 Knowledge Bundle provenance、Room v4→v5 迁移和全事务应用；不得覆盖既有 READY。
- [x] READY 进入 AI 指导必须具有有效 Provider 或 Knowledge Bundle provenance。
- [x] 项目页区分“导入离线知识包”和“连接本机并逐张分析”；无项目级 summary 时不虚构模型汇总或推荐主参考。
- [x] 增加单项目删除确认，删除该项目记录/私有图片/汇总且不影响其他项目。
- [x] 补齐 JVM/Compose/API 35 合成测试、构建、lint、隐私和权限 Gate。
- [x] 更新用户说明、资格报告、任务清单和 Obsidian；提交并非 force push P22 候选。

## 本轮边界

- 不启动或修改 Qwen 服务、TLS、防火墙或局域网。
- 不访问兄弟 Pipeline 仓库、运行输出、数据库或真实 Bundle；仅使用本仓库合成 fixture。
- 不读取真实照片、不操作物理设备、不合并 main、不分发 APK、不执行五人试点。
- 按 Jovi 本轮要求不进行独立 Reviewer 审查；本轮结论只到实现方资格与候选推送。

## P22 复验结论（2026-08-21）

- [x] 官方 API 35 单设备流程 PASS：系统 Picker、100%/200% 字体、LocalTransport、D2D 自动恢复和 reconciliation 均通过。
- [x] P22/P21 Android instrumentation PASS：13/13；JVM 124/124；服务 5/5；合同 12/12；lint 0 errors；privacy/diff PASS。
- [x] 签名 Release APK/AAB、证书指纹、API 35 安装和启动 PASS；产物只写入仓库外逻辑证据目录。
- [x] 报告 `reports/P22_OFFLINE_PRODUCT_CLOSURE_QUALIFICATION.md` 已脱敏；不包含绝对私人路径、照片、模型、数据库、设备 serial 或原始日志。
- [ ] 独立审查、Qwen 私有 LAN、夜间 Pipeline 实际 artifact、main 合并、发布和五人试点：NOT_RUN（均需新的独立授权）。
- [x] Qwen 激活只做了安全前置检查：模型/Python 通过；系统 PowerShell 确认真实以太网为 `Public`、FlClash 为虚拟 `Public`，因此以 `P20_PILOT_SESSION_BLOCKED_NETWORK_PROFILE` 停止；未启动服务、TLS、防火墙或真实分析。

---

# P21：无 Qwen 服务的 Android 产品收口与接入就绪闭环

状态：`ANDROID_OFFLINE_BETA_PRODUCT_CLOSURE_READY_AWAITING_QWEN_AND_PIPELINE`
基线：`origin/codex/android-beta-release-candidate` @ `0bd618109202b1ce7c5584275da903e4728b0d32`

## 目标

离线闭环：`新建项目 → 最多 20 张私有导入 → 筛选/删除/选择主参考 → 无 AI 指导拍摄 → 系统另存为`。

真实 AI 路径保持为：`连接本机服务 → 逐张 READY 分析 → READY-only 汇总 → AI Camera Director`。

## 执行清单

- [x] 从干净 Beta 候选建立独立 P21 worktree 和分支；主工作树 Owner 修改已保留。
- [x] 建立脱敏候选/Owner 基线证据；不读取真实照片、不启动 Qwen、夜间管线、LAN 或五人试点。
- [x] 非 `READY` 主参考只允许无 AI 指导拍摄；路由层不得被旧导航绕过。
- [x] 分析详情页仅允许 `READY` 进入 AI Director Card；示例指导不能进入实际指导拍摄。
- [x] 未配对状态常驻、可访问的“本机分析服务未连接”提示；分析 CTA 改为“连接本机并逐张分析”。
- [x] 零 `READY` 汇总页提供一致的离线说明与无指导拍摄入口。
- [x] 新增 Photo Knowledge Bundle Consumer 静态契约、合成 fixture 与兼容测试；不读取 Pipeline 运行时输出。
- [x] 使用合成媒体完成 JVM/Compose/API 35 离线 Gate、D2D 和 Release 复验。
- [x] 复核 P20 核心 Provider/Coordinator/Room/summary canonical blob 未漂移；P0 UI 变化由合成路由测试覆盖。
- [x] 运行隐私、权限、敏感产物与差异检查；不提交私人数据、APK/AAB、证书或配对材料。
- [x] 创建文档提交、非 force push、detached 独立审查，并更新最终任务/Obsidian状态。

## 审查结论（2026-08-15）

- [x] 三个 P21 线性提交已推送至 `codex/p21-offline-product-closure`；固定独立审查边界为 `6edb41ddeca79c24f981f5691f6c7673291d74eb`。
- [x] 新 detached worktree 独立审查为 `PASS`：P20 核心 blob、签名 APK/AAB、119 项 JVM、lint、服务/隐私、官方 API 35 D2D、离线真实性、另存为与 Release 启动均已复验。
- [x] 专用 API 35 AVD 已恢复出厂并停止；没有接触物理设备、真实照片、Qwen、LAN 服务、夜间 Pipeline 或五人试点。
- [ ] 后续只保留两个需独立授权的激活 Gate：本机 Qwen 私有 LAN 与真实逐张分析；夜间 Pipeline Photo Knowledge Bundle 实际接入。

## 边界

- 非 `READY` 绝不把 Demo 或固定 Bundle 冒充当前照片的真实分析。
- 不修改 `ReferenceBundle`、Room schema、`PhotoAnalysisCoordinator`、本机 Provider、READY-only 汇总协议或公开 API。
- Qwen 私有 LAN 激活与夜间 Bundle 实际接入是 P21 审查通过后的两个独立授权 Gate。
