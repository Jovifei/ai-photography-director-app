# Phase 1 Product Readiness Review — Reference → Director MVP

**Review status:** `CONDITIONALLY_READY_FOR_OWNER_DECISION_PHASE1_5`
**Scope:** Product maturity review only. This document does not authorize a new model, network path, Pose provider, CameraX change, or UI0 design-system change.

## 1. Product conclusion

The MVP demonstrates the intended **Reference → Director** value loop: a photographer can enter the import surface, see a clearly labelled local demo interpretation, receive an actionable director card, and carry that reference guidance into Camera Director.

The loop is complete as an **in-session, local Demo**. It is not yet a production intelligence feature: analysis is fixed demo content, historical references are deliberately not persisted, and no real-time pose capability exists. The real Photo Picker activity-result delivery into an imported-session record has not been proven end-to-end with a user-owned image, so it remains `NOT_VERIFIED`. Therefore Phase 1 is ready for an Owner decision about a tightly scoped Phase 1.5 VLM intelligence discovery/implementation, not a production-release decision and not Phase 2 Pose work.

```text
Choose reference photo
        ↓
Import Reference (system Photo Picker, image-only)
        ↓
Reference Analysis (explicit “Demo Analysis”)
        ↓
Director Card (environment / subject / emotion / camera)
        ↓
Camera Director (Reference Guidance, no live Pose)
```

## 2. Current capability matrix

| Capability | Current status | Product interpretation |
| --- | --- | --- |
| Photo import surface and intent | PASS | Android image-only system Photo Picker; no expanded media-read permission. |
| Real Picker-result → imported session reference E2E | NOT_VERIFIED | Instrumentation separately proves the picker intent and a fake-URI continuation; it does not prove delivery of a user-owned image through the actual activity result. |
| Reference-image analysis | Demo | Fixed local structured result, visibly labelled `Demo Analysis`; not real AI. |
| Background-value analysis | Demo | Demonstrates the story/opportunity format, not a scene-specific inference. |
| Composition analysis | Demo | Demonstrates an actionable composition field, not an actual image judgment. |
| Subject guidance | Demo | Textual reference intent only; not live pose estimation. |
| Real-time Pose | Frozen | Deferred to Phase 2; no provider is selected or integrated. |
| Real AI / VLM | Not integrated | No model, download, cloud API, upload, or inference framework. |
| Nightly Pipeline | Not integrated | The future Producer/Consumer boundary is planned only. |

## 3. Five-screen product review

| Screen | What the MVP proves | Product-quality opportunities before a production launch |
| --- | --- | --- |
| Reference Library | A session-scoped list can reopen a completed Demo analysis without retaining the image. | Explain the session-only lifetime in the empty state; add an intentional “no saved references yet” state and a clear future persistence/privacy decision. |
| Import Reference | The entry point launches the system image-only picker without library permission. | Make the privacy promise and cancellation recovery more prominent; keep a persistent statement beside the demo CTA that the chosen image does not change fixed Demo output; show an import-in-progress state only when a future real analyzer has latency. |
| Reference Analysis | Every output is visibly marked as Demo rather than AI. | Strengthen hierarchy: one photographic thesis first, then supporting fields; separate evidence/uncertainty from advice; define no-result and analysis-failure states for Phase 1.5. |
| Director Card | Analysis is transformed into environment, subject, emotion, and camera actions. | Tighten one primary next action; reduce repeated explanatory copy; add a compact “shoot this first” priority and a safe return-to-analysis path. |
| Camera Director | Reference guidance can reach the existing camera shell without claiming live pose. | Keep the centre frame visually primary; progressively disclose side guidance; define a no-camera-permission/error state and an orientation-safe compact layout. |

### Apple Glass / professional-tool assessment

The existing Apple Glass language is a strong visual starting point: translucent surfaces make the app feel intentional rather than like a settings tool. The professional-camera use case needs firmer information hierarchy than a pure concept shell. Recommended future design review areas, without changing UI0 in this task:

1. **Visual hierarchy:** one photographic decision per surface, with the capture frame as the dominant element in Camera Director.
2. **Information density:** concise imperative guidance at shooting time; retain explanatory copy on the analysis/card screens.
3. **Interaction:** predictable back/cancel/retry behavior for picker, analysis, and camera permission outcomes.
4. **Animation:** use brief state transitions to explain hand-off, never motion that obscures a live preview or an action.
5. **Empty and error states:** explicitly design no session references, picker cancellation, unavailable camera, and future VLM failure/timeout states.

### Interaction-affordance finding

The Home search field and scene-category chips currently expose product-like affordances without a matching Phase 1 action. Before external product testing, either remove them from the active MVP surface or label/disable them clearly as unavailable. The two import paths should likewise be simplified to a single obvious first action. This is a review finding only; no interaction was changed in this task.

## 4. Evidence and acceptance boundary

Existing evidence proves build/unit/instrumentation navigation and the explicit privacy/runtime boundary. The Phase 1 fake-URI instrumentation verifies picker intent and a test-only continuation without reading a gallery. The second Phase 1 test reaches the import surface and then uses the built-in Demo card; it does **not** prove the full real activity-result delivery into an imported-session reference. Evidence also does **not** verify a user-owned photo selection, model accuracy, on-device image understanding, live preview quality, capture, orientation, accessibility, or real-time pose behavior.

The original reviewed candidate remains `2bbe3c42879e5c2d0d43cded8104d2c78edf1e4d`; its evidence remediation lineage is documented in `AA0_PHASE1_REFERENCE_DIRECTOR_MVP_IMPLEMENTATION_REPORT.md` and `reports/PHASE1_GIT_SCOPE_EVIDENCE.md`.

## 5. Readiness decision

| Decision area | Result |
| --- | --- |
| Reference → Director demo product flow | PASS, in-session demo scope |
| Privacy boundary | PASS for implemented scope: no media-read permission, network, model, or upload path |
| Production intelligence | NOT_READY: Demo Analyzer must not be represented as real analysis |
| Real-time pose | FROZEN: Phase 2 only, not an entry criterion for Phase 1.5 |
| Camera Director product evolution | PLANNED in `CAMERA_DIRECTOR_EVOLUTION_PLAN.md` |
| Pipeline contract | PLANNED in `PIPELINE_APP_BUNDLE_CONTRACT.md` |
| Recommended next decision | Owner approval or rejection of the bounded Phase 1.5 VLM plan |

## 6. Out of scope and unchanged

This review made no product-code change and does not introduce MediaPipe, any Pose SDK, ML Kit, MoveNet, model download, GPU work, CameraX low-level changes, UI0 core changes, iOS, or access to private photos.

## 7. Finalization quality gate

Run on the Phase 1 branch during this finalization, with documentation-only changes present:

| Command | Result |
| --- | --- |
| `git diff --check` | PASS — exit 0, no output. |
| `./gradlew.bat assembleDebug` | PASS — `BUILD SUCCESSFUL` (35 tasks up-to-date). |
| `./gradlew.bat testDebugUnitTest` | PASS — `BUILD SUCCESSFUL` (22 tasks up-to-date). The established suite result remains 91 passed, 0 failures/errors. |
| `./gradlew.bat lintDebug` | PASS — `BUILD SUCCESSFUL` (26 tasks; 1 executed, 25 up-to-date). |
| `python scripts/prepush_privacy_audit.py` | PASS — no forbidden private assets, images, model weights, databases, reference clones, or common secrets detected. |

These are build-quality gates for this documentation finalization. They do not replace the separately recorded physical-device evidence and do not turn `REAL_PICKER_E2E_NOT_VERIFIED` into a PASS.
