# T16 camera lifecycle, permission and accessibility

## Delivery scope

Remote plan `c2c_4d16`, accepted T15 documentation base `a8b67bcd0f2344c027b65d6ea8ed72cef8a11239`; isolated branch `codex/t16-camera-lifecycle-accessibility-20261002`. No Owner checkout changes. Target: `CAMERA_LIFECYCLE_AND_ACCESSIBILITY_MACHINE_VALIDATED — PHYSICAL_DEVICE_AND_HUMAN_ACCEPTANCE_PENDING`.

T14 remains independently waiting for a published producer PKB1 golden-vector handoff. No Room/schema/migration/reservation/Bundle/T11/Qwen/producer authority change, physical-device operation, signing, release or merge.

## Implementation

- Runtime transitions no longer discard an accepted capture: permission loss, CameraFailed and CameraStopped preserve captureInFlight until its original success/error outcome. Repeated outcomes are ignored after settlement.
- CameraX remains lifecycle-owned. Pause disables new driver operations and invalidates focus/exposure/zoom tickets without forced unbind or capture cancellation. Accepted capture callbacks settle once independently of control generations, including synchronous exception versus asynchronous callback races.
- Current CameraState OPEN is observed through binding identity/generation. User operations synchronously recheck live permission, RESUMED, current OPEN, PreviewView STREAMING, restoration/pending states and both capture guards. Bind success alone cannot enable the shutter. Resume restores confirmed settings on the existing binding after accepted capture settles; restoration may start before STREAMING, readiness waits for every gate.
- The upper chrome now lays out topbar, growing reference card and full guidance sequentially in an intentional scroll region above controls. Long titles are width-bounded, capability buttons wrap, full instruction tails remain reachable, and actionable labels use finite widths with natural wrapping. No font shrinking or silent instruction removal.
- Shutter accessibility identity stays 拍摄; state description conveys readiness/pending/saving. Zoom/exposure sliders expose operation and units with SetProgress behavior. Unsupported capabilities remain hidden; confirmed/drag/pending values stay distinct.
- Host runners enforce exact emulator identity before mutation. Permission PREPARE, denied and recovery use fresh instrumentation processes, no GrantPermissionRule, independent all-flags restoration before/after recovery. Font runner checks actual system1.0/2.0 in portrait/landscape and restores the exact original string, including absence.

## Verification

- Full JVM159/159,0 failure/error/skip; Debug/AndroidTest build and debug/static Release lint PASS,0 errors/17 warnings each. Release lint excludes verifyReleaseSigning and does not qualify signing.
- Final affected API35 matrix88/88 PASS on the final focus-hitbox source; includes all accepted T15 affected regressions plus T16 lifecycle/accessibility. Earlier82/88 and87/88 runs are retained as negative evidence, not final qualification.
- Integration lifecycle: actual stale paused shutter rejection without a new row; background/resume, true Activity recreation, confirmed2x/reference context restoration and subsequent associated capture. The recreation host installs production CameraScreen after MainActivity creation; this is focused screen-host proof, not a new root navigation acceptance. Preview STREAMING is independently measured.
- Accepted driver capture interrupted by actual lifecycle transition settles exactly once, success or error; no saved-success requirement manufactured.
- Final permission run `475fd63732a14a53b489aeece72c2369`: PREPARE PID11766, denied PID11838, recovery PID11945. Original CAMERA granted with the two sensitivity classification flags; grant and every original flag restored and independently compared before and after recovery. PREPARE had completed before revoke; do not claim revoke necessarily killed that already exited process. Earlier successful run `e12ff3b373584c1c937cac1bd22f2d02` is supplemental evidence.
- Final actual font run `7dd99d4272664736b5a5fd1c073e3c38`:1.0/portrait,1.0/landscape,2.0/portrait,2.0/landscape all PASS; original exact font_scale1.0 restored. Checks include synthetic all-capability chrome and actual CameraScreen reference-card action text. Synthetic LocalDensity2 probes are separately labeled; earlier run `abf376e12a224db294c0dd30f1b1cdfa` is supplemental only.
- PKB1 consumer12/12 and Phase1.5 contracts PASS. T11 preflight remains BLOCKED_LEGAL_REVIEW with all acquisition/network/runtime/App/Pipeline authority false.
- Independent production source reviews: lifecycle/capture and chrome/card PASS with no actionable findings after a readiness effect race was corrected.

## Negative evidence retained

Original reducer24 tests had2 genuine failures after permission/runtime loss; fixed focused model/driver36/36 and full JVM159/159. The first accessibility fixture was invalid (empty required guidance) and is not production failure proof. Valid baseline exposed missing slider names, disabled shutter identity and stale paused shutter reservation. The first direct lifecycle driver case used an invalid duplicate rule.setContent host, repaired to Activity.setContent; outcome evidence was rerun.

Strict large-font testing exposed return-button width overflow and actual reference-action width overflow; finite widths and wrapping repaired them. A full hint failed actual landscape text overflow, repaired with finite full-width text. TestLayoutResult text identity is checked for the reference action, rather than relying on merged title semantics. Initial permission denied invocation queried semantics before composition; explicit idle synchronization fixed the test host. Both failed permission and failed font invocations independently restored original state.

Initial complete affected matrix82/88 passed. Six old camera/export/exposure/focus tests waited only for shutter/control existence; stable disabled operation names now exist before actual readiness. Their waits now require enabled semantics, retaining all original capture/export/preview/focus/exposure/cleanup assertions. A missing test isEnabled import caused compile failure; repaired build evidence is retained. A subsequently launched stale-harness run is excluded from final qualification. No unknown-data deletion was used.

The corrected harness then passed87/88 and exposed a genuine tap-focus regression: a weighted full-height scroll modifier intercepted blank preview touches. Its noninteractive weighted wrapper now contains only a natural-height capped scroll region. Short guidance leaves blank preview hittable; overflowing font content still scrolls above controls. The unchanged focus outcome test plus five accessibility tests passed6/6 after the repair; the final whole matrix is rerun.

## Acceptance boundaries

Final Debug APK SHA-256: `56C77D533DCFF5DD66E0971A9AFBE80EED43A64684CA9F60A334DAF0015390EF`.
Final AndroidTest APK SHA-256: `9A66085480184A93A390D8F2A7CA68BEEFEA5FAB0840C6C0D173230761253814`.

Original AVD config.ini SHA-256 remains `11C65542C12B5A1B684F842A675800085B6D0434AA825EC3D96AA2EDBC982671`; BACK-only runtime and persistent data retained. T15 software-FRONT evidence is a baseline reference only, not new T16 physical/lens qualification.

Local machine qualification PASS. Remote exact-source review: PENDING.

Physical-device variance NOT_RUN; sensor/photo usefulness NOT_EVALUATED; human accessibility/TalkBack and photography acceptance NOT_RUN; provider/Pipeline integration, signed release and merge NOT_RUN. Machine semantics/layout checks do not constitute human acceptance.

Raw initial/final logs and APK metadata remain outside Git in the Codex visualizations evidence folder for this task. No private images, databases, model weights, credentials or generated runtime output are committed.
