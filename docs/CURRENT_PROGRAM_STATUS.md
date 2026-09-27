# Current program status

**As of:** 2026-09-27

**Machine-readable record:** [`current_program_status.v1.json`](current_program_status.v1.json)

**Next-stage plan:** [`tasks/plans/2026-09-27-t9-product-ready-promotion-gate.md`](../tasks/plans/2026-09-27-t9-product-ready-promotion-gate.md)
**Gate matrix:** [`NEXT_GATE_MATRIX.md`](NEXT_GATE_MATRIX.md)

## Current authority

The current Android delivery is the T4 offline product-flow stack. Its delivery head is `e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb`, with reviewed product source `a69ede68f3e54e5ab006dbfd7a65c33040590f93`, based on T3 `14a56f49e1220eb8139bf7280c747124118fdb21`. Draft PR #8 is stacked on the T3 branch and remains open, Draft and unmerged. Owner `main` remains dirty and untouched; active source review uses the clean exact-head clone. `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE` refers only to arbitrary historical base-range patch rendering.

T4 qualification is synthetic/API 35 emulator evidence. The final receipt records T4 7/7, T3 7/7, root 2/2, P25U camera 1/1, export 3/3, force-stop recovery prepare/verify/cleanup 1/1 each, JVM 141/141, lint 0 errors, contract and privacy checks PASS. Local exact-SHA independent review is PASS. The bound C2C connector still reads the dirty Owner `main`, so its code-diff review is explicitly `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE`; the remote evidence review must not be read as a connector-performed code review.

This delivery proves an offline Android product flow: project references, all-project reference browsing, source/state labels, persistent project naming, truthful guided/direct Camera entry, Camera controls, private captures and explicit export. It does not prove real-photo quality, a physical device, Qwen/Private LAN, Pipeline, human editorial acceptance, signing, beta or public release.

## T6 P1B authorization readiness

T6 has prepared a metadata-only P1B authorization packet from exact T5 head `0b04abdc57a3fa332689cfe664f9bcb997160e5e`. [`p1b_readiness_manifest.v1.json`](phase1_5/p1b/p1b_readiness_manifest.v1.json) binds the reviewed P1A model, revision, expected artifact bytes/hash, corpus r3 counts and contract identities without rewriting the frozen evidence. The packet is `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED`.

The primary candidate remains `Qwen/Qwen3-VL-2B-Instruct` at immutable revision `89644892e4d85e24eaac8bacfd4f463576704203`; download, runtime, inference, App, Pipeline and private-media authorization flags remain false. Legal review remains `NOT_LEGAL_APPROVED`, artifact redirect verification remains unresolved, and no Owner decision is tracked. The stdlib-only validator returns `READY_FOR_OWNER_DECISION`; its execution-authority probe is required to return `BLOCKED_OWNER_AUTHORIZATION_REQUIRED`.

T6 does not mean Qwen is qualified or Provider-ready. Artifact acquisition, runtime/inference, real photos, physical device, Private LAN, Pipeline, human editorial, signing, beta/public release and main merge remain separate `NOT_RUN`/`BLOCKED`/`FROZEN` gates.

## T7 evaluation governance readiness

T7 has prepared a provider-neutral evaluation-governance packet from exact T6 head `3233313af8be946cc43245daf15f24f853933d88`. [`evaluation_governance.v1.json`](phase1_5/t7/evaluation_governance.v1.json) encodes the existing 18-criterion rubric, blinded reviewer slots, deterministic R3 adjudication, severe-hallucination categories, uncertainty semantics and the still-blocked Pipeline mapping. The state is `EVALUATION_GOVERNANCE_PACKET_READY — HUMAN_REVIEW_NOT_AUTHORIZED`.

The existing rubric remains `PROPOSED_OWNER_THRESHOLD — NOT_MEASURED`; proposed thresholds are not launch or Provider PASS criteria. T7 creates no human-review records, image/media payloads, reviewer identities, provider result, Pipeline adapter or Android change. Human-review execution, Provider qualification, Pipeline compatibility, real photos, physical device, signing, release and main merge remain closed.

## T8 synthetic evaluation evidence harness readiness

T8 has prepared a contract-only synthetic harness from exact T7 head `61cb6776b2b6f00d6965789e0589c5337536d258`. [`synthetic_evaluation_manifest.v1.json`](phase1_5/t8/synthetic_evaluation_manifest.v1.json) and the JSON-only runner exercise SUCCESS/FAILED/CANCELLED, Pipeline incompatibility, blinded review projections and T7 adjudication states without executing a Provider, human review, Pipeline or media path. The state is `SYNTHETIC_EVALUATION_HARNESS_READY — REAL_EVALUATION_NOT_AUTHORIZED`.

Synthetic metrics are contract evidence only. `METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED` is not Provider qualification, confidence, human acceptance or release readiness. T6 P1B remains execution-not-authorized and T7 human review remains not-authorized.

## T9 Product READY promotion gate

T9 defines a machine-readable, fail-closed boundary for a future Provider result
to become trusted product READY guidance. It is `PRODUCT_READY_PROMOTION_GATE_READY — REAL_READY_PROMOTION_NOT_AUTHORIZED` and contains only synthetic, image-free policy fixtures and validators. It does not authorize Provider execution, human review, Pipeline, device, Android READY writes, signing, release or merge. The existing offline Photo Knowledge Bundle consumer READY path remains separate and preserved.

## Product goal

`参考图 → 来源明确的逐图状态 → READY-only 可信指导 → Camera Director 或无指导拍摄 → 私有成片 → 系统导出`

The future live director remains a separate route requiring a qualified provider, device evidence and additional authorization. Pose is frozen until its own decision and qualification gate. The active C2C source review uses the clean exact-head clone; the Owner `main` remains dirty and untouched.

## Historical material

The following remain useful evidence or design constraints but are not current execution authority: G0/AH0/AA0 bootstrap plans, the six-week roadmap, P20/P21/P22 operational banners, P1A/P1B implementation plans, older P25/T3 handoffs and Obsidian P22/P24 notes. Do not rewrite their historical claims as though they were current; use this page and the gate matrix for current status.

## Next stage

T5 consolidated this status into one machine-checkable authority, T6 added a machine-verifiable P1B readiness boundary, T7 added provider-neutral evaluation governance, T8 exercised the future evidence flow with contract-only synthetic records, and T9 defines the future READY-promotion boundary. T9 changes no Android runtime code, shared contracts, models, network, Pipeline code, signing settings, human-review records or Owner decisions. The next executable step is an Owner decision or another separately planned non-runtime Gate; no artifact, provider, human review or Pipeline action follows automatically.
