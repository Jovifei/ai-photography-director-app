# VLM Uncertainty and Truthfulness Contract

**Decision:** `RECOMMEND_APPROVE`. P0 records the contract only; no current UI displays an Envelope.

## Required per-field declaration

Every terminal Envelope carries a value for all nine fields: `scene`, `background_story`, `lighting`, `composition`, `subject_intent`, `emotion`, `pose_template`, `camera_position`, and `director_prompt`. Each declaration has:

- `level`: `LOW`, `MEDIUM`, `HIGH`, or `NOT_ASSESSED`;
- `basis`: `DIRECT_OBSERVATION`, `PHOTOGRAPHIC_INTERPRETATION`, `CREATIVE_RECOMMENDATION`, or `INSUFFICIENT_EVIDENCE`.

There is no single synthetic percentage for the whole photograph. `confidence_summary` names field groups and evidence types, not a global score.

| Field class | Typical basis | Future UI wording |
| --- | --- | --- |
| Scene, lighting direction/quality, composition | Direct observation when evidence is clear | “图片中可见…” |
| Background story, subject intent, emotion | Photographic interpretation | “可尝试理解为…” |
| Pose template, camera position, director prompt | Creative recommendation | “建议…” |
| Failed/no evidence | Insufficient evidence | “未能可靠判断。” |

Direct observation is not a guarantee: occlusion, low light, ambiguity, and model limitations may require `MEDIUM`, `HIGH`, or `NOT_ASSESSED`. A future UI must never present story, emotion, or recommendation as an objective observed fact.

## Provenance

`provenance.result_origin` distinguishes `PIPELINE`, `LOCAL_VLM`, `CLOUD`, `ON_DEVICE`, and `UNAVAILABLE`. It also records only safe release and field-basis information. Model artifact identifiers belong in the Envelope, never Bundle. Current Phase 1 is separately and visibly Demo; it has no real-result provenance.
