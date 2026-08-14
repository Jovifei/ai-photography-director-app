# P21 Offline Product Closure Qualification

Status: `ANDROID_OFFLINE_BETA_PRODUCT_CLOSURE_READY_AWAITING_QWEN_AND_PIPELINE`

Baseline: `0bd618109202b1ce7c5584275da903e4728b0d32`
Runtime candidate: `542011dc4e7075c5e19870641fa2a10be6d8d77f`
Independent-review boundary: `6edb41ddeca79c24f981f5691f6c7673291d74eb`

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
| Independent review | PASS | clean detached worktree at `6edb41d`; all required static, signing and API 35 layers re-run |

## Signed artifact binding

The final release qualification is external to Git under redacted evidence ID
`p21-offline-product-closure/20260814T165113Z/signed-release-final-542011d`.

- Source SHA: `542011dc4e7075c5e19870641fa2a10be6d8d77f`
- Package/version: `com.jovi.photoai` / `0.2.0-beta.1` / code `2`
- APK: 9,072,664 bytes; SHA-256 `69260fbc6620a114c89530928869fe62d3aeebc116879649ae006d804110ee83`
- AAB: 8,573,784 bytes; SHA-256 `1941bf7e4e9eb8b1de1d880649041e2b1abf06da728688302effdb290f09a159`
- Signing certificate SHA-256: `623C7DB70A8AA8552BD59B20BC103152326062F7FEC3D6D9313F8BBB94469CD0`
- `apksigner verify --verbose --print-certs` and `jarsigner -verify` passed inside the qualification script.
- External signed-summary SHA-256: `2499D292457E5731F66A8997D65250FB0CD6F4D1AA86A85694271ADD99FAB680`

The earlier pre-commit artifact run is intentionally not cited as final evidence because its source metadata preceded the fixed runtime commit.

## D2D binding

External D2D evidence ID:

`p21-offline-product-closure/20260814T165113Z/ui1-d2d-api35-r2/backup-restore-summary.json`

It records `PASS` for system Picker, 100%/200% semantic checks, LocalTransport restore, official D2D automatic restore, and reconciliation. Summary SHA-256: `35C857A81F1C177A391C2C6FA14D008C4A41357469C7A845705336BE348C31D6`.

## P20 evidence compatibility

The Provider, Coordinator, Room database, READY-only summary Provider, and summary model retain their baseline canonical Git blobs. P21 intentionally changes `PhotographyDirectorApp` and `AnalysisDetailScreen` to enforce the non-`READY` offline boundary; those routes are requalified synthetically above. P21 does not re-read the 20 private photos or re-run the Qwen runtime.

## Independent review binding

The independent review ran from a new clean detached worktree at `6edb41ddeca79c24f981f5691f6c7673291d74eb` and returned `PASS`. It independently re-ran the Debug/Test APK, JVM (119/0/0/0), Debug and Release lint (0 errors), signed APK/AAB verification, service/contract/privacy checks, official API 35 D2D, P21 truthfulness (3/3), offline Beta smoke (3/3), exporter retry (1/1), and signed Release install/launch. Its redacted external evidence ID is `independent-review-6edb41d.md`; SHA-256 `0DA6A1BE19CD59C0CD011784F911C41B78B025D81922102D9CD5827D525F64FE`.

The signed runtime artifact is bound to `542011d`; commits through the review boundary add only qualification/documentation material after that runtime code. No Qwen service, LAN Provider, night pipeline, real photo, physical device, five-person trial, main merge, or public distribution was performed.

## Next gate

P21 is closed as an honest offline Android Beta candidate. The only future activation gates are separate authorization for (1) private-LAN Qwen service and real per-photo analysis, and (2) night-pipeline Bundle consumption. Main merge, APK distribution, five-person pilot and public release remain out of scope.
