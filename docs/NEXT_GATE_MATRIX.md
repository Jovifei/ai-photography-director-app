# Next-gate matrix

This matrix describes possible future tracks. It records prerequisites; it does not authorize any track.

| Future track | Existing basis | Required before execution | Current disposition |
|---|---|---|---|
| Provider / Qwen P1B | P1A desk, license and corpus evidence; T6 readiness packet binds exact revision/artifact metadata | Owner decision for exact artifact quarantine, then separate runtime/platform/resource plan | `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED` |
| Private LAN service | Historical service and pilot planning | Provider qualification, private-network authority, authentication/TLS/privacy protocol | `NOT_RUN` |
| Pipeline | Field mapping and Bundle contract references | Producer implementation, human approval, rights/privacy and release/integrity evidence | `BLOCKED` |
| Human editorial | Product corpus and review guide | Reviewer role, rights/privacy/retention decisions and acceptance rubric | `NOT_RUN` |
| Physical device | T3/T4 emulator evidence | Explicit device protocol, allowed device and privacy-safe evidence scope | `NOT_RUN` |
| Pose | Frozen domain and prior spike evidence | Separate provider decision and real-device qualification | `FROZEN` |
| Signing / beta / release | Historical release work | Separate authorization tied to a reviewed candidate and signing identity | `NOT_RUN_FOR_T4_STACK` |
| More offline Android work | T4 product PASS | A concrete reviewed defect or requirement | `NO_SPECULATIVE_SCOPE` |

The matrix must not promote an old Closed Beta candidate, an emulator result, an HTTP success, an editorial draft or an AI pre-review into a current release or human PASS.

## Stage-selection rule

Until a new Owner authorization names one of the runtime, editorial, Pipeline, device or signing tracks, the next executable plan remains non-runtime preparation. No model download, inference, real-photo access, network activation, device action or merge follows automatically from this matrix.

T6's `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED` status is metadata coherence only. It does not qualify Qwen or a Provider, approve licensing or transport, authorize artifact acquisition, or open the runtime branch. A future Owner decision must bind the readiness manifest and may only select `DEFER`, `REJECT_PRIMARY`, or exact artifact quarantine preparation; runtime/inference/App/Pipeline/private-media authorization remains a later gate.
