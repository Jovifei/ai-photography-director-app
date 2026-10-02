# T7 Evaluation Governance / Human-Review Protocol Readiness

## Authority and boundaries

- Exact base: `3233313af8be946cc43245daf15f24f853933d88` (reviewed T6 receipt head).
- Keep Owner `main` untouched; use only the isolated T7 branch/worktree.
- Metadata/validator-only. No model, artifact, network, image, private-media, human-review, device, Pipeline, signing, release or merge action.
- Preserve the existing 18-criterion rubric and `PROPOSED_OWNER_THRESHOLD — NOT_MEASURED`; do not promote thresholds or create review results.

## Milestones

- [ ] M0: verify exact T6 status/matrix and clean isolation.
- [ ] M1: add provider-neutral governance manifest/schema encoding 18 criteria, verifier classes, scores, threshold state, blinded provenance and deterministic adjudication.
- [ ] M2: add future review-packet schema and human-review boundary documenting role-only identities, media prohibition and unapproved thresholds.
- [ ] M3: add stdlib-only fail-closed validator and negative tests for rubric drift, score/adjudication/privacy/media/authority/provider/Pipeline/uncertainty violations.
- [ ] M4: update canonical status/matrix to `EVALUATION_GOVERNANCE_PACKET_READY — HUMAN_REVIEW_NOT_AUTHORIZED` while retaining T6 P1B execution-not-authorized and all external gates closed.
- [ ] M5: add exact-base/clean-worktree scope guard and evidence report.
- [ ] M6: run T7 tests, T6 regressions, contracts/privacy, bounded Android smoke inherited without Android changes, exact local review, push stacked Draft PR and obtain remote review.

## Stop rules

Stop and re-plan if T6 authority disagrees, any historical rubric semantics must be changed, a real reviewer/image/provider/Pipeline/network/runtime/device action becomes necessary, thresholds are promoted, any privacy/contract/Android path changes, or a required validator/scope/review gate fails.
