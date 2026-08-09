# P20 Real 20-Photo Analysis Qualification

Date: 2026-08-09
Qualification state: `P20_REAL_20_PHOTO_ANALYSIS_COMPLETE_AWAITING_INDEPENDENT_REVIEW`

## Candidate

- Candidate branch: `codex/p20-real-20-photo-analysis-closure`
- Runtime-bound candidate commit: `e7a2d931fa27355edfc93c2a4849a254eff7ac75`
- Final branch HEAD is recorded in the external gate ledger; descendants after the runtime-bound commit are report/evidence-only.
- Base: `bdb04f344241149b32f9412dc23807d6c47a2179`
- Main was not reset, cleaned, staged, committed, or merged.

## Model and runtime

- Model: `Qwen/Qwen3-VL-2B-Instruct`
- Revision: `89644892e4d85e24eaac8bacfd4f463576704203`
- Weight SHA-256: `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`
- Runtime: native Windows, Transformers 4.57.6, CUDA `cuda:0`, BF16, offline/local-files-only.
- Single-image smoke: `PASS` (`P20_SINGLE_IMAGE_VLM_SMOKE_PASS`).

## P20 real batch

- Total: `20`
- READY: `20`
- FAILED: `0`
- CANCELLED: `0`
- READY-only project summary: `SUCCESS`
- Recommended primary reference: belonged to READY input.
- Per-photo inference latency: P50 `22467 ms`; P95 `26748 ms`; minimum `17555 ms`; maximum `29086 ms`.
- Existing P20 runtime evidence was reused after analysis-source hash binding; no Provider, Coordinator, Room schema, project-state, summary, or real-analysis UI source changed in this D2D run.

## API 35 UI1 runtime

- Target policy: dedicated API 35 emulator only; physical devices were not used for instrumentation.
- System Photo Picker selection/cancellation: `PASS`.
- 100% font responsive/accessibility semantics: `PASS_SEMANTICS_ONLY`.
- 200% font responsive/accessibility semantics: `PASS_SEMANTICS_ONLY`.
- LocalTransport backup/restore: `PASS`.
- D2D transport: `PASS_D2D_TRANSPORT` (final HEAD-bound evidence).
- D2D method: official single-device automatic restore flow; no explicit D2D restore-token command.
- Missing derived JPEG startup reconciliation: `PASS`.
- Orphan derived JPEG cleanup: `PASS`.
- Deletion/clear confirmation and restart routing were covered by the existing UI1/P20 instrumentation set.

## Static regression

- `clean`: `PASS`
- `assembleDebug`: `PASS`
- `assembleDebugAndroidTest`: `PASS`
- JVM: `110` tests, `0` failures, `0` errors.
- Lint: `0` errors; primary run recorded `21` warnings and the final detached rerun observed `20` non-blocking warnings (warning metadata varied, errors remained zero).
- Local service unittest: `5` tests, `0` failures.
- Python compile: `PASS`.
- Privacy audit: `PASS`.
- `git diff --check`: `PASS`.
- Manifest permissions: `CAMERA`, `INTERNET`; no broad media-read permission.
- Debug APK SHA-256: `c867dbea888536e795ca23e3cf45f63da04280a0335663a4ba4dfafca9eab9bc`.
- AndroidTest APK SHA-256: `91ef9536dae1a624f258e6dddcc96db345cfb62c9e352f7e428f0fd7eafa6025`.

## Scope of this run

- Updated only the D2D qualification script and synthetic backup fixture assertions after evidence showed the previous D2D test path did not follow the official automatic-restore sequence; later remediation removed the private source-path default and aligned the offline 2B model documentation/manifest.
- No production restore logic, Qwen runtime, Provider contract, Room schema, Camera, Pose, Pipeline, Cloud, iOS, or release code was changed in this run.
- The candidate is fixed and pushed through seven linear commits without force push; three small remediation commits preserve the already-pushed history.

## External evidence

Evidence root: `E:\project\_benchmark_evidence\p20-runtime-closure\20260809T125221Z\`

| Evidence | SHA-256 |
|---|---|
| `candidate-baseline.json` | `c291d1f10f645bb406acab2213d962fac07d96fa1241774c616147075388ac20` |
| `d2d-restore/backup-restore-summary.json` | `a5edf91a1ba8fd4f9f1b8b5f8412ce87877d507c8b7160680767ea389a164879` |
| `d2d-restore/reconciliation-fixture-summary.json` | `84c046efd6546d7cc968977e1b1a1e2e62b2b6c6a53bf1f544dc134b3e50f4ff` |
| `static-regression-summary.json` | `6df910dce8afb4569437573e5afc76a7964d5ad115706a8c060e1923dec61515` |
| `p20-evidence-binding.json` | `64492363cd0b9b63ab91b4a887191b8dbba8e2eb721cc9e1f72fd208d59c649b` |
| `d2d-final-e7a2d93/final-head-d2d-binding.json` | `01a83b506d60fa06d57d6701930ea103116811fb61df0a44d67930a78a817bce` |
| `static-remediation-a1b8563.json` | `ea86142a2066e954beb30105fe00e837147a0a80ba47cf8c819d6245780bced7` |

Evidence contains only aggregate statuses, counts, error codes, hashes and model identity. It does not contain photos, original filenames, URIs, database contents, raw inference, device serials, credentials or private keys. D2D file binding uses canonical Git blob OIDs; candidate and detached worktrees may differ in CRLF/LF checkout representation.
The external `gate-ledger.json` is the mutable run-state index and is intentionally not duplicated as a static report hash.

## Remaining gates

- Exact-file staging and non-force push: `PASS`; final branch SHA is recorded in the external gate ledger.
- Independent detached-worktree review: `PASS` on final detached review; D2D evidence is bound to the e7 runtime commit and later descendants are docs/evidence-only.
- Merge to `main`: not authorized in this task.
