# ProviderAnalysisEnvelope v1

**Decision:** `RECOMMEND_APPROVE` as a planning-only operational envelope. It is separate from `ReferenceBundle`; the Bundle remains the user-consumable payload.

## Boundary

The envelope records a terminal provider attempt: identity, candidate model/runtime provenance, timing, status, bounded warnings, uncertainty, safety flags, retry semantics, and a safe error. It must never contain raw image bytes, image URI/path, Photo Picker URI, API key, credential, user account, device serial, exact location, raw prompt, or raw exception.

## Required shape

`docs/reference/provider_analysis_envelope.v1.schema.json` freezes these fields: `envelope_version`, `request_id`, `reference_id`, `provider_id`, `provider_type`, `model_id`, `model_revision`, `model_artifact_sha256`, `runtime_id`, timestamps, latency, status, output schema version, `bundle`, confidence summary, uncertainty flags, warnings, provenance, safety flags, `retryable`, and `error`.

`provider_type` is limited to `PIPELINE`, `LOCAL_SERVICE`, `CLOUD`, and `ON_DEVICE`; statuses are terminal: `SUCCESS`, `FAILED`, or `CANCELLED`.

| Terminal status | Bundle | Error | Required behavior |
| --- | --- | --- | --- |
| `SUCCESS` | valid v1 Bundle | `null` | `output_schema_version` is `"1.0"`; render only after validation. |
| `FAILED` | `null` | required | Explain the safe, mapped product action; never fabricate a Bundle. |
| `CANCELLED` | `null` | required | Use `USER_CANCELLED`; no hidden retry or Demo substitution. |

Missing model metadata is represented by `null` only for non-produced/error results. The planning fixture deliberately says `CANDIDATE_UNSELECTED`; it is not model provenance.

## Compatibility and transition

Envelope and Bundle have independent exact `"1.0"` versions. A consumer must validate both before display. Provider metadata never enters Bundle, while source disclosure/uncertainty must be passed to a future UI adapter by a separately authorized implementation. Existing Phase 1 UI hard-codes Demo source; this P0 contract does not alter that behavior.
