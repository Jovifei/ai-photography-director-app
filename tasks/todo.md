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
- [ ] Record exact source/evidence boundary, run privacy audit, push branch, create Draft PR, and obtain independent review.

### T3 review

PASS local emulator qualification on source `7ddfdb66fc0d6725b1af67db2a9bf53a35615ee9`: JVM 138/138; debug and AndroidTest builds pass; lint 0 errors / 25 warnings; T3 UI 7/7, Director root 2/2, P25U camera 1/1, export 3/3, and default-App recovery prepare/verify/cleanup 1/1 each. Evidence run `0871703be1de4a7cb895b6eefd397e4b` is outside the repository and hash-bound in `docs/handoff/T3_STAGE_STATE_20260927.json`. Independent review, privacy audit, push, and Draft PR remain pending.
