# P23A 本地结果复核与 R1 修复说明

## 审核结论

维持 `REQUEST_CHANGES`。基线 `be040188c93d7613d9433a23e0d1f5d640e317f1`。

远端 compare 确認 be040188 相对 1d13b8e 为三个线性提交，实际仅变化两个路径：
P22SystemDocumentImportAndroidTest.kt 的一条断言，及 tasks/todo.md 的本地资格记录。
生产逻辑没有改变；本地 128/128 等验证记录存在，但外部 E: 证据路径没有在本会话实际读取。
本次不声称在模拟器独立复现了 findings，也不把旧测试命令 OK 当成竞态复现证明。

| Finding | 静态复核依据 | 修复实现 | 新鲜 Android 结论 |
|---|---|---|---|
| P1 分析覆盖 Bundle | 旧实体在事务外读取，Coordinator 忽略状态转换返回结果，结果写回缺乏归属 | 事务内读取 + 持久化 token + 取消归属 + 当前来源检查 | NOT_RUN |
| P1 异常伪称回滚 | 所有 Exception 被压成 DATABASE_COMMIT_FAILED，UI 重开提交 | 完整预校验，提交回执分类，显式 OutcomeUnknown，取消传播 | NOT_RUN |
| P2 同帧返回 | 父回调 reset 后无条件导航，子 handler 依赖新一帧 isApplying | 全部出口同步 tryLeave，常驻子 handler | NOT_RUN |
| P2 测试缺口 | 旧测试主要是 happy-path/fake write/首页语义检查 | 新增20个真实 Room/Coordinator/VM/UI/迁移/字节编码测试方法 | 编写完成，未编译/未执行 |

上一轮网页代码没有彻底解决数据库级并发和不确定提交，不应归咎于本地 Codex 只修了测试文案。
独立审查发挥了应有作用；主线合并仍禁止。

## 技术取舍

### 持久化归属，不只加一个界面锁

新增 analysisAttemptId 是内部数据库字段，不发送到 Provider 或 Bundle。
如果数据库已是 READY（尤其 Bundle 来源）、已有其他 token、已删除或旧 token 失效，写入被拒绝。
生成 token 在挂起排队调用之前，防止“排队已提交但取消丢掉返回值”造成无人拥有的任务。
finally 只清理该 token，不按整个 project 取消可能属于新任务的工作。
Room v6 的代价是必须重新验收迁移、备份恢复与旧调用者；该代价明确保留，未伪装成文案小改。

### 回滚与回执丢失是两回事

observeCommitOutcome 覆盖整个 withTransaction 调用（含最终提交），不是只包住事务体。
CancellationException 继续抛出，ViewModel 以结果不确定处理。其它异常保守归为 Unknown。
只有全部校验失败且未触发任何更新、并正常返回的分支才是确定拒绝。
针对 SQL ABORT 的测试可以验证真实回滚，但生产 UI 不依据“任何异常”推断回滚。

Kotlin 官方 withContext 文档明确：调用方取消时，返回调度可能丢弃已经计算完毕的结果。
资料：https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html
该资料用于解释回执边界，不等于该项目的 Room 提交失败已经在本环境重现。

### 返回操作不等重组

新增 tryLeave() 同步访问会话 gate。父 handler、屏幕返回回调均检查成功才导航。
子级 BackHandler 常驻并转发同一个受保护回调，按钮 disabled 只负责体验，不承担数据正确性。

## 实际运行

- Kotlin 1.9.0 / OpenJDK 21：归属策略148个断言、提交结果分类8个断言，共156 PASS。
- Python 临时合成 Git 仓库：应用脚本12个测试 PASS，覆盖干净工作树、错误基线/blob/锚点、Owner checkout、
  dirty worktree、新文件冲突（含 ignored）、符号链接、越界路径和中途写失败恢复。
- 仅验证修复包文本/语法/文件哈希；完整仓库 prepush 审计尚未执行。
- 没有完整 Android 源码 checkout，未执行真实基线的整包 dry-run；应用脚本在本地必须先通过。
- 没有 Gradle / Android SDK 验证结果，无签名、设备、模型、Pipeline、远端写入或独立审查 PASS。

因此不能把本包状态标成“修复已验证关闭”，更不能重用旧的 P23A Android PASS。

## 远端操作边界

本轮已执行 GitHub 读取、分支核对、源码复核与 compare。
本轮发现的 GitHub 工具集只有读取能力；按 create 搜索无结果，插件目录确认 GitHub 已连接。
不是宣称用户仓库没有写权限，也不是宣称 GitHub 完全不可访问；只是当前会话没有写操作入口。
本地 git clone 亦因网络 DNS 不可用失败。因此没有新 commit、branch 或 PR。

后续交付使用固定基线修复包，由本地 Codex 完成真实应用、构建、回归和非 force 推送。
