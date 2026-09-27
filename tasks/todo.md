# Current authority — 2026-09-27

Read [`docs/CURRENT_PROGRAM_STATUS.md`](../docs/CURRENT_PROGRAM_STATUS.md) and [`docs/NEXT_GATE_MATRIX.md`](../docs/NEXT_GATE_MATRIX.md) before using any older task entry. The current delivery stack is T4 `e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb` with reviewed product source `a69ede68f3e54e5ab006dbfd7a65c33040590f93`, PR #8 Draft/unmerged. T5 is documentation/status authority consolidation; it does not authorize runtime, network, real-photo, device, Pipeline, signing or release work. `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE` remains an explicit remote-review limitation.

## T8 synthetic evaluation harness readiness — 2026-09-27

Remote plan `c2c_f8b2` selected this contract-only, image-free, human-free dry-run stage from exact T7 head `61cb6776b2b6f00d6965789e0589c5337536d258`. The harness must prove the future Envelope → blinded review → adjudication evidence flow without executing a Provider or creating human-review evidence. The detailed checklist is [`tasks/plans/2026-09-27-t8-synthetic-evaluation-harness.md`](plans/2026-09-27-t8-synthetic-evaluation-harness.md).

- [x] Verify exact T7 authority and isolation.
- [x] Add synthetic manifest, schemas and JSON-only harness.
- [x] Add deterministic synthetic fixtures and negative tests.
- [x] Update status authority only after harness validation passes.
- [x] Run T6/T7 regressions, contracts/privacy/scope and bounded Android smoke.
- [ ] Perform exact review, push stacked Draft PR and obtain remote evidence review.

## T9 Product READY promotion gate — 2026-09-27

Remote plan `c2c_9e31` selected this non-runtime, image-free, provider-neutral
stage from exact T8 authority `acf43a8d734675ec23da534472cade129b7b1068`.
T9 defines the future promotion boundary without authorizing Provider execution,
human review, Pipeline, device, signing, release or merge work.

- [x] Verify exact T8 authority and isolated T9 branch.
- [x] Add machine-readable promotion policy and strict candidate/decision schemas.
- [x] Bind frozen Envelope/Bundle/error-policy/T7/T8/offline-bundle identities.
- [x] Implement deterministic fail-closed promotion validator and authority probes.
- [x] Add synthetic blocked fixtures, Demo truth and hypothetical-only positive branch.
- [x] Add negative/capability/privacy/offline-bundle preservation tests.
- [x] Update canonical status/matrix/report without changing Android or frozen contracts.
- [x] Run T8/T7/T6/knowledge-bundle/contract/privacy/scope and bounded Android regressions.
- [ ] Perform exact review, commit/push Draft PR stacked on T8, record evidence and request remote review.

## T8-R post-submission conformance repair — 2026-09-27

Remote audit `c2c_aa42` returned `REPAIR_REQUIRED`: the previous T8 receipt proved
the high-level status but did not prove that the runner executed the frozen
ProviderAnalysisEnvelope/ReferenceBundle/error-policy validators or the complete
R1/R2/R3 and non-success exclusion paths. Stay on T8/PR #12; do not start T9.

- [x] Wire the runner to the frozen Draft 2020-12 Envelope and Bundle schemas plus semantic and error-policy validators.
- [x] Enforce SUCCESS reference identity and validated Bundle eligibility before review packets.
- [x] Implement an allowlist-only provider-blinded projection and provider-invariance tests.
- [x] Add complete R1/R2/R3 lifecycle fixtures/tests, including severe finding persistence.
- [x] Add explicit FAILED/CANCELLED/Pipeline exclusion and frozen retry/fallback policy tests.
- [x] Add named negative/capability/scope tests without touching frozen contracts or Android.
- [x] Add a sanitized, readable audit evidence recorder and post-submission conformance table.
- [x] Re-run focused T8/T7/T6/status/contracts/privacy and bounded Android JVM smoke checks.
- [ ] Commit/push the repaired T8 candidate and request remote re-audit before planning any next stage.

### T8-R review

Local focused and regression checks are passing. The first Android invocation lacked
an SDK variable in the isolated checkout; the bounded JVM smoke was rerun with the
existing user SDK path supplied per command and passed. Exact clean scope/diff,
privacy-before-push, evidence recording, commit/push and remote re-audit remain.

## T8-R3 implementation conformance repair — 2026-09-27

The clean review connector independently found four remaining gaps in the
existing T8 candidate. Stay on PR #12 and do not start a new phase.

- [x] Add automated criterion 18 as a contract-derived 2-point score; prove 36/36 → 100.00.
- [x] Execute the tracked T8 manifest schema and per-case evaluation summary schema.
- [x] Remove unstructured warnings from the reviewer payload and test warning-based provenance leakage.
- [x] Refresh audit/report binding language for the clean review clone while retaining the old main limitation accurately.
- [ ] Requalify, push, record sanitized evidence, and request final remote T8-R3 review.

## T8-R4 evidence-state closure — 2026-09-27

Remote source review found the implementation complete and requested only an
explicit schema self-check plus final report wording reconciliation.

- [ ] Add explicit Draft 2020-12 `check_schema()` evidence lines for manifest and summary schemas.
- [ ] Mark all repaired conformance rows PASS and remove historical R3-gap wording.
- [ ] Requalify, push, record the final receipt and obtain remote DONE.

## T8-R5 final receipt correction — 2026-09-27

Remote source review found only two evidence details after R4: the invalid
schema fail-closed negative was not named, and the report count lagged the
15-test suite.

- [x] Add invalid T8 schema negative coverage and explicit receipt line.
- [x] Correct the report to 15/15 and retain all final gate lines.
- [ ] Requalify, push, record the final receipt and obtain remote DONE.

## T7 evaluation governance readiness — 2026-09-27

Remote plan `c2c_d4e1` selected this provider-neutral, metadata-only stage from exact T6 head `3233313af8be946cc43245daf15f24f853933d88`. The 18-criterion rubric remains `PROPOSED_OWNER_THRESHOLD — NOT_MEASURED`; T7 must not perform human review, use media, connect Pipeline, qualify a provider or authorize a launch threshold. The detailed checklist is [`tasks/plans/2026-09-27-t7-evaluation-governance-readiness.md`](plans/2026-09-27-t7-evaluation-governance-readiness.md).

- [x] Verify exact T6 isolation and canonical authority.
- [x] Add machine-readable governance and future review-packet schemas.
- [x] Add deterministic adjudication/privacy/uncertainty/provider-neutral validators and negative tests.
- [x] Update status authority only after validators pass.
- [x] Run regressions, scope/privacy/contract checks and bounded Android smoke.
- [ ] Perform exact-diff review, push stacked Draft PR and obtain remote evidence review.

## T6 P1B authorization readiness — 2026-09-27

Remote plan `c2c_7a6d` selected this metadata-only stage from exact T5 head `0b04abdc57a3fa332689cfe664f9bcb997160e5e`. The tracked P1A evidence remains frozen; no model download, network, image/private-media access, runtime, Android, Pipeline, signing or Owner authorization is permitted. The detailed checklist is [`tasks/plans/2026-09-27-t6-p1b-authorization-readiness.md`](plans/2026-09-27-t6-p1b-authorization-readiness.md).

- [x] Verify exact T5 isolation and re-read current status/matrix.
- [x] Add P1B readiness packet and future decision schema.
- [x] Add fail-closed validator and negative safety tests.
- [x] Update status authority only after readiness validation passes.
- [x] Run T6 scope, contract, privacy, validator, and bounded Android smoke checks.
- [ ] Perform exact-diff review, push stacked Draft PR, and obtain remote evidence review.

T5 documentation qualification: program-status tests PASS, scope guard PASS for 18 approved paths, contract/privacy PASS, JVM 141/141, lint 0 errors/10 warnings, Debug assemble PASS, Android source unchanged.

## P25U review remediation

## T4 offline product-flow integration — 2026-09-27

- [x] Bound remote ChatGPT stage PLAN `c2c_a91f`, corrected it against exact T3 code, and saved the revised milestone plan.
- [x] Created clean isolated branch from `14a56f49e1220eb8139bf7280c747124118fdb21`; origin T3 head matches.
- [x] Audited library, project naming, navigation, Camera truth boundaries and test inventory.
- [x] Implement all-project library entry, real search/filter/source/owner and coherent return route.
- [x] Implement explicit project creation name and persistent rename through existing Room title.
- [x] Add T4 JVM/Android tests; verify synthetic end-to-end flow and T3 regressions on API 35 emulator.
- [x] Run the clean exact-commit T4 qualifier, review its evidence and final diff.
- [x] Commit and deliver exact candidate for independent review; keep external Gates separate.

### T4 Review

- First exact candidate `fef13a1`: Debug/AndroidTest builds, JVM 141/141, lint 0 errors, T4 5/5, T3 + root + P25U capture/export 13/13, force-stop prepare/verify/cleanup, contract tests, privacy audit and diff check passed on the dedicated API 35 emulator. Independent exact-SHA review identified missing live-state coverage; this candidate is superseded by the replacement exact candidate below.
- Later Gradle JVM/lint attempts hit host virtual-memory pressure before completing; single-worker, bounded-JVM per-command reruns passed JVM 141/141, AndroidTest compilation and lintDebug. Generated `android/.kotlin/` is ignored rather than deleted; no system setting or other task process was changed. These interrupted attempts remain diagnostic failures, not PASS.
- Replacement exact source `a69ede68f3e54e5ab006dbfd7a65c33040590f93` qualified with T4 7/7, T3 7/7, root 2/2, P25U camera 1/1/export 3/3/recovery 3/3, JVM 141/141, build/lint/contract/privacy/diff PASS. Independent exact-SHA read-only review returned PASS with zero actionable findings; evidence and APK hashes matched. Draft PR #8 is open on the T3 branch, unmerged.
- Real photos, physical device, Qwen/LAN, Pipeline, signing and release are outside T4.

- [x] Verify independent review findings against the final source and existing evidence.
- [x] Add an Activity recreation export-result regression and observe the expected failure.
- [x] Preserve the active export token across Activity recreation and pass the regression.
- [x] Bind default-App recovery verification to the prepared capture ID and identify force-stop evidence in the report.
- [x] Run focused Android verification and refresh the source manifest/report/handoff.
- [x] Run privacy audit, non-force push, and update Draft PR #6.
- [x] Obtain a fresh independent review of the final delivery commit.

## Review

PASS: independent review of delivery 753163efddfd8e28a6b267ac70af95cdd86c282e returned FINDINGS=NONE and OWNER_LANDING=ALLOWED. Owner landing remains a separate decision; no merge was performed.

## T3 reference comparison and camera controls

- [x] Bind clean isolated branch to reviewed P25U SHA and confirm baseline build/tests.
- [x] Add current-primary-reference thumbnail and full-view flow with private-file failure handling.
- [x] Add lifecycle-fenced tap focus and capability-bound exposure compensation.
- [x] Verify real App navigation, camera capture, layout, rotation, and affected P25U regressions on dedicated API 35 emulator.
- [x] Fix Reviewer finding: wait for the initial persisted capture/export snapshot, select exactly one new available capture, and wait for exact-record cleanup; focused P25U export instrumentation passes 3/3.
- [x] Re-run full qualification on the fixed source and refresh exact source/evidence records.
- [x] Run final privacy audit and non-force push the updated branch; refresh Draft PR #7 metadata.
- [x] Obtain independent confirmation of the corrected final evidence report and PR head.

### T3 review

Independent review first found an Important race in the export test's pre-capture snapshot and cleanup identity. Fixed in `47c2d8f192f2d92a4fd07539bd9ff3bf28b1ecfa`; focused export instrumentation and full API 35 qualification pass. Reviewer then found a mismatch between the report PID and evidence; it was corrected to 8352. Final confirmation on PR head `663d52991f7e6a436e3c91c82a72e80f411119cd` is PASS with no remaining findings. Evidence is at `E:\project_benchmark_evidence\t3-reference-camera-controls-20260926\5750a52b8c9e4507a4f5b162a8799cba`, with 9/9 artifact hashes verified.
