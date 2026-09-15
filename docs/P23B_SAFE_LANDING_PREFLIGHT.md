# P23B：安全合入预检工具

状态：`P23B_TOOLING_IMPLEMENTED_LOCAL_VALIDATION_PENDING`。本阶段沿着
`P23_SAFE_STACK_LANDING_AND_PUBLIC_KNOWLEDGE_PACK_ENTRY` 继续，先完成安全合入准备，
不重做已通过审查的 Android 实现，也不提前运行 Pipeline 或生产公共知识包。

## 两个 SHA，不要混用

- 待 Owner 决策的 Android 候选：`e4a954b313320944fe751a2388366ee72d3e9083`。
- P23B 工具分支：`codex/p23b-safe-landing-preflight-20260915`，是上面候选的后继。

Owner 提供了前者的独立审查 PASS；本工具不鉴定 Reviewer 身份、不代签审查记录，
工具分支的新提交也不会自动继承整个候选的审查授权。运行前自行核对 origin 和最新 refs。
当前预期 main：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`。

## 1. 规范化源码清单

`scripts/p23b_manifest.py` 只读指定提交的 tree / blob，不读待校验源码的工作树副本，
不使用 `git hash-object --path`、clean/smudge 或 textconv。它同时验证源提交祖先关系、
文件数量、文件模式、候选源码与源提交的相同 blob，以及内容摘要。

```powershell
python scripts/p23b_manifest.py --repo . verify --candidate e4a954b313320944fe751a2388366ee72d3e9083 --manifest docs/handoff/P23A_R1_SOURCE_MANIFEST_20260913.json --expected-count 27
```

旧 R1 清单可直接读取，不需要修改旧文件。新清单的域：

| 字段 | 来源 |
|---|---|
| git_blob | 原始提交 blob 的 Git SHA-1 对象 ID |
| git_blob_bytes / git_blob_sha256 | 原始提交 blob 的长度和 SHA-256 |
| bytes / sha256 | 仅 CRLF→LF 后的 UTF-8 长度与 SHA-256 |
| Owner worktree_raw_sha256 | 另一个外部快照中的实际工作树字节；不能作为源码审批摘要 |

新清单的生成先指定已经存在的 source commit，再通过重复 `--path` 指定非空、唯一的
源码路径；结果默认输出 JSON。`--output` 必须在所有已登记 worktree 和 Git 元数据目录外，
父目录须已经存在，目标文件不能已经存在。清单提交放在源码提交之后，避免自引用。

```powershell
# 将 <SOURCE_SHA> 替换为真实、完整的已提交 SHA；不能写 HEAD 或工作树 hash。
python scripts/p23b_manifest.py --repo . --output <仓库外新文件.json> build --source <SOURCE_SHA> --path scripts/p23b_git.py --path scripts/p23b_manifest.py
```

## 2. Owner 文件安全检查

在单独的 linked worktree 运行，而不是 Owner 主工作树。先由本地执行者 fetch；脚本自身
不联网，拒绝 partial/promisor clone 以避免隐式获取对象。默认只接受约定的 HTTPS/SSH origin。

```powershell
python scripts/p23b_landing_preflight.py --repo . --owner-worktree E:\project\ai-photography-director-app --output <仓库外新文件.json>
```

检查内容：main 与候选本地 remote-tracking refs 是否仍匹配、快进祖先关系、27 项 R1 清单，
Owner 是否仍在预期 main、进行中的 Git 操作、隐藏的 index 标志、候选新增/修改/删除路径
与 Owner 修改/未跟踪/ignored 路径的冲突。路径比较覆盖大小写、NFC、目录前缀及常见
Windows 保留名；`tasks/todo.md` / `tasks/lessons.md` 与其他路径同等处理。

对已修改/未跟踪的有限普通文本，只记录相对路径、状态、字节数、权限和原始字节 SHA-256，
不输出正文。图片、数据库、私有目录、过大文本、符号链接和 Windows reparse point 不读取
内容；需要保护而无法安全快照的普通条目会 BLOCKED。ignored 目录仅记录边界，不遍历、不
做内容哈希；若与候选写入路径相交仍 BLOCKED。最多 5,000 个条目，累计文本上限 20 MB，
单文件上限 2 MB；超限不自动放宽。

Configured Git clean/process filters 会阻断检查，避免 `git status` 间接运行外部程序。
不要为了 PASS 而删除过滤器配置、清理 Owner 文件或取消其 index 标记；先报告真实原因。

检查前后各取一次快照，比较 Owner 状态/文件摘要/index 及 refs。也可传入同候选、同 main
的前次报告做跨运行比较：

```powershell
python scripts/p23b_landing_preflight.py --repo . --owner-worktree E:\project\ai-photography-director-app --previous <上次外部报告.json> --output <本次新的外部报告.json>
```

外部报告含私人相对路径/摘要，不得提交 Git、上传 PR 或放进公开制品。它不是备份文件，
不能恢复原始内容。没有与上次 18 项 Owner 快照逐项核对时，不能声称那 18 项再次全部匹配。

## 3. 返回值与能力边界

- `0`：只完成本工具的本地预检，状态为 `LOCAL_PREFLIGHT_PASS_NOT_MERGE_AUTHORIZATION`。
- `2`：`BLOCKED`，有明确代码；不会尝试修复本地仓库。
- 每份报告固定 `merge_authorized=false`、`release_authorized=false`。

脚本没有 fetch、checkout、add、commit、stash、merge、push 或设备控制命令。测试里的
Git 写命令仅用于独立临时合成仓库，已隔离用户 HOME/模板/钩子和提交签名设置。
本预检不是跨进程文件锁；执行者仍需避免同时编辑，正式动作前重新 fetch 和预检。
只观察本地 remote-tracking refs 不能证明远端刚刚没有变化。

## 4. 本地自测与剩余产品验证

需要已有 Python 3.10+、Git。没有新的 Python 包依赖，不需要 Android SDK 即可跑工具测试。

```powershell
python -m unittest discover -s scripts -p "test_p23b_*.py" -v
```

Linux 当前通过 50 个 unittest 方法。Windows symlink/junction、真实 Owner 文件状态、
真实完整仓库的 27 项验证仍需要本地执行；跳过的测试需报告 skip，不伪装全部通过。

本工具不会把 JVM、签名、备份传输、OS process death 或字体截图标记为 PASS。
最新独立审查通知中备份传输、OS process death、系统字体截图为 NOT_RUN；较早 handoff
的备份 PASS 是历史执行者记录，不能冒充该次独立审查的 fresh 结果。

## 5. 后续顺序

本地工具复验和 Owner 冲突清单 → 补齐 v6 恢复/系统字体/进程销毁证据，保留签名阻塞 →
Owner 对精确 SHA 决策及必要的候选审查 → 安全合入 → 单独授权公共知识包生产/导入。

仅在确有 Owner 路径冲突时设计候选侧路径修复；不删除 Owner 文件，不因为有风险就先删
远端 tasks 文件。新候选的审查覆盖与授权应明确记录，不重新改写旧 PASS。

## 官方参考（2026-09-15）

- Git `cat-file`：无 filters/textconv 时的对象字节读取。
  https://git-scm.com/docs/git-cat-file
- Git `status`：porcelain v1 NUL 分隔、ignored=matching、可选 index 锁。
  https://git-scm.com/docs/git-status

未复制第三方项目代码，未新增运行库或改变 Android 依赖。
