# Next-gate matrix

This matrix describes possible future tracks. It records prerequisites; it does not authorize any track.

| Future track | Existing basis | Required before execution | Current disposition |
|---|---|---|---|
| Provider / Qwen P1B | P1A desk, license and corpus evidence; T6 readiness packet binds exact revision/artifact metadata | Owner decision for exact artifact quarantine, then separate runtime/platform/resource plan | `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED` |
| Private LAN service | Historical service and pilot planning | Provider qualification, private-network authority, authentication/TLS/privacy protocol | `NOT_RUN` |
| Pipeline | Field mapping and Bundle contract references | Producer implementation, human approval, rights/privacy and release/integrity evidence | `BLOCKED` |
| Human editorial | Product corpus, review guide and T7 governance packet | Reviewer role, rights/privacy/retention decisions, approved thresholds and an explicitly authorized public-corpus review run | `HUMAN_REVIEW_NOT_AUTHORIZED` |
| Physical device | T3/T4 emulator evidence | Explicit device protocol, allowed device and privacy-safe evidence scope | `NOT_RUN` |
| Pose | Frozen domain and prior spike evidence | Separate provider decision and real-device qualification | `FROZEN` |
| Signing / beta / release | Historical release work | Separate authorization tied to a reviewed candidate and signing identity | `NOT_RUN_FOR_T4_STACK` |
| More offline Android work | T4 product PASS | A concrete reviewed defect or requirement | `NO_SPECULATIVE_SCOPE` |
| Product READY promotion gate | T8 synthetic contract evidence and offline Photo Knowledge Bundle consumer contract | Non-runtime policy/schema/validator evidence only; no Provider qualification or product write authorization | `PRODUCT_READY_PROMOTION_GATE_READY — REAL_READY_PROMOTION_NOT_AUTHORIZED` |
| Owner Decision Capture / P1B Route Resolution | T6 P1B packet, exact T9 authority and explicit Owner choice | Existing P1B schema validation; exact artifact binding; no download/runtime/inference/App/Pipeline/private-media authorization | `P1B_ARTIFACT_QUARANTINE_SCOPE_APPROVED — ARTIFACT_ACQUISITION_NOT_YET_AUTHORIZED` |

The matrix must not promote an old Closed Beta candidate, an emulator result, an HTTP success, an editorial draft or an AI pre-review into a current release or human PASS.

## Stage-selection rule

T10 has captured `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`, but this does not authorize model download, inference, real-photo access, network activation, device action or merge. A separate plan is required before any artifact acquisition or quarantine operation.

T6's `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED` status is metadata coherence only. It does not qualify Qwen or a Provider, approve licensing or transport, authorize artifact acquisition, or open the runtime branch. A future Owner decision must bind the readiness manifest and may only select `DEFER`, `REJECT_PRIMARY`, or exact artifact quarantine preparation; runtime/inference/App/Pipeline/private-media authorization remains a later gate.

T7's `EVALUATION_GOVERNANCE_PACKET_READY — HUMAN_REVIEW_NOT_AUTHORIZED` status is protocol coherence only. It does not mean a reviewer scored any output, a threshold was approved, a Provider passed, or Pipeline compatibility exists. The 18-criterion rubric remains proposed and unmeasured; the human-review rights/privacy gate remains `NOT_RUN`.

T8's `SYNTHETIC_EVALUATION_HARNESS_READY — REAL_EVALUATION_NOT_AUTHORIZED` status proves only that contract-only JSON fixtures can flow through Envelope eligibility, blinded projection and T7 adjudication states. It does not represent Provider execution, human review, image/media processing, confidence, Pipeline compatibility or approved thresholds.

T9's `PRODUCT_READY_PROMOTION_GATE_READY — REAL_READY_PROMOTION_NOT_AUTHORIZED` status proves only that the future promotion boundary is machine-readable and fail-closed. It does not authorize a real Provider result, trusted READY guidance, Android READY writes, human review, Pipeline, device, signing, release or merge. The offline Photo Knowledge Bundle consumer remains a separate preserved READY path.
