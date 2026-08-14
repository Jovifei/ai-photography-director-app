# P21 Offline Product Closure Qualification

Status: `P21_OFFLINE_PRODUCT_CLOSURE_QUALIFIED_AWAITING_INDEPENDENT_REVIEW`

Baseline: `0bd618109202b1ce7c5584275da903e4728b0d32`
Runtime candidate: `542011dc4e7075c5e19870641fa2a10be6d8d77f`

## Product truthfulness result

- A project primary reference may still be selected manually, but only `READY` may enter AI Camera Director.
- `IMPORTED`, `QUEUED`, `RUNNING`, `UNAVAILABLE`, `FAILED`, `CANCELLED`, and legacy `EXAMPLE_GUIDANCE` route to explicit **no-AI-guidance capture**.
- Analysis detail renders structured guidance and the Director Card action only for `READY`.
- With no in-process pairing, the project board says that the local analysis service is not connected. It opens the existing pairing path; it does not synthesize `READY`, a summary, or guidance.
- Zero-`READY` summaries state that no real analysis conclusion exists and keep the manual capture route available.

## Scope and data boundary

- This qualification used only synthetic fixtures on the dedicated API 35 emulator.
- It did not start Qwen, contact a LAN Provider, run a night pipeline, read real photos, or access a sibling repository.
- No APK, AAB, photo, model, database, certificate, private key, pairing material, path to private media, raw output, or device identifier is committed.

## Gate ledger

| Gate | Result | Evidence / observation |
| --- | --- | --- |
| Debug build | PASS | `assembleDebug` |
| Android test APK | PASS | `assembleDebugAndroidTest` |
| JVM | PASS | 119 tests; 0 failures, 0 errors, 0 skipped |
| Lint | PASS | Debug and Release: 0 errors; 24 warnings observed in each run |
| Local service | PASS | `unittest discover`: 5/5; `compileall` PASS |
| Bundle Consumer v1 | PASS | 9 static privacy/compatibility cases; existing Phase 1.5 contract suite PASS |
| Privacy and diff | PASS | `prepush_privacy_audit.py` and `git diff --check` PASS |
| API 35 P21 truthfulness | PASS | 3/3 synthetic Compose tests |
| API 35 capture/export | PASS | system Save As success/cancel 3/3; output-stream failure preserves cache/retry 1/1 |
| API 35 Picker, responsive, Local backup | PASS | real system Picker plus 100%/200% semantic tests and LocalTransport restore |
| API 35 D2D | PASS | official single-device automatic restore and reconciliation |
| Signed release | PASS | APK/AAB verification, certificate pin, install and MainActivity launch |
| Independent review | NOT_RUN | required after push from a detached worktree |

## Signed artifact binding

The final release qualification is external to Git at:

`E:\project\_benchmark_evidence\p21-offline-product-closure\20260814T165113Z\signed-release-final-542011d\`

- Source SHA: `542011dc4e7075c5e19870641fa2a10be6d8d77f`
- Package/version: `com.jovi.photoai` / `0.2.0-beta.1` / code `2`
- APK: 9,072,664 bytes; SHA-256 `69260fbc6620a114c89530928869fe62d3aeebc116879649ae006d804110ee83`
- AAB: 8,573,784 bytes; SHA-256 `1941bf7e4e9eb8b1de1d880649041e2b1abf06da728688302effdb290f09a159`
- Signing certificate SHA-256: `623C7DB70A8AA8552BD59B20BC103152326062F7FEC3D6D9313F8BBB94469CD0`
- `apksigner verify --verbose --print-certs` and `jarsigner -verify` passed inside the qualification script.
- External signed-summary SHA-256: `2499D292457E5731F66A8997D65250FB0CD6F4D1AA86A85694271ADD99FAB680`

The earlier pre-commit artifact run is intentionally not cited as final evidence because its source metadata preceded the fixed runtime commit.

## D2D binding

External D2D summary:

`E:\project\_benchmark_evidence\p21-offline-product-closure\20260814T165113Z\ui1-d2d-api35-r2\backup-restore-summary.json`

It records `PASS` for system Picker, 100%/200% semantic checks, LocalTransport restore, official D2D automatic restore, and reconciliation. Summary SHA-256: `35C857A81F1C177A391C2C6FA14D008C4A41357469C7A845705336BE348C31D6`.

## P20 evidence compatibility

The Provider, Coordinator, Room database, READY-only summary Provider, and summary model retain their baseline canonical Git blobs. P21 intentionally changes `PhotographyDirectorApp` and `AnalysisDetailScreen` to enforce the non-`READY` offline boundary; those routes are requalified synthetically above. P21 does not re-read the 20 private photos or re-run the Qwen runtime.

## Next gate

Push the three P21 commits, then perform a detached independent review. If it passes, the only future activation gates are separate authorization for (1) private-LAN Qwen service and real per-photo analysis, and (2) night-pipeline Bundle consumption.
