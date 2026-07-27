# Owner P1B Decision Matrix

P1A outcome after remediation: r2 corpus integrity and exact-revision license metadata are ready for independent delta review. All model bytes and runtime work remain blocked pending independent review and an explicit Owner decision.

| Owner choice | Preconditions | Benefit | Risk / cost | Resulting state |
| --- | --- | --- | --- | --- |
| Approve exact 2B artifact quarantine download | Approve frozen revision, domains, license review, target quarantine location | Enables immutable local artifact verification | 4.26 GB artifact, storage and supply-chain handling | P1B download verification only |
| Defer all model download | None | Keeps no-model privacy and operations boundary | Delays VLM experimentation | Remain P1A-complete |
| Reject 2B route | Record reason | Avoids runtime investment | Requires a new candidate review later | Primary marked rejected |
| Request 4B backup review | New VRAM/runtime decision | Potentially greater capability | 8.89 GB published footprint; high 12 GB risk | Desk review only unless separately approved |
| Approve runtime gate after verified artifact | Verified artifact plus separate platform/privacy plan | Allows bounded public-corpus experiment | Runtime, memory, package, and output-quality risks | Separate P1B runtime task |

No option authorizes Android changes, CameraX/UI changes, pose work, Pipeline integration, user-photo access, cloud service use, GPU use, or production deployment unless explicitly stated in a later Owner task.
