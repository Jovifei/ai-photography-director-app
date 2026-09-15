# P23A：离线知识包导入可靠性增量

日期：2026-09-13

状态：`P23A_IMPLEMENTED_HOST_TESTED_ANDROID_REVIEW_PENDING`

这不是 P23 全阶段收口、P22 独立审查 PASS、可分发 APK 或生产知识包交付。

## 分支与代码边界

- 仓库：`Jovifei/ai-photography-director-app`
- 新分支：`codex/p23a-bundle-import-reliability-20260913`
- P22 基线：`902072065efe5bc2da143a26ad05119f46e6ed8c`
- 第一提交：`38fc1601f58a48fc78d88951779b66a732bda2da`
- 第二提交／实现源码：`43b1dcd23ca0b2ad621c9dfff960ea3e18ac7d3b`
- 本报告和接力文件是后续 docs-only 提交。最终分支 HEAD 以远端实际值为准。
- 本轮没有修改 `main`、既有 P22 分支或 Pipeline 仓库，没有强制推送或重写历史。

## 本轮实现

### 1. 拒绝宽松 JSON 造成的合同歧义

新增 `StrictKnowledgeBundleJson`，在 `PhotoKnowledgeBundleParser` 调用 Android
`JSONObject` 前校验完整 JSON 对象。拒绝注释、单引号、未引用字段名、等号/分号分隔、
尾逗号、尾随文档或垃圾、非法数值、解码后重复键、孤立代理字符，以及超过 32 层的容器嵌套。

原来只检测重复键的局部扫描已移除。现有 v1 字段白名单、512 KiB 上限、1–20 条限制、
UTF-8 无 BOM、PKB1 字段顺序和摘要算法保持不变；没有新增模型或传输字段。
这是针对 v1 数据的有界预检，不是通用 JSON 库，也没有自动修复损坏文档。

### 2. 导入请求隔离与可恢复映射

新增主线程操作 gate，使用递增 ticket 识别读取/提交所属会话。
重新选文件或 reset 会取消旧读取并让旧结果失效；取消系统 Picker 不再销毁已有预览。
ViewModel 接收可注入的 suspend 依赖，保留旧 repository/resolver 构造入口。

映射必须完整、非空且一对一。重复点击当前选中照片可解除绑定，不能静默抢占其他条目的
已确认目标。提交期间不允许 reset、读取、重新绑定或第二次提交。
意外提交异常显示结果不确定，引导返回项目核查；不伪造回滚成功，也不立即重试。

### 3. 有界读取、取消检查与界面说明

读取最多获取限制加一个探测字节，超限不继续复制；对异常的零长度读取作单字节推进。
文档流始终由调用者 `use` 关闭，读取间检查取消，取消异常继续传播。
任意第三方 provider 阻塞的单次系统 I/O 不能保证立即中断；ticket 负责阻止迟到结果回写。

界面增加取消读取、绑定进度、导演提示预览、来源/生成方/版本/摘要；
明确 SHA-256 一致不是发布者身份认证或内容质量认证。已删除或已进入不可覆盖状态的
目标会阻止确认；数据库原有事务检查继续作为最终防线。
写入期间界面返回和放弃入口受保护，并注册子级 BackHandler。

## 实际执行的验证

| 检查 | 本轮结果 | 范围 |
|---|---|---|
| `python scripts/test_p23_bundle_core.py` | PASS，125 个断言 | 实际生产 Kotlin helpers；84 语法 + 15 会话 + 16 映射 + 10 有界读取 |
| Kotlin 编译／JVM 执行 | PASS | 上述三个纯 Kotlin 源文件及共享断言文件；Kotlin 1.9.0、OpenJDK 21.0.11 |
| Python 入口语法 | PASS | 新增 host runner，AST 检查 |
| 仓库原版隐私扫描脚本 | PASS_CHANGED_SOURCE_SNAPSHOT_ONLY | 在当前环境物化的改动源码快照上执行，不是全仓库/Owner 机器审计 |
| `git diff --cached --check` | PASS_CHANGED_SOURCE_SNAPSHOT_ONLY | 所有本轮物化并暂存的改动文件 |
| 远端提交范围 | PASS | P22 之后两条线性代码提交，12 个代码/测试/脚本文件 |
| Gradle 全量 JVM、assemble、lint | NOT_RUN | 当前环境无 Android SDK、Gradle 工程依赖缓存 |
| Android 新增集成测试 | NOT_RUN | 6 个 parser 测试 + 5 个 ViewModel 测试已提交，等待专用模拟器 |
| Compose／Room／Picker／D2D | NOT_RUN | 不复用 P22 历史 PASS 冒充本轮证据 |
| 签名 APK/AAB、实体机、用户试点 | NOT_RUN | 未访问签名材料或设备 |
| 独立审查／main 合并 | NOT_RUN | 本轮实现自测不等于独立审查 |

共享核心断言由 JUnit 的 4 个测试方法复用；125 是断言数量，不是宣称整个 App 的
125 项 JUnit 测试已经通过。新增的 11 个 Android 测试没有在本轮运行。

核心源码及全部 12 个改动文件的 Git blob/SHA-256 位于
`docs/handoff/P23A_SOURCE_MANIFEST_20260913.json`。Windows 的 CRLF checkout 可能改变
工作区字节哈希；跨平台绑定以对应提交的 canonical Git blob 为准，不能跳过本地复验。

## 必须由本地接力完成

1. 完整 Android 构建、现有回归与新增 11 个 Android 测试；发现问题继续线性修复。
2. 系统 Back／返回按钮在提交开始同一帧、连续双击、旋转、进程销毁时的真实行为。
   特别检查父级导航与子级 BackHandler 的调用顺序；需要时在 App 导航层增加同步 busy guard，
   不得仅以按钮变灰当成验证证据。
3. Room 事务失败、目标状态并发变化、恢复后的来源和 P21 READY-only 边界。
4. 100%/200% 字体、长来源标识/64 字符摘要的换行与无障碍；导入 UI 未在此环境渲染。
5. 原始主工作树 Owner 文件哈希快照，以及 `tasks/todo.md` / `tasks/lessons.md` 冲突检查。
   本轮没有删除这些路径，不能宣称旧候选的路径捕获风险已解决。
6. 新候选精确 SHA 的独立审查，然后另行处理 Beta/P22 栈的安全落地。

## 保持关闭的范围

没有真实照片、模型推理/替换、私有 LAN/防火墙改动、Pipeline 执行、生产 Bundle、
公开知识库、实时 Pose、Cloud、iOS、签名材料或公开发布。
没有新增第三方依赖，没有复制第三方源码或修改 `REFERENCE_LOCK.json` 冒充锁定。

## 参考来源

本轮参考官方实现的行为与工程原则，新增 Kotlin 实现和测试自行编写：

- Android/AOSP JSONTokener：其文档明确说明 lenient 行为，用于确定拒绝用例。
  https://android.googlesource.com/platform/libcore/+/master/json/src/main/java/org/json/JSONTokener.java
- RFC 8259：JSON 语法、Unicode 互操作性及解析器资源限制依据。
  https://www.rfc-editor.org/rfc/rfc8259
- Android coroutine best practices：生命周期、依赖注入、CancellationException 传播。
  https://developer.android.com/kotlin/coroutines/coroutines-best-practices

查阅日期为 2026-09-13；动态页面不是本项目已锁定的源码依赖。
