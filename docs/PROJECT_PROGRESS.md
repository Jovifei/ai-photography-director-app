# 项目进度总览

更新时间：2026-10-02
总体规划状态：**APPROVED**

由 project_progress.json 自动生成。每条进度表示已有证据的检查点完成数，不代表摄影质量或整体产品完成百分比。

候选：[PR #22](https://github.com/Jovifei/ai-photography-director-app/pull/22)；证据基准 `57081b4aeb5cfeffdfe7460b359c5e3e031b27b3`。

| 大阶段 | 进度条 | 检查点 | 状态 | 首发要求 |
|---|---|---|---|---|
| M1 产品范围与数据契约 | ██████████ | 4/4 | IN_PROGRESS | 是 |
| M2 Android 离线产品闭环 | ████████░░ | 5/6 | IN_PROGRESS | 是 |
| M3 夜间分析生产端与 App 接入 | ██░░░░░░░░ | 1/5 | WAITING_EXTERNAL | 是 |
| M4 真实 AI 能力资格与路由 | ██░░░░░░░░ | 2/7 | ROUTE_DECISION_PENDING | 路线待远端审定 |
| M5 真实设备、摄影体验与试点 | █░░░░░░░░░ | 1/6 | HUMAN_ACCEPTANCE_PENDING | 是 |
| M6 Android 发布与持续维护 | █░░░░░░░░░ | 1/6 | RELEASE_PENDING | 是 |

**下一动作：** 远端复审进度完整性防护及本地确定性恢复，20项行为验证PASS；T14等待生产端独立向量。

[大阶段实施步骤与完成标准](PROJECT_MASTER_ROADMAP.md)

## M1 产品范围与数据契约

| 检查点 | 状态 | 证据或阻塞 |
|---|---|---|
| 产品目标与 Android 首发边界 | DONE | 当前 README / T17 交接；用户 Reference→Director 目标 |
| PKB1 消费端合同 | DONE | 合同12/12及严格解析/摘要规则 |
| App/生产端责任及交接要求 | DONE | PKB1_PRODUCER_GOLDEN_VECTOR_HANDOFF_REQUEST.md |
| 远端总路线图审定 | DONE | 远端恢复会话技术规划APPROVED；PR23回执078fe8ff759b357e8825017219c88c737b01e124 |

## M2 Android 离线产品闭环

| 检查点 | 状态 | 证据或阻塞 |
|---|---|---|
| 项目/参考图/指导界面 | DONE | T3–T13 阶段源码与报告 |
| 拍摄/成片库/重拍/导出 | DONE | P25U/T12–T17 源码与报告 |
| 阶段级合成/API35远端资格 | DONE | T17远端96/96，逐阶段远端接受 |
| 当前候选本地JVM/构建/Debug lint | DONE | 本轮159/159，Debug/AndroidTest构建PASS，lint0错误26警告；本机限定复验报告 |
| 当前候选本机API35复验 | DONE | 本机API35限定T17六阶段+8保护PASS；完整96矩阵本轮未重跑；LOCAL_API35_T17_20261002.md |
| 整合PR远端复核与主线接收 | PENDING | PR22/PR23 Draft；远端交接已接收，完整审查与主线接收尚未完成 |

## M3 夜间分析生产端与 App 接入

| 检查点 | 状态 | 证据或阻塞 |
|---|---|---|
| 消费者交接要求 | DONE | PKB1生产端golden-vector请求已准备 |
| 生产端原始向量/版本/摘要 | PENDING | PENDING_EXTERNAL_GOLDEN_VECTORS |
| 双端canonical摘要兼容 | NOT_RUN | 等待生产端独立生成结果 |
| App明确绑定/替换/拒绝无写入兼容 | NOT_RUN | T14待运行；App-only fixture不是生产端证明 |
| 端到端来源与逐图状态交接 | NOT_RUN | 实际生产端交接尚未完成 |

## M4 真实 AI 能力资格与路由

| 检查点 | 状态 | 证据或阻塞 |
|---|---|---|
| Provider/READY权限边界 | DONE | T6/T9/T10/T11授权与promotion合同 |
| 评价协议与合成工具 | DONE | T7/T8 rubric和synthetic harness |
| 首发AI路由决定 | PENDING | 夜间生产端与Qwen可选路线待总体规划 |
| 具体路线权利/运输/资源条件 | BLOCKED | Qwen T11 BLOCKED_LEGAL_REVIEW；其他路线未资格 |
| 最小运行与单图真实结果 | NOT_RUN | 当前候选无真实Provider运行证明 |
| 实测质量/延迟/失败行为 | NOT_RUN | rubric仍为未测/阈值待审定 |
| 可信READY接线与降级 | NOT_RUN | 政策工具不等于真实产品promotion |

## M5 真实设备、摄影体验与试点

| 检查点 | 状态 | 证据或阻塞 |
|---|---|---|
| 人工审核工具与说明 | DONE | P25R审核包生成器及指导；不代表人工评分 |
| 当前候选物理手机验证 | NOT_RUN | 无当前候选实体设备验收 |
| 真实照片与摄影指导实用性 | NOT_RUN | 无人类接受结果 |
| TalkBack/大字体/设备相机差异 | NOT_RUN | 机器测试不替代真人无障碍评价 |
| 实测问题修复闭环 | PENDING | 等实际问题与修复复验 |
| 受控试点与观察回执 | NOT_RUN | 历史试点准备不是当前试点PASS |

## M6 Android 发布与持续维护

| 检查点 | 状态 | 证据或阻塞 |
|---|---|---|
| 已有交付工具和流程 | DONE | 历史P24签名/安装工具可复用；需当前候选重资格 |
| 当前候选外部签名身份资格 | BLOCKED | 缺少PHOTOAI_RELEASE_STORE_FILE |
| 已审查主线与发布决定 | PENDING | PR22 Draft；人工/代码门未闭合 |
| 签名包安装升级/数据保存 | NOT_RUN | 当前候选未验证 |
| 回滚与版本支持追溯 | NOT_RUN | 当前发布版本未演练 |
| 发布范围与上线回执 | NOT_RUN | 没有正式发布结果 |

更新命令：`python scripts/render_project_progress.py`；一致性检查：`python scripts/render_project_progress.py --check`。
