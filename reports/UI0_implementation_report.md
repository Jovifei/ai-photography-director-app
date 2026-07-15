# UI0 Android Apple Glass Product Shell — Implementation Report

## Delivery identity

- Task: `UI0_ANDROID_APPLE_GLASS_PRODUCT_SHELL`
- Approved baseline: `3f1aab3a56cac5f3c0b9d8541e5bc9277a5ce181`
- Branch: `feat/ui0-apple-glass-product-shell`
- Worktree: isolated UI0 worktree outside the main repository checkout
- Platform: Android, minSdk 24
- Install/runtime dependency changes: none

## Stage boundary

- `AH0_FULL_DEVICE_GATE = DEFERRED_BY_OWNER`
- This report does not claim that AH0 passed.
- AA0 is not authorized and was not started.
- No MediaPipe, ML Kit, MoveNet, Pose, or other model was downloaded or connected.
- No real AI, network, backend, database, video, Pipeline, shared-contract, or iOS work was added.

## Implementation summary

UI0 changes the default entry point from the bare AH0 camera screen to a simple state-driven Compose product shell. The shell provides an inspiration library, system Photo Picker import, explicit Demo analysis, and navigation into the real CameraX runtime with a restrained translucent director chrome.

The visual concept is **Quiet Frame / 静默导演**: environment guidance owns the left edge, subject guidance owns the right edge, and capture remains the quiet center. It uses translucent surfaces, borders, highlight gradients, soft elevation, and large radii; no runtime backdrop blur or Apple proprietary asset is used.

## Six-page completion

| Surface | Status | Evidence boundary |
| --- | --- | --- |
| Inspiration library | Complete for UI0 | Header, search appearance, recent Demo cards, eight scene categories, user-reference empty state, import and camera actions |
| Import reference | Complete for UI0 | `PickVisualMedia(ImageOnly)`, session-only URI, sampled platform bitmap decode, empty/loading/selected/failure/clear states, privacy copy |
| Reference analysis | Complete for UI0 | Top reference image, seven required analysis modules, onsite plan, explicit `示例分析，尚未连接 AI` |
| Camera director | Complete for UI0 | Real `PreviewView`, real cache `ImageCapture`, empty `ImageAnalysis`, one hint, grid, Demo overlay modes, chrome and capture count |
| Environment panel | Complete for UI0 | 32dp edge gesture plus explicit button, 86% panel, scene/value/story/position/camera/composition/Plan B |
| Subject panel | Complete for UI0 | 32dp edge gesture plus explicit button, overlay selector, full body guidance, emotion, Plan B, non-shaming match labels |

## Architecture and capability boundary

- Navigation uses `AppDestination`, `rememberSaveable`, and `BackHandler`; no navigation dependency was added.
- UI state is immutable and reducer-driven. `GuidePanel` is a single enum value, so two panels cannot be open simultaneously.
- Guidance selection is a pure function ordered `SAFETY → FRAMING → POSITION → BODY → HAND → HEAD → EMOTION`.
- The Photo Picker receives only the URI explicitly chosen by the user. The app does not request broad storage access, persist URI permission, upload, or copy media to a public directory.
- Demo analysis data is deterministic, bundled text and is visibly labeled Demo/示例.
- Skeleton, outline, and reference overlay modes are visual placeholders marked `示意`; they perform no recognition.
- Lens switching is shown as unavailable because the frozen CameraX manager does not implement it.

## CameraX preservation

`CameraXManager.kt`, CameraX dependencies, Android SDK levels, manifest permissions, use cases, analyzer backpressure, and cache output location are unchanged from the approved baseline.

The UI-layer refactor preserves these invariants:

- CameraX is not initialized without camera permission.
- `CameraContent` exists only with permission and recreates its manager after false-to-true permission changes.
- permission is rechecked on `ON_RESUME`.
- every `ImageProxy` is closed.
- `shutdown()` is called on disposal.
- the shutter calls real `ImageCapture` and writes under `cacheDir/captures`.

## Design system and previews

Central tokens cover color, typography, shape, spacing, touch targets, camera glass, gesture width, panel width, and motion. Reusable components include GlassSurface/Pill/TopBar/BottomBar/HintCard, AppSegmentedControl, photo/category/analysis/empty-state content, primary/secondary/icon actions, CameraShutterButton, EdgeGuideHandle, and CameraPreviewPlaceholder.

Compose tooling includes previews for the design-system gallery, home, import empty and selected states, analysis, camera placeholder chrome, environment panel, subject panel, and permission state. Preview code never initializes CameraX.

## Verification evidence

| Gate | Result |
| --- | --- |
| `gradlew.bat clean` | exit 0 after stopping one stale Gradle/Lint file lock; the first attempt exited 1 on locked generated lint-cache JARs |
| `gradlew.bat assembleDebug` | exit 0 |
| `gradlew.bat testDebugUnitTest` | exit 0; 28 tests, 0 failures, 0 errors, 0 skipped |
| `gradlew.bat lintDebug` | exit 0; 0 errors, 12 warnings |
| Lint warning scope | Existing build/manifest maintenance notices: target/dependency updates, data extraction rules, and missing application icon; no dependency or manifest widening was authorized for UI0 |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk`, 11,715,411 bytes |
| APK SHA-256 | `BF2AB4834C7C79A17BC003A2941B8688AD19EB6415B2ABCD8AA65F1ADEF05965` |
| Privacy audit | exit 0; no forbidden private assets, unapproved images, model weights, databases, reference clones, or common secrets detected |
| `git diff --check` | exit 0 before commit; no whitespace errors |

JVM coverage includes exact enum contracts, every adjacent priority ordering, stable tie behavior, optional panel filtering, Demo repository consistency and required analysis modules, permission/runtime transitions, overlay constraints, single-panel state, capture state, stale decode rejection, and snapshot restoration.

## Changed area

Before this report, the implementation changed 35 paths: 34 Kotlin paths plus the design specification, including replacement of the placeholder arithmetic test. This report is the 36th delivery path. Build outputs, APK, logs, local.properties, screenshots, private images, and device identifiers remain untracked and uncommitted.

## Current limitations and review risks

- UI0 has compile, JVM, lint, and APK evidence but no instrumentation, screenshot, accessibility-device, or independent visual review.
- AH0 device stability and second-device work remain deferred, so the branch must not be interpreted as a device-gate pass.
- Overlay/guidance content is static Demo content, not frame analysis.
- Lens switching is intentionally unavailable.
- The platform BitmapFactory path performs bounded sampling without adding an EXIF library; reviewers should check representative rotated picker images before product release.
- Translucent styling intentionally avoids real backdrop blur; final opacity and contrast need device review over varied scenes.
- The app has no production icon/data-extraction declaration yet; these are existing lint warnings and are outside this UI0 change boundary.

## Independent reviewer checklist

1. Confirm the branch descends from the exact approved baseline and does not modify main.
2. Confirm `CameraXManager.kt`, Gradle dependencies/SDK levels, manifest permissions, and capture path are unchanged.
3. Inspect all six surfaces and nine preview groups at phone and landscape dimensions.
4. Confirm every analysis, overlay, and guidance result is visibly Demo/示意 and cannot be mistaken for AI output.
5. Verify Photo Picker cancellation, clear, decode failure, and a rotated-image case without broad storage permission.
6. Verify permission deny/allow/settings-return behavior and real Preview/ImageCapture on a device.
7. Confirm only 32dp edge zones capture horizontal drags and central preview gestures remain untouched.
8. Check 48dp targets, 76dp shutter, TalkBack order, large font panel scrolling, and contrast over bright/dark scenes.
9. Re-run clean, assemble, JVM tests, lint, privacy audit, and `git diff --check` from a clean checkout.
10. Do not merge until Owner approval and independent review are both recorded.
