# AA0-P0 remediation implementation report

## Identity and commit lineage

- Repository: `E:\project\ai-photography-director-app`
- Remediation worktree: `E:\project\_worktrees\ai-photography-director-app-aa0`
- Branch: `codex/aa0-android-pose-spike`
- Main baseline: `2a7605f5ba85803b721741027f0d73c517ca578b`
- Historical AA0 candidate under review: `2d4f13a56f6e584cef8f0ec533a4da7440d64fad`
- Current remediation implementation candidate: `e4bbdfa` (`fix(aa0): harden pose lifecycle and bounded metrics`)
- Final branch tip: the exact `git rev-parse HEAD` value is recorded in the owner handoff after the documentation commit; no reset, rebase, amend, squash, merge, or force push was used.

## Authorized remediation matrix

| Requirement | Current implementation and evidence |
|---|---|
| B1 state invariant | `PoseEstimate` enforces person/point/diagnostic invariants for every `PoseState`; tests assert the parameterized-style matrix and exact exception messages. |
| B2 request identity | `PoseAnalysisCoordinator` uses an opaque monotonic request token as the pending key; frame id/generation are metadata checks. Same-id reuse and old callback tests pass. |
| B3 timeout race | Pending insertion precedes schedule; timeout future is conditionally bound and cancelled if terminalized before binding. Synchronous callback and callback/timeout race tests pass with a controllable scheduler. |
| B4 scheduler fail-close | `RejectedExecutionException` and other scheduler runtime failures remove the request, close the lease, avoid engine submission, record scheduler/infrastructure metrics, deliver one `ENGINE_ERROR`, and return accepted ownership (`true`). |
| B5 frame release | `FrameReleaseResult` distinguishes `RELEASED`, `ALREADY_RELEASED`, and `RELEASE_FAILED`; release failures are terminal and never retried. Coordinator records the returned result, not a cast or guess. |
| B6 bounded metrics | Fixed latency ring (default 2048; constructor-tested capacity 1/3), nearest-rank P50/P95, total-observed/window counts, one injected clock domain, and SUCCESS-only completion FPS. |
| B7 fake/geometry | Fake requests have independent tokens, bounded clearable/drainable history, and manual old/new/reverse triggers. Geometry composition covers non-full crop, 90°/270°, portrait/wide center-crop, and front mirror with hand-calculated values. Claim is only `GEOMETRY_PURE_CONTRACT_VERIFIED`. |
| B8 device evidence | Current preflight exposed only an emulator. Direct emulator instrumentation passed 3/3, but no physical-device P0 evidence is claimed. |

## Verification results

| Gate | Result |
|---|---|
| `gradlew --stop` | PASS |
| `clean` | PASS |
| `assembleDebug` | PASS |
| `testDebugUnitTest --rerun-tasks` | PASS; 75 tests, 0 failures, 0 errors, 0 skipped |
| `assembleDebugAndroidTest` | PASS |
| `lintDebug --rerun-tasks` | PASS; 0 errors, 12 warnings |
| `connectedDebugAndroidTest --stacktrace` | `BLOCKED_INFRA_UTP_GRPC`; listener failed before assertions. A retry was captured outside Git under an opaque run directory. |
| Direct emulator `am instrument` | PASS; exact `PoseContractAndroidTest`, 3/3. This is not physical-device P0 evidence. |
| Physical direct instrumentation | NOT_AVAILABLE; current device preflight had no physical device. Historical device results were not reused. |
| `python scripts/prepush_privacy_audit.py` | PASS |
| Banned SDK/model source audit | PASS; no production dependency/import/model asset |
| `git diff --check` and tracked-artifact audit | PASS |

### Current lint warnings

The rerun produced 12 warnings, all outside the AA0 remediation files: `OldTargetApi` (1), `GradleDependency` (9), `DataExtractionRules` (1), and `MissingApplicationIcon` (1). The independent reviewer’s historical candidate noted 8 warnings; the current rerun is the authoritative count. No baseline or suppression was added.

### Current APK identity

Built after remediation and recorded outside Git:

- `app-debug.apk`: 11,797,449 bytes; SHA-256 `D3B491876D686426D19F79A89A524AF6989C3A8A40B04A840D02239DF70F8297`
- `app-debug-androidTest.apk`: 583,017 bytes; SHA-256 `7D667C843953FC22EC4F7D26BB1D7EFAA7BEE98FC65FAD200642C4E7D1CFCD4E`

No APK, test APK, model, image, logcat, UTP stacktrace, device serial, account data, or private path was added to Git. The direct-run material is outside the repository at `E:\project\_device-evidence\aa0-p0-remediation\20260720-000216-ee0b17a3`.

## Boundary declaration

No changes were made to `CameraXManager`, `CameraScreen`, `CameraDirectorChrome`, `CameraUiState`, `PhotographyDirectorApp`, `DemoContentRepository`, `Guidance`, `AndroidManifest`, `app/build.gradle.kts`, Gradle wrapper, UI0, Pipeline, iOS, shared-contract, AA1, or any model/runtime integration. No model was downloaded. Main worktree untracked user artifacts remain outside this remediation worktree and were not touched.

## Final disposition

Code and repository quality gates are complete. Physical P0 evidence is the remaining required gate; therefore the correct stop status is:

`AA0_P0_REMEDIATION_CODE_COMPLETE_BLOCKED_PHYSICAL_DEVICE`
