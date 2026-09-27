# P25U review remediation

## T4 offline product-flow integration — 2026-09-27

- [x] Bound remote ChatGPT stage PLAN `c2c_a91f`, corrected it against exact T3 code, and saved the revised milestone plan.
- [x] Created clean isolated branch from `14a56f49e1220eb8139bf7280c747124118fdb21`; origin T3 head matches.
- [x] Audited library, project naming, navigation, Camera truth boundaries and test inventory.
- [x] Implement all-project library entry, real search/filter/source/owner and coherent return route.
- [x] Implement explicit project creation name and persistent rename through existing Room title.
- [x] Add T4 JVM/Android tests; verify synthetic end-to-end flow and T3 regressions on API 35 emulator.
- [ ] Run the clean exact-commit T4 qualifier, review its evidence and final diff.
- [ ] Commit and deliver exact candidate for independent review; keep external Gates separate.

### T4 Review

- Pre-commit: Debug/AndroidTest builds, JVM 141/141, lint 0 errors/10 warnings, T4 5/5, T3 + root + P25U capture/export 13/13, force-stop prepare/verify/cleanup, contract tests, privacy audit and diff check passed on the dedicated API 35 emulator. The first recovery verify failure was a UI test timing race; the test now waits for the gallery before selecting, and the replacement run completed. These are not exact-commit qualification claims yet.
- Real photos, physical device, Qwen/LAN, Pipeline, signing and release are outside T4.

- [x] Verify independent review findings against the final source and existing evidence.
- [x] Add an Activity recreation export-result regression and observe the expected failure.
- [x] Preserve the active export token across Activity recreation and pass the regression.
- [x] Bind default-App recovery verification to the prepared capture ID and identify force-stop evidence in the report.
- [x] Run focused Android verification and refresh the source manifest/report/handoff.
- [x] Run privacy audit, non-force push, and update Draft PR #6.
- [x] Obtain a fresh independent review of the final delivery commit.

## Review

PASS: independent review of delivery 753163efddfd8e28a6b267ac70af95cdd86c282e returned FINDINGS=NONE and OWNER_LANDING=ALLOWED. Owner landing remains a separate decision; no merge was performed.

## T3 reference comparison and camera controls

- [x] Bind clean isolated branch to reviewed P25U SHA and confirm baseline build/tests.
- [x] Add current-primary-reference thumbnail and full-view flow with private-file failure handling.
- [x] Add lifecycle-fenced tap focus and capability-bound exposure compensation.
- [x] Verify real App navigation, camera capture, layout, rotation, and affected P25U regressions on dedicated API 35 emulator.
- [x] Fix Reviewer finding: wait for the initial persisted capture/export snapshot, select exactly one new available capture, and wait for exact-record cleanup; focused P25U export instrumentation passes 3/3.
- [x] Re-run full qualification on the fixed source and refresh exact source/evidence records.
- [x] Run final privacy audit and non-force push the updated branch; refresh Draft PR #7 metadata.
- [x] Obtain independent confirmation of the corrected final evidence report and PR head.

### T3 review

Independent review first found an Important race in the export test's pre-capture snapshot and cleanup identity. Fixed in `47c2d8f192f2d92a4fd07539bd9ff3bf28b1ecfa`; focused export instrumentation and full API 35 qualification pass. Reviewer then found a mismatch between the report PID and evidence; it was corrected to 8352. Final confirmation on PR head `663d52991f7e6a436e3c91c82a72e80f411119cd` is PASS with no remaining findings. Evidence is at `E:\project_benchmark_evidence\t3-reference-camera-controls-20260926\5750a52b8c9e4507a4f5b162a8799cba`, with 9/9 artifact hashes verified.
