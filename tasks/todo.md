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
- [x] Re-run full qualification on the fixed source and refresh exact source/evidence records.
- [x] Run final privacy audit and non-force push the updated branch; refresh Draft PR #7 metadata.
- [x] Obtain independent confirmation of the corrected final evidence report and PR head.

### T3 review

Independent review first found an Important race in the export test's pre-capture snapshot and cleanup identity. Fixed in `47c2d8f192f2d92a4fd07539bd9ff3bf28b1ecfa`; focused export instrumentation and full API 35 qualification pass. Reviewer then found a mismatch between the report PID and evidence; it was corrected to 8352. Final confirmation on PR head `663d52991f7e6a436e3c91c82a72e80f411119cd` is PASS with no remaining findings. Evidence is at `E:\project_benchmark_evidence\t3-reference-camera-controls-20260926\5750a52b8c9e4507a4f5b162a8799cba`, with 9/9 artifact hashes verified.
