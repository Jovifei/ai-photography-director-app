# T12 capture reference and guided retake

Date: 2026-10-01 (Asia/Shanghai)
Base: `adcf4dcd11dbf4c0ec738a8da5c73a1c25961ded`
Current branch: `codex/t12-capture-provenance-retake-20261001`
Implementation includes base checkpoint `b9df8951aa377f35009ee1ae147cadeff7fc283c`; the final delivery commit is pending.

## Result

`CAPTURE_REFERENCE_AND_RETAKE_MACHINE_VALIDATED — HUMAN_ACCEPTANCE_NOT_RUN`

The capture library now names the associated current reference, source, status, and ordinal. Open-reference and guided-retake actions re-read the persisted capture/project/reference, remain enabled only for the exact current relationship and existing READY+provenance eligibility, and preserve the gallery on failure. Direct captures and deleted references have explicit unavailable states. No historic guidance snapshot or analysis of the captured image is claimed.

The production Room v6 factory now shares one thread-safe database instance per process. This fixes production Repository Flow invalidation across distinct Repository objects, which previously left the default App root showing a persisted project as missing. Test-owned injected databases remain isolated.

## Verification

- Android JVM: 147/147 PASS.
- Debug build and AndroidTest compilation: PASS.
- Debug lint: 0 errors, 17 warnings.
- Release lint: 0 errors, 17 warnings; this was a static lint run with `-x :app:verifyReleaseSigning`. Signed Release/APK qualification: `NOT_RUN` (signing inputs were not read or supplied).
- T12 UI/default-root, Room multi-repository Flow, T4 guidance, P25U capture/UI/export, T3 camera, T4 root and P23R Bundle regression batch: 43/43 PASS on `T3_API35_20260927` (`emulator-5580`). A preceding combined run had 3 P25U export UI tests fail because an assertion incorrectly required “保存副本” to be initially in the viewport after the new reference details. The button remained in the existing vertical scroll container. After the remote-approved test-only change to scroll to the button before visibility/action assertions, the standalone export class passed 3/3 and the complete batch passed 43/43.
- Capture ledger disk/process recovery: prepare 1/1, force-stop/reopen verification 1/1, cleanup 1/1.
- ReferenceLibraryDatabase process restart: final dedicated synthetic run `d01405dcefab40b0aa3a10170564fef9` passed PREPARE / VERIFY / exact CLEANUP. PREPARE wrote one runId-scoped project/reference and generated JPEG through the production Room factory; VERIFY opened a new process epoch/PID and read those same IDs from the existing v6 database; CLEANUP deleted only that run's project. Runtime identity was processName/targetPackage `com.jovi.photoai`, UID `10209`, PID `8457`; verification ran in PID `8512`. The PREPARE invocation process had already exited before the runner's target-package `am force-stop`, so the runner recorded `INSTRUMENTATION_PROCESS_EXIT_FORCE_STOP_IDEMPOTENT`; the changed epoch and PID prove the subsequent read ran in a fresh process. Test package was `com.jovi.photoai.test`, UID `10210`. No MainActivity was launched.
- The run marker and raw logs live in a new runId-specific subdirectory under the external Codex visualizations evidence folder. App storage changes only by adding/removing this run's one synthetic project/reference/JPEG; no marker is written under Android App/Test storage, and no other project/reference is enumerated for deletion.
- Legacy P23D process-death harness: `NOT_RUN_SAFETY_SCOPE`; it calls `ReferenceRepository.clearAll()` against the default app database. Its cleanup could erase unrelated reference-library rows, so T12 used the runId-scoped restart harness instead.
- Phase 1.5 contract tests and PKB1 consumer contract tests: PASS. T11 preflight remains `BLOCKED_LEGAL_REVIEW`; acquisition/network/runtime/App/Pipeline authority flags stay false.
- `scripts/prepush_privacy_audit.py`: PASS. `git diff --check`: PASS. Schema version 6, database filename, migrations, Bundle contracts, and T10/T11 authority files unchanged.

Debug APK metadata verification: PASS (`com.jovi.photoai`, versionCode 2, versionName `0.2.0-beta.1`, signed=true, debuggable=false). APK SHA-256 `C725054E0779A16ECB0561D0D32478E5441D835328D4CF8C0879C453BF985DCB`; signing certificate fingerprint `9AB144E824ABF26A5941819ABB06831288C36A8BFE622657E3DC9D88281FC774`. These are public artifact metadata; no keystore material was read.

Gradle 8.9 was invoked from the already-installed cache because this execution sandbox denied the wrapper’s attempted network access. No new dependencies were downloaded. The emulator runs only synthetic generated JPEGs; the connected physical phone was not used.

Raw outputs and the verified APK fingerprint/hash are stored outside Git under the Codex visualizations evidence folder, `t12-capture-reference-20261001`. The restart marker existed only there; test identity output contained opaque project/reference IDs, process epoch and package/UID values. No private photos, production Bundle, signing material, or database were committed.

## Remote review and next stages

Remote PLAN / PLAN_FEEDBACK (`c2c_2537`, iteration 1) approved the Android track, the process-scoped Room fix after the production Flow defect was demonstrated, and the narrow export test scrolling update. T11’s optional Qwen/legal acquisition branch remains separate.

Exact-diff review, final privacy audit, push, Draft PR stacked on PR #16, and exact-head remote review are the remaining delivery steps for T12. C2C local bridge/MCP checks pass, but Doctor reports the named Cloudflare tunnel start timing out; the tunnel login command confirms an existing login and the repair still needs Jovi's Cloudflare-domain selection before remote review can resume. No remote review control message has been sent while Doctor is red. After remote DONE, return for the next machine-only stage: explicit, all-or-none replacement of existing Bundle-backed READY guidance. Producer golden-vector compatibility follows when the separate nightly project exposes a stable exporter. Camera zoom/front-back switching remains a separately planned Android candidate. Physical-device and human photo-usefulness acceptance are not claimed here.
