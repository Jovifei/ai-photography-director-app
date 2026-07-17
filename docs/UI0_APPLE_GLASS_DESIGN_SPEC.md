# UI0 Apple Glass Product Shell Design Specification

## 1. Status and scope

- Gate: `UI0_ANDROID_APPLE_GLASS_PRODUCT_SHELL`
- Baseline: `3f1aab3a56cac5f3c0b9d8541e5bc9277a5ce181`
- Branch: `feat/ui0-apple-glass-product-shell`
- Platform: Android first, minSdk 24
- AH0 Full Device Gate: `DEFERRED_BY_OWNER`
- AA0: not authorized

UI0 is a runnable product shell around the approved CameraX baseline. It does not add AI, pose recognition, networking, a database, a backend, video, iOS, or Pipeline behavior. Analysis and overlay content are explicitly marked as Demo or 示意.

## 2. Product visual goal

The visual direction is **Quiet Frame / 静默导演**: a calm photography editor that offers one useful action at a time. It is Apple-inspired in restraint, proportion, translucency, and hierarchy, but it does not copy Apple trademarks, SF Symbols, proprietary assets, Dynamic Island, Control Center, or iOS navigation patterns.

The memorable spatial idea mirrors the product promise:

- the left edge belongs to environment guidance;
- the right edge belongs to subject guidance;
- the center stays quiet for framing and capture.

The interface should feel airy and premium rather than decorative. Camera chrome must remain legible over unpredictable preview content without obscuring the scene.

## 3. Design tokens

### Color

| Token | Value | Use |
| --- | --- | --- |
| AppBackground | `#F5F5F7` | Primary page background |
| SurfacePrimary | `#FFFFFF` | Opaque content surface |
| TextPrimary | `#1D1D1F` | Headings and primary content |
| TextSecondary | `#6E6E73` | Supporting content |
| TextTertiary | `#86868B` | Captions and metadata |
| AccentBlue | `#007AFF` | Single primary accent |
| AccentBlueSoft | `#EAF3FF` | Selected and informational surfaces |
| Success | `#34C759` | Positive state |
| Warning | `#FF9F0A` | Caution and needs-adjustment state |
| Error | `#FF3B30` | Error state |
| Divider | `#D2D2D7` | Hairline separation |
| CameraGlassLight | White at 72% | Camera floating surfaces |
| CameraGlassDark | Black at 22% | Stable preview contrast |
| CameraText | White | Camera primary text |
| CameraTextSecondary | White at 76% | Camera supporting text |
| CameraChromeSurface | `#EBFFFFFF` (alpha `235/255`) | Stable top bar, hint, bottom controls and edge handles over any preview |
| CameraChromeText | `#1D1D1F` | Camera chrome primary text and large icon contrast |
| CameraChromeSecondaryText | `#515156` | Camera chrome small labels |
| CameraChromeBorder | `#D2D2D7` | Stable chrome edge definition |
| CameraChromeDisabledText | `#646469` | Disabled ordinary text, including `更多` and `镜头切换 · 禁用` |
| CameraChromeDisabledGraphic | `#6E6E73` | Disabled large graphic only: the 62dp shutter inner circle |

### Runtime camera-chrome contrast contract

`AppColors` is the sole runtime source for the camera-chrome surface, primary text, secondary text, border, Disabled Text, and Disabled Large Graphic tokens. `CameraDirectorChrome` renders those same tokens, and the JVM contrast tests convert the actual Compose `Color` instances through `toChromeRgba()` before compositing and measuring them. There are no test-only duplicate camera-chrome color values.

The surface alpha is the runtime value `235/255` (not an approximated `0.92f`). The test contract composites that actual surface over black, white, and mid-gray Preview frames. Disabled ordinary text is verified at `4.9361:1` / `5.8845:1` / `5.3974:1` (black / white / mid-gray), all `>= 4.5:1`. The disabled large graphic is verified at `4.2540:1` / `5.0713:1` / `4.6516:1`, all `>= 3:1`. Normal primary and secondary text remain verified at `>= 4.5:1` on all three frames.

This is JVM token and math evidence only. Final device visual review, orientation review, 200% font verification, and TalkBack verification remain outstanding.

### Shape and spacing

- Radius: 12dp, 18dp, 24dp, 30dp, and full pill.
- Spacing scale: 4dp, 8dp, 12dp, 16dp, 20dp, 24dp, 32dp.
- Page horizontal padding: 20dp.
- Minimum interactive target: 48dp.
- Camera shutter: 76dp visual and interactive diameter.
- Edge gesture zone: 32dp.
- Guide panel width: 86% of the available width.
- Edge gesture threshold: 56dp inward drag.

### Type

Chinese system typography is used to avoid bundling proprietary fonts. Character comes from weight, line height, tracking, and whitespace rather than a downloaded typeface.

- Hero: 28/34sp, semibold.
- Page title: 22/28sp, semibold.
- Section title: 17/24sp, semibold.
- Body: 15/22sp.
- Label: 13/18sp, medium.
- Caption: 12/16sp.

## 4. Glass construction and degradation

UI0 does not implement backdrop blur. The glass appearance is built from:

- translucent light or dark surfaces;
- a subtle vertical highlight gradient;
- a 1dp low-contrast border;
- soft elevation;
- generous radius and padding;
- stable dark scrims behind camera text.

This is the complete API 24 behavior, not a broken fallback. A real backdrop blur may only be considered later after performance and device testing. It must never be required for information hierarchy or accessibility.

## 5. Component system

The reusable component layer contains:

- `GlassSurface`
- `GlassPill`
- `GlassTopBar`
- `GlassBottomBar`
- `GlassHintCard`
- `AppSegmentedControl`
- `ReferencePhotoCard`
- `SceneCategoryChip`
- `AnalysisSection`
- `EmptyState`
- `PrimaryActionButton`
- `SecondaryActionButton`
- `CameraShutterButton`
- `EdgeGuideHandle`
- `AppIconButton`
- `CameraPreviewPlaceholder`

Light pages and Camera Preview use distinct glass palettes. A visual pill must not imply interactivity unless it exposes an actual click action. Every important action has a content description and at least a 48dp target.

## 6. Page model

### Inspiration library

The default destination. It presents the product promise, a search appearance, recent Demo content, an honest empty state for real user references, eight scene categories, import, and direct camera entry. Demo cards always carry a visible `示例` or `Demo` label.

### Import reference

Uses the system Photo Picker. The app receives only the URI selected by the user, keeps it in the current session, decodes a sampled local thumbnail, and does not request broad storage permission, upload, copy to a public directory, or read unselected media. Empty, selected, and decode-failure states are distinct.

### Analysis detail

Shows the selected local thumbnail when available and a fixed Demo template otherwise. It must state `示例分析，尚未连接 AI` near the top and must not present generated text as a real model result.

### Camera director

The runtime background remains the real CameraX `PreviewView`; the shutter remains real `ImageCapture`; `ImageAnalysis` remains the approved empty KEEP_ONLY_LATEST pipeline. UI0 adds only chrome, reducer-owned grid/overlay/panel/capture state, one guidance card, guide panels, and navigation.

The current camera hint is data-injected rather than hardcoded:

`DemoContentRepository → ShootingPlan.guidance → selectHighestPriorityGuidance → CameraUiState.currentGuidance → CameraDirectorChrome`

For the supplied Demo plan, the visible first guidance is `确认人物脚下安全 · Demo`. Empty guidance lists show an honest no-guidance state.

The lens-switch appearance is disabled because the approved `CameraXManager` does not implement lens switching. Overlay modes are labeled `示意` and perform no person recognition.

### Environment panel

Enters from the left explicit button or a 32dp left-edge inward gesture. The drag threshold is 56dp converted through `LocalDensity` at runtime, so it is 56/112/168px at 1x/2x/3x density. It covers about 86% of the width and explains scene, background value, story, position, camera height, composition, and Plan B.

### Subject panel

Enters from the right explicit button or a 32dp right-edge inward gesture. It covers about 86% of the width and describes head, shoulders, hands, torso, legs, weight, emotion, Plan B, and non-shaming match states: 接近、需要调整、尚未判断.

Only one panel may be open. Back closes the panel before leaving the camera.

## 7. Camera overlay rules

- The center preview never becomes a horizontal navigation surface.
- Gesture enhancement is restricted to the two 32dp edge zones.
- An edge panel opens only after a 56dp inward horizontal drag; wrong-direction, small, and substantial vertical drags do not open it.
- Explicit environment and subject buttons remain the accessible primary actions.
- Only one guidance item is visible at a time.
- Guidance is selected by priority: safety, in-frame, framing, body, hand, head/gaze, emotion.
- Grid and Demo overlays never block shutter input.
- Top bar, current guidance, bottom controls, and explicit edge handles use the stable `CameraChromeSurface` runtime token (`#EBFFFFFF`, alpha `235/255`) with dark runtime text tokens; they do not depend on preview brightness or shadow for readability.
- Ordinary disabled labels use `CameraChromeDisabledText` and are verified at >= 4.5:1 against the surface composited over pure black, pure white, and mid-gray Preview. The disabled 62dp shutter inner circle uses `CameraChromeDisabledGraphic` and is verified at >= 3:1 on those same frames.
- Selected overlay modes add the text `已选`; Demo, disabled, and selected states never rely on color alone.
- No runtime placeholder may replace CameraX.

Back closes a reducer-owned open panel first. Camera return routing preserves source: Home → Camera → Home, and Analysis → Camera → Analysis.

## 8. Accessibility

- All interactive elements are at least 48dp; the shutter is 76dp.
- Core camera controls have explicit Chinese content descriptions.
- Selected segments expose selected semantics and a tab role.
- Demo, 示意, disabled, and error states use text rather than color alone.
- Camera text is placed on a stable scrim to maintain contrast.
- Panels are vertically scrollable for large fonts and landscape.
- TalkBack order follows: back, title/reference, more, guidance, environment/subject, overlay mode, capture controls.
- The edge gesture handle is not the only means of opening a panel.
- Private URI strings and filenames are never used as accessibility labels.

## 9. Demo and real capability boundary

| Capability | UI0 status |
| --- | --- |
| CameraX Preview | Real |
| ImageCapture to app cache | Real |
| ImageAnalysis pipeline | Real but empty |
| Camera permission lifecycle | Real, inherited from AH0 baseline |
| System Photo Picker | Real, local selection only |
| Selected thumbnail | Real local decode, session-only |
| Reference analysis | Demo template |
| Skeleton/outline/reference overlay | Visual Demo only |
| Environment and subject guidance | Demo copy |
| Lens switching | Disabled appearance |
| AI, pose, networking, upload | Not connected |

## 10. Compose Preview policy

Preview uses `CameraPreviewPlaceholder`, a static abstract gradient clearly labeled `设计预览 / 示意`. It must never initialize CameraX or appear in the runtime camera path. Preview coverage includes the component gallery, home, import empty/selected, analysis, camera chrome, both guide panels, and permission state.

## 11. Stitch handoff

Future Stitch designs may calibrate spacing, hierarchy, surface opacity, icon geometry, and motion. They must map onto the existing tokens and components, preserve the product's left/environment–center/capture–right/subject model, and keep all Demo/real boundaries visible. Stitch must not replace CameraX with a mock, add unapproved AI, broaden storage access, or introduce real-time blur without a separate performance decision.
