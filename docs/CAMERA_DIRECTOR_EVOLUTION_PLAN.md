# Camera Director Evolution Plan

**Status:** `PLANNING_ONLY — NO_CAMERA_OR_UI0_CHANGE_AUTHORIZED`

## Product intent

Camera Director should help a photographer turn a reference idea into the next observable shooting action. It must not pretend to be a live pose tracker or an automatic scene judge while Phase 1 is using a reference-derived Demo bundle.

## Current composition

| Zone | Current responsibility | Phase 1 truth boundary |
| --- | --- | --- |
| Left | Environment guidance | Comes from `ReferenceBundle`; not a live environment measurement. |
| Center | Camera preview and immediate capture framing | Must remain the visual primary. No new CameraX behavior is proposed here. |
| Right | Subject guidance | Reference pose/intent language only; no skeleton, keypoints, or live pose claim. |

## Evolution path

| Increment | User value | Design contract | Preconditions |
| --- | --- | --- | --- |
| E0 — current | Carry a reference plan into the camera surface. | Reference Guidance is labelled Demo/no live Pose. | Existing Phase 1 scope. |
| E1 — clearer guidance hierarchy | Make the next best action readable during shooting. | One centre-safe concise cue; side panels remain secondary and collapsible. | UI/product approval; no CameraX change assumed. |
| E2 — composition assistance | Translate the reference composition into non-deceptive framing aids. | Only show an aid when its source and confidence are explicit; never represent a static template as live recognition. | Separate UX and technical approval. |
| E3 — environment overlay | Surface reference-derived light/background reminders. | “Reference suggestion”, not real-time analysis, until a validated analyzer exists. | VLM output contract, privacy review. |
| E4 — action and emotion coaching | Sequence photographer-facing direction: place, pose intent, expression, camera position. | No biometric or live-pose assertion. | Director prompt quality evaluation. |
| E5 — real-time provider | Add live pose only after an independent provider decision and device qualification. | Provider health, latency, privacy, fallback, and disclosure are mandatory. | A separately authorized Phase 2. |

## UX acceptance questions for a future design review

1. Does the center preview remain unobstructed in portrait and landscape?
2. Can the photographer understand the next action in one glance while moving?
3. Is every cue labelled as reference-derived, live, or unavailable?
4. Are permission denial, no preview, orientation change, and failed analysis recoverable without a dead end?
5. Can a user dismiss secondary guidance and return to it predictably?

## Non-goals

No code is proposed by this document. It does not alter `CameraXManager`, camera permissions, live preview/capture behavior, UI0 tokens, the formal Pose Domain, or any Pose provider decision.
