# T13 explicit Bundle guidance replacement

Remote T12 DONE and T13 PLAN_FEEDBACK: `c2c_2537`, iteration 1, original Ai相机 chat. Base: accepted PR #17 head `6685dfe5792b45c00081249f16d83e7782927900`. Branch: `codex/t13-bundle-explicit-replacement-20261002`.

## Contract and implementation

Keep initial import, PKB1 parsing, Room v6 and existing commit-outcome handling. Add an explicit replacement mode to the existing importer, never a second importer. Replacement is limited to Bundle READY targets with complete provenance and no provider provenance. In-progress analysis states are rejected; a historical attempt field on a terminal READY row is not an automatic rejection.

Require incoming bundleId, producerId, origin and producerReferenceId to match each old association. Different release IDs carry no ordering or newness claim. Same release/different SHA rejects identity conflict; same release/same SHA is already applied without changing timestamps. Mixed initial/replacement batches reject.

Preview is read-only and freezes exact project/reference IDs, old complete provenance and incoming identity. Confirmation re-reads every target inside the existing Room transaction and compares all old provenance fields before any write. One stale/deleted/provider target rejects the whole batch. Preserve originals and capture relationships. Unknown commit goes to the existing inspect-project flow.

Remote set-boundary decision: only the exact incoming explicit mapping set is replaced. Existing same-bundle/source rows omitted by the incoming release remain byte-for-byte unchanged, with their prior provenance; no inferred membership synchronization or deletion. UI names the selected mapping scope. Qualification adds a 3-old/2-replaced/1-unchanged synthetic Room case.

## Checklist

- [x] Remote T12 review DONE, T13 policy refined and approved; origin/base verified.
- [x] Reuse accepted exact T12 baseline evidence and verify PKB1/Phase1.5 contracts.
- [x] Add replacement policy and repository transaction fence with synthetic Room negative/rollback tests.
- [x] Add ViewModel preview/confirm/cancel and invalidate preview on mapping/mode/document changes.
- [x] Extend existing import UI/mapping eligibility and root callbacks; show complete old/incoming provenance with explicit confirmation.
- [x] Test initial-import regressions, replacement, cancel, provider/mixed/identity conflict, stale-preview, atomic failure, unknown-commit and omitted-member behavior.
- [x] Final JVM 147/147, build/static lint PASS, API35 combined 73/73.
- [ ] Independent review, privacy audit, non-force push Draft PR stacked on T12; remote review and next plan.

## Acceptance

`BUNDLE_RELEASE_REPLACEMENT_MACHINE_VALIDATED — PIPELINE_PRODUCER_COMPATIBILITY_PENDING — HUMAN_ACCEPTANCE_NOT_RUN` only after tests and review. No producer implementation, network/runtime, Qwen, iOS, signing, merge or human acceptance. T11 authority remains unchanged.
