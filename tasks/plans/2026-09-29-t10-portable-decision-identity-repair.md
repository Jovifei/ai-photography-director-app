# T10-R3 Portable Decision Identity Repair — 2026-09-29

## Remote plan and authority boundary

Remote C2C task c2c_8b2a revised T11 after the T10 M0 portability failure. T10-R3 must run first on the existing branch codex/t10-owner-decision-capture-20260928, exact scope base d332ba8ee6f1ea036be655de8b09f8c9f4180592, and existing Draft PR #15.

The Owner decision remains AUTHORIZE_ARTIFACT_QUARANTINE_ONLY. Do not modify docs/phase1_5/p1b/p1b_owner_decision.v1.json or change any authority flag. Acquisition, download, network, runtime, inference, App/Pipeline, private-media, device, signing, release, merge, and Android work remain unauthorized. The original T10 decision authority base recorded in project status remains dd5d297ffa26adc38470bfa5737ac82b94eaa2ec; the R3 scope base is separately d332ba8ee6f1ea036be655de8b09f8c9f4180592.
The frozen T6 P1B readiness manifest remains unchanged and its `authorization.owner_decision` stays `NONE_TRACKED`. The T10 Owner decision is a separate tracked file validated against that readiness manifest; the P1B manifest does not track the T10 choice.

T11 remains blocked until T10-R3 receives remote DONE. After acceptance, T11 must use a fresh worktree/branch from the accepted T10-R3 head; do not continue the paused T11 worktree as if its M0 passed.

## Reproduced failure and correct canonical identity

At exact base d332ba8, the tracked decision checkout is 970 bytes with one CRLF and 23 LF line endings. Its mixed-EOL raw SHA-256 is 646626a536eee32bcf171927090a10d9c8df06b9890c2ec3fcd788964a617bdd. A fresh all-CRLF checkout is 993 bytes and has raw SHA-256 57b07ab70d34b64802be4a765e04479bdd66bd079958510029b56c133b49425b. Both raw hashes are NON_AUTHORITY_DIAGNOSTIC_ONLY.

The remote-specified strict UTF-8 / LF-normalized identity is 969 bytes and SHA-256 45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9 for both representations and the Git blob. Canonical status must migrate from the old raw digest to this portable digest and add mode UTF8_TEXT_EOL_NORMALIZED_SHA256_V1. The old raw digests may appear only as NON_AUTHORITY_DIAGNOSTIC_ONLY.

The frozen T6 P1B readiness manifest remains byte-identical at 3,286 bytes and SHA-256 166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a, with `authorization.owner_decision` still `NONE_TRACKED`. The separate tracked T10 Owner decision validates against it; this repair changes T10 decision identity handling and the P1B compatibility test only.

## Portable identity algorithm

For the T10 Owner decision only:

1. Read bytes and decode strict UTF-8.
2. Normalize CRLF to LF, then lone CR to LF.
3. Apply no other transformation: no JSON reserialization, key sorting, whitespace removal, Unicode normalization, or BOM stripping.
4. Encode the resulting text as UTF-8 and compute SHA-256.

Malformed UTF-8 must fail closed. The computed canonical identity is 45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9.

## Milestones

- [x] M0: Verify the exact d332 base, T10 branch, origin, unchanged Owner decision, closed authority flags, original M0 failure, and corrected 45e462 portable identity.
- [x] Review the remote correction and record the exact local scope before code changes.
- [x] M1: Replace raw checkout-byte hashing for the T10 Owner decision with a dedicated strict UTF-8 / LF-normalized hash helper; keep P1A/P1B source hashing semantics unchanged.
- [x] M2: Update canonical program status to the 45e462 digest and explicit hash mode. Keep every authority bit false.
- [x] M3: Make T10 validation and program-status validation require the exact portable hash mode and digest.
- [x] M4: Add explicit pure-LF, pure-CRLF, and mixed-EOL fixtures. Prove distinct raw hashes produce the same portable digest and identical derived T10 authority. Add a valid-JSON non-EOL textual mutation that changes the portable digest and is rejected. Preserve the existing 16-case decision-integrity matrix.
- [x] M5: Update the T10 report to identify the 45e462 portable digest as canonical and label 646626/57b07 raw hashes NON_AUTHORITY_DIAGNOSTIC_ONLY.
- [x] M6: Add an exact-base T10-R3 scope guard that rejects any change to the Owner decision file and rejects paths outside the approved list.
- [x] M7: Run the T10 and P1B unit suites, T10/P1B/status validators, contract and status tests, privacy audit, authority probes, diff check, and exact-base scope guard after the implementation commit on a clean worktree. Record the parent-run bounded JVM smoke separately; no device/provider action is authorized.
- [ ] M8: Review the exact diff, push the tested update to Draft PR #15, and obtain remote DONE for the exact head.
- [ ] M9: Only after remote DONE, create a fresh T11 worktree from that accepted head and rebind T11's exact base and decision digest.

## Approved changed paths

- scripts/validate_t10_owner_decision.py
- scripts/test_t10_owner_decision.py
- scripts/test_phase1_5_p1b_readiness.py
- scripts/validate_program_status.py
- scripts/validate_t10r3_scope.py
- docs/current_program_status.v1.json
- docs/CURRENT_PROGRAM_STATUS.md
- reports/T10_OWNER_DECISION_CAPTURE_REPORT.md
- tasks/plans/2026-09-29-t10-portable-decision-identity-repair.md
- tasks/todo.md

## Explicitly forbidden changes

- The P1B Owner decision file, its schema, and the P1B readiness manifest.
- P1A, T7, T8, T9, Android, local analysis service, model/artifact files, network tools, Pipeline code, signing, and release files.
- Any change that opens download, runtime, inference, App/Pipeline, private-media, device, signing, release, or merge authority.
- Any artifact, network, or external evidence action.

## Acceptance evidence

- T10_DECISION_HASH_MODE=UTF8_TEXT_EOL_NORMALIZED_SHA256_V1
- T10_DECISION_PORTABLE_SHA256=45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9
- T10_DECISION_NORMALIZED_BYTES=969
- LF, CRLF, and mixed-EOL representations validate with the same derived authority.
- Non-EOL textual mutation is rejected; existing 16-case negative decision matrix still passes.
- Owner decision file is byte-for-byte unchanged; all forbidden authority flags remain false.
- T10 and P1B unit suites, T10/P1B/status validators, contracts, privacy, exact-base scope, diff, and authority probes pass; Android source remained unchanged.
- Parent-run bounded JVM smoke `:app:testDebugUnitTest` used `--no-daemon --max-workers=1 --rerun-tasks` and completed with `BUILD SUCCESSFUL` (27 tasks executed). Android SDK variables were supplied only to that process; no device or provider action occurred.
- P1B manifest `authorization.owner_decision` remains `NONE_TRACKED`; the separate tracked T10 Owner decision passes `validate_owner_decision` with quarantine-only scope and all six execution flags false.
- P1B_UNIT_TESTS=PASS_11_OF_11
- P1B_STALE_ABSENCE_ASSERTION_REPAIRED=PASS
- P1B_READINESS_MANIFEST_OWNER_DECISION_FIELD=NONE_TRACKED_UNCHANGED
- TRACKED_T10_OWNER_DECISION_PRESENT=PASS
- TRACKED_T10_OWNER_DECISION_CONTRACT=PASS
- TRACKED_T10_QUARANTINE_SCOPE_ONLY=PASS
- TRACKED_T10_EXECUTION_FLAGS_ALL_FALSE=PASS
- P1B_VALIDATOR=READY_FOR_OWNER_DECISION
- P1B_EXECUTION_PROBE=BLOCKED_OWNER_AUTHORIZATION_REQUIRED
- P1B_MANIFEST_UNCHANGED=PASS
- PR #15 remains Draft/open/unmerged and receives remote DONE on the exact repaired head.
