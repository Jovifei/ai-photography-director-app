# T9 Product READY Promotion Gate

## Authority

- Exact T8 base: `acf43a8d734675ec23da534472cade129b7b1068`.
- Non-runtime, image-free, provider-neutral policy stage.
- No Provider/model/media/human review/Pipeline/device/signing/release/merge.
- Preserve the existing offline Photo Knowledge Bundle READY path.

## Milestones

- [ ] M0 exact T8 authority and clean isolation.
- [ ] M1 machine-readable promotion policy and bindings.
- [ ] M2 strict candidate and decision schemas.
- [ ] M3 deterministic fail-closed validator and blocker precedence.
- [ ] M4 synthetic blocked fixtures plus hypothetical-only positive branch.
- [ ] M5 offline-bundle and Demo truth preservation.
- [ ] M6 negative/capability/authority tests.
- [ ] M7 canonical status/matrix/report updates.
- [ ] M8 regressions, scope/privacy, bounded Android smoke and exact review.
- [ ] M9 Draft PR delivery and remote review.

## Acceptance

Current authority must never produce `ELIGIBLE_AFTER_ALL_REQUIRED_GATES` or
`trusted_ready_guidance_allowed=true`. The future eligible branch is unit-test
only. All current fixtures remain blocked/non-ready, with deterministic full
blocker lists and explicit Demo/unavailable truth.

## Hard stops

Stop if a frozen contract or Android path must change, offline consumer
semantics must change, any runtime/provider/media/network/device capability is
needed, or an authority probe succeeds.
