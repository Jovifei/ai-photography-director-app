# Current authority — 2026-09-27

Read [`docs/CURRENT_PROGRAM_STATUS.md`](../docs/CURRENT_PROGRAM_STATUS.md) and [`docs/NEXT_GATE_MATRIX.md`](../docs/NEXT_GATE_MATRIX.md) before using any older task entry. The current delivery stack is T4 `e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb` with reviewed product source `a69ede68f3e54e5ab006dbfd7a65c33040590f93`, PR #8 Draft/unmerged. T5 is documentation/status authority consolidation; it does not authorize runtime, network, real-photo, device, Pipeline, signing or release work. `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE` remains an explicit remote-review limitation.

T5 documentation qualification: program-status tests PASS, scope guard PASS for 16 approved paths, contract/privacy PASS, JVM 141/141, lint 0 errors/10 warnings, Debug assemble PASS, Android source unchanged.

## P25U review remediation

## T4 offline product-flow integration — 2026-09-27

- [x] Bound remote ChatGPT stage PLAN `c2c_a91f`, corrected it against exact T3 code, and saved the revised milestone plan.
- [x] Created clean isolated branch from `14a56f49e1220eb8139bf7280c747124118fdb21`; origin T3 head matches.
- [x] Audited library, project naming, navigation, Camera truth boundaries and test inventory.
- [x] Implement all-project library entry, real search/filter/source/owner and coherent return route.
- [x] Implement explicit project creation name and persistent rename through existing Room title.
- [x] Add T4 JVM/Android tests; verify synthetic end-to-end flow and T3 regressions on API 35 emulator.
- [x] Run the clean exact-commit T4 qualifier, review its evidence and final diff.
- [x] Commit and deliver exact candidate for independent review; keep external Gates separate.

### T4 Review

- First exact candidate `fef13a1`: Debug/AndroidTest builds, JVM 141/141, lint 0 errors, T4 5/5, T3 + root + P25U capture/export 13/13, force-stop prepare/verify/cleanup, contract tests, privacy audit and diff check passed on the dedicated API 35 emulator. Independent exact-SHA review identified missing live-state coverage; this candidate is superseded by the replacement exact candidate below.
- Later Gradle JVM/lint attempts hit host virtual-memory pressure before completing; single-worker, bounded-JVM per-command reruns passed JVM 141/141, AndroidTest compilation and lintDebug. Generated `android/.kotlin/` is ignored rather than deleted; no system setting or other task process was changed. These interrupted attempts remain diagnostic failures, not PASS.
- Replacement exact source `a69ede68f3e54e5ab006dbfd7a65c33040590f93` qualified with T4 7/7, T3 7/7, root 2/2, P25U camera 1/1/export 3/3/recovery 3/3, JVM 141/141, build/lint/contract/privacy/diff PASS. Independent exact-SHA read-only review returned PASS with zero actionable findings; evidence and APK hashes matched. Draft PR #8 is open on the T3 branch, unmerged.
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
