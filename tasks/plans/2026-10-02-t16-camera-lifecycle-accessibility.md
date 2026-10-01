# T16 Camera lifecycle, permission and accessibility hardening

## Authority and scope

Jovi authorizes the continuing local implementation / remote planning and review loop. Remote `c2c_8b15` reviewed T15 code `3d6204887363c586fb8fd227975394d4a8c745d3` as DONE and approved this plan plus concrete refinements. Implementation base includes only the acceptance documentation commit `a8b67bcd0f2344c027b65d6ea8ed72cef8a11239`. Branch: `codex/t16-camera-lifecycle-accessibility-20261002`.

Target: `CAMERA_LIFECYCLE_AND_ACCESSIBILITY_MACHINE_VALIDATED — PHYSICAL_DEVICE_AND_HUMAN_ACCEPTANCE_PENDING`.

Production allowlist: CameraXManager, CameraControlFence, CameraScreen, CameraUiState, CameraDirectorChrome. Add focused JVM/Android tests and host qualification scripts. No Room/schema/reservation/Bundle/T11/Qwen/producer changes, no physical-device operations, no signing or merge. T14 awaits published producer PKB1 golden vectors independently.

## Detailed execution and verification

- [x] Verify clean accepted base and exact origin; obtain remote current-source T15 verdict and T16 refinement.
- [x] M1: Add failing reducer tests: PermissionDenied and CameraFailed preserve accepted capture until original success/error settles exactly once. Runtime transitions must not manufacture a capture outcome.
- [x] M2: Add failing readiness/fence tests. Interactive ready requires current binding, live permission, RESUMED, CameraState OPEN, Preview STREAMING, restoration settled and no pending capture/control. Pause invalidates control tickets/epoch without cancelling accepted capture.
- [x] M3: Minimal manager/screen lifecycle repair. Observe OPEN by current camera identity; observe preview stream. Pause disables controls and invalidates old control callbacks; retain CameraX automatic stop/start ownership, never force-unbind on pause. Restore saved confirmed settings on current foreground binding; restoration may start before STREAMING, but readiness waits for every gate. Live callbacks recheck permission/foreground/readiness and both capture guards.
- [x] M4: Dedicated API35 actual lifecycle tests: background/resume, true Activity recreation, confirmed context/lens/zoom restoration, no spurious reservation and capture after recovery. Lifecycle-interrupted accepted capture settles once even if driver returns error. Fixtures publish exact created IDs; cleanup only exact baseline-bound IDs.
- [x] M5: Add failing large-font/semantics tests for long-title topbar, growing reference card, capability row, landscape instruction visibility, operation/unit slider labels and stable shutter name. Measure actual text overflow and interactive bounds, not only semantics existence.
- [x] M6: Minimal adaptive chrome/card layout repair. Preserve guidance information, use reachable scrolling/wrapping where required, minimum48dp actions, named slider SetProgress and truthful pending/confirmed semantics. Keep original T15 capability/busy assertions.
- [x] M7: Host permission runner requires exact emulator-5580 / T3_API35_20260927 / API35/qemu identity before mutation. PREPARE outputs measured original CAMERA state, runId and PID; host revoke after PREPARE completes; fresh denied instrumentation without GrantPermissionRule handles only deny system dialog and verifies no camera controls. Host finally restores measured original permission, verifies equality independently of instrumentation survival. If originally granted, fresh recovery invocation proves actual bind/readiness. Never pm clear/reset other packages. Logs outside Git.
- [x] M8: Host font runner snapshots exact settings font_scale (including absent value), tests1.0 and2.0 in fresh invocations, portrait and landscape. Outer finally restores original exact setting (delete only if originally absent), verifies it. No global reset; no physical device.
- [x] M9: Independent source review, integrate bounded changes, repair concrete findings.
- [x] M10: New final full JVM, Debug/AndroidTest builds, debug/static Release lint excluding signing verification. Affected API35 T15/T16/P25U/T12/T3/T4/T13 camera regressions; actual permission boundary and font runners; PKB1/Phase1.5 contracts and unchanged blocked T11 preflight. Retain initial failures and final exact artifact hashes outside Git.
- [x] M11: Report evidence as emulator lifecycle, OS permission process boundary and machine font/semantics checks separately. Physical-device, human accessibility/photo usefulness and signed release remain NOT_RUN/NOT_EVALUATED. Privacy audit and diff check before non-force push; create stacked Draft PR; remote exact-source review, repair and next-stage plan.

## Review

Read-only local audits identified runtime readiness/foreground gaps, control callback invalidation, reducer settlement loss and font-dependent layout risks. Remote approved failing tests first and the allowlist. T13/T15 PASS is accepted baseline only, never final T16 qualification.
