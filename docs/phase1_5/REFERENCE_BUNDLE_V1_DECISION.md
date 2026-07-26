# ReferenceBundle v1 Freeze Decision

**Decision:** `RECOMMEND_APPROVE` for the app-local consumer contract only. This is not a shared-contract change and does not authorize a model, Pipeline connection, persistence, or Android implementation.

## Canonical entry point

`docs/reference/reference_bundle.v1.schema.json` is the canonical v1 schema. The existing `docs/reference/reference_bundle.schema.json` is retained as a compatibility alias (`$ref`) so the Phase 1 Demo fixture and Kotlin `ReferenceBundle.CURRENT_VERSION = "1.0"` remain valid. It is neither deprecated nor a migration endpoint.

## Frozen consumer payload

All eleven fields are required and `additionalProperties` is false:

| Field | Maximum | Rule |
| --- | ---: | --- |
| `reference_id` | 128 | `^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$`; opaque-token syntax only. |
| `scene`, `lighting`, `emotion` | 240 | Single-line plain text. |
| `background_story`, `pose_template` | 480 | Single-line plain text. |
| `composition`, `subject_intent`, `camera_position` | 360 | Single-line plain text. |
| `director_prompt` | 720 | Single-line actionable suggestion. |
| `version` | exact | String `"1.0"`. |

All user-visible text has `minLength >= 1`, its stated maximum, no CR/LF, and at least one non-whitespace character. The schema permits neither empty nor whitespace-only text; the semantic validator additionally rejects leading/trailing whitespace rather than silently trimming provider output. Markdown has no supported semantics and consumers must render it as plain text, not rich content. Enums are intentionally not used for photography language; the envelope supplies the constrained operational fields.

### Identifier boundary

**SCHEMA_ENFORCED:** `reference_id` is a bounded opaque-token syntax. It has no colon, slash, backslash, `@`, whitespace, CR/LF, URI separator, Windows-path, Unix-path, or email syntax.

**PRODUCER_POLICY_ONLY:** syntax alone cannot prove that a token was not derived from an account, device, location, file name, URI, or media hash. An approved producer must generate it from a random/opaque ID generator and retain evidence of that policy. The schema does not make provenance claims it cannot prove.

`pose_template` remains a textual intent derived from the reference. It must never be changed into keypoints, skeleton coordinates, biometric classification, real-time Pose data, or a Pose-provider payload. The similarly named program-level `shared-contract` Pose schema is not semantically compatible and is not imported here.

## Version and compatibility policy

- A v1 producer emits exactly `"1.0"`; it cannot append provider-defined Bundle fields.
- A v1 consumer rejects unknown version or field data and returns unavailable rather than partially guessing guidance.
- A later contract that changes payload semantics or fields receives a new major schema and explicit dual-read/dual-publish migration approval.
- New consumers may explicitly retain v1 support. Old consumers fail closed for a new major. There is no implicit v1 minor extension lane.

This strict policy deliberately preserves the legacy Demo version string. The integer `1` shown in older planning examples is not compatible with the implemented Demo and is rejected.
