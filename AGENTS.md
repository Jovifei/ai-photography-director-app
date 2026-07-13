# Project Agent Rules

- 先读 `PROJECT_BINDING.json` 和 `docs/00_ANDROID_FIRST_EXECUTION_PLAN.md`；
- 验证 `origin`，不得覆盖不同 remote；
- 只完成当前 Gate，完成后停止；
- 首发 Android，Android Gate 前不得创建 iOS 功能；
- 不提交私人图片、模型权重、数据库、密钥、keystore、运行输出或参考仓库 clone；
- 兄弟项目只通过 Bundle Contract 通信；
- 外部仓库无兼容许可时只研究；
- 每次 push 前运行 `python scripts/prepush_privacy_audit.py`。
