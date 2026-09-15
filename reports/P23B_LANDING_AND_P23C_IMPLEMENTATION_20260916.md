# P23B 合入与 P23C 离线知识包准备｜2026-09-16

## A. 已完成的远端代码合入

Owner本轮提交安全落地候选，并委托决定精确SHA是否合入。网页端读取远端确认：

- 合入前main：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`。
- 已独立审查App候选（Owner提供PASS）：`e4a954b313320944fe751a2388366ee72d3e9083`。
- 安全落地候选：`f2d6a87f85ba3b2ddd9f83544a6d912890019e7c`。
- f2两父提交按序为61a、e4a；树为`b5af21f9789d8a277e32df9817ad844f9d4d090a`。
- GitHub compare e4a...f2：仅删除tasks/lessons.md、tasks/todo.md，无其他差异。
- 根目录树未发现`.github`工作流；本次没有触发显式发布操作。

本轮接受代码合入，并以`force=false`将main前进到f2；随即远端回读一致。
不改变旧分支、不重写历史；tasks仅从当前提交树排除，不代表已从Git历史/旧分支移除。
P23B工具验证分支34949f81不在此次合入范围内。

依据：Owner提供的e4独立PASS、f2合并树28/28及38/38等复验结果、Owner18项0 mismatch，
加上本轮远端父提交/差异核对。**这些Android和Owner磁盘证据不是网页端新鲜重跑。**
这是源码集成决策，不是正式闭测发布。签名仍缺Owner外部输入，不生成密钥、不触设备。

## B. 新阶段与逐步提交

状态：`P23C_PREPARATION_IMPLEMENTED_LOCAL_VALIDATION_PENDING`。
分支：`codex/p23c-public-pack-preparation-20260916`，从f2建立，不修改main的生产逻辑。

| 步骤 | 提交 | 实现 |
|---|---|---|
| P23C-01 | b9e52345e5127f587ffe977d85c02ba9b5fe6d95 | 严格PKB1合同检查、既有golden对照、合成Unicode向量与4个Android兼容测试 |
| P23C-02 | 787bbde15b9beaa9f45da761006ab56fb04ae5a5 | 逐条审核回执、用途与字节绑定、可重现未签名候选归档、CLI与文件安全测试 |
| P23C-02b | ae29bc32c122830cd6d03b06ec647ca73ae51759 | 区分测试资源CRLF检出与运行时原文件审核摘要，新增回归 |
| P23C-03 | 本报告所在后续docs-only提交 | 说明、状态、源码manifest、验收与本地接力 |

新增能力不是更多合入门禁：为未来真实公共知识提供从既有JSON到逐条审核表、候选包、验包的可执行链路。
不把模型原始输出直接当Bundle；不调用模型补齐字段，也不自动生成审批。

## C. 实际已执行的验证

环境：Linux、Python3.13.5、Git2.47.3。

| 验证 | 结果 | 边界 |
|---|---|---|
| Python合同测试 | 22/22 PASS | 合成数据+远端原P22 fixture（blob 7e3a78a3029928558938836f3152ad40956753c8） |
| Python回执/ZIP/CLI/文件测试 | 34/34 PASS | 临时目录，模拟回执，非真实人工审核 |
| 合计 | 56方法、0失败/错误/跳过 | 不代表Android测试或内容质量 |
| Python compileall/AST | PASS | 4个新增Python源码/测试文件 |
| 既有隐私审计+staged diff check | PASS_CHANGED_SOURCE_SNAPSHOT_ONLY | 原scanner blob bf228f561ac5a19dc5c9fda316fa0230d95a64d8；不是全仓库/Owner审计 |
| 远端6项源码blob回读 | PASS | 与本轮测试的字节逐项Git OID一致 |
| 全量clone/Gradle/Android4项 | NOT_RUN | 容器git联网DNS失败；无完整Android工程/SDK资格环境 |
| Windows/NTFS/junction平台行为 | NOT_RUN | 需本地复验，不用Linux结果外推 |
| 真实公共素材/人审/生产候选 | NOT_RUN | 没有生产数据，也没有审批证据 |
| 内容签名及App发布 | NOT_RUN | 未签名准备包不认证发布者；App签名仍Owner-reported BLOCKED |
| 新阶段独立审查 | NOT_RUN | 代码作者自测不是独立审查 |

源码manifest绑定ae29bc3；记录精确Git blob及LF字节SHA-256，不使用Windows工作树CRLF哈希。
Android合成向量PKB1：`889ccdde3f9e9065795f8440c97e8c1364c87864c7f8b519e903ae6573213ca1`。
4个Android方法已编写且位于androidTest，未称其编译或运行通过。

## D. 必须保留的边界

- 审核回执没有数字签名，仅证明结构/声明完整及对输入的绑定，不能证明审核人或版权。
- 所有候选强制`UNSIGNED_NOT_FOR_DISTRIBUTION`；认证、App导入、发布授权标记全false。
- 没有生产公共条目，没有公共图像交付，没有图像授权结论；20条只在合成测试中构造。
- 没有更改现有App消费者、Room、Provider、v1合同、签名配置及生产资源。
- JSON外部CRLF改动不一定影响PKB1，但会使旧文件审批摘要失效；不自动重写回执。
- 文件安全假设Owner受控目录、无并发目录替换攻击；原子硬链接发布并非断电持久性保证。
- ZIP为审核候选格式，不是App的新进口。真实导入还需要批准和已有照片的显式映射。
- OS process death的READY/provider/source/summary验收仍不得由旧IMPORTED测试外推。

## E. 本地接手与下一内容阶段

先按`docs/handoff/P23C_LOCAL_CODEX_HANDOFF_20260916.md`复验56项工具和4项Android，
补Windows平台、真实Parser/Room合成接入和全仓库隐私，修复实际问题后推独立验证分支。
不再回到Owner main合入决策：main=f2已完成。P23C本身仍待本地与独立审查，不自动并入main。

后续必须单独批准公共语料/生产模型资格，产生真实20条并由人工审核，再完成内容签名信任、
批准交付和App实际导入。本阶段只是提供执行工具，不启动兄弟Pipeline，不把旧Pipeline SHA当当前事实。
