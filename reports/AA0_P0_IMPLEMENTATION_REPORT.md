# AA0-P0 final metrics/fake/device remediation report

## Identity and lineage

- Repository: `E:\project\ai-photography-director-app`
- Worktree: `E:\project\_worktrees\ai-photography-director-app-aa0`
- Branch: `codex/aa0-android-pose-spike`
- Main baseline: `2a7605f5ba85803b721741027f0d73c517ca578b`
- Review base: `615bec01e50d067fc7978df1f1ee4121ea20109f`
- Current remediation code candidate: `d739fe4ed9e76a4a04f0915670547fee587f09b2`
- Evidence parent SHA: `d739fe4ed9e76a4a04f0915670547fee587f09b2`
- Final branch SHA: reported in the Owner handoff after the documentation commit because a commit cannot contain its own hash.

No reset, rebase, amend, squash, merge, or force push was used.

## Final blocker remediation

| Finding | Closure |
|---|---|
| BF-1 SUCCESS timestamp/FPS | `PoseMetricsAccumulator` now requires a non-null, non-negative, non-decreasing completion timestamp before changing SUCCESS state. `timedSuccessCount` is explicit. FPS uses `(timedSuccessCount - 1) / elapsed` for at least two timed successes; missing, reverse, negative, or zero elapsed values cannot inflate FPS. |
| BF-2 Fake close/history callback | Fake `close()` marks closed, cancels/removes all active submissions, shuts down its owned scheduler, and retains bounded history. `emitHistoricalRequest()` works after close; only explicit `clearHistory()`/`drainHistory()` removes retained history, and callbacks never re-add active requests. |
| BF-3 Fake submit/close race | Closed check, token allocation, request creation, history insertion, and active insertion are one locked transition. Close wins reject submit without active leakage; submit wins is subsequently cancelled by close. Deterministic 100-way `CyclicBarrier`/executor coverage passes without sleeps. |
| Non-blocking additions | Explicit valid `STALE_RESULT_DROPPED`/`CANCELLED` estimates; 32+ concurrent release result statuses; concurrent metrics writers plus snapshot reader; timestamp, FPS, bounded-window, and Fake lifecycle tests. |

B1-B5 production contract implementations remain unchanged in this increment and were not redesigned.

## Quality gates

| Gate | Result |
|---|---|
| `gradlew --stop` | PASS, exit 0 |
| `clean` | PASS, exit 0 |
| `assembleDebug` | PASS, exit 0 |
| `testDebugUnitTest --rerun-tasks` | PASS, 81 tests, 0 failures, 0 errors, 0 skipped |
| `assembleDebugAndroidTest` | PASS, exit 0 |
| `lintDebug --rerun-tasks` | PASS, 0 errors, 8 warnings |
| `connectedDebugAndroidTest --stacktrace` | `CONNECTED_GRADLE_GATE = BLOCKED_INFRA_UTP_GRPC`; listener startup failed before assertions, not an assertion/runner/app failure |
| Gradle/JDK | Gradle 8.9, Kotlin 1.9.23, Temurin JDK 17.0.19 |
| Privacy audit | `python scripts/prepush_privacy_audit.py` PASS |
| Diff/name/artifact audits | PASS; only authorized code/tests/docs changed, no forbidden tracked artifacts |

### Lint warning accounting

The current rerun has 8 warnings, all outside this remediation scope: `OldTargetApi` 1 and `GradleDependency` 5 in the existing `app/build.gradle.kts`, plus `DataExtractionRules` 1 and `MissingApplicationIcon` 1 in the existing `AndroidManifest.xml`. No AA0 source warning, baseline, or suppression was added.

## APK and package verification

Built from exact code candidate `d739fe4ed9e76a4a04f0915670547fee587f09b2`:

- app APK: 11,797,449 bytes; SHA-256 `383869E7A3C3502B75FE5333E09C4B15424EC8A3F9B65B17C57ECD1F27F21ED9`
- AndroidTest APK: 583,017 bytes; SHA-256 `7D667C843953FC22EC4F7D26BB1D7EFAA7BEE98FC65FAD200642C4E7D1CFCD4E`
- Both exact APKs installed successfully; both install exit codes were 0.
- App package/version: `com.jovi.photoai`, versionName `0.1.0`, versionCode `1`.
- Test package/runner: `com.jovi.photoai.test` / `androidx.test.runner.AndroidJUnitRunner` targeting `com.jovi.photoai`.
- Both APKs verified with APK Signature Scheme v2; debug certificate SHA-256 `9ab144e824abf26a5941819abb06831288c36a8bfe622657e3dc9d88281fc774`.

## Physical device qualification

- Device class: OnePlus GM1910, Android 11/API 30, ABI `arm64-v8a`.
- `ro.kernel.qemu` was not `1`; ADB state was `device`.
- Before every round, redacted state recorded: physical=true, `keyguardShowing=false`, `dreamingLockscreen=false`, `deviceLocked=0`, screenOn=true, resumedActivity=true.
- Direct command: `am instrument -w -r -e class com.jovi.photoai.aa0.PoseContractAndroidTest com.jovi.photoai.test/androidx.test.runner.AndroidJUnitRunner`.
- Round 1: PASS, 3/3, exit 0, 0 failures/errors.
- Round 2: PASS, 3/3, exit 0, 0 failures/errors.
- Round 3: PASS, 3/3, exit 0, 0 failures/errors.
- Runner completed normally; no instrumentation abort or app crash observed.

No serial, IMEI, Android ID, full fingerprint, PIN, account, location, private device identifier, screenshot, video, full logcat, APK, or test APK was committed. Redacted external evidence is under opaque run ID `20260720-224559-ace3fc69` outside Git.

## Scope boundary

Only these code/test files changed in the code commit: `PoseMetrics.kt`, `FakePoseEstimator.kt`, `PoseMetricsTest.kt`, `FakePoseEstimatorTest.kt`, `PoseContractTest.kt`, and `FrameLeaseTest.kt`. The documentation commit changes only `docs/AA0_P0_CONTRACT.md` and this report.

No changes or entry into CameraX runtime wiring, UI0, Pipeline, iOS, AA0-P1, AA1, MediaPipe, ML Kit, MoveNet, LiteRT/TFLite, model download, Gradle, Manifest, or production pose runtime.

## Final disposition

Physical evidence is qualified. The connected Gradle UTP issue remains separately classified infrastructure-only; direct physical instrumentation is the accepted P0 evidence path.

`AA0_P0_FINAL_REMEDIATION_COMPLETE_AWAITING_DELTA_REVIEW`
