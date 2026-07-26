# Phase 1.5 P1A Qualification Report

Status: `P1A_QUALIFICATION_PASS_AWAITING_INDEPENDENT_REVIEW`.

P1A is a public-corpus and model-artifact authorization preparation gate. It intentionally did not download model weights, tokenizer/runtime artifacts, models, containers, or packages; did not run inference, GPU, Docker, WSL2, or cloud work; and did not modify Android sources, CameraX, UI, Pose, the sibling Pipeline, or product media.

## Baseline and scope

- Base/main: `3c6be0314669b4ea4bf12c57f366df0c3510a69c`.
- Working branch: `codex/phase1-5-p1a-corpus-artifact-authorization`.
- Tracked change scope: `docs/phase1_5/p1a/` and the two P1A-only offline validation scripts.
- A Git-ignored host SDK configuration was copied only into the isolated worktree to run regression; it is not a tracked change.

## Result

- Public corpus: 24 acquired, 20 manually approved, 4 quarantined; approved set is the required minimum and has source/license/hash/sanitization evidence.
- Primary model: Qwen3-VL-2B-Instruct revision `89644892e4d85e24eaac8bacfd4f463576704203`, with frozen 4,255,140,312-byte `model.safetensors` and LFS SHA-256 recorded.
- Backup: Qwen3-VL-4B-Instruct is inventory-only and high risk for a 12 GB baseline.
- Primary state: `READY_FOR_OWNER_DOWNLOAD_DECISION`, not “download authorized.”

The qualification is evidence-level only. Runtime fit, output quality, privacy behavior under inference, and deployment suitability remain unverified.

## Executed verification

| Check | Result |
| --- | --- |
| P1A manifest contract tests | PASS, 3/3 |
| External corpus SHA/dimension/decode/EXIF validator | PASS, 20 approved samples |
| Existing Phase 1.5 contract suite | PASS, 75 checks |
| Android `clean assembleDebug testDebugUnitTest lintDebug` | PASS, 52 Gradle tasks in 50 seconds |
| Privacy audit | PASS; no prohibited media, weights, databases, reference clones, or common secrets |

The external evidence root is referenced by ID only in Git. It contains public source copies, sanitized copies, metadata, transport records, and quarantine records; no image bytes or model bytes are committed.
