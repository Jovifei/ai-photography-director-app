# Current authority — 2026-09-27

Read [`docs/CURRENT_PROGRAM_STATUS.md`](../docs/CURRENT_PROGRAM_STATUS.md) and [`docs/NEXT_GATE_MATRIX.md`](../docs/NEXT_GATE_MATRIX.md) before using any older task entry. The current delivery stack is T4 `e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb` with reviewed product source `a69ede68f3e54e5ab006dbfd7a65c33040590f93`, PR #8 Draft/unmerged. T5 is documentation/status authority consolidation; it does not authorize runtime, network, real-photo, device, Pipeline, signing or release work. `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE` remains an explicit remote-review limitation.

## T8 synthetic evaluation harness readiness — 2026-09-27

Remote plan `c2c_f8b2` selected this contract-only, image-free, human-free dry-run stage from exact T7 head `61cb6776b2b6f00d6965789e0589c5337536d258`. The harness must prove the future Envelope → blinded review → adjudication evidence flow without executing a Provider or creating human-review evidence. The detailed checklist is [`tasks/plans/2026-09-27-t8-synthetic-evaluation-harness.md`](plans/2026-09-27-t8-synthetic-evaluation-harness.md).

- [x] Verify exact T7 authority and isolation.
- [x] Add synthetic manifest, schemas and JSON-only harness.
- [x] Add deterministic synthetic fixtures and negative tests.
- [x] Update status authority only after harness validation passes.
- [x] Run T6/T7 regressions, contracts/privacy/scope and bounded Android smoke.
- [x] Perform exact review, push stacked Draft PR and obtain remote evidence review.

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
- [x] Perform exact review, commit/push Draft PR stacked on T8, record evidence and request remote review.

## T9-R2 tracked-fixture authority closure — 2026-09-28

Remote review `c2c_9e31` confirmed the T9 route, PR #13 identity and R1 behavior repairs,
then required one fail-closed authority guard: tracked fixtures must stay synthetic-only.

- [x] Enforce `SYNTHETIC_CONTRACT_ONLY` for every tracked fixture; keep `REAL_PROVIDER_RESULT` unit-test-only.
- [x] Add a negative test that mutates a tracked fixture to `REAL_PROVIDER_RESULT` and proves `run()` rejects it.
- [x] Reconcile canonical clean-clone review wording without rewriting historical T4 evidence.
- [x] Requalify T9, update the report to 13/13 and record `TRACKED_FIXTURE_SYNTHETIC_ONLY=PASS`.
- [x] Push the final T9 candidate and obtain remote `DONE` on exact head.

## T10 Owner Decision Capture / P1B Route Resolution — 2026-09-28

Remote plan `c2c_4f7b` selected an Owner-decision stage from exact T9 head
`dd5d297ffa26adc38470bfa5737ac82b94eaa2ec`. Jovi explicitly selected
`AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`; this stage records and validates that
choice without downloading or running the artifact.

- [x] Confirm exact Owner decision and exact T9 base.
- [x] Record the decision with the existing P1B schema and exact artifact binding.
- [x] Add the T10 validator, 8/8 tests with 16-case decision-integrity matrix, status projection, report and scope guard.
- [x] Run T10/P1B/T9/T8/T7/status/contracts/privacy/scope and bounded Android checks.
- [x] Perform exact review, push Draft PR #15 stacked on PR #13, and obtain remote `DONE`.

## T10-R3 portable decision identity repair — 2026-09-29

Remote C2C c2c_8b2a corrected its plan after local byte-level evidence showed that the prior recorded checkout hash was not portable. The stable identity is SHA-256 45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9 over strict UTF-8 text with only CRLF/lone-CR normalized to LF. Mixed-EOL raw hash 646626a536eee32bcf171927090a10d9c8df06b9890c2ec3fcd788964a617bdd and all-CRLF raw hash 57b07ab70d34b64802be4a765e04479bdd66bd079958510029b56c133b49425b are NON_AUTHORITY_DIAGNOSTIC_ONLY. Work stays on T10 / PR #15 from exact repair base d332ba8ee6f1ea036be655de8b09f8c9f4180592; the Owner decision and all closed authority flags remain unchanged. T11 is blocked until remote DONE, then must start from a fresh worktree. The frozen T6 P1B manifest stays unchanged with `authorization.owner_decision` equal to `NONE_TRACKED`; the separate tracked T10 decision is validated against it and is not represented in the manifest.

Detailed plan: tasks/plans/2026-09-29-t10-portable-decision-identity-repair.md.

- [x] Reproduce T10 validator failure in a clean checkout and confirm P1B manifest remains stable.
- [x] Verify mixed-EOL and all-CRLF source bytes normalize to the same 45e462 portable digest.
- [x] Obtain corrected remote plan and exact path boundary before implementation.
- [x] Implement the portable T10 decision hash, canonical status migration/mode, report update, tests, and exact-base scope guard.
- [x] Run the T10 and P1B unit suites, T10/P1B/status validators, contract and status tests, privacy audit, authority probes, diff check, and exact-base scope guard after commit; record the addendum evidence fields.
- [x] Review exact diff, push Draft PR #15, and obtain remote DONE on the repaired head.
- [x] Rebind T11 to the accepted T10-R3 head using a fresh worktree.
## T11 Artifact Acquisition Authorization Preflight / Legal-Transport Readiness — 2026-09-29

Remote C2C task c2c_f785 selected a non-network preflight from exact accepted T10-R3 head 2ff77cadc8696cbd06a504458da4d58c74aa6133. The old pre-R3 T11 worktree is superseded; this is a fresh managed worktree and branch. T10 portable decision mode/SHA is UTF8_TEXT_EOL_NORMALIZED_SHA256_V1 / 45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9. The T6 P1B manifest remains NONE_TRACKED and is byte-identical; the T10 quarantine-only decision is separate. T11 must end blocked on legal/transport/metadata/destination evidence and later Owner authorization; it does not authorize artifact acquisition. Frozen T6 P1B source tuples retain their exact Windows-checkout bytes/hashes under FROZEN_P1B_CHECKOUT_BINDING_V1, with cross_platform_canonical=false. Git-blob/EOL-normalized differences are NON_AUTHORITY_DIAGNOSTIC_ONLY and must be called EOL_ONLY_CHECKOUT_REPRESENTATION_DIFFERENCE when CRLF-to-LF explains them. Any P1B validator failure or non-EOL source-content drift is SOURCE_CONTENT_DRIFT — STOP. Do not modify P1A/P1B records or recompute replacement authority hashes.

Detailed plan: tasks/plans/2026-09-29-t11-artifact-acquisition-preflight.md.

- [x] Create a fresh branch from exact T10-R3 head and run M0: T10/P1B/status validators PASS, legal NOT_LEGAL_APPROVED, transport REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED, all authority flags false.
- [x] Confirm no T11 implementation files exist at the accepted base; read the remote T11 plan.
- [x] Record the exact 2ff77ca base, portable decision mode/SHA and P1B manifest NONE_TRACKED boundary.
- [x] Add T11 preflight manifest, schemas, capability-free validator, 41-case coverage, authority probes, canonical status/report, and exact-base scope guard.
- [x] Resolve the T10/T11 status conflict with remote GPT: keep top-level current_stage at T9 and add the T11 nested projection; do not modify the T10 validator.
- [x] Requalify portable T11 manifest identity: T11 34/34, LF/CRLF/mixed authority equal, stale and wrong Owner manifest bindings rejected, T10 12/12, P1B 11/11, T9 13/13, T8/T7 15/15, status 8/8, contracts/validators/privacy PASS, five probes blocked, Android JVM 27 tasks PASS.
- [x] Re-run exact-base scope guard on the portable-identity repair head: PASS for 18 paths; Owner/P1B blobs and T10 validator unchanged.
- [x] Local exact read-only re-review: `PASS_NO_ACTIONABLE_FINDINGS`; hypothetical authority projections, terminal Owner choices, portable manifest binding, and negative binding cases reviewed.
- [x] Push initial T11 implementation as Draft PR #16 stacked on #15; remote exact-head review confirmed the scope/authority model and identified the portable manifest identity repair.
- [x] Repair T11 manifest identity with `UTF8_TEXT_EOL_NORMALIZED_SHA256_V1`; T11 34/34 with LF/CRLF/mixed equality, stale/wrong binding rejection, and malformed-UTF-8 rejection.
- [x] Commit portable manifest identity repair as `7edffea940344107678bc3648e1af4f81bbf5f04`.
- [ ] Push the repair, update Draft PR #16, record iteration 2 evidence, and obtain remote exact-head DONE.
- [ ] Run final pre-push privacy audit; keep PR #16 Draft/open/unmerged.
- [ ] Keep T11 blocked until separately authorized external evidence or an Owner defer/reject decision arrives; do not download or execute the artifact.
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
- [x] Commit/push the repaired T8 candidate and request remote re-audit before planning any next stage.

### T8-R review

Local focused and regression checks are passing. The first Android invocation lacked
an SDK variable in the isolated checkout; the bounded JVM smoke was rerun with the
existing user SDK path supplied per command and passed. Exact clean scope/diff,
privacy-before-push, evidence recording, commit/push and remote re-audit are complete.

## T8-R3 implementation conformance repair — 2026-09-27

The clean review connector independently found four remaining gaps in the
existing T8 candidate. Stay on PR #12 and do not start a new phase.

- [x] Add automated criterion 18 as a contract-derived 2-point score; prove 36/36 → 100.00.
- [x] Execute the tracked T8 manifest schema and per-case evaluation summary schema.
- [x] Remove unstructured warnings from the reviewer payload and test warning-based provenance leakage.
- [x] Refresh audit/report binding language for the clean review clone while retaining the old main limitation accurately.
- [x] Requalify, push, record sanitized evidence, and request final remote T8-R3 review.

## T8-R4 evidence-state closure — 2026-09-27

Remote source review found the implementation complete and requested only an
explicit schema self-check plus final report wording reconciliation.

- [x] Add explicit Draft 2020-12 `check_schema()` evidence lines for manifest and summary schemas.
- [x] Mark all repaired conformance rows PASS and remove historical R3-gap wording.
- [x] Requalify, push, record the final receipt and obtain remote DONE.

## T8-R5 final receipt correction — 2026-09-27

Remote source review found only two evidence details after R4: the invalid
schema fail-closed negative was not named, and the report count lagged the
15-test suite.

- [x] Add invalid T8 schema negative coverage and explicit receipt line.
- [x] Correct the report to 15/15 and retain all final gate lines.
- [x] Requalify, push, record the final receipt and obtain remote DONE.

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
## T12 Android capture reference and guided retake — 2026-10-01

Remote PLAN `c2c_2537` iteration 1 accepts independent Android development. Detailed checklist: `tasks/plans/2026-10-01-t12-capture-reference-retake.md`. T11 evidence stays unchanged; nightly producer integrates later through PKB1.

- [x] Exact clean base, origin and C2C identity verified.
- [x] Implement current reference resolver, truthful gallery/detail and safe root open/retake.
- [x] Verify focused JVM/UI/root tests and affected regressions on dedicated emulator.
- [x] Exact-diff independent review returned PASS with no findings; final privacy audit passed.
- [x] Push the isolated branch non-force and create Draft PR #17, stacked on T11 / PR #16.
- [x] Remote T12 review DONE; T13 explicit replacement plan and identity policy approved.

Review: local exact-diff PASS; remote T12 DONE on 2026-10-02. No human acceptance or production Pipeline claim.

### T12 root-flow defect discovered

The real `MainActivity` root test persisted the synthetic capture with exact project/reference IDs and independently verified the project and READY Bundle reference through the writer repository. The production UI nevertheless displayed `拍摄项目已不存在` and kept both reference actions disabled. A diagnostic Compose dump confirmed the UI resolver had a stale projects/reference Flow from a second same-name Room database instance. Remote PLAN_FEEDBACK approved a process-scoped Room singleton factory; DB schema, migrations, journal mode, permissions, files and backup policy stay fixed. Production repositories remain distinct. A new cross-repository persistent-collector test covers both project and reference Flow invalidation.

### T12 verification update — 2026-10-01

- [x] Share the production `ReferenceLibraryDatabase` instance per process; keep Room v6 and `createForTest` isolated.
- [x] Preserve click-time DB revalidation and enabled open/retake semantics on the default root.
- [x] Verify project/reference Flow updates across two production Repository objects.
- [x] Run T12/T4 mixed tests 10/10, full affected batch 43/43, export retest 3/3, Room disk/process recovery prepare/force-stop/verify/cleanup 3/3, Android JVM 147/147.
- [x] Build Debug/AndroidTest and run debug plus static Release lint; release signing remains `NOT_RUN`.
- [x] Run PKB1/Phase1.5 contracts, T11 preflight and privacy audit. T11 stays `BLOCKED_LEGAL_REVIEW`; database schema/migrations, Bundle and T10/T11 authorities stay unchanged.
- [x] Exact final source review PASS and final `scripts/prepush_privacy_audit.py` PASS.
- [x] Push non-force and create stacked Draft PR #17 against `codex/t11-artifact-acquisition-preflight-20260929`.
- [x] C2C Doctor green; remote T12 DONE and next T13 plan received.

Remote approved the T12 process restart gate. Final synthetic run `d01405dcefab40b0aa3a10170564fef9` passed: PREPARE ran as process `com.jovi.photoai`, target UID 10209, PID 8457; the instrumentation package UID is 10210. PREPARE's process had already exited naturally, so target-package force-stop was explicitly recorded as idempotent; VERIFY ran under PID 8512 with a changed epoch and reopened the same project/reference. CLEANUP deleted only that project and host marker. Synthetic project/reference/JPEG writes were scoped to that run ID; no Android app/test marker file was created and MainActivity was not launched. The legacy P23D `clearAll()` harness is not run against unknown App rows.

T12 details and initial failure evidence are recorded in `reports/T12_CAPTURE_PROVENANCE_RETAKE_REPORT.md`; raw synthetic logs/APK metadata remain outside Git under the Codex visualizations evidence folder. No human acceptance or photo-quality judgement is claimed.

## T13 explicit Bundle replacement — 2026-10-02

Remote T13 plan and identity refinement approved in `c2c_2537`. Base accepted PR #17 head `6685dfe5792b45c00081249f16d83e7782927900`; branch `codex/t13-bundle-explicit-replacement-20261002`. Detailed milestones: `tasks/plans/2026-10-02-t13-bundle-explicit-replacement.md`.

- [x] Inspect existing PKB1 importer/Room transaction/provenance; confirm policy with remote.
- [x] Implement shared replacement policy and all-target transaction fence.
- [x] Implement explicit mode, read-only preview, cancel and confirm in existing ViewModel/UI.
- [x] Final synthetic/affected Android batch 73/73; JVM 147/147; Debug/AndroidTest builds and static lint PASS. Independent final review PASS.
- [x] Independent review/privacy PASS; non-force push and stacked Draft PR #18; remote evidence and key-source audits DONE.

T13 code commit `b6f655bd58bb875f18915281b56f2c5f816b158b` is accepted for machine implementation. T14 has no published PKB1 golden-vector handoff in the inspected producer main tree `ffc4130823c1308f089b835c766e341ec2173e82`; no compatibility/production acceptance is inferred. Remote agreed to inspect and refine independent T15 zoom/lens-switch work while that handoff is pending.

## T15 CameraX capability controls — 2026-10-02

Remote plan/policy: `c2c_8b15`. Base PR #18 head `58119b8541a49e27eedff2497c306e9212c3d654`; branch `codex/t15-camera-capability-controls-20261002`. Detailed milestones: `tasks/plans/2026-10-02-t15-camera-capability-controls.md`.

- [x] Implement actual capability discovery, bounded/fenced zoom, lens bind/rollback and busy capture guards.
- [x] Integrate confirmed saveable lens/zoom, fresh binding restore, explicit fallback/retry and manual chrome controls.
- [x] Strict builds/static lint and JVM155/155 PASS; independent source review PASS.
- [x] Current BACK-only API35 81/81, separate software FRONT auxiliary7/7; original AVD runtime restored, config.ini unchanged and no data wipe.
- [x] Update report, privacy audit, non-force stacked Draft PR #19 and remote source/evidence review.

Remote current-source review `c2c_8b15` iteration1 explicitly re-read clean PR19 code HEAD `3d6204887363c586fb8fd227975394d4a8c745d3` through the exact bound connector and returned DONE / PASS without tool limitations. T16 lifecycle, permission and accessibility hardening is next; its final qualification must be new evidence.

## T16 lifecycle, permission and accessibility — 2026-10-02

Plan: `tasks/plans/2026-10-02-t16-camera-lifecycle-accessibility.md`. Base accepted T15 plus documentation `a8b67bcd0f2344c027b65d6ea8ed72cef8a11239`; remote detailed plan and concrete state/semantics refinements approved.

- [x] Current source audit and detailed plan confirmed with remote.
- [x] Failing tests, minimal lifecycle/settlement repairs and adaptive accessibility fixes; preserved tap-focus hit testing after the final regression exposed interception.
- [x] Dedicated emulator lifecycle, exact permission recovery, font1/2 and affected regression qualification: final88/88, JVM159/159, builds/static lint0errors/17warnings, original permission/allflags/font/config restored.
- [ ] Independent review, report/privacy, stacked Draft PR, remote current-source review and next stage.
