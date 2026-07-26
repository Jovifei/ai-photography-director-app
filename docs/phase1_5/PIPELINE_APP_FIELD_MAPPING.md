# Pipeline Producer to App Consumer Field Mapping

**State:** `PIPELINE_TO_REFERENCEBUNDLE_DIRECT_INTEGRATION = BLOCKED_BY_CONTRACT_AND_STAGE_EVIDENCE`.

This is a read-only structural mapping of `nightly-photo-intelligence-pipeline`; it does not connect repositories or change either shared contract. There is no Pipeline `analysis.json`: its planned release uses `bundle.json` and `items/<asset_id>.json`, and its only populated example is a fixture with `models: []`, not a production VLM result.

| Pipeline field | App `ReferenceBundle` field | Transform | Required | Risk |
| --- | --- | --- | --- | --- |
| `item.asset_id` | `reference_id` | Conditional direct copy after proving opaque, non-personal semantics. | Yes | Schema alone does not prove it is not filename/account-derived. |
| Arbitrary `observed_facts.facts[].kind/value` | `scene` | No safe direct mapping; needs controlled producer vocabulary. | Yes | Selector would invent meaning. |
| `story_candidates` / `BACKGROUND_VALUE` interpretation | `background_story` | Deterministic, labelled interpretation selection only. | Yes | Story is not an observed fact. |
| `LIGHTING_USE` interpretation | `lighting` | Bounded deterministic selection/join after presence check. | Yes | 0..N insight values can be absent. |
| `COMPOSITION_RESOURCE` interpretation | `composition` | Bounded deterministic selection/join after presence check. | Yes | Same cardinality/absence risk. |
| No direct field | `subject_intent` | No mapping. Require a future proposed shooting-direction field. | Yes | Must not infer personal intent. |
| No direct field | `emotion` | No mapping. | Yes | Mood/story cannot become asserted emotion. |
| Structured `pose_template` | `pose_template` | Never copy. Require a separate textual pose-intent producer field. | Yes | Contains coordinates/keypoints/model provenance. |
| `director_prompts.photographer_technical.lines` | `camera_position` | No direct mapping; require new controlled producer field. | Yes | Generic advice is not camera position. |
| `director_prompts.standard_guidance.lines` | `director_prompt` | Join validated, bounded ordered lines. | Yes | Must remain recommendation. |
| Pipeline `item.schema_version` | Bundle `version` | Do not copy. Adapter emits `"1.0"` only after an approved compatibility rule. | Yes | Producer and consumer versions differ. |

## Envelope-only mapping

Pipeline `provenance` can inform Envelope provenance after a future contract review: code commit, schema version, model identifiers/revisions/licenses/weight hashes, algorithms, prompt version, and config hash stay outside Bundle. Map `uncertainties[]` to the Envelope’s per-field declarations without a global confidence percentage.

`bundle.generated_at` is a release timestamp, and `review.reviewed_at` is review time; neither proves per-analysis start/completion/latency. Pipeline has no provider ID/type, runtime ID, provider outcome, retry/error, or retention/deletion fields. They remain `UNKNOWN`/unavailable until a later producer and Envelope contract is approved.

Exclude source hashes, perceptual hashes, sanitized filenames, dimensions, observed EXIF, reviewer identity/history, item paths, thumbnails, masks, cutouts, overlays, and derivative paths from both Bundle and Envelope.

## Future adapter acceptance sequence

1. Verify an Owner-approved producer release, outer integrity, and human approval.
2. Validate known producer and consumer versions; reject unknown majors.
3. Validate all required mapped values. Missing `scene`, `lighting`, `composition`, `subject_intent`, `emotion`, textual `pose_template`, or `camera_position` yields `PIPELINE_BUNDLE_INCOMPATIBLE` and no Bundle.
4. Emit a validated Bundle and a separate safe Envelope. Never invent missing fields.

N3 facts, N4 director prompts, and N7 app-bundle integration are currently locked in the Pipeline repository; this mapping is not readiness evidence.
