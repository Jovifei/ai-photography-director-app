# T10 Owner Decision Capture / P1B Route Resolution

## Authority

- Exact T9 base: `dd5d297ffa26adc38470bfa5737ac82b94eaa2ec`.
- Branch: `codex/t10-owner-decision-capture-20260928`.
- Explicit Owner decision supplied for this task: `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`.
- Current project status remains `PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED`.
- T10 records the decision and validates its scope. It does not download, install, run, infer, integrate, access private media, access Pipeline, change Android, sign, release or merge.
- P1B manifest SHA-256: `166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a`.
- Exact artifact binding: model `Qwen/Qwen3-VL-2B-Instruct`, revision `89644892e4d85e24eaac8bacfd4f463576704203`, file `model.safetensors`, `4255140312` bytes, LFS SHA-256 `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`.

## Milestones

- [x] M0: Confirm the exact Owner decision and exact T9 base; hard-stop on any mismatch.
- [x] M1: Re-read current status, T9, P1B manifest/schema and existing P1B validator; preserve all closed authority flags.
- [x] M2: Add `docs/phase1_5/p1b/p1b_owner_decision.v1.json` using the existing schema and exact quarantine binding. Keep all download/runtime/inference/App/Pipeline/private-media flags false.
- [x] M3: Add `scripts/validate_t10_owner_decision.py` by reusing `validate_phase1_5_p1b_readiness.validate_manifest` and `validate_owner_decision`; add exact T9 base and canonical projection checks.
- [x] M4: Add `scripts/test_t10_owner_decision.py` with 8/8 test methods and a 16-case decision-integrity negative matrix covering all three legal branches, exact artifact identity, forbidden authority flags, wrong IDs/hashes, and current T9 authority.
- [x] M5: Add branch-specific `t10_owner_decision` status projection to the canonical status/matrix without rewriting T9 as Provider-ready.
- [x] M6: Add `reports/T10_OWNER_DECISION_CAPTURE_REPORT.md` with decision, identities, hashes, closed gates and no-execution boundary.
- [x] M7: Add `scripts/validate_t10_scope.py` with exact base `dd5d297` and T10 allowlist only.
- [x] M8: Run T10/P1B/T9/T8/T7/status/contracts/privacy/scope and bounded Android checks.
- [x] M9: Run negative authority probes; every forbidden authorization must fail closed.
- [ ] M10: Perform exact review, push a Draft PR stacked on PR #13, record evidence, and request remote review.

## Acceptance

- The tracked decision exactly equals the explicit Owner choice.
- The quarantine branch binds the exact model, revision, filename, byte count, artifact hash and readiness-manifest hash.
- `download_authorized`, `runtime_authorized`, `inference_authorized`, `app_integration_authorized`, `pipeline_integration_authorized` and `private_media_authorized` remain false.
- T9 real READY promotion, T8 real evaluation and T7 human review remain unauthorized.
- No external capability is invoked; no artifact is downloaded.
- Exact source review, privacy, scope, contract, status and regression checks pass.
- Draft PR is stacked on PR #13 and remains unmerged.

## Hard stops

- Any missing or conflicting Owner decision.
- Any identity/hash/byte mismatch.
- Any request to download, run, infer, access media/Pipeline/device or change Android.
- Any attempt to weaken existing P1B/T9/T8/T7 contracts.
- Any authority probe succeeds.
- Any scope/privacy/status/regression failure.
