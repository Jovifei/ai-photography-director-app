# T15 manual CameraX capability controls

Remote PLAN and policy refinement: `c2c_8b15`, iteration 0, original Ai相机 chat. Base: PR #18 head `58119b8541a49e27eedff2497c306e9212c3d654`. Branch: `codex/t15-camera-capability-controls-20261002`. T14 awaits an explicit producer PKB1 golden-vector handoff; T11 remains independent.

## Specification

Use installed CameraX 1.4.0 APIs and existing manager/use-case ownership. Add pure lens/zoom capability descriptors, discover BACK/FRONT with hasCamera, and expose zoom only for finite positive non-fixed actual ranges. Manual slider only; no pinch conflict with tap focus. Out-of-range/non-finite zoom requests reject. Add independent zoom sequence to the existing generation+camera-identity fence; only a successful future and current camera can confirm the displayed ratio.

Persist confirmed lens and zoom in separate saveable fields keyed by project/reference; existing CameraUiSnapshot v2 stays fixed. Successful lens switch uses new lens actual default zoom. Failed switch attempts prior lens rollback and reapplies its confirmed zoom; rollback failure remains FAILED with retry. Same-lens recreation/reference-gallery return restores confirmed zoom clamped to newly measured capability. New context prefers BACK and clamped 1x. Unavailable saved lens prefers available BACK then FRONT with an explicit fallback notice; empty lens set remains FAILED. Never persist pending state, capabilities or hardware objects.

Before rebind, enter non-capturable STARTING synchronously and clear old pending focus/exposure/zoom state. Rebind only owned use cases. Shutter, switch and zoom handlers recheck both UI captureInFlight and current durable library capturing state, plus pending zoom/bind. Switch while capture pending is rejected. Capture callbacks still settle the original reservation after disposal; never generation-filter or delete the reserved output as a camera-control callback.

Project/reference/guidance and capture destination stay fixed across all controls. Query new lens exposure range/step and restore only after fresh validation. Capability absence hides decorative controls. Portrait/landscape touch targets must remain usable and avoid the reference/hint/shutter bounds.

## Milestones

- [x] Inspect CameraX manager/fence/screen/chrome/capture callback paths; remote detailed plan and concrete restoration policy approved.
- [x] Reuse accepted T13 source-identical baseline evidence (JVM147, API35 73, builds/static lint); this is not T15 final PASS.
- [x] Add CameraCapability model, lens discovery, bounded zoom, zoom fence and safe rollback driver.
- [x] Add manual chrome controls with capability-conditioned rendering and independent shutter gate.
- [x] Integrate saveable confirmed state, synchronous busy/rebind guards, fallback/retry and fresh exposure restoration in CameraScreen.
- [x] Unit tests: finite/range/fixed zoom, independent generation, lens availability/rollback, restoration and all busy gates; JVM155/155.
- [x] Dedicated API35 tests: actual BACK/zoom and unavailable FRONT, separately emulated FRONT/default zoom, capture association/original settlement, Compose state restoration/reference-gallery rebind and layout. Bind failure/rollback is pure injected-model evidence.
- [x] JVM155/155, builds/static lint, primary API35 81/81 and separate FRONT auxiliary7/7; final independent review PASS. Contract/privacy checks complete before push.
- [x] Non-force push stacked Draft PR #19 on T13; remote current-source/evidence review DONE and next unblocked T16 plan received.

## Acceptance

`CAMERA_CAPABILITY_CONTROLS_MACHINE_VALIDATED — DEVICE_VARIANCE_ACCEPTANCE_PENDING`. Report actual emulator capability limitations; injected/model branches are synthetic proof, not physical device or camera quality evidence. No schema/Bundle/producer/Qwen/network/Pose/signing/merge/human acceptance.

## Qualification refinements

The initial AVD reports BACK only, zoom1..10. Remote approved Stage A normal capability qualification followed, if needed, by a separately labeled `T15_FRONT_CAMERA_EMULATED_RUNTIME_QUALIFICATION` on the same AVD with `-camera-front emulated`, no webcam/wipe/config-file edit. Preserve data and measure actual FRONT after restart; never label the BACK-only run as FRONT runtime PASS. Pure injected bind failure covers rollback; hardware behavior remains emulator evidence.

Initial runtime failures exposed the landscape411dp threshold and an in-memory reference fixture that production CaptureRepository correctly detached. Source now uses landscape controls whenever width>height, and the runtime fixture uses unique persisted production project/reference identities. Test-created IDs are published and cleanup requires the exact baseline-bound capture plus matching unique project. The initial generated unassigned capture with no retained exact ID remains preserved; broad/unassigned/latest-row deletion is forbidden. A further 40dp return-button assertion was fixed with an explicit48dp minimum; bounds tests remain strict.
