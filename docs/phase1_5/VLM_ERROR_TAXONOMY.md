# VLM Error Taxonomy

**Decision:** `RECOMMEND_APPROVE` for a uniform fail-closed contract. Event logging means a redacted code/timing event only; diagnostic upload is always `false` in Phase 1.5.

| Code | Retryable | Preserve in-session reference | Safe event log | Product action |
| --- | --- | --- | --- | --- |
| `USER_CANCELLED` | No | No | No | Stop. |
| `REFERENCE_URI_UNAVAILABLE`, `REFERENCE_PERMISSION_EXPIRED` | No | No | Yes | Require explicit reselection. |
| `IMAGE_DECODE_FAILED`, `IMAGE_UNSUPPORTED`, `IMAGE_TOO_LARGE` | No | No | Yes | Explain and require a supported reselection. |
| `IMAGE_PRIVACY_BLOCKED` | No | No | Yes | Block and explain; no network or fallback. |
| `PROVIDER_NOT_CONFIGURED` | No | No | Yes | Show unavailable. |
| `PROVIDER_UNAVAILABLE`, `PROVIDER_TIMEOUT`, `PROVIDER_OOM` | Yes | Yes, session-only | Yes | Offer bounded explicit retry. |
| `PROVIDER_OUTPUT_EMPTY`, `PROVIDER_OUTPUT_MALFORMED`, `PROVIDER_OUTPUT_SCHEMA_INVALID` | Yes | Yes, session-only | Yes | Do not render; offer retry after provider remediation. |
| `PROVIDER_SAFETY_REJECTED`, `PROVIDER_LICENSE_BLOCKED` | No | No | Yes | Block and explain. |
| `PIPELINE_BUNDLE_INCOMPATIBLE` | No | No | Yes | Show unavailable; require compatible producer. |
| `UNKNOWN_FAILURE` | No | No | Yes | Show unavailable; do not retry automatically. |

Every error object contains a code, user-safe message, reference-preservation decision, safe-event-log flag, `allow_diagnostic_upload=false`, and a constrained product action. It intentionally excludes arbitrary exception text.

## Demo truth rule

An analysis error never silently becomes fixed Demo output. A future UI may offer `EXPLICIT_DEMO_FALLBACK` only after an unmistakable user choice and a visible `Demo` source label. It must not label that fallback as real, local, Pipeline, or cloud analysis.
