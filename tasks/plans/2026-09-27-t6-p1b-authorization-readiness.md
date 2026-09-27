# T6 P1B Provider/Qwen Authorization Readiness

## Authority and boundaries

- Base exactly `0b04abdc57a3fa332689cfe664f9bcb997160e5e` (T5 receipt head).
- Work only in the isolated T6 worktree and branch; never modify, clean, reset, rebase, or merge Owner `main`.
- This stage is metadata-only. It must not download model artifacts, open network connections, access images or private media, install a runtime, change Android/provider/Pipeline code, or create an Owner authorization decision.
- Bind, rather than rewrite, the frozen P1A evidence and preserve `NOT_LEGAL_APPROVED`, unresolved transport, and all authorization flags false.

## Milestones

- [ ] M0: verify exact T5 base, clean isolation, and the T5 status/matrix before editing.
- [ ] M1: add the P1B readiness manifest, human-readable boundary, strict manifest schema, and future Owner-decision schema.
- [ ] M2: implement a stdlib-only fail-closed validator; normal mode must return `READY_FOR_OWNER_DECISION`, while `--require-execution-authority` must return non-zero `BLOCKED_OWNER_AUTHORIZATION_REQUIRED`.
- [ ] M3: add temporary-fixture negative tests for identity, bytes/hash, inventory, authorization, legal/transport, corpus, privacy, base, contract, path, and future-decision drift; include a static capability safety check.
- [ ] M4: after readiness validation passes, update current status, JSON status, gate matrix, and task authority to `P1B_AUTHORIZATION_PACKET_READY — EXECUTION_NOT_AUTHORIZED`; keep all external gates closed.
- [ ] M5: add exact-base/clean-worktree T6 scope guard and evidence report; prohibit Android, contracts, P1A evidence, runtime, Pipeline, and signing changes.
- [ ] M6: run focused validators, P1A/contract/privacy checks, diff check, bounded JVM/lint/Debug smoke; do not rerun the full API35 matrix for a metadata-only change.
- [ ] M7: perform exact SHA local independent review, push a stacked Draft PR from T6, record the final identity, and request remote evidence review while retaining `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE`.

## Stop rules

Stop and re-plan if T5 identities/status disagree, P1A frozen evidence needs edits, network/model/image/runtime/package access becomes necessary, any authorization is supplied during this stage, Android/contracts/provider/Pipeline/signing scope changes, or any validator/scope/privacy/test/review gate fails.

## Review

Final review must bind the exact T5 base and T6 head, list changed paths, show all authorization flags false, show the execution-authority negative gate blocked, prove no product/runtime scope, and distinguish local exact-diff review from the connector's dirty-main limitation.
