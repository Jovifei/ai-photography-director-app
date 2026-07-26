# ProviderAnalysisEnvelope v1

**Decision:** `RECOMMEND_APPROVE` as a planning-only operational envelope after schema and semantic validation. It is separate from `ReferenceBundle`; the Bundle remains the user-consumable payload.

## Boundary

The envelope records a terminal provider attempt: identity, candidate model/runtime provenance, timing, status, bounded warnings, uncertainty, safety flags, retry semantics, and a safe error. It must never contain raw image bytes, image URI/path, Photo Picker URI, API key, credential, user account, device serial, exact location, raw prompt, or raw exception.

## Required shape

`docs/reference/provider_analysis_envelope.v1.schema.json` freezes these fields: `envelope_version`, `request_id`, `reference_id`, `provider_id`, `provider_type`, `model_id`, `model_revision`, `model_artifact_sha256`, `runtime_id`, timestamps, latency, status, output schema version, `bundle`, confidence summary, uncertainty flags, warnings, provenance, safety flags, `retryable`, and `error`.

`request_id` and `reference_id` use the same bounded opaque-token syntax as the Bundle. That syntax excludes URI/path/email separators; it does not prove token provenance. Producers must separately use approved random/opaque generators and must not derive IDs directly from account, device, location, filename, URI, or media-hash data.

`provider_type` is limited to `PIPELINE`, `LOCAL_SERVICE`, `CLOUD`, and `ON_DEVICE`; statuses are terminal: `SUCCESS`, `FAILED`, or `CANCELLED`.

| Terminal status | Bundle | Error | Required behavior |
| --- | --- | --- | --- |
| `SUCCESS` | valid v1 Bundle | `null` | `output_schema_version="1.0"`, `retryable=false`, non-empty model ID/revision/runtime ID, 64-hex artifact hash, and validated Bundle/reference ID match. |
| `FAILED` | `null` | required | Error code must be in the frozen policy; retry/preserve/log/action must exactly match it; never fabricate a Bundle. |
| `CANCELLED` | `null` | required | `USER_CANCELLED`, `retryable=false`, `STOP`, no safe event log, no hidden retry, and no Demo substitution. |

Missing model metadata is represented by `null` only for non-success/error results. The SUCCESS fixture uses synthetic contract-only metadata and a synthetic 64-hex value solely to prove required shape; it is not VLM execution, a selected model, or artifact provenance.

## Time and error semantics

`started_at_utc` and `completed_at_utc` are timezone-aware audit timestamps (UTC `Z` is recommended). The semantic validator requires `completed_at_utc >= started_at_utc`. `latency_ms` is a non-negative duration measured by a provider monotonic clock; it is not required to equal the wall-clock timestamp delta because precision and scheduling differ.

`docs/phase1_5/error_policy.v1.json` is the sole machine-readable error policy. It freezes retryability, reference preservation, safe logging, diagnostic-upload prohibition, product action, cleanup, and Demo fallback behavior for every taxonomy code. `allow_diagnostic_upload` is always `false`. `EXPLICIT_DEMO_FALLBACK` is never an automatic provider outcome: it can occur only after a separate explicit user choice, must retain the `Demo Analysis` label, and must not be represented as a SUCCESS provider result.

The semantic validator rejects safe-error messages that contain obvious stack traces, local absolute paths, Photo Picker URIs, bearer/API-key tokens, or device-serial fields. Raw exceptions are mapped only to a taxonomy code and a safe user message.

## Compatibility and transition

Envelope and Bundle have independent exact `"1.0"` versions. A consumer must validate both before display. Provider metadata never enters Bundle, while source disclosure/uncertainty must be passed to a future UI adapter by a separately authorized implementation. Existing Phase 1 UI hard-codes Demo source; this P0 contract does not alter that behavior.
