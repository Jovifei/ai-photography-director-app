# Project Agent Rules

<!-- BEGIN:codex-token-efficiency -->
## Token-efficient navigation

- Obey the mandatory reading order below, but do not preload any additional history or repository-wide content.
- When `.codegraph/` exists, start code exploration with lightweight CodeGraph tools: file map, symbol search, callers/callees, impact, and single-node lookup. Use broad context/explore only if these are insufficient.
- Use `rg` for exact identifiers, errors, config keys, and headings; read only matching ranges. Read a whole file only when its full behavior is necessary.
- Search documentation names/headings first and open only task-relevant sections. Do not read generated output, dependencies, private media, model weights, databases, or logs by default.
- Run focused checks first. Keep verbose output on disk and return only exit status, failing cases, and the relevant error region.
<!-- END:codex-token-efficiency -->

- 先读 `PROJECT_BINDING.json` 和 `docs/00_ANDROID_FIRST_EXECUTION_PLAN.md`；
- 验证 `origin`，不得覆盖不同 remote；
- 只完成当前 Gate，完成后停止；
- 首发 Android，Android Gate 前不得创建 iOS 功能；
- 不提交私人图片、模型权重、数据库、密钥、keystore、运行输出或参考仓库 clone；
- 兄弟项目只通过 Bundle Contract 通信；
- 外部仓库无兼容许可时只研究；
- 每次 push 前运行 `python scripts/prepush_privacy_audit.py`。

## Project milestone progress

For each feature, test-result, review, producer-handoff or release-state change, update `docs/project_progress.json` in the same commit and run `python scripts/render_project_progress.py`. Before push, run `python scripts/render_project_progress.py --check`. Keep `PENDING_REMOTE_PLANNING` until the bound ChatGPT Project actually returns and approves the master roadmap. Checkpoint progress is evidence completeness, not a product-quality percentage.
