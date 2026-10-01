# T15 manual CameraX capability controls

Date: 2026-10-02 (Asia/Shanghai)
Base: accepted T13 PR #18 head `58119b8541a49e27eedff2497c306e9212c3d654`
Branch: `codex/t15-camera-capability-controls-20261002`
Remote plan and refinements: `c2c_8b15`, original Ai相机 chat.

## Result

`CAMERA_CAPABILITY_CONTROLS_MACHINE_VALIDATED — DEVICE_VARIANCE_ACCEPTANCE_PENDING`

Manual zoom is offered only for the currently bound camera's finite, positive, non-fixed range. Invalid/out-of-range requests reject; confirmed display updates only through the current camera and independent zoom generation/sequence fence. Lens availability comes from CameraX selectors; unsupported lens switching is hidden. A switch failure attempts the prior lens binding and restores its confirmed zoom/exposure, while double failure stays unavailable with retry.

The screen saves only confirmed lens/zoom keyed by project/reference. Successful lens switches use the new lens's actual default zoom. Same-lens Compose state restoration and reference/gallery return recreate the manager and restore confirmed zoom against fresh capability. Unavailable saved lenses choose available BACK then FRONT with an explicit notice. Pending controls, futures and camera objects are not saved; CameraUiSnapshot v2 stays unchanged.

Before rebinding, controls become noncapturable and pending focus/exposure/zoom is cleared. Handlers recheck both UI and live durable capture state, including the reserve-to-driver gap. Binding or zoom pending disables shutter and hardware controls. Capture callbacks remain independent of control-generation fencing and still settle after shutdown. Project/reference IDs, capture destination and original private reference JPEGs remain unchanged.

Landscape uses a low-height horizontal control strip at all landscape heights. The reference card, guidance hint and controls have explicit non-overlap tests; active actions retain at least48dp height. Full reference guidance remains available in existing panels.

## Verification and evidence boundaries

- JVM:155/155,0 failures/errors/skips. This includes8 new pure capability/fence/fallback/rollback/busy tests.
- Debug and AndroidTest builds PASS. Debug/static Release lint:0 errors,17 warnings each. Release lint excludes `verifyReleaseSigning`; signed Release qualification NOT_RUN.
- Stage A, original `T3_API35_20260927` / `emulator-5580`:81/81 final instrumentation,0 reported skips. Includes7 new T15 tests and74 existing T13/Bundle/T12/navigation/capture/export/CameraX Activity-recreation regressions.
- Original AVD actual capability:BACK, zoom1..10; FRONT unavailable. This run proves actual back zoom, unavailable-front hiding, saveable rebind/reference-gallery return, production capture association/original preservation, synchronous capture switch guard, callback settlement after manager shutdown, and real portrait/landscape layout. It is not FRONT runtime proof.
- Separate `T15_FRONT_CAMERA_EMULATED_RUNTIME_QUALIFICATION`:7/7 PASS after same-AVD software-front runtime override. Actual measured FRONT range1..12.931958/default1; BACK2x was confirmed, successful FRONT binding used actual default1, then FRONT manual zoom/rebind/capture preserved association and original. No webcam or physical phone was used. This is Android Emulator synthetic-frame evidence, not physical lens quality/orientation proof.
- Bind failure/rollback and unavailable/empty lens fallback are injected pure-model tests; a real hardware bind failure was not manufactured. Zoom restoration range and busy gates are also independently tested. Capture after shutdown actually delivered one ERROR callback, so the original operation settled; it is not a saved-photo success claim.
- AVD was restored to its original launch without front override. Persistent data was not wiped; `config.ini` SHA-256 remained `11C65542C12B5A1B684F842A675800085B6D0434AA825EC3D96AA2EDBC982671`; restored camera service reports1 device. Fixture rows/files were created and deleted by exact test identities; no unknown-data cleanup was used.
- Independent final source review PASS_NO_ACTIONABLE_FINDINGS. Privacy and staged diff gates are required before each push. Room/capture schemas, Bundle contracts, T11 authority and existing capture reservation protocol remain unchanged.

Final Debug APK SHA-256:`4E3FF0912543DE3DE18A79CCCFFAC6A6E5ED05F838812786113DC7865472DEAF`.
Final AndroidTest APK SHA-256:`7C0C8FCB72FAF0C00E35DDEF7B8831CACE8C94ED9EC5A988633FE1E26C59CA1B`.

## Failures retained and repaired

The first compile failed on a missing test `setContent` import. The next lint run rejected a StateFlow.value read used during composition; rendering now uses the parent's subscribed capture state while event handlers deliberately recheck live state. These gates were fixed without suppressions.

Initial7-test runtime had3 failures: actual411dp landscape bypassed the inherited `<400dp` compact condition and overlapped surfaces; the layout now uses landscape controls for every wide viewport. The capture fixture used in-memory reference identities, while production CaptureRepository correctly detached identities absent from its production reference factory. The fixture now uses one unique persisted synthetic project/reference, publishes created IDs and exact-cleans its baseline-bound capture and project. Any generated unassigned result from the first run was retained because its exact ID had not been recorded; no guessed/latest/unassigned-row deletion was attempted. A second7-test run had6 PASS and caught a40dp return button; an explicit48dp minimum fixed it while preserving the strict assertion. Final81 and auxiliary7 runs passed.

Raw logs remain outside Git under Codex visualizations `t15-camera-capabilities-20261002`. No images, databases, model weights, signing secrets, AVD configuration or private device output were committed.

## Continuation

Delivery is Draft PR #19 stacked on T13. Remote review `c2c_8b15` iteration1 explicitly re-read clean current-source HEAD `3d6204887363c586fb8fd227975394d4a8c745d3`, including capability, fence, manager, screen, chrome and tests, and returned DONE / PASS with no actionable finding or source-read limitation. Review covered live capture guards, late zoom callbacks, binding restoration, actual new-lens defaults, rollback restoration and capture settlement after disposal. T16 lifecycle/permission/accessibility hardening is independently executable next; baseline T15 evidence does not qualify T16. T14 awaits a stable producer PKB1 golden-vector handoff. Physical-device variance, real-photo usefulness, signing/release/merge and human acceptance remain separate pending evidence.
