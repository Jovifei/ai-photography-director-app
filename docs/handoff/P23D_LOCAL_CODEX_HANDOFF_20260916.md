# P23D 本地 Codex 接力｜READY / Provider / Summary OS 进程恢复

唯一任务：`P23D_LOCAL_ANDROID_QUALIFICATION`。

## 固定入口

- 仓库：`Jovifei/ai-photography-director-app`
- 当前 main：`9db98bad24347d286ded251aef76108ebe8872ba`
- P23D 分支：`codex/p23d-durable-ready-recovery-20260916`
- P23D source commit：`2e02bbb7be32cd00f96961d12e4b77c36cba9c3c`
- 最终远端 HEAD：先 fetch 后以实际分支为准；后续 docs-only 提交不是新的测试代码边界。

P23C 已经完成代码合入。不要回退 main，不再重复 P23C 的 20 条 Bundle READY force-stop 任务。

## 保护与工作树

先只读核对 `git remote -v`、`git status --porcelain=v2`、`git worktree list --porcelain`，然后 fetch。若远端有新提交先读差异，不回退。

保护 Owner 主工作树，使用新的 linked worktree，例如：

`codex/p23d-local-validation-20260916`

先确认分支/目录不存在。禁止 `git clean`、整包 stash、`reset --hard`、force push 或覆盖 Owner 文件。

完整读取：

- `AGENTS.md`
- `docs/P23D_DURABLE_READY_RECOVERY.md`
- `reports/P23C_REVIEW_AND_P23D_IMPLEMENTATION_20260916.md`
- `docs/handoff/P23D_SOURCE_MANIFEST_20260916.json`
- `docs/handoff/P23D_STAGE_STATE_20260916.json`
- `reports/P23C_LOCAL_QUALIFICATION_20260916.md`

## 先检查源码绑定

从 `2e02bbb...` 的 Git object 核对两项 blob：

- `P23DReadyStateProcessDeathAndroidTest.kt` → `16568556c64267f32f206f34e595188c872db7d8`
- `qualify_p23d_ready_recovery.ps1` → `c9d935deebc855eea3532043628fe4c237f79d0f`

生成本地完整 manifest 时增加 source-commit 原始字节数和 SHA-256；不要把 Windows CRLF checkout hash 当 canonical Git bytes。

## Android / emulator 执行

构建 Debug 与 AndroidTest APK，执行全量 JVM、`lintDebug`、`lintRelease`。标准 release signing 若仍缺 `PHOTOAI_RELEASE_STORE_FILE`，明确 BLOCKED，不生成新密钥。

只允许经过确认的专用 API35 emulator。先验证：

- serial 匹配 `^emulator-[0-9]+$`
- `adb -s <serial> shell getprop ro.kernel.qemu` = `1`
- `adb -s <serial> shell getprop ro.build.version.sdk` = `35`

所有 adb 必须显式 `-s`，不得触碰实体设备。

推荐在仓库外的新证据目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\qualify_p23d_ready_recovery.ps1 `
  -Serial emulator-XXXX `
  -EvidenceDirectory E:\project\_benchmark_evidence\p23d-ready-recovery-<UTC>
```

脚本应严格完成：cleanup → prepare → 启动 MainActivity → 确认 PID 存在 → `am force-stop` → 确认 PID 消失 → 新 instrumentation verify → cleanup。

只有 summary JSON 为 `result=PASS`，且 `force_stop=PASS_OS_PROCESS_TERMINATED`、prepare/verify/cleanup 均 PASS 时，才能关闭原 `READY/provider/source/summary OS process death` 缺口。

重点核对 verify 方法实际检查：

- `READY` 状态；
- `sourceLabel=本机 VLM`；
- scene/directorPrompt；
- providerId/type/model/revision/runtime/model artifact SHA；
- knowledge provenance 为 null；
- summary READY/failed count、inputDigest、model、recommendedPrimaryReference；
- summary Flow；
- project primary reference；
- `lastActiveReferenceId`。

## 回归

P23D 只增加 test/harness，但仍需执行新鲜回归：

- P23D 3 个 instrumentation 方法；
- P23C Parser / Room 测试；
- R1 findings/regression；
- 既有 Android 回归；
- 全量 JVM；
- Bundle contract、Phase 1.5、service、Python compileall；
- 全仓库 `prepush_privacy_audit.py` 与 `git diff --check`。

报告实际方法数量，不沿用旧 28/38/130 作为当前结果。

如果脚本或 test 在 Windows/Android 上发现真实问题，可在本任务范围内修复并补测试，分阶段非 force push 新验证分支，无需再次请求相同范围授权。不要通过放宽 emulator guard、跳过 PID 消失、删除断言或把 Activity recreation 改名为 process death 来获得 PASS。

## 完成与停止

完成后提交 P23D 专属报告、handoff 和更新后的 source manifest；不要修改 Owner tasks。

成功状态：`P23D_READY_RECOVERY_VALIDATED_AWAITING_INDEPENDENT_REVIEW`。

失败/不完整：`P23D_BLOCKED_WITH_EXACT_GATES`。

输出实际远端分支、逐个 SHA、P23D summary、回归结果、release signing BLOCKED/其它 NOT_RUN，以及下一 Reviewer 的精确候选。

本阶段不自动合并 main。继续禁止 Qwen、真实照片、真实 LAN、防火墙、Pipeline、真实公共内容、人审结果伪造、知识包生产签名密钥、实体设备、Cloud、iOS 和公开发布。
