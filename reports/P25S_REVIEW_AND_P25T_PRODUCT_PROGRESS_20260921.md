# P25S 审核与 P25T 可见产品增量｜2026-09-21

状态：`P25T_IMPLEMENTED_HOST_VERIFIED_ANDROID_VALIDATION_PENDING`。
本轮在原分支 `codex/p25s-internal-handoff-validation-20260920` 继续线性提交；没有修改 main、旧分支或 Pipeline。

## 1. 审核基线与证据层级

- App main：`1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`。
- 收到的最新 P25S：`61a7c1da92c51b791aac4ea094e6efa56adf474e`。
- P25R 前序：`0d0d72e8c658cecde416e77405fefbbb37b32b22`；到 P25S 为四条后续提交，新增内部 fixture、Python/Android 测试与报告，没有 Android 生产功能变化。
- Pipeline main：`ffc4130823c1308f089b835c766e341ec2173e82`。
- Pipeline 新过渡分支：`gpt/real20-transition-20260921@50d9ffac597570ceba3bfea44ff9dc6448ba85c0`，只增加 R0 元数据准备；其 STATE 明确实际 Real20 executor 尚未实现。

这是本轮源码审查及有限宿主测试，不是声称对两个仓库所有文件进行了完整安全审计，也不是独立 Android 全量资格审查。Windows E:/C: 的原始证据、私钥、设备均未访问。

## 2. 对本地 Codex 工作的判断

工作是真实的、有用的：P24 留下签名身份/产物报告；P25 形成二十条编辑底稿；P25R 修复网页提交中的真实 SyntaxError 和 PackError 捕获问题；P25S 使用实际 Parser、Repository、隔离 Room 做显式绑定与无部分写入测试。

但它们的成果类别不同。测试夹具通过不等于生产 Pipeline 出包；二十条通用编辑指导不等于二十张真实照片被模型分析；签名通过不等于用户流程或摄影效果已验收。P25R 的字符串/摘要检查不能认证真人身份或核验权利证据，模拟 APPROVED 只能留在测试内部。

最新 P25S 报告末尾已经记录新建专用 API35 AVD 后 P25S 2/2、P23C Parser 4/4、Room/重启 2/2 通过。旧模拟器阻塞是历史事件，不应继续列作当前阻塞。本轮没有重跑这些 Android 测试。P24 已有签名身份；后续进程缺少环境变量不证明密钥不存在，禁止为此重新生成身份。

## 3. 本轮逐项发现与处理

| 项目 | 发现 | 处理 |
|---|---|---|
| 交接 fixture 校验 | 原函数只检查少量字段；数值/非法文本可在 hash 阶段抛非稳定异常，也可返回下游拒收对象 | 严格根结构/元数据/ID/唯一性检查，最终复用实际 PKB1 checker；仍固定 synthetic producer/release |
| 固定 JSON 测试资源 | 原测试直接比较 Windows checkout bytes 与 LF 生成结果 | 仅对 checked-in fixture 做 CRLF→LF；外部 corpus/review 的原字节绑定不变 |
| Room 交接测试 | 只检查数量/状态/来源，不能排除九字段错配或按数组顺序配图 | 反向绑定目标，逐条核对九字段及全部 Bundle provenance、无 Provider、一次读取与 Flow 无 summary |
| 导演卡文案 | 实际 READY 指导仍显示固定示例文字 | 改为当前参考内容及来源说明，明确非实时现场检测 |
| 导演卡映射 | environment 只含 scene/background，漏掉 lighting/composition | 保留并显示光线、构图 |
| 导入操作 | 二十条知识每条二十个编号，无法可靠看图选择；只展示三项指导 | 缩略图选择对话框、绑定预览、显式解绑、完整九项详情；大项目不再铺四百编号按钮 |
| 项目切换 | UI 可能收到上一项目的暂存 rows | 当前项目过滤，外来 rows 不用于选图/启用提交；数据库原有事务检查不变 |
| 进度文档 | 原 P25S 报告混有历史失败和已解除的待办 | 明确时间顺序，当前待办不再要求重修已经恢复的 AVD |

## 4. P25T 产品增量与边界

新增的五项可选拍摄准备：现场安全、环境、人物、情绪、相机。勾选只代表用户自己的准备，不改变 READY、不认证现场、不阻止原有拍摄入口。保存的只有内容/来源范围摘要和五位状态；离开本页后不承诺作为项目拍摄历史留存。

选图只读取已有私有派生 JPEG，不扫描原图库、不保留原 Picker URI。对话框使用 LazyColumn 只构建可见候选。所有绑定仍走现有 ViewModel/Repository；不按顺序猜测、不覆盖 READY、不抢占别的映射。没有引入人审批准、生产知识、网络调用、Room schema 或签名身份变更。

## 5. 实际执行结果

| 验证 | 本轮结果 |
|---|---|
| `test_p25s_fixture_boundary.py` | 16/16 unittest 方法 PASS；真实 adapter + 原 PKB1 checker；合成输入 |
| `test_p25t_director_core.py` | 实际 Kotlin preparation 源码编译/执行，34 个断言 PASS |
| Python compileall | 本轮物化 Python 文件 PASS |
| 原 privacy scanner + diff check | 本轮物化源码快照 PASS，不是全仓库/Owner 磁盘审计 |
| 既有 P25/P23C 完整回归 | NOT_RUN；完整仓库未在当前执行环境克隆 |
| 新增 Android | 7 个方法已编写，NOT_COMPILED / NOT_RUN；P25S 原 2 方法已加强但亦未重跑 |
| Windows / JPEG 真缩略图 / Compose / 字体截图 | NOT_RUN，必须本地验证 |
| 独立审查 / main 合并 / 生产出包 / 真机 | NOT_RUN |

新增 Android 用例中的 P23RRoomFixture 是隔离 Room + 合成元数据，不含实际 JPEG。它能测试操作状态，不能证明缩略图真实解码；本地必须用程序生成 JPEG 增补这部分证据。saved-instance-state 测试不是 OS force-stop 测试。

源代码提交：`d720640` 修复交接；`6569ef6` 实现可见功能；`9ea075a` 增加七项 Android 方法。新 manifest 绑定完整 `9ea075a95891faa68db45cd436096ea9c94a21d8`，不改写历史清单。

## 6. 当前离终点还缺什么

App 具备项目/私有照片/显式状态/参考分析结果/指导/基础相机和导出基础。CameraScreen 的 analyzer 目前只关闭 imageProxy，因此没有持续的现场环境识别或实时姿态纠正。latestCapture/pendingSave 仍是 remember 状态，下一轮要验证及完善拍摄结果在旋转/退出/保存返回时的恢复和项目关联，不能宣称已有完整作品管理。

Pipeline 具备本地摄取、状态恢复、模型/合成研究和受控执行基础，但本次未找到已可用的真实二十张生产执行—可审查知识—正式导出—App 消费闭环证据。其新 Real20 分支明确只是元数据准备。不能直接按 500–600 张自动夜间生产承诺。

关键合同不一致：Pipeline `docs/04_export_contract.md` 是目录式 Photo Intelligence Bundle v1（items/assets/CHECKSUMS）；App 是无图片单 JSON Photo Knowledge Bundle v1（PKB1）。需要正式投影器和跨仓库向量，不能只改文件名、origin 或把合成夹具当 exporter。

行动见 `docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md`；本地入口见 `docs/handoff/P25T_LOCAL_CODEX_HANDOFF_20260921.md`。
