# Phase 1.5 VLM Integration Plan

**Status:** `OWNER_DECISION_REQUIRED — PLANNING_ONLY`
**Purpose:** Replace the fixed `DemoReferenceAnalyzer` only after an approved privacy, deployment, model, and evaluation decision. No model, inference framework, network API, image upload, or code is added by this plan.

## 1. Product outcome

Phase 1.5 should make a reference image produce a truthful, structured photographic interpretation:

```text
User-consented reference image
        ↓
Approved VLM adapter
        ↓
Schema validation and uncertainty checks
        ↓
ReferenceBundle-compatible analysis
        ↓
Director Card and reference-only Camera Guidance
```

The first production goal is not real-time Pose. It is reliable reference intelligence: scene, background story, lighting, composition, subject intent, emotion, camera position, and a concise director prompt. A model response must never be rendered as an asserted fact without provenance and uncertainty handling.

## 2. Stable app contract

The existing app-local `ReferenceBundle` remains the consumer compatibility target. A Phase 1.5 analyzer must emit these non-empty fields after validation:

```json
{
  "reference_id": "opaque-reference-id",
  "scene": "string",
  "background_story": "string",
  "lighting": "string",
  "composition": "string",
  "subject_intent": "string",
  "emotion": "string",
  "pose_template": "reference-derived textual intent only",
  "camera_position": "string",
  "director_prompt": "string",
  "version": 1
}
```

Compatibility rules:

1. Keep `pose_template` textual and reference-derived; it must not become live keypoints or a real-time Pose claim.
2. Reject incomplete, overlong, malformed, or schema-incompatible provider output before UI rendering.
3. Keep provider metadata, model identifier, analysis timestamp, and confidence/provenance in a separate envelope; do not silently change the user-facing Bundle contract.
4. Never persist raw image bytes, credentials, or a user-media path in the Bundle.
5. Preserve the visible distinction between Demo results and VLM results during the transition.

## 3. Candidate route comparison

| Route | Privacy/deployment fit | Android deployment fit | Expected product role | Main risk | Phase 1.5 recommendation |
| --- | --- | --- | --- | --- | --- |
| Qwen-VL family | Potentially local/private when deployed in an approved controlled environment; exact weights/license and serving stack must be frozen per chosen release. | Not a direct Android in-app dependency under this plan; would require an approved local service or offline producer. | Primary local-first evaluation candidate. | Operational footprint, model/weight licensing, latency, and multilingual photography quality require measurement. | Evaluate first in an offline benchmark; no app integration until approved. |
| InternVL family | Potentially self-hosted, but repository license does not automatically settle individual model-weight or deployment obligations. | Same: not assumed suitable for direct Android inference. | Comparative open-source candidate. | Hardware/serving complexity and model-weight provenance. | Evaluate second against the same locked set and rubric. |
| GPT Vision via API | Requires explicit user consent and an approved network/data-processing path; therefore it is not local-first. | Thin Android client is technically possible only after a separately authorized backend/security design. | Quality/latency benchmark or future opt-in service path. | User-image transmission, account/secret handling, cost, retention policy, and network reliability. | Do not integrate in Phase 1.5 without a separate Owner privacy authorization. |

The Qwen official repository documents a current vision-language family with local-file image examples and links to Qwen2.5-VL-compatible utilities; it does not by itself authorize this product to download or use any specific checkpoint. The InternVL repository is an official source-code baseline, while its own license note says parts and models can have separate terms. OpenAI’s image/vision documentation describes image input support, but an API path is a network/data-governance decision rather than a local implementation. [Qwen official repository](https://github.com/QwenLM/Qwen3-VL), [InternVL official repository](https://github.com/OpenGVLab/InternVL), [OpenAI image and vision guide](https://developers.openai.com/api/docs/guides/images-vision)

## 4. Privacy-first architecture decision

Preferred order of investigation:

1. **Offline producer evaluation:** use only an approved non-private benchmark set outside the Android app. Score structured outputs against a photography rubric.
2. **Local/private deployment feasibility:** freeze model, checksum, license, runtime, hardware, latency budget, failure behavior, and deletion policy before any user-media integration.
3. **App adapter:** pass a narrow, user-consented request to an approved local service or producer; receive only validated Bundle data. Android must not contain provider credentials or a raw-image upload fallback.
4. **Cloud option:** consider only through a separately approved consent, backend, retention, security, cost, and outage design. It is not an implicit fallback.

## 5. Evaluation gate before implementation

| Gate | Required proof | Failure action |
| --- | --- | --- |
| Product quality | Locked photography rubric for scene, background story, lighting, composition, subject intent, emotion, and director usefulness. | Keep Demo; do not ship a misleading VLM label. |
| Contract validity | 100% schema-valid, bounded, safe structured output on the approved evaluation corpus. | Fix provider adapter outside the app boundary. |
| Privacy | Data flow, retention, consent, and deletion behavior approved. | No user-media path. |
| Operations | Known model/license/version/hash, hosted runtime, latency, availability, and rollback plan. | Do not enable the provider. |
| UX honesty | Source/provenance, uncertainty, retry, unavailable, and cancellation states reviewed. | Keep the result labelled Demo/unavailable. |

## 6. Explicit non-goals

This plan does not authorize model downloads, model inference, GPU use, cloud API calls, user-image upload, Pose SDKs, MediaPipe, ML Kit, MoveNet, RTMPose, CameraX changes, UI0 core changes, or iOS work.
