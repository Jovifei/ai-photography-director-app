# T10 Owner Decision Capture report

**Base:** `dd5d297ffa26adc38470bfa5737ac82b94eaa2ec`
**Decision:** `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`
**Status:** `P1B_ARTIFACT_QUARANTINE_SCOPE_APPROVED — ARTIFACT_ACQUISITION_NOT_YET_AUTHORIZED`

## Decision identity

- Decision file: `docs/phase1_5/p1b/p1b_owner_decision.v1.json`
- Decision hash mode: `UTF8_TEXT_EOL_NORMALIZED_SHA256_V1`
- Decision portable SHA-256: `45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9` (969 normalized UTF-8 bytes)
- Raw mixed-EOL SHA-256 `646626a536eee32bcf171927090a10d9c8df06b9890c2ec3fcd788964a617bdd` and all-CRLF SHA-256 `57b07ab70d34b64802be4a765e04479bdd66bd079958510029b56c133b49425b`: `NON_AUTHORITY_DIAGNOSTIC_ONLY`
- Readiness ID: `phase1-5-p1b-qwen3-vl-2b-authorization-readiness-20260927`
- Readiness manifest SHA-256: `166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a`

The T6 P1B readiness manifest is unchanged and keeps `authorization.owner_decision` at `NONE_TRACKED`. The T10 Owner choice is tracked separately in the decision file above and validated against that frozen readiness manifest; the manifest does not track the T10 choice.

## Exact artifact binding

- Model: `Qwen/Qwen3-VL-2B-Instruct`
- Immutable revision: `89644892e4d85e24eaac8bacfd4f463576704203`
- Weight file: `model.safetensors`
- Weight bytes: `4255140312`
- Weight LFS SHA-256: `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`

## Scope truth

`artifact_quarantine_only=true`. The following remain false:

- `download_authorized`
- `runtime_authorized`
- `inference_authorized`
- `app_integration_authorized`
- `pipeline_integration_authorized`
- `private_media_authorized`

No artifact was downloaded. No model/runtime/network/device/media/Pipeline action was performed. T9 real READY promotion, T8 real evaluation and T7 human review remain unauthorized.

## Initial T10 capture qualification

- `scripts/test_t10_owner_decision.py`: 8/8 PASS; decision-integrity negative matrix 16/16 PASS
- `scripts/validate_t10_owner_decision.py`: PASS
- P1B validator: `READY_FOR_OWNER_DECISION`; execution probe: `BLOCKED_OWNER_AUTHORIZATION_REQUIRED`
- T9 tests: 13/13 PASS; T9 validator, T7/T8/status/contracts/privacy/scope checks PASS
- T10 authority probes: runtime, real READY, human review and Pipeline all BLOCKED as expected
- Android `testDebugUnitTest`: PASS; no Android source/config delta

T10 records an Owner decision and its exact constraints. It does not authorize artifact acquisition or runtime execution; any later acquisition requires a separate explicitly planned and reviewed stage.

## T10-R3 portable identity repair

- Canonical decision identity: `UTF8_TEXT_EOL_NORMALIZED_SHA256_V1`, SHA-256 `45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9`, 969 normalized UTF-8 bytes.
- `scripts/test_t10_owner_decision.py`: 12/12 PASS, including the unchanged 16-case semantic/authority negative matrix, LF/CRLF/mixed-EOL and formatting-mutation cases, malformed UTF-8, and changed/missing Owner decision blob identity cases.
- `scripts/validate_t10_owner_decision.py`: PASS with the canonical portable identity; the six forbidden authority flags remain false.
- `scripts/validate_program_status.py`: PASS with the exact mode and digest projection.
- `scripts/test_phase1_5_p1b_readiness.py`: 11/11 PASS; the stale absence assertion is replaced by a compatibility check for the unchanged manifest and separate tracked T10 decision.
- `scripts/validate_t10r3_scope.py --base d332ba8ee6f1ea036be655de8b09f8c9f4180592`: PASS for 10 approved paths on the clean committed candidate, including the T6/T10 status clarification.
- `git diff --check`: PASS.
- Android source remained unchanged. The parent-run bounded JVM smoke `:app:testDebugUnitTest` used `--no-daemon --max-workers=1 --rerun-tasks` and completed with `BUILD SUCCESSFUL` (27 tasks executed); Android SDK variables were supplied only to that process. No device or provider action occurred.
- The P1B readiness manifest remains at SHA-256 `166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a`; the Owner decision file remains unchanged.
- This is local implementation evidence. PR #15 remote review/`DONE` remains pending.

### Remote-required iteration 4 evidence

P1B_UNIT_TESTS=PASS_11_OF_11
P1B_STALE_ABSENCE_ASSERTION_REPAIRED=PASS
P1B_READINESS_MANIFEST_OWNER_DECISION_FIELD=NONE_TRACKED_UNCHANGED
TRACKED_T10_OWNER_DECISION_PRESENT=PASS
TRACKED_T10_OWNER_DECISION_CONTRACT=PASS
TRACKED_T10_QUARANTINE_SCOPE_ONLY=PASS
TRACKED_T10_EXECUTION_FLAGS_ALL_FALSE=PASS
P1B_VALIDATOR=READY_FOR_OWNER_DECISION
P1B_EXECUTION_PROBE=BLOCKED_OWNER_AUTHORIZATION_REQUIRED
P1B_EXECUTION_PROBE_EXIT_CODE=2
P1B_MANIFEST_UNCHANGED=PASS
