# T11 Artifact Acquisition Authorization Preflight / Legal-Transport Readiness — 2026-09-29

## Remote plan, base, and route

Remote ChatGPT Project task c2c_f785 selected T11 from the T10-R3 DONE handoff. The accepted exact base is 2ff77cadc8696cbd06a504458da4d58c74aa6133. Branch: codex/t11-artifact-acquisition-preflight-20260929. PR #15 is the Draft/open/unmerged T10-R3 base; a completed T11 delivery will be a new Draft PR stacked on #15.

The route remains a non-network, metadata-only, capability-free preflight. T11 defines what external legal, transport, fresh-metadata, quarantine-destination and later Owner-decision evidence must exist. It does not collect that evidence and does not acquire the artifact. Target tracked status is ARTIFACT_ACQUISITION_PREFLIGHT_READY — EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED; current evaluation remains BLOCKED_LEGAL_REVIEW.

## T10-R3 authority binding

- T10-R3 exact head and T11 base: 2ff77cadc8696cbd06a504458da4d58c74aa6133.
- Owner decision: AUTHORIZE_ARTIFACT_QUARANTINE_ONLY.
- Decision identity mode: UTF8_TEXT_EOL_NORMALIZED_SHA256_V1.
- Portable decision SHA-256: 45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9 (969 normalized UTF-8 bytes).
- Owner-decision Git blob: 74d980e7dccdc67f659836b39126557d91e5460b.
- Frozen P1B readiness manifest SHA-256: 166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a.
- Frozen P1B manifest authorization.owner_decision remains NONE_TRACKED. T10 is a separate later decision record.
- Exact artifact: Qwen/Qwen3-VL-2B-Instruct; revision 89644892e4d85e24eaac8bacfd4f463576704203; model.safetensors; 4,255,140,312 bytes; LFS SHA-256 7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0.

## M0 — fresh exact-base authority check (completed before edits)

The paused pre-T10-R3 T11 worktree is superseded. This plan uses the fresh managed worktree from the accepted T10-R3 head. Before any T11 source edits, require all of:

- HEAD exactly 2ff77cadc8696cbd06a504458da4d58c74aa6133; clean worktree; origin points to Jovifei/ai-photography-director-app.
- T10 validator PASS with decision_hash_mode UTF8_TEXT_EOL_NORMALIZED_SHA256_V1 and decision_sha256 45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9.
- Owner decision remains AUTHORIZE_ARTIFACT_QUARANTINE_ONLY; artifact_quarantine_only=true; download/runtime/inference/App/Pipeline/private-media flags are all false.
- Owner decision blob, P1B manifest blob and manifest SHA match the accepted T10-R3 binding.
- P1B manifest remains authorization.owner_decision=NONE_TRACKED, legal_review_status=NOT_LEGAL_APPROVED, transport_status=REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED.
- P1B validator returns READY_FOR_OWNER_DECISION; its execution probe stays BLOCKED_OWNER_AUTHORIZATION_REQUIRED.
- T10 PR #15 is Draft/open/unmerged at the accepted head.
- No T11 implementation files exist at the accepted base.

Any mismatch stops T11 before edits.

## Non-negotiable boundary

T11 must not issue HTTP/HEAD/GET/Range requests, follow redirects, refresh Hugging Face metadata, download or Git-LFS-pull any artifact, make a legal conclusion, choose or commit a private quarantine path, install or execute a model/runtime, run inference, access photos/private media/Pipeline/device, change Android, sign, release, merge, or create tracked positive external evidence.

The currently proposed transport domains remain exactly huggingface.co and us.aws.cdn.hf.co. They are unverified proposals, not authorized destinations.

## Milestones

- [x] M0: Create a fresh branch from exact T10-R3 head, verify binding/origin/clean state, and pass T10/P1B/status validators with portable identity.
- [x] Read the renewed remote C2C T11 plan and rebind the prior T11 route to the new base.
- [ ] M1: Add the T11 preflight description, machine-readable manifest and strict schema. Bind exact T11 base, T10 decision path/mode/portable SHA/Git blob, P1B readiness ID/SHA and artifact identity.
- [ ] M2: Record current blocker set and fail-closed statuses: LEGAL_REVIEW_REQUIRED; TRANSPORT_VERIFICATION_REQUIRED; FRESH_METADATA_REVALIDATION_REQUIRED; QUARANTINE_DESTINATION_REQUIRED; OWNER_ACQUISITION_AUTHORIZATION_REQUIRED. Current legal=NOT_LEGAL_APPROVED, transport=REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED, fresh metadata=NOT_RUN, destination=NOT_DECLARED, future Owner acquisition decision=NONE_TRACKED, acquisition_authorized=false, artifact_body_access_authorized=false.
- [ ] M3: Add only the future legal-disposition input schema. Bind scope, model/revision and license-evidence identity; allow APPROVED_FOR_QUARANTINE_ACQUISITION, MORE_REVIEW_REQUIRED and REJECTED. No reviewer PII and no tracked positive record.
- [ ] M4: Add only the future transport-verification input schema. Bind model/revision/file/bytes/LFS SHA and initial/redirect/final domains. Reject unknown/unlisted domains, unresolved redirects, mirror substitution, identity drift and artifact_body_accessed=true. T11 makes zero network calls.
- [ ] M5: Add only the future fresh-metadata input schema. Rebind publisher/model/revision/license/filename/bytes/LFS SHA; any mismatch is ARTIFACT_IDENTITY_DRIFT with no fallback.
- [ ] M6: Add only the future quarantine-destination schema. Use an opaque destination ID, never an absolute path. Require an external quarantine directory, all in-repository/product/evidence/app-private flags false, artifact_only=true and at least 4,255,140,312 available bytes.
- [ ] M7: Add only the future acquisition Owner-decision schema. Do not track a decision instance. Future choices are DEFER, REJECT_ACQUISITION and AUTHORIZE_QUARANTINE_ACQUISITION_ONLY. Bind the T10 mode/SHA, T11 manifest, exact artifact, legal/transport/metadata evidence, destination ID and approved domains. Keep runtime/inference/App/Pipeline/private-media false.
- [ ] M8: Implement a deterministic, fail-closed preflight state machine. Current tracked inputs stop at BLOCKED_LEGAL_REVIEW; hypothetical complete external evidence may reach READY_FOR_OWNER_ACQUISITION_DECISION only in unit tests; a hypothetical future Owner authorization may reach READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN only in unit tests.
- [ ] M9: Prove no tracked positive legal/transport/metadata/destination/acquisition record exists. Positive fixtures remain temporary unit-test objects only.
- [ ] M10: Keep validator capability-free: no requests/httpx/urllib/socket/Hugging Face clients/Git LFS/curl/wget/model frameworks/torch/transformers/PIL/cv2/ADB/GPU. subprocess is permitted only in the separate Git scope validator.
- [ ] M11: Add 41 table-driven cases described below. Preserve the current tracked result at BLOCKED_LEGAL_REVIEW and keep positive paths unit-test-only.
- [ ] M12: Add four authority probes for artifact access, network probe, runtime and private media; all must return their expected BLOCKED codes.
- [ ] M13: Update canonical status/matrix after qualification. Preserve current_product_state=OFFLINE_ANDROID_PRODUCT_FLOW_VALIDATED; recommended next_authorized_stage=EXTERNAL_LEGAL_TRANSPORT_DESTINATION_EVIDENCE_OR_OWNER_DEFER. Do not rewrite T9/T10 as Provider-ready.
- [ ] M14: Add a report that distinguishes preflight readiness from legal approval, transport verification, acquisition authorization and artifact possession; include exact base/head/hash/status, blockers, no-network/no-artifact boundary and regressions.
- [ ] M15: Add scripts/validate_t11_scope.py. Pin exact base 2ff77cadc8696cbd06a504458da4d58c74aa6133; require a clean worktree, exact allowlist, unchanged Owner-decision and P1B-manifest blob IDs, and zero Android/shared-contract/P1A/P1B/T7/T8/T9/runtime/Pipeline/release changes.
- [ ] M16: Run T11/P1B/T10/T9/T8/T7/status/contracts/privacy/scope/diff checks and bounded Android JVM regression. No emulator or device is required; Android source/config must remain unchanged.
- [ ] M17: Review exact diff, run pre-push privacy audit, push a Draft PR stacked on #15, and obtain remote exact-head review. No merge.
- [ ] M18: After remote DONE, keep T11 blocked and wait for separately authorized external legal/transport/metadata/destination evidence or an Owner defer/reject decision.

## Required 41-case test coverage

1. Exact accepted T10-R3 identity loads.
2. Wrong T10 base is rejected.
3. Wrong portable T10 decision SHA is rejected.
4. Wrong T10 decision hash mode is rejected.
5. Any Owner decision other than quarantine-only is rejected for this route.
6. Any T10 execution flag set true is rejected.
7. Current tracked state evaluates to BLOCKED_LEGAL_REVIEW.
8. Model-card/license metadata alone cannot satisfy legal approval.
9. Missing legal record blocks.
10. MORE_REVIEW_REQUIRED blocks.
11. REJECTED yields terminal legal rejection.
12. Wrong legal model/revision is rejected.
13. Unresolved transport blocks.
14. Unlisted initial domain is rejected.
15. Unlisted redirect domain is rejected.
16. Unlisted final domain is rejected.
17. artifact_body_accessed=true is rejected.
18. Missing fresh metadata blocks.
19. Publisher drift is rejected.
20. Revision drift is rejected.
21. Filename drift is rejected.
22. Byte-count drift is rejected.
23. LFS SHA drift is rejected.
24. Missing destination blocks.
25. Repository destination is rejected.
26. Android-project destination is rejected.
27. Evidence-corpus destination is rejected.
28. Product-cache destination is rejected.
29. Insufficient capacity is rejected.
30. Tracked absolute/private destination path is rejected.
31. Hypothetical complete external evidence reaches READY_FOR_OWNER_ACQUISITION_DECISION.
32. Future Owner decision missing any evidence binding is rejected.
33. Wrong T10 hash in future decision is rejected.
34. Positive future decision cannot enable runtime.
35. Cannot enable inference.
36. Cannot enable App integration.
37. Cannot enable Pipeline.
38. Cannot enable private media.
39. Unit-only hypothetical full authorization reaches READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN.
40. Tracked current repository can never reach either positive state.
41. Validator has no network/downloader/model/device capability.

## Scope allowlist

- docs/phase1_5/t11/ARTIFACT_ACQUISITION_PREFLIGHT.md
- docs/phase1_5/t11/artifact_acquisition_preflight.v1.json
- docs/phase1_5/t11/artifact_acquisition_preflight.v1.schema.json
- docs/phase1_5/t11/legal_review_input.v1.schema.json
- docs/phase1_5/t11/transport_verification_input.v1.schema.json
- docs/phase1_5/t11/fresh_metadata_input.v1.schema.json
- docs/phase1_5/t11/quarantine_destination_input.v1.schema.json
- docs/phase1_5/t11/artifact_acquisition_owner_decision.v1.schema.json
- docs/CURRENT_PROGRAM_STATUS.md
- docs/current_program_status.v1.json
- docs/NEXT_GATE_MATRIX.md
- reports/T11_ARTIFACT_ACQUISITION_PREFLIGHT_REPORT.md
- scripts/validate_t11_artifact_acquisition_preflight.py
- scripts/test_t11_artifact_acquisition_preflight.py
- scripts/validate_program_status.py
- scripts/validate_t11_scope.py
- tasks/plans/2026-09-29-t11-artifact-acquisition-preflight.md
- tasks/todo.md

Explicitly forbidden: android/**, docs/phase1_5/p1a/**, docs/phase1_5/p1b/**, docs/phase1_5/t7/**, docs/phase1_5/t8/**, docs/phase1_5/t9/**, docs/reference/**, local_analysis_service/**, shared-contract/**, model/artifact files, network tooling, runtime code, Pipeline code, signing/release files. The T10 Owner decision and P1B manifest blob IDs must remain identical to base.

## Authority probes

- --require-artifact-access -> BLOCKED_ARTIFACT_ACQUISITION_AUTHORIZATION_REQUIRED
- --require-network-probe -> BLOCKED_TRANSPORT_VERIFICATION_AUTHORITY_REQUIRED
- --require-runtime -> BLOCKED_RUNTIME_AUTHORIZATION_REQUIRED
- --require-private-media -> BLOCKED_PRIVATE_MEDIA_AUTHORIZATION_REQUIRED

Each command must exit nonzero. No probe may turn capability on.

## Acceptance

- Exact branch base and HEAD M0 checks pass; portable T10 identity is valid from fresh LF/CRLF checkout representations.
- The tracked T11 preflight remains blocked with all five blockers and exact source identities.
- No positive external evidence or future acquisition decision is tracked.
- All 41 T11 cases pass; T10/P1B/T9/T8/T7/status/contracts/privacy/scope/diff regressions pass.
- The four authority probes block; P1B execution remains BLOCKED_OWNER_AUTHORIZATION_REQUIRED.
- Scope passes from exact base with Owner/P1B blobs unchanged and zero Android/source-contract/runtime/Pipeline/security-sensitive delta.
- Local exact review returns PASS_NO_ACTIONABLE_FINDINGS.
- Draft PR is stacked on #15, exact head is reviewed remotely, and no merge occurs.
- T11 ends at ARTIFACT_ACQUISITION_PREFLIGHT_READY — EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED, not acquisition-ready.

## Hard stops

- Any mismatch in T10 head, portable mode/SHA, Owner decision/blob, P1B manifest identity or any false execution flag.
- Any new/unlisted transport domain, identity/bytes/license drift, or unknown redirect.
- Any HTTP/HEAD/GET/Range, redirect-follow, model metadata refresh, download, Git-LFS fetch, artifact-body access or network probe.
- Any Codex-generated legal approval or destination record; any tracked positive external evidence.
- Any actual absolute/private quarantine path entering Git or sanitized report.
- Any runtime/model/inference/Android/Pipeline/private-media/device/signing/release/merge change.
- Any authority probe succeeds or any validator acquires an external capability.

## Expected handoff

After exact T11 source is implemented, tested, scope/privacy reviewed and pushed as a Draft PR stacked on #15, send C2C EXECUTED for task c2c_f785 iteration 1 with exact base/head, changed paths, 41-case result, regression evidence, all blockers and authority probes. Request remote exact-head review. No artifact acquisition is authorized.