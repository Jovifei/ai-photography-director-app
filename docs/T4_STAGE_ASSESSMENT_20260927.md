# T4 stage assessment — 2026-09-27

This is a scoped assessment for the T4 implementation branch. It does not replace the separate canonical-roadmap cleanup in progress.

## Product objective and current boundary

Android first: select and organize up to 20 project references, preserve per-photo provenance and state, use only qualified READY content for real guidance and READY-only project summary, enter Camera Director or clearly labelled direct capture, keep captured originals in the app-private capture library, and export an explicit copy through the system contract. Editorial advice, a Provider result, and a Pipeline knowledge release are different kinds of evidence.

T3 `codex/t3-reference-camera-controls@14a56f49e1220eb8139bf7280c747124118fdb21` has API 35 emulator qualification and independent review PASS; Draft PR #7 remains stacked on Draft PR #6. It supplies reference comparison, tap focus and bounded exposure, along with earlier capture/export recovery. T4 works from this exact commit. The older P25U REQUEST_CHANGES was for an earlier candidate and was remediated. `main` and the older Obsidian project notes are historical context, not the current implementation base.

The current independent gates remain: real photos and Qwen/Private LAN; human review of 20 editorial items and rights/privacy; real Pipeline producer through the versioned Bundle Contract; physical Android device and five-person pilot; DPAPI signing identity; Owner merge and public release decisions. T4 synthetic/API35 results do not satisfy any of those gates.

## Plan audit

- `docs/00_ANDROID_FIRST_EXECUTION_PLAN.md` and early AA0/Pose spike documents start at bootstrap gates and are obsolete as next-step instructions. The project no longer treats Pose as an Android-first blocker.
- `docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md` provides useful T1–T5 intent, but T2/T3 are implemented and its ordering must be read against current Git. Its T4 all-project library/search/filter/project naming acceptance remains unfinished at the exact T3 base.
- `docs/P20_PHOTOGRAPHY_PROJECT_EXECUTION_SPEC.md` contains durable provenance/READY-only semantics but marks its own old execution lines historical. The old `tasks/todo.md` and Obsidian overview still describe P21/P22/P24 as future and are not current status receipts.
- `docs/handoff/T3_STAGE_STATE_20260927.json` plus the exact Git ref determine the present T3 evidence boundary. The separate roadmap cleanup proposal must not be folded into this T4 code branch.
- The actionable stage plan is `tasks/plans/2026-09-27-t4-offline-product-flow.md`, revised after local exact-T3 code audit and remote ChatGPT planning task `c2c_a91f`.

## Concrete T4 gaps seen at the base

1. Home has no all-project reference library entry. `ReferenceLibraryScreen` is effectively reached through capture selection and does not search/filter actual records or show owning project navigation.
2. The root already declares `searchQuery`, but it was not used. Existing pure search tests did not prove product wiring.
3. Home silently creates `拍摄项目 N`; a deleted project can make this name repeat. Repository persistence has a title column but no rename mutation.
4. An opened private reference is a UI snapshot; live status changes may not refresh the displayed guidance until reopened. Test the transition before broadening its fix.
5. No T4-specific API35 end-to-end qualifier existed. T3 camera and P25U capture/export tests cover their earlier scopes, not the new library and naming route.
6. The dedicated API35 emulator exposed a compact-landscape Camera hint/shutter overlap. T3's former assertion assumed vertical order, although the reference card is correctly to the left of the shutter in landscape. T4 moves the hint into the available top space and tests rectangle non-overlap; this is a scoped responsive fix, not a CameraX dependency change.

## Official-source technology review

| Candidate | What to borrow or test later | T4 decision |
| --- | --- | --- |
| [CameraX releases](https://developer.android.com/jetpack/androidx/releases/camera), [Android camera samples](https://github.com/android/camera-samples) | Current project uses CameraX 1.4.0 with `PreviewView`; the official 1.6.2 release and Compose-focused samples offer viewfinder and lifecycle patterns. Verify sample file licenses before reuse. | Keep current dependency. A separate ADR should compare existing PreviewView, version upgrade retaining PreviewView, and [CameraXViewfinder](https://developer.android.com/reference/kotlin/androidx/camera/compose/CameraXViewfinder.composable) on two physical devices. |
| [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), [LiteRT samples](https://github.com/google-ai-edge/litert-samples), [AI Edge Gallery](https://github.com/google-ai-edge/gallery) | Apache-2.0 code and on-device vision/model-management examples; Gallery's Android 12+ requirement does not cover the project's Android 11 primary device. Model weights and runtime privacy need separate review. | Research only; no T4 model/runtime dependency. |
| [ExecuTorch](https://github.com/pytorch/executorch) | BSD-licensed Android/XNNPACK path; [Java/Kotlin APIs are experimental](https://github.com/pytorch/executorch/blob/main/docs/source/using-executorch-android.md). | Future device/runtime ADR only. |
| [MMPose](https://github.com/open-mmlab/mmpose), [MediaPipe](https://github.com/google-ai-edge/mediapipe), [ML Kit Pose](https://developers.google.com/ml-kit/vision/pose-detection/android) | MMPose deployment is substantial; MediaPipe is open source but the project's target-device CPU hang remains unresolved; ML Kit Pose remains beta and is an SDK rather than an open-source replacement. | Pose remains a separate future Gate. |

No reviewed upstream project supplies this app's full reference-provenance, offline guidance, capture and export contract as a drop-in replacement. The current product sequence is executable once tied to exact Git and Gate evidence; the outdated entry documents need navigational correction in the separate roadmap task.
