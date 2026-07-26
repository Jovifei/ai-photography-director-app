# Pipeline ↔ App Bundle Contract Plan

**Status:** `SUPERSEDED_PLANNING_DRAFT — NO_CROSS-REPOSITORY_CONTRACT_CHANGE_AUTHORIZED`

This historical product-planning draft is not the v1 contract entry point. Use `docs/reference/reference_bundle.v1.schema.json` and `docs/phase1_5/PIPELINE_APP_FIELD_MAPPING.md` for the current app-local contract and mapping boundary. `PIPELINE_DIRECT_INTEGRATION = BLOCKED_BY_CONTRACT_AND_STAGE_EVIDENCE`; this document does not authorize a Pipeline connection.

## 1. Product boundary

`nightly-photo-intelligence-pipeline` is the **offline knowledge producer**.
`ai-photography-director-app` is the **on-device field-execution consumer**.

```text
Approved offline reference intelligence
        ↓
Photo Knowledge Bundle
        ↓
Android validation / display adapter
        ↓
Director Card → reference-only Camera Guidance
```

The mobile app should consume an already validated knowledge bundle. It must not become a silent model-serving client, an image uploader, or a replacement for the offline pipeline.

## 2. Proposed portable payload

The producer should emit a versioned, image-free payload. `photo_id` must be an opaque producer-scoped identifier, not a file path, account identifier, image URL, EXIF record, device identifier, or personal-media database key.

```json
{
  "version": 1,
  "photo_id": "opaque-id",
  "scene": "string",
  "story": "string",
  "lighting": "string",
  "composition": "string",
  "pose_template": "reference-derived textual intent only",
  "emotion": "string",
  "director_prompt": "string"
}
```

For the current Android consumer, the adapter may map `story` to `background_story`. If the producer can safely supply them, `subject_intent` and `camera_position` are optional **future extension fields**; their absence must produce a clear fallback, never invented guidance.

## 3. Required envelope and validation

The canonical shared contract, schema registry, and source-of-truth location must be approved jointly before either repository changes its frozen shared contract. The proposed transport envelope must include:

| Field | Requirement |
| --- | --- |
| `schema_version` | Explicit integer/semantic version; consumers reject unsupported major versions. |
| `bundle_id` | Opaque, non-personal identifier for idempotency and audit correlation. |
| `producer` | Name and released version of the offline producer. |
| `created_at` | UTC production time, without device/user identifiers. |
| `analysis_provenance` | Provider/model/version only after legal and privacy approval; no credentials or prompts containing user data. |
| `payload` | The validated image-free structure above. |
| `integrity` | Canonical payload hash/signature strategy to be selected in an ADR. |

App validation rules:

1. Validate schema version, required fields, maximum sizes, and character safety before display.
2. Treat an invalid or unknown bundle as unavailable; do not partially improvise a director plan.
3. Keep the bundle separate from original image media. Passing a Bundle must not grant access to a photo.
4. Preserve source labelling: Demo, offline-produced, or future consented VLM result.
5. Support backward-compatible additive fields only through an explicit versioning policy.

## 4. Operational ownership

| Concern | Pipeline producer | Android app consumer |
| --- | --- | --- |
| Reference understanding | Produces evaluated knowledge from approved inputs. | Does not perform hidden inference. |
| Privacy governance | Applies source-input consent, retention, and deletion rules. | Stores/displays only approved data according to local policy. |
| Bundle compatibility | Publishes a versioned schema and release notes. | Validates/declines unsupported bundles deterministically. |
| Camera execution | Does not control device capture. | Turns guidance into user-visible shooting actions. |
| Failure handling | Marks unavailable/low-confidence output. | Shows an honest unavailable/fallback state. |

## 5. Required Owner decisions before any connection

1. Canonical shared-contract repository and owner.
2. Whether offline-produced knowledge may reach a device, by which user-consented transfer channel.
3. Data classification, retention/deletion policy, and whether any reference image ever leaves the owner-controlled environment.
4. Schema/versioning and integrity mechanism.
5. Evaluation corpus policy and acceptance rubric for photography guidance.

## 6. Non-goals

This planning document does not connect repositories, change a shared contract, transfer data, download a model, run image analysis, access private photos, or alter CameraX/UI0/Pose behavior.
