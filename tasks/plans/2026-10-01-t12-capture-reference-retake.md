# T12 Capture reference and guided retake

Remote PLAN: `c2c_2537`, iteration 1, original Ai相机 Project/chat. Base `adcf4dcd11dbf4c0ec738a8da5c73a1c25961ded`; branch `codex/t12-capture-provenance-retake-20261001`. Owner authorized continuing independent machine-verifiable Android development, local implementation/tests/push and remote stage review.

## Route

T12 closes capture→current reference→detail/continue shooting. T13 extends existing PKB1 import with explicit Bundle-only replacement, preview, atomic writes and recovery. T14 checks producer golden vectors when a stable exporter exists; do not invent missing fields. Camera zoom/lens controls remain a concrete follow-up candidate for remote selection. Physical-device qualification and human photo usefulness/release decisions are separate. Optional Qwen T11 acquisition evidence stays unchanged and cannot block unrelated Android work.

## Scope and semantics

No Room schema, migration, model or PKB1 contract changes. Persisted IDs represent an association, not a frozen historical guidance snapshot. Every visible source/status is labelled current; no AI analysis of the capture is claimed.

Remote revised PLAN_FEEDBACK approves one necessary production factory repair: `data/reference/ReferenceDatabase.kt` must return a thread-safe process-scoped Room instance. Production repositories currently create separate same-name databases without shared invalidation; existing root collectors remain stale after writes from another factory-created repository. Preserve filename, schema v6, entities, SQL, migrations, journal mode and backup/privacy behavior. `createForTest` remains caller-owned; no reset hook or multi-process architecture.

- [x] M4 add `t12/T12ReferenceRepositoryInvalidationAndroidTest.kt`: start an existing production repo B collector and observe its initial snapshot before repo A writes a unique project / imports generated JPEG; require that same collector to emit the change.
- [x] M5 retain enabled-action/default-root assertions; run two-repository invalidation regressions and T12 root navigation against exact Room repair.
- [x] M6 add synthetic-only `T12ReferenceDatabaseProcessRestartAndroidTest` and its phase runner `scripts/qualify_t12_reference_database_process_restart.ps1`: prepare a UUID-owned project/reference in a production Repository and emit only opaque IDs/process identity via instrumentation status; the outer runner persists those fields as a host-side marker, force-stops the measured target package/UID, verifies a changed JVM epoch and re-opens the same project/reference through the production factory, then deletes only the test project and host marker. Never create markers in Android app/test storage, launch MainActivity or clean unrelated app rows.
- [x] M7 run the no-Activity synthetic restart harness and verify process epoch, persistence and exact cleanup.
- [ ] M8 complete final static/privacy/exact-scope checks, independent review, branch push, Draft PR and C2C roundtrip.

- [x] Verify clean base/origin, restored C2C identity and JVM baseline; T11 remains BLOCKED_LEGAL_REVIEW.
- [x] M1 add pure `ui/capture/CaptureReferenceContext.kt` resolver. Require exact reference ID, non-null matching project and existing project; never guess from ordinal/name. Use existing trusted READY eligibility for retake.
- [x] M2 extend `CaptureLibraryScreen.kt` with optional contexts/actions, preserve existing hosts. Show direct/missing/current reference cases and separate open/retake controls. Export/delete unchanged.
- [x] M3 wire `PhotographyDirectorApp.kt`: requery current persisted capture, project and reference at click time; close gallery only after validated navigation. Set owning project, active reference, return destination and persisted active ID. Failed recheck retains gallery and original; never silently direct-capture or substitute.
- [x] Tests: JVM resolver/direct/provider/bundle/untrusted/deleted/cross-project/no mutation; synthetic Compose UI list/detail/actions; real-root generated Bundle READY→capture association→Analysis Detail/Camera Director, plus stale/deleted references.
- [x] Verify full JVM, debug build, AndroidTest compilation, debug/release lint. On dedicated T3 API35 emulator run new T12 plus affected P25U capture/export/recovery, T3 camera, T4 root/guidance and focused Bundle regressions. Preserve physical device.
- [ ] Exact-diff independent review; fix findings. Privacy audit and non-force push Draft PR stacked on T11 branch. Update clean remote review clone, record evidence and send EXECUTED iteration 2; await exact-head review then request/execute next bounded stage.

## Evidence commands

Concrete scope addition: unchanged-base combined instrumentation failed 1/23 at `T4GuidanceTransitionAndroidTest:85`; isolated rerun failed the same 1/2. Initial Room Flow project semantics are absent immediately after `setContent`. Remote PLAN_FEEDBACK review supports a bounded readiness helper, without production scope expansion or fixed sleep. Modify only that existing test's project-click readiness and preserve both failed logs in external evidence. Requalify T4 alongside T12.

Per-command ANDROID_HOME points to existing SDK. `android/gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m` (quote JVM argument in PowerShell). Use explicit emulator serial for adb installs/instrumentation; generated media only. `python scripts/validate_t11_artifact_acquisition_preflight.py`, contract checks, `python scripts/prepush_privacy_audit.py`, `git diff --check`. Store raw runtime output outside Git, publish summarized exact evidence in `reports/T12_CAPTURE_REFERENCE_RETAKE_REPORT.md`.

## Failure and rollback

Failed route stays in gallery with honest unavailable reason; no file/state rewrite. Failed tests keep stage incomplete. If migration, producer/contract changes, actual network/inference/private photo/signing are necessary, stop this increment and discuss narrowed/revised scope with remote. The revised shared-DB factory scope is explicitly approved; no migration or repository rewrite. Rollback is reverting isolated stage source commits; never clean/reset Owner worktrees or delete originals.

## Acceptance

All linked/direct/missing/cross-project cases correct; current trusted reference required for retake; root routes prove detail and camera navigation. Existing capture export/recovery and Bundle import remain green. Room unchanged, T11 unchanged, no guessed reference, no private media added. Target `CAPTURE_REFERENCE_AND_RETAKE_MACHINE_VALIDATED — HUMAN_ACCEPTANCE_NOT_RUN`; remote DONE is code review only.

Review: local exact-diff review PASS; remote exact-head review pending after C2C tunnel recovery.
