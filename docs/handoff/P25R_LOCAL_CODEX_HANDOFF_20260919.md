# P25R 本地 Codex 接力

状态：P25R_IMPLEMENTED_LOCAL_VALIDATION_AND_HUMAN_REVIEW_PENDING。

## 固定入口

- 仓库：Jovifei/ai-photography-director-app
- 当前 main：1b776ba9932a7fdc96112c8cb85c7258f3f7d6af
- 原 P25 draft：e06fd01d1282689601b17d1dfecb7cab1cc88773
- P25R 分支：codex/p25-human-review-gate-20260919
- P25R source boundary：f45618add0a265a5e576eb4c89368a7e0467e371

先 fetch，以实际远端 HEAD 为准。不要回退后续提交，不要在 Owner 主工作树直接开发。

## 本轮云端审查发现

P25 原提交没有伪造人审或 PIPELINE 来源，方向正确。但：
1. editorial validator 少了 P23C 已经采用的 URL/URI/path 和 Unicode fail-closed 边界；
2. source_evidence_id 只是脱敏句柄，不是权利/来源证明；
3. 原创 editorial 不能直接改成 source.origin=PIPELINE。当前 Bundle v1 只有 PIPELINE / LOCAL_SERVICE 两种 producer origin，因此必须先经过真人审核，再交给真实 Pipeline release 产出 Bundle。

P25R 已实现：
- 更严格 editorial validator；
- review-template；
- validate-review；
- export-pipeline-input；
- PENDING/身份/evidence/hash/20条覆盖 fail-closed；
- 中间 handoff 明确 NOT_APP_IMPORTABLE；
- 复用 P23C 的安全本地读取/不覆盖输出机制；PackError 必须稳定映射为 BLOCKED/exit 2，不能泄漏 traceback。

## 本地验证任务

建立新的 isolated linked worktree，例如：
codex/p25r-local-validation-20260919

保护 Owner 主树和历史 18 项文件。禁止 git clean、整包 stash、reset --hard、force push。

完整阅读：
- docs/P25_REAL_20_CONTENT_REVIEW.md
- reports/P25_EDITORIAL_20_DRAFT_20260919.md
- reports/P25R_HUMAN_REVIEW_GATE_20260919.md
- docs/handoff/P25R_STAGE_STATE_20260919.json
- docs/handoff/P25R_SOURCE_MANIFEST_20260919.json
- docs/reference/PHOTO_KNOWLEDGE_BUNDLE_CONSUMER_V1.md
- scripts/p25_validate_editorial_corpus.py
- scripts/p25_human_review_gate.py
- scripts/p23c_prepare_review_pack.py

先从 source commit 的 Git object 验证 6 项 blob，补齐 canonical byte count 和 SHA-256；不要用 CRLF 工作树 hash 冒充 source bytes。

运行：
python scripts/p25_validate_editorial_corpus.py docs/reference/p25_real_20_editorial_draft.v1.json
python -m unittest discover -s scripts -p "test_p25_*.py" -v
python -m unittest discover -s scripts -p "test_p23c*.py" -v
python -m compileall -q scripts
python scripts/prepush_privacy_audit.py
git diff --check

网页端的 21 个 Python unittest 是在同结构合成 fixture 上执行的，不是本地真实仓库资格结论。请报告本机真实数量。

## 审核模板负路径

只在仓库外新目录生成：

python scripts/p25_human_review_gate.py review-template docs/reference/p25_real_20_editorial_draft.v1.json <external>\p25-review.json

必须确认：
- reviewer_id/review_id/rights_basis_evidence_id 为 null；
- 20 条 evidence_id 为 null；
- 三项 review 全 PENDING；
- 将该模板直接送 validate-review 必须 exit 2；
- 不能通过批量文本替换把 PENDING 变 APPROVED 来宣称真人审核。

对缺 review identity、缺 evidence、重复 evidence、错误 source_evidence_id、错误 corpus SHA、任一 REJECTED/PENDING 都必须 fail closed。

## 真人审核边界

Codex 可以：
- 生成审核模板；
- 展示每条内容；
- 收集 Owner/真人实际给出的审核决定；
- 校验决定和 evidence 是否完整；
- 在发现内容问题后修正 draft，并要求旧 review 因 corpus hash 改变而失效。

Codex 不可以：
- 自己冒充真人 reviewer；
- 自动将 20 条批量 APPROVED；
- 编造 rights evidence；
- 把 PROJECT_ORIGINAL_EDITORIAL_DRAFT 声明本身当作版权证明；
- 把 source_evidence_id 当作已经验证的来源证据。

人工审核特别检查安全站位：007 城市行走、008 楼梯、015 低机位、016 高机位、019 夜间城市。若建议可能引导进入车流、楼梯边缘、危险高位或不稳定支撑，必须修改后重新审核。

## 人审完成后的中间导出

只有真人提供完整 review 后才运行：

python scripts/p25_human_review_gate.py validate-review <corpus> <review>

成功状态必须是：
P25_REAL_20_CONTENT_REVIEWED

然后才能：

python scripts/p25_human_review_gate.py export-pipeline-input <corpus> <review> <external>\p25-curated-input.json

导出必须：
- format=photoai.curated-editorial-input.v1
- status=HUMAN_REVIEW_APPROVED_NOT_APP_IMPORTABLE
- app_import_authorized=false
- public_distribution_authorized=false
- 没有 contract_version/source/integrity/references 的 Bundle 根结构

它只是下一阶段真实 Pipeline release 的输入，不能用 Android OpenDocument 导入。

## 提交和停止

如果本地发现真实代码问题，在独立验证分支最小修复并补测试，non-force push。
阶段报告和 manifest 可以提交；外部 review/evidence/导出文件不得提交 Git，除非 Owner 明确确认它们已脱敏且允许公开。

若只完成工具验证、尚无人审：
P25R_LOCAL_VALIDATED_AWAITING_HUMAN_REVIEW

若 20/20 真人审核真实完成并验证：
P25_REAL_20_CONTENT_REVIEWED_AWAITING_PIPELINE_RELEASE

若有问题：
P25R_BLOCKED_WITH_EXACT_GATES

本阶段不要自动合并 main，不要启动 Pipeline、Qwen、LAN、真实照片、实体设备、Cloud/iOS 或发布。

## 2026-09-20 Windows 本地验证收口

- 本地验证分支：`codex/p25r-local-validation-20260920`。
- 初始修复候选：`2ff8afe294a3e0a36df7d545ca903a53adcdecc5`；安全措辞修订后最终候选：`d69e272cd0cf56858e28c78fb9e5799c0c3b1e25`。
- P25R 21/21、P23C 55 PASS/1 SKIPPED、compileall、privacy、diff 均已在 Windows 实际运行。
- PENDING review template 已写入仓库外逻辑证据 ID `p25r-20260920`；直接 validate-review 以 exit 2 拒绝，无 traceback。
- symlink 只因当前 Windows 权限 SKIPPED；junction/reparse、hardlink、UNC、existing output、repo-inside、文件中途变化均已验证为稳定 BLOCKED。
- 当前状态：`P25R_LOCAL_VALIDATED_AWAITING_HUMAN_REVIEW`；真人完成数量仍为 `0/20`。

## 2026-09-20 Owner 审核材料增量

- 新增 `scripts/p25_generate_human_review_packet.py`，从严格验证后的当前 corpus 生成仓库外、PENDING-only 的完整九字段人审包；不填身份、evidence 或 APPROVED，且拒绝覆盖已有输出。
- 修正 Owner 指南中的 corpus ID 为真实值 `P25-PORTRAIT-EDITORIAL-20-20260919`。
- source commit 更新为 `342e25acb12da98b4ea2f58261ec561e2b193bb0`；manifest 扩展为 8 项 Git-object 绑定。
- P25R Windows 测试现为 24/24；最新外部人审包为 20 条 PENDING、0 APPROVED。真人 Gate 仍为 0/20，未启动 Pipeline 或 P26。
