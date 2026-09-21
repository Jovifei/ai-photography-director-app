# P25S 内部交接验证与 Pipeline 只读预检

历史资格状态：`P25S_LOCAL_VALIDATED_AWAITING_INDEPENDENT_REVIEW`（绑定 `61a7c1da92c51b791aac4ea094e6efa56adf474e`）。
后续源码修复和 P25T 功能增量必须重新本地验证，见 `reports/P25S_REVIEW_AND_P25T_PRODUCT_PROGRESS_20260921.md`，不能继承本页 PASS。

## 决定与边界

Jovi 批准当前二十条 corpus 用于内部工程验证。正式内容、权利、隐私审核仍为 `0/20 NOT_RUN`。测试内的 APPROVED 回执使用 SIMULATED 身份/证据，不作为真实审核回执。

测试夹具固定 producer=`synthetic_internal_handoff_test`、release=`not_for_release`。它不是生产 Pipeline 输出；App/public/release authority 不因此获得批准。

## Pipeline 历史接口预检（2026-09-20）

当时只读核对 `nightly-photo-intelligence-pipeline@1b9997ba0b099fd159932a8de0697f78d1fc7867`；未修改 Owner 的 Pipeline 工作树。

| 能力 | 当时结论 |
|---|---|
| photoai.curated-editorial-input.v1 输入 | 无该格式 importer/schema |
| 二十条九字段保留与来源版本记录 | 无该输入的生产实现 |
| 导出格式文档 | docs/04_export_contract.md 定义目录式 Photo Intelligence Bundle，不是 App 单 JSON PKB1 |
| APPROVED-only 生产出口 / App 接入 | 当时 N5/N7 为 LOCKED，没有可用生产 exporter 证据 |

2026-09-21 后续只读核对看到 main=`ffc4130823c1308f089b835c766e341ec2173e82` 与新 Real20 R0 分支；请按新报告和当前远端继续，不能再把上述历史 SHA 当最新。

Producer 下一实现需明确：严格输入解析；九字段与 reference/source ID 保留；corpus/review/producer 版本记录；真实导出投影；跨仓库向量。现有目录合同与 App PKB1 要显式适配，禁止只改 origin 或把 fixture 当正式出包。

## App 内部交接实现

P25S 增加测试专用映射器与固定 Android 资产。Python 从模拟 curated handoff 构造 deterministic PKB1，验证二十条 ID/顺序/九字段。Android 命中实际 Parser、隔离 Room、二十个显式 binding、离线知识来源及无 Bundle-only summary；最后一条坏 binding 验证无部分写入。

既有 P23C 持久化 harness 负责默认数据库的 prepare/外部 force-stop/verify 读取。P25S 原始提交没有生产 Android、Room、Provider 或签名代码变更。

## 资格经过（保留历史与最新结果，不混在同一阻塞项）

初期两套旧 AVD 虽报告 Boot completed，但 ADB 未注册、没有可用 emulator endpoint，因此当时没有执行 instrumentation，也没有用实体手机代替。

随后在 2026-09-21 创建新的专用 API35 AVD，原始报告记录设备 qemu/SDK/boot 检查通过，P25S `2/2`、P23C Parser `4/4`、P23C Room/重启 `2/2` 通过。**该候选的 Android runtime 阻塞已解除。** 本次文档整理没有重新运行这些测试，原始日志仍保存在仓库外逻辑证据目录 `p25s-internal-handoff-20260920`。

| 历史候选 Gate | 记录结果 |
|---|---|
| P25 Python | 27/27 PASS |
| P23C Python | 55 PASS，1 symlink 权限 SKIPPED |
| AndroidTest Kotlin / Debug / Test APK | PASS |
| JVM | 130/130 PASS |
| Debug/Release lint，排除 signing guard | 0 errors，24/23 warnings |
| fresh API35 | P25S 2/2、P23C 4/4 + 2/2 PASS |
| 当前运行进程 release signing 输入 | 缺 PHOTOAI_RELEASE_STORE_FILE；不代表 P24 签名身份丢失 |
| 真机 / 真实 Pipeline release | NOT_RUN |

## 后续实际待办

1. 审查并复验后续修改后的精确 SHA；不再把修复旧 AVD 列为未完成的 P25S Gate。
2. 工程侧完成真实 producer importer/导出及格式投影；本轮只读核验不是已接通。
3. 正式生产内容/权利/隐私证据与真实放行单独处理；内部 synthetic 工程继续，不反复要求 Owner 手写 JSON。
4. P26 知识包身份与 App 验签单独设计，不复用 Android APK 私钥。

本页证明历史内部测试结果，不代表生产知识包发布，也不代表后续 P25T UI 已运行通过。
