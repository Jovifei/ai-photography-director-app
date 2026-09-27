# Current program status

**As of:** 2026-09-27

**Machine-readable record:** [`current_program_status.v1.json`](current_program_status.v1.json)

**Next-stage plan:** [`tasks/plans/2026-09-27-t5-program-status-consolidation.md`](../tasks/plans/2026-09-27-t5-program-status-consolidation.md)
**Gate matrix:** [`NEXT_GATE_MATRIX.md`](NEXT_GATE_MATRIX.md)

## Current authority

The current Android delivery is the T4 offline product-flow stack. Its delivery head is `e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb`, with reviewed product source `a69ede68f3e54e5ab006dbfd7a65c33040590f93`, based on T3 `14a56f49e1220eb8139bf7280c747124118fdb21`. Draft PR #8 is stacked on the T3 branch and remains open, Draft and unmerged.

T4 qualification is synthetic/API 35 emulator evidence. The final receipt records T4 7/7, T3 7/7, root 2/2, P25U camera 1/1, export 3/3, force-stop recovery prepare/verify/cleanup 1/1 each, JVM 141/141, lint 0 errors, contract and privacy checks PASS. Local exact-SHA independent review is PASS. The bound C2C connector still reads the dirty Owner `main`, so its code-diff review is explicitly `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE`; the remote evidence review must not be read as a connector-performed code review.

This delivery proves an offline Android product flow: project references, all-project reference browsing, source/state labels, persistent project naming, truthful guided/direct Camera entry, Camera controls, private captures and explicit export. It does not prove real-photo quality, a physical device, Qwen/Private LAN, Pipeline, human editorial acceptance, signing, beta or public release.

## Product goal

`参考图 → 来源明确的逐图状态 → READY-only 可信指导 → Camera Director 或无指导拍摄 → 私有成片 → 系统导出`

The future live director remains a separate route requiring a qualified provider, device evidence and additional authorization. Pose is frozen until its own decision and qualification gate.

## Historical material

The following remain useful evidence or design constraints but are not current execution authority: G0/AH0/AA0 bootstrap plans, the six-week roadmap, P20/P21/P22 operational banners, P1A/P1B implementation plans, older P25/T3 handoffs and Obsidian P22/P24 notes. Do not rewrite their historical claims as though they were current; use this page and the gate matrix for current status.

## Next stage

T5 has consolidated this status into one machine-checkable authority, labelled forward-facing documents, recorded the next-gate matrix and added drift validation. Its qualification changed no Android runtime code, shared contracts, models, network, Pipeline code, signing settings or Owner decisions. The next stage is selected only after Owner review of the matrix.
