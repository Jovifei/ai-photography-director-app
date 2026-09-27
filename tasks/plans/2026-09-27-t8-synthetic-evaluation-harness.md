# T8 Synthetic Evaluation Evidence Harness Readiness

## Authority and boundaries

- Exact base: `61cb6776b2b6f00d6965789e0589c5337536d258` (reviewed T7 receipt head).
- Contract-only synthetic JSON dry-run. No model, artifact, network, image/media, human reviewer, Pipeline, device, signing, release or merge action.
- Existing T6/T7 contracts and evidence are read-only dependencies; do not alter frozen fixtures or T7 governance semantics.

## Milestones

- [ ] M0: verify exact T7 authority and clean isolation.
- [ ] M1: add synthetic-run manifest, strict schemas and text boundary with explicit `CONTRACT_ONLY_SYNTHETIC_NO_PROVIDER_EXECUTED` markers.
- [ ] M2: add deterministic JSON-only harness for envelope eligibility, blinded projection and T7 adjudication/metrics states.
- [ ] M3: add synthetic fixtures for SUCCESS, FAILED, CANCELLED, Pipeline incompatibility, malformed/mismatched envelopes and R1/R2/R3 paths.
- [ ] M4: add negative tests for provenance blindness, authority flags, media/PII/path safety, threshold promotion, score/confidence confusion and no-provider states.
- [ ] M5: after harness validation passes, update canonical status/matrix to `SYNTHETIC_EVALUATION_HARNESS_READY — REAL_EVALUATION_NOT_AUTHORIZED` while preserving T6/T7 states.
- [ ] M6: add exact-base/clean scope guard, evidence report, regressions, contracts/privacy and bounded Android smoke; perform exact review, push Draft PR and request remote review.

## Stop rules

Stop if a real Provider, model, image, reviewer, Pipeline, network, device, external corpus, threshold approval, Android/contract change or unresolved validator/scope/review finding is required.
