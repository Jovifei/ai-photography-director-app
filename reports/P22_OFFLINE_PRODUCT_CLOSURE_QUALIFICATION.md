# P22 Offline Product Closure Qualification

状态：`ANDROID_LOCAL_BUNDLE_IMPORT_PRODUCT_READY_AWAITING_APPROVED_PIPELINE_ARTIFACT`

本报告记录 P22 的实现级资格结果。它不启动 Qwen3、本机 LAN、夜间管线、真实照片、实体设备或五人试点；独立审查按 Jovi 指令未执行。

## 候选与边界

- runtime/test source SHA：`967dc92`（`fix(offline)` + `test(offline)`）
- 产品边界：最多 20 个私有参考、显式 Photo Knowledge Bundle 映射、离线手动拍摄、系统另存为。
- 真实 AI 边界：只有受信任的 `READY` Provider/Bundle provenance 才能进入 AI Director；未分析或来源无效的主参考只能进入明确的无 AI 指导拍摄。
- P20 Provider、Coordinator、ReferenceBundle、ProjectSummaryProvider 和本机 LAN Provider 文件相对 P21 基线保持不变；Room、项目看板和离线导入是本阶段有意变化，因此不复用 P20 真实照片运行证据作为 P22 证明。

## Gate 结果

| Gate | 结果 | 说明 |
|---|---|---|
| Photo Knowledge Bundle v1 contract | PASS | schema、fixture、PKB1 digest、篡改/路径/URI/模型字段拒绝共 12 项通过 |
| JVM | PASS | 124 tests，0 failure，0 error，0 skipped |
| AndroidTest APK | PASS | Debug/Test APK 构建成功 |
| API 35 P22 instrumentation | PASS | 严格解析 4/4、Room 4→5 1/1、事务映射/删除 1/1、系统 OpenDocument 显式映射 1/1 |
| P21 truthfulness regression | PASS | 3/3；无 provenance 的 READY/示例指导不能进入 AI Director |
| System Picker / responsive semantics | PASS | Picker 2/2、响应式/无障碍 1/1；官方脚本另有 100%/200% 字体 PASS |
| Official Local/D2D fixture flow | PASS | LocalTransport 与官方单设备 D2D 自动恢复、reconciliation 均通过 |
| Debug lint | PASS | 0 errors，17 warnings |
| Release lint | PASS | 0 errors |
| Local service unittest | PASS | 5/5 |
| Phase 1.5 contract suite | PASS | 全部通过 |
| Privacy audit | PASS | 未发现照片、模型、数据库、APK/AAB、密钥或常见秘密进入仓库 |
| `git diff --check` | PASS | 仅有 Windows 换行提示，无差异错误 |
| Signed Release APK/AAB | PASS | APK `apksigner`、AAB `jarsigner -verify`、证书 pin 均通过 |
| Signed Release install/launch | PASS | API 35 专用 emulator 安装、启动、包名和版本核对通过 |
| Qwen3 / private LAN | BLOCKED_PREFLIGHT | 模型/Python 前置通过；系统 PowerShell 确认真实以太网接口 `index 21` 为 `Public`，FlClash `index 46` 为虚拟 `Public`；预检以 `P20_PILOT_SESSION_BLOCKED_NETWORK_PROFILE` 安全停止，未启动服务、TLS 或防火墙 |
| Night pipeline artifact | NOT_RUN | 等待独立批准的 Bundle artifact |
| Real photos / physical device | NOT_RUN | 本轮未读取真实照片，未触碰实体设备 |
| Independent review | NOT_RUN | 按 Jovi 指令不执行 |

## 产物与外部证据

证据只保存在仓库外的逻辑目录 `p22-offline-product-closure/<UTC_RUN_ID>/`，仓库不包含 APK/AAB 或运行日志。

- qualification summary SHA-256：`EC0A49601C800FD3DB206F79CE2D7D468756278632C71C4E13D7D9AEBBF73611`
- APK SHA-256：`7B95C6A3D133554241F9BA810BAB55622DE2FD9E1B0468E234379409608AA0C3`
- AAB SHA-256：`B95CDEACFE7074EE2FDA0D987A1E2052C6386656A360F0C72452CAF6F5789758`
- signing certificate SHA-256：`623C7DB70A8AA8552BD59B20BC103152326062F7FEC3D6D9313F8BBB94469CD0`
- D2D summary SHA-256：`DB897C190D8ED0C8C46BDCEC864662EF62BF6A357373CFCF25D24CCCE7F9C58E`

包契约只允许 opaque ID、结构化摄影字段、来源、版本和完整性摘要；不接收原图、URI、路径、EXIF、账号、设备标识、密钥、模型权重或原始输出。Bundle 映射必须由用户明确确认，不能按序号猜测，也不会覆盖已有 `READY/RUNNING/QUEUED` 记录。

## 后续唯一入口

1. 将受控 Wi-Fi 的 Windows 网络类别设为 `Private` 后重新运行预检，再经独立授权接入本机 Qwen3 Private LAN：逐张真实分析、READY-only 汇总和 AI Camera Director。
2. 独立授权后接入夜间管线 Bundle artifact；先通过 PKB1 合同、ID 映射和完整性 Gate，再进入 App。

P22 到此停止，不进入 Pose、Pipeline 运行、Cloud、iOS、发布或五人试点。
