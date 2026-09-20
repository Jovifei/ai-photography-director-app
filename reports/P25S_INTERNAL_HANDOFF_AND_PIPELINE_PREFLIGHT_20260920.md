# P25S 内部交接验证与 Pipeline 只读预检

状态：`P25S_INTERNAL_HANDOFF_ENGINEERING_COMPLETE_ANDROID_RUNTIME_BLOCKED`。

## 决定与边界

Jovi 暂时批准当前 20 条 corpus 用于内部工程验证。正式内容、权利、隐私审核仍是 `0/20 NOT_RUN`；本轮使用的 APPROVED 回执仅在测试进程内构造，身份和 evidence 均以 `SIMULATED-*` 标记，不生成或提交真实回执。

本轮不会把测试夹具声明成真实 Pipeline 输出。夹具固定为：

- producer：`synthetic_internal_handoff_test`
- release：`not_for_release`
- app/public/release authority：全部 false

## Pipeline 只读接口核验

只读核验 `Jovifei/nightly-photo-intelligence-pipeline` 的 `origin/main@1b9997ba0b099fd159932a8de0697f78d1fc7867`。本地 Pipeline 主树有 Owner 改动，因此未写入、未切分支、未重置。

| 能力 | 当前代码证据 | 结论 |
|---|---|---|
| `photoai.curated-editorial-input.v1` 输入 | 全仓库无格式标识、schema 或 importer | `MISSING` |
| 20 条原始九字段保留 | 现有 item schema 面向资产分析，不接受该 handoff 根结构 | `MISSING` |
| 输入 corpus/review/producer 版本记录 | 当前生产代码无该输入的 provenance record | `MISSING` |
| Photo Knowledge Bundle v1 | `docs/04_export_contract.md`、schema、示例存在 | `CONTRACT_ONLY` |
| APPROVED-only exporter | N5 任务仅规划，状态 `LOCKED`；源码无生产 exporter | `MISSING_AND_LOCKED` |
| App Bundle 集成 | N7 任务状态 `LOCKED` | `LOCKED` |

### Producer 实施交接

后续须在 Pipeline 自身获得明确阶段授权后实现，验收条件如下：

1. 严格解析 `photoai.curated-editorial-input.v1`，拒绝未知字段、重复键、错误 hash、非 20 条或非正式审核绑定。
2. 逐条保留 `reference_id` 与九个 photography 字段；记录 corpus SHA、review SHA、Pipeline commit、producer/release ID。
3. 由真实 Pipeline exporter 生成 App 的 PKB1 根结构、canonical payload digest 和不可变 release 记录。
4. 生产 Bundle 必须只含正式审核通过内容，并通过跨仓库合同测试；测试夹具不得升级为生产 provenance。

## App 内部交接实现

新增测试专用映射器与固定资产，从模拟 `photoai.curated-editorial-input.v1` 生成确定性 PKB1。Python 检查当前 20 条 ID、顺序和九字段逐字保留，并验证提交的 Android 资产与生成结果一致。

新增 Android 测试覆盖：

- 真实 Android Parser 解析 20 条；
- 20 个显式一对一 binding；
- 真实隔离 Room 原子写入；
- `离线知识包` 来源、PIPELINE provenance 和 Bundle-only summary 为空；
- 最后一条 binding 无效时 20 条全部保持 `IMPORTED`，无部分写入。

既有 `P23CPythonBundleRoomAndroidTest` 继续承担默认数据库持久化与 OS process restart 读取验证；本轮没有改动生产 Android、Room、Provider 或签名代码。

## 本轮结果

| Gate | 结果 |
|---|---|
| P25 Python | PASS `27/27` |
| P23C Python | PASS `55`、SKIPPED `1`（Windows symlink privilege） |
| AndroidTest Kotlin compile | PASS |
| JVM | PASS `130/130` |
| Debug APK / Test APK | PASS |
| lintDebug / lintRelease（排除 signing guard） | PASS，0 errors；24/23 warnings |
| API35 emulator instrumentation | `BLOCKED`：2026-09-21 对专用 API35 AVD 重复冷启动；普通窗口、`-wipe-data`、`-no-snapshot-load/-no-snapshot-save`、显式 `-ports 5556,5557 -no-direct-adb -adb-path` 均报告 boot complete，但 5556/5557 无监听且 ADB 未注册 serial |
| 实体 OnePlus | `NOT_RUN`；未 install、未 instrumentation |
| release signing | `BLOCKED`：当前进程缺少 `PHOTOAI_RELEASE_STORE_FILE` |
| Pipeline production release | `NOT_RUN` / `LOCKED` |

模拟器原始日志仅保存在仓库外逻辑证据目录 `p25s-internal-handoff-20260920`，不提交设备标识或私人路径。

### 2026-09-21 宿主复验结论

- Android Emulator：`36.6.11.0 (build_id 15507667)`；Windows Hypervisor Platform 检查通过。
- AVD：`MateLink_P0_Qualification_API35`，目标 API 35，启动日志显示 `Boot completed`。
- 复验矩阵：普通窗口启动、`-no-window`、`-wipe-data`、显式 `-no-snapshot-load/-no-snapshot-save`、显式 console/ADB 双端口与 `-no-direct-adb`，全部相同结果。
- 观测：`adb devices -l` 始终没有 `emulator-5556`；`Get-NetTCPConnection` 没有 5556/5557 listener；emulator 日志反复报告 `adb.exe ... emulator-5556 ... device not found`。
- 判断：这是宿主 Emulator↔ADB bridge 注册故障。没有运行 instrumentation，也没有把实体设备当作替代品；需要恢复 ADB/Emulator 安装或换用已确认可用的专用 API35 AVD 后再继续。

## 剩余正式 release 条件

- 真实 20/20 内容、权利、隐私审核回执及唯一 evidence；
- Pipeline 阶段授权、curated input importer、版本记录和生产 PKB1 exporter；
- 专用 API35 emulator 恢复后，运行新增 P25S 2 个方法及既有 P23C process-restart 链；
- P26 独立知识包签名身份与 App 验签设计，不复用 Android APK signing identity。

当前只可描述为“内部交接工程实现完成，Android 运行 Gate 阻塞，等待正式 release 条件”，不得描述为生产知识包已发布。
