# P25U review remediation

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
- [ ] Re-run full qualification on the fixed source, refresh exact source/evidence records, run privacy audit, push the update, and obtain an independent review of the final PR head.

### T3 review

Initial local emulator qualification passed on `7ddfdb66fc0d6725b1af67db2a9bf53a35615ee9`: JVM 138/138; debug and AndroidTest builds pass; lint 0 errors / 25 warnings; T3 UI 7/7, Director root 2/2, P25U camera 1/1, export 3/3, and default-App recovery prepare/verify/cleanup 1/1 each. Independent review of PR head `f26f98b875ae8506ded7e0634a3fc7a45b6251e7` found an Important race in the export test's pre-capture snapshot and cleanup identity. The test fix compiles and the focused export instrumentation passes 3/3 on `emulator-5554` API 35; full qualification and independent re-review of the updated PR head are still pending. The original run's evidence remains outside the repository at `E:\project_benchmark_evidence\t3-reference-camera-controls-20260926\0871703be1de4a7cb895b6eefd397e4b`.
