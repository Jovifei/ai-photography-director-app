# UI0 Android Apple Glass Product Shell — Implementation Report

## Delivery identity

- Task: `UI0_ANDROID_APPLE_GLASS_PRODUCT_SHELL`
- Approved baseline: `3f1aab3a56cac5f3c0b9d8541e5bc9277a5ce181`
- Branch: `feat/ui0-apple-glass-product-shell`
- Worktree: isolated UI0 worktree outside the main repository checkout
- Platform: Android, minSdk 24
- Install/runtime dependency changes: none

## Reviewer remediation: `REQUEST_CHANGES_UI0`

The original UI0 candidate received four blocking findings. This remediation keeps the approved CameraX baseline and addresses the findings in the runtime path, rather than only changing tests or documentation.

| Finding | Closure |
| --- | --- |
| B1: hardcoded camera hint | App injects the Demo plan guidance into CameraScreen; a pure mapper calls `selectHighestPriorityGuidance`; reducer state owns the selected item; Chrome renders that item. The supplied plan displays `确认人物脚下安全 · Demo`. Empty input renders an honest no-guidance state. |
| B2: pixel gesture threshold | Runtime uses `with(LocalDensity.current) { 56.dp.toPx() }` in the two 32dp edge zones. Shared pure helpers and tests prove 56/112/168px at density 1/2/3 and reject small, wrong-direction, and vertical drags. |
| B3: unstable camera contrast | Top bar, hint, bottom controls, and explicit edge handles now use named 92% light CameraChrome tokens and dark text. Pure sRGB compositing tests verify normal/small text >=4.5:1 and large icon >=3:1 over pure white and pure black preview backgrounds. |
| B4: reducer not used at runtime | CameraScreen owns one saveable CameraUiState and dispatches CameraUiEvent. Overlay, panel, grid, capture state/count, and injected guidance flow through the same reducer used by JVM tests. CameraX objects, Context, PreviewView, Uri, and permission API state remain outside the reducer. |

Related non-blocking remediation:

- NB1 closed: camera return source is saved; Home returns to Home, Analysis returns to Analysis. A panel consumes first Back through `ClosePanel` before navigation.
- NB3 closed: `更多` is explicitly disabled with `暂未开放` semantics; `参考图 · Demo` is a non-clickable status chip, not a false button.

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
| `gradlew.bat clean` | remediation rerun exit 0 |
| `gradlew.bat assembleDebug` | exit 0 |
| `gradlew.bat testDebugUnitTest` | remediation rerun exit 0; 38 tests, 0 failures, 0 errors, 0 skipped |
| `gradlew.bat lintDebug` | exit 0; 0 errors, 12 warnings |
| Lint warning scope | Existing build/manifest maintenance notices: target/dependency updates, data extraction rules, and missing application icon; no dependency or manifest widening was authorized for UI0 |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk`, 11,715,411 bytes |
| APK SHA-256 | `E7602CAE3CDAF70DA3A05C625125E35397305CEA72768268DE84DB163880E27B` (this remediation build only; not a reproducibility gate) |
| Privacy audit | remediation rerun exit 0; no forbidden private assets, unapproved images, model weights, databases, reference clones, or common secrets detected |
| `git diff --check` | remediation comparison exit 0; no whitespace errors |

JVM coverage includes exact enum contracts, every adjacent priority ordering, stable tie behavior, optional panel filtering, Demo repository consistency and required analysis modules, permission/runtime transitions, overlay constraints, reducer-owned single-panel/grid/capture state, stale decode rejection, snapshot restoration, injected safety guidance, empty guidance safety, density-aware edge thresholds, directional/vertical gesture rejection, navigation return routing, and white/black preview contrast compositing.

## Remediation changed area

The remediation is restricted to Camera Director runtime binding, Chrome readability, App-level return routing, focused pure Kotlin utilities/tests, and the two UI0 documents. Build outputs, APK, logs, local.properties, screenshots, private images, and device identifiers remain untracked and uncommitted.

## Current limitations and review risks

- UI0 has compile, JVM, lint, and APK evidence but no instrumentation, screenshot, accessibility-device, or independent visual review.
- AH0 device stability and second-device work remain deferred, so the branch must not be interpreted as a device-gate pass.
- Overlay/guidance content is static Demo content, not frame analysis.
- Lens switching is intentionally unavailable.
- NB2 remains deferred: the platform BitmapFactory path performs bounded sampling without adding an EXIF library; reviewers should check representative rotated picker images before product release.
- Translucent styling intentionally avoids real backdrop blur; final opacity and contrast need device review over varied scenes.
- The app has no production icon/data-extraction declaration yet; these are existing lint warnings and are outside this UI0 change boundary.
- NB4 remains deferred: Home search and scene chips are visual shell controls without product behavior.
- NB5 remains deferred: system-bar Insets, 200% font sizing, and landscape require dedicated device validation.
- NB7 remains deferred: a real Picker race integration test is outside this JVM-only remediation scope.
- Full device visual review and TalkBack verification, a production app icon, and dependency upgrades remain deferred by scope.
- Debug APK SHA-256 is reported only as this local build output identifier; it is not claimed to be a cross-machine or time-independent reproducible-build gate.

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
