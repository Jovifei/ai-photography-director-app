# 本地 Codex：P23B 复验与安全合入准备

唯一任务：`P23B_LOCAL_PREFLIGHT_AND_LANDING_EVIDENCE`。
用户已经要求继续开发并逐段提交，本任务内的工具测试、只读预检、合成模拟器补验可直接
执行，不必再次请求同一授权。本任务不构成 main 合入、真实照片或公开发布授权。

## 固定入口

远端：`https://github.com/Jovifei/ai-photography-director-app.git`

工具分支：`codex/p23b-safe-landing-preflight-20260915`
工具实现 SHA：`8aa403bac97ef948e291613ccb4c91ea8e7cb3b1`。
之后还有本交接等 docs-only 提交，以 fetch 得到的最新分支 HEAD 为起点，不回退。

已通过 Owner 提供的独立审查的 App 候选：
`e4a954b313320944fe751a2388366ee72d3e9083`。
该候选分支：`codex/p23a-r1-review-remediation-20260913`。
预期 main：`61a9b26f7a17ae84a2b4ec8d7bb02e18695801b6`。

不要混淆“工具分支 HEAD”和“被预检的 App 候选”。P23B 没有修改任何旧 Android、Room、
Provider、签名配置、tasks 文件或 R1 manifest。旧报告中的审查前状态是历史快照；Owner
随后提供的审查 PASS 只绑定 e4a954b，不表示工具的新 SHA 也已独立审查。

## 0. 读取与保护

先读 AGENTS、PROJECT_BINDING（绑定信息，不用其旧阶段文字重置 G0/AH0），以及：
- docs/P23B_SAFE_LANDING_PREFLIGHT.md
- reports/P23B_REMOTE_IMPLEMENTATION_20260915.md
- docs/handoff/P23B_STAGE_STATE_20260915.json
- docs/handoff/P23B_SOURCE_MANIFEST_20260915.json
- reports/P23A_R1_ANDROID_REQUALIFICATION_20260913.md
- docs/handoff/P23A_R1_SOURCE_MANIFEST_20260913.json

只读核对 origin、status、worktree 清单，保存 Owner 文件原始状态。
fetch 后核对三个 ref；有新提交就先审阅差异，禁止回退或重置。
从新的工具分支建立新的 linked worktree，例如 `codex/p23b-local-validation-20260915`；
先确认目录/分支未存在，绝不删除旧工作树或覆盖 Owner 文件。

禁止 git clean、整包 stash、reset --hard、force push、修改全局 Git 配置、生成密钥。
不要为了让预检通过而删除 Owner 的 tasks/todo.md 或 tasks/lessons.md。

## 1. 先验证工具，不接触相机模型

在独立工作树根目录运行：

```powershell
python -m unittest discover -s scripts -p "test_p23b_*.py" -v
python -m compileall -q scripts/p23b_git.py scripts/p23b_manifest.py scripts/p23b_landing_preflight.py
```

当前 Linux 实测 21+29=50 项 unittest 方法通过；这是工具测试，不是 Android 的 130 项。
Windows 上实际记录 pass/fail/skip、Python/Git 版本。检查 Windows junction/reparse point、
大小写路径、输出目录保护、UTF-8 路径和 CRLF 的行为；发现问题在独立分支补测试并修复，
不得靠降低 gate 或删除负例过关。

## 2. 验证实际提交，不再使用工作树 hash 冒充 Git blob

```powershell
python scripts/p23b_manifest.py --repo . verify --candidate e4a954b313320944fe751a2388366ee72d3e9083 --manifest docs/handoff/P23A_R1_SOURCE_MANIFEST_20260913.json --expected-count 27
```

再用实际工具分支 HEAD 验证 `docs/handoff/P23B_SOURCE_MANIFEST_20260915.json`，数量 5。
不要把源码 SHA `8aa403b` 当成清单所在 HEAD：清单在其后 docs-only 提交。
对比 `e4a954b..工具HEAD` 应仅有 P23B 工具/测试/文档；若 Android、旧 manifest、tasks、
shared-contract 或 signing 改动，先调查而不是套用“生产不变”。

## 3. Owner 只读安全预检

仓库外新建本轮 evidence 目录，只存脱敏汇总和外部 Owner 快照。工具报告本身包含私人的
相对路径与摘要，绝不提交 Git/PR。使用 `--output`，不要把整份快照转发到聊天或公开日志。

```powershell
python scripts/p23b_landing_preflight.py --repo . --owner-worktree E:\project\ai-photography-director-app --output <本轮外部证据目录>\owner-preflight-before.json
```

脚本不 fetch；其 PASS 只基于已经 fetch 的本地 refs。必须检查退出码。
若出现 partial clone、custom filter、Owner 不在预期 main、隐藏 index 标志或 paths overlap，
记录代码及受影响范围；不得修改 Owner 配置或文件来“解决”。确需候选侧路径调整时，在新
分支拟定最小差异，保持 Owner 文件不变，并为新精确 SHA 安排审查。

结束前用 `--previous owner-preflight-before.json` 和新的 output 文件再次检查。
另行核对之前的 18 项 Owner SHA 快照；工具的新 JSON 不等于已经核验了那 18 项历史条目。

## 4. 完成仍缺的有限本地证据

只使用明确确认的专用 API35 emulator（验证 emulator- 前缀、SDK=35、ro.kernel.qemu=1）；
任何设备命令都绑定 serial，不运行可能遍历实体设备的无筛选 connected tests。

- 对 e4a954b 产品代码重跑 v6 的官方 LocalTransport/D2D 流程，记录代码/设备/证据绑定；
  最新 Reviewer 的备份传输为 NOT_RUN，早期 PASS 不代替本轮。
- 专门完成 OS 级 process death + 重启恢复，不能只用 Activity recreation；需要时写
  test-only 外部控制 harness。覆盖导入提交中和已有 READY/来源/summary 的重启行为。
- 系统 font_scale 100%/200% 的知识包导入页面截图与操作可达性，恢复模拟器原设置；
  Compose 注入 density 测试不能替代系统字体验收。截图仅合成内容，留仓库外。
- 若 Android/test harness 有变更，重跑 R1 28/28、既有 38/38、JVM、Debug/Test APK、
  Debug/Release lint、合同和 service；记录真实数量。工具-only 变更也要检查全部已有
  源码 blob 不变，不能借此宣布新 Android 测试已通过。
- 外部签名输入仍缺失时将 signing 保留 BLOCKED。允许观察 guard 报错；不得生成新生产
  密钥。既有 -x verifyReleaseSigning 仅为无签名静态检查，不得标成签名通过。

签名材料、Qwen、真实照片、真实 LAN、TLS/防火墙、Pipeline、公共生产 Bundle、实体设备、
模型下载/替换、Cloud、iOS 和公开发布均不在本任务范围。

## 5. 提交与阶段结束

push 前完整运行仓库 `python scripts/prepush_privacy_audit.py`、`git diff --check`，检查暂存
清单。不要修改通用 tasks/todo.md 记录本阶段，使用 reports/P23B_* 和 docs/handoff/P23B_*。
可以分步非 force 推送独立验证分支，给出每步提交 SHA 与目的。

如修复工具源码，先提交源码，再从实际 source commit 生成新的工具 manifest/报告；
不覆盖历史 R1 清单，不重新计算 Windows checkout hash 作为批准摘要。

产出：工具测试与五项清单、27项 R1清单、Owner前后差异计数、真实剩余 gate、精确候选
SHA、证据位置说明（统一本轮逻辑 evidence ID，不复制原始路径到公开仓库）。

全部范围内检查结束：`P23B_LOCAL_CHECKS_COMPLETE_AWAITING_OWNER_LANDING_DECISION`。
存在无法补齐的条件：`P23B_BLOCKED_WITH_EXACT_GATES`，保留已通过项和阻塞原因。
不能把“工具返回 0”当作自动 merge 权限。本阶段不执行 main 合入。
随后由 Owner 对精确 SHA 和冲突情况做合入决策；安全落地后才进入单独的公共知识包任务。
