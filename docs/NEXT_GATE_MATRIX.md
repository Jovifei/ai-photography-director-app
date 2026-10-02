# Next-gate matrix

This matrix describes possible future tracks. It records prerequisites; it does not authorize any track.

Current Android source candidate: T3–T17 is locally integrated on `codex/mainline-consolidation-20261002`; it is not merged to GitHub `main`. See [`LOCAL_MAINLINE_RECONCILIATION_20261002.md`](LOCAL_MAINLINE_RECONCILIATION_20261002.md) for exact source, local evidence and remaining blockers.

| Future track | Existing basis | Required before execution | Current disposition |
|---|---|---|---|
| Provider / Qwen P1B | P1A desk, license and corpus evidence; T6 readiness packet binds exact revision/artifact metadata | Owner decision for exact artifact quarantine, then separate runtime/platform/resource plan | `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED` |
| Private LAN service | Historical service and pilot planning | Provider qualification, private-network authority, authentication/TLS/privacy protocol | `NOT_RUN` |
| Pipeline | Field mapping and Bundle contract references | Producer implementation, human approval, rights/privacy and release/integrity evidence | `BLOCKED` |
| T14 PKB1 producer compatibility | T17 consumer-side golden-vector request | Producer revision, raw single/multi/A/B vectors, exact byte hashes, canonical PKB1 digests and independent expected mappings | `PENDING_EXTERNAL_GOLDEN_VECTORS` |
| Human editorial | Product corpus, review guide and T7 governance packet | Reviewer role, rights/privacy/retention decisions, approved thresholds and an explicitly authorized public-corpus review run | `HUMAN_REVIEW_NOT_AUTHORIZED` |
| Physical device | T3/T4 emulator evidence | Explicit device protocol, allowed device and privacy-safe evidence scope | `NOT_RUN` |
| Pose | Frozen domain and prior spike evidence | Separate provider decision and real-device qualification | `FROZEN` |
| Signing / beta / release | Historical release work | Separate authorization tied to a reviewed candidate and signing identity | `NOT_RUN_FOR_T4_STACK` |
| More offline Android work | T3–T17 local candidate; latest stage T17 machine validation | A concrete reviewed defect or requirement; no speculative continuation | `NO_SPECULATIVE_SCOPE — T14 waits for producer vectors` |
| Product READY promotion gate | T8 synthetic contract evidence and offline Photo Knowledge Bundle consumer contract | Non-runtime policy/schema/validator evidence only; no Provider qualification or product write authorization | `PRODUCT_READY_PROMOTION_GATE_READY — REAL_READY_PROMOTION_NOT_AUTHORIZED` |
| Owner Decision Capture / P1B Route Resolution | T6 P1B packet, exact T9 authority and explicit Owner choice | Existing P1B schema validation; exact artifact binding; no download/runtime/inference/App/Pipeline/private-media authorization | `P1B_ARTIFACT_QUARANTINE_SCOPE_APPROVED — ARTIFACT_ACQUISITION_NOT_YET_AUTHORIZED` |
| T11 Artifact Acquisition Preflight | Accepted T10-R3 identity, frozen P1B source bindings and exact Qwen artifact metadata | External legal disposition, verified transport chain, fresh immutable metadata, opaque quarantine destination, and a later separately bound Owner decision | `ARTIFACT_ACQUISITION_PREFLIGHT_READY — EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED`; current blocker `BLOCKED_LEGAL_REVIEW` |

The matrix must not promote an old Closed Beta candidate, an emulator result, an HTTP success, an editorial draft or an AI pre-review into a current release or human PASS. The historical T17 96/96 remains remote evidence. Current local T17 qualification now passed six host phases, eight Android guards and nineteen host policies; the full 96-test matrix was not rerun. SDK-access preflight corrected the earlier sandbox-empty AVD result. See reports/LOCAL_API35_T17_20261002.md; physical-device acceptance remains NOT_RUN.

## Stage-selection rule

T10 has captured `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`, but this does not authorize model download, inference, real-photo access, network activation, device action or merge. A separate plan is required before any artifact acquisition or quarantine operation.

T11 adds a nested artifact-acquisition preflight under the existing top-level T9 stage. Its current blocker is `BLOCKED_LEGAL_REVIEW`; transport, fresh metadata, destination and a later Owner decision remain unresolved. T11 makes no network request and does not acquire the artifact. The next authorized stage is `EXTERNAL_LEGAL_TRANSPORT_DESTINATION_EVIDENCE_OR_OWNER_DEFER`.

T6's `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED` status is metadata coherence only. It does not qualify Qwen or a Provider, approve licensing or transport, authorize artifact acquisition, or open the runtime branch. A future T11 acquisition Owner decision must bind the T10 identity, T11 manifest, legal/transport/fresh-metadata evidence, destination and approved domains; it may only select `DEFER`, `REJECT_ACQUISITION`, or exact quarantine acquisition. Runtime/inference/App/Pipeline/private-media authorization remains closed.

T7's `EVALUATION_GOVERNANCE_PACKET_READY — HUMAN_REVIEW_NOT_AUTHORIZED` status is protocol coherence only. It does not mean a reviewer scored any output, a threshold was approved, a Provider passed, or Pipeline compatibility exists. The 18-criterion rubric remains proposed and unmeasured; the human-review rights/privacy gate remains `NOT_RUN`.

T8's `SYNTHETIC_EVALUATION_HARNESS_READY — REAL_EVALUATION_NOT_AUTHORIZED` status proves only that contract-only JSON fixtures can flow through Envelope eligibility, blinded projection and T7 adjudication states. It does not represent Provider execution, human review, image/media processing, confidence, Pipeline compatibility or approved thresholds.

T9's `PRODUCT_READY_PROMOTION_GATE_READY — REAL_READY_PROMOTION_NOT_AUTHORIZED` status proves only that the future promotion boundary is machine-readable and fail-closed. It does not authorize a real Provider result, trusted READY guidance, Android READY writes, human review, Pipeline, device, signing, release or merge. The offline Photo Knowledge Bundle consumer remains a separate preserved READY path.

## 主线整合后当前状态（2026-10-02）

PR22合并061d7627aa1a9dd461411c1f1141fec62e8857a6，PR23合并98b28b03a74c81e972d5806ee6637f9b7b1fd845。Owner main已安全同步整合源码，原件备份及Owner修改保留，20项完整性行为测试与进度/隐私门通过。前文Draft/未合并描述保留为历史，不再代表当前主线；详见reports/MAINLINE_CONSOLIDATION_20261002.md。T14、物理手机、人审和发布资格独立待验收。
