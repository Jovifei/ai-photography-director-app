# P21：无 Qwen 服务的 Android 产品收口与接入就绪闭环

状态：`P21_OFFLINE_PRODUCT_CLOSURE_QUALIFIED_AWAITING_PUSH_AND_INDEPENDENT_REVIEW`
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
- [ ] 创建文档提交、非 force push、detached 独立审查，并更新最终任务/Obsidian状态。

## 边界

- 非 `READY` 绝不把 Demo 或固定 Bundle 冒充当前照片的真实分析。
- 不修改 `ReferenceBundle`、Room schema、`PhotoAnalysisCoordinator`、本机 Provider、READY-only 汇总协议或公开 API。
- Qwen 私有 LAN 激活与夜间 Bundle 实际接入是 P21 审查通过后的两个独立授权 Gate。
