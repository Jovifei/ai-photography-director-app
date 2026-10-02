# T5 Program Status / Roadmap Authority Consolidation

Remote planning task: `c2c_b592`. Base: T4 delivery head `e88d2ab1eb0c9c5cf9f891c5107d951fbbbe15cb`; reviewed product source `a69ede68f3e54e5ab006dbfd7a65c33040590f93`; T3 base `14a56f49e1220eb8139bf7280c747124118fdb21`. This stage is documentation and status control only.

## M0 — exact-base isolation

- Create a clean T5 branch from the exact T4 delivery head. Dirty Owner `main` remains byte-for-byte unchanged.
- Confirm T4 handoff, delivery head, reviewed product source, T3 base and PR #8 metadata. Stop on any mismatch or concurrent branch movement.
- Allowed data: public Git SHAs, branch/PR identifiers, documented test counts and high-level Gate states.

## M1 — canonical current status

- Maintain `docs/CURRENT_PROGRAM_STATUS.md` and `docs/current_program_status.v1.json` as the only forward-facing current-status authority.
- Bind the delivery head, reviewed product source, T3 base, PR state, qualification state, local review state and `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE` limitation.
- Keep the distinction `VALIDATED DELIVERY STACK ≠ MERGED MAIN ≠ PHYSICAL DEVICE PASS ≠ REAL AI PASS ≠ RELEASE`.
- Do not include absolute paths, media, device IDs, IPs, pairing material, model paths or secrets.

## M2 — forward-facing document reconciliation

Update only documentation/task pointers. Preserve old reports and locked decision content. Add a small historical preamble or pointer to:

- `README.md`: point readers to current status and next-gate matrix; remove G0/AH0 or old P25 release claims as current instructions.
- `docs/PROGRAM_HANDOFF_REFERENCE.md`: retain provenance and contract integrity; label G0/AH0 as historical and point to current status.
- `tasks/todo.md`: put T4 delivery and T5 consolidation at the top-level current authority; move August Closed Beta/P21 text under historical context.
- `docs/00_ANDROID_FIRST_EXECUTION_PLAN.md` and `docs/01_CAMERAX_AND_POSE_SPIKE.md`: label bootstrap/pose plans historical; preserve their constraints.
- `docs/P25T_PRODUCT_AND_PIPELINE_ACTION_PLAN_20260921.md`, `docs/UI1_P1B_ENGINEERING_COMPLETION_PLAN.md` and `docs/P25R_OWNER_FINAL_REVIEW_GUIDE.md`: label stage-specific plans historical or scope-specific, retaining evidence and Owner decisions.
- `docs/ANDROID_BETA_PHONE_SETUP.md` and `docs/BETA_USER_GUIDE.md`: mark pilot/Qwen/physical-device instructions as historical references and link current status/gate authority.

Do not retroactively rewrite historical reports or Owner decisions. Do not require an external Obsidian edit for Git qualification; Obsidian P22/P24 notes remain historical support only.

## M3 — next-gate matrix

Maintain `docs/NEXT_GATE_MATRIX.md`. Separate existing evidence from missing authorization/evidence for Provider/Qwen, Private LAN, Pipeline, human editorial, physical device, Pose, signing/release and additional offline work. A matrix row must never authorize execution by itself.

## M4 — drift prevention

Add `scripts/validate_program_status.py` and `scripts/test_program_status.py` using only Python standard library. The validator must reject:

- malformed or inconsistent SHA identities;
- mismatch between JSON and Markdown current status;
- claims that PR #8 is merged;
- omission of `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE`;
- missing or promoted external Gate statuses;
- a forward-facing document that presents G0/AH0/P21/P22/P24/old Closed Beta/P1A as current execution authority;
- missing pointers from README/current documents to the canonical status entry.

Tests must include negative fixtures for false main merge, Qwen connected, Pipeline integrated, physical-device PASS, missing review limitation and conflicting SHA; historical text with an explicit historical label must pass.

## M5 — qualification and evidence

Required from the clean T5 worktree:

```text
python scripts/test_program_status.py
python scripts/validate_program_status.py
python scripts/test_phase1_5_contracts.py
python scripts/prepush_privacy_audit.py
git diff --check
cd android
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The path guard must prove the diff contains only approved documentation, task, status-validator and report paths. No Android/API 35 rerun is required for a documentation-only delta. If any Android, shared-contract, model/runtime, Provider, CameraX, Pipeline or signing path changes, stop and create a new product-code plan.

## M6 — review and delivery loop

- Write `reports/T5_PROGRAM_STATUS_CONSOLIDATION_REPORT.md` with exact base/final SHA, changed paths, validator results, smoke results, status/gate classifications and review boundary.
- Run the privacy audit before each push; commit in reviewable units and push only the T5 branch.
- Create a Draft PR stacked on the T4 branch containing `e88d2ab`, never on dirty `main`.
- Perform local exact-diff independent review. Then send the execution receipt and PR metadata to remote ChatGPT. If the connector still reads dirty main, retain `C2C_CODE_DIFF_REVIEW_NOT_POSSIBLE` and do not call that a code-diff PASS.
- Only after local and remote evidence review is accepted, start a new C2C task for the next-gate plan. No runtime track is selected without new Owner authorization.

## Hard stops

- T4 identities or PR head drift;
- dirty main must be modified, cleaned, rebased or merged;
- document work requires Android/runtime/contract changes;
- a status document promotes Qwen/LAN, Pipeline, human editorial, physical-device, signing, beta/release or main merge without its own Gate;
- external Obsidian editing becomes necessary;
- any validator, privacy, contract, build or smoke check fails.

## Data boundary

Allowed: Git SHAs, branch/PR identifiers, test counts/status, contract versions, high-level Gate states, public model/corpus identifiers already tracked in approved docs and historical/current classifications. Forbidden: real/user photos, Picker URIs, private paths, database contents, device serials, LAN/IP details, pairing codes, TLS private material, credentials, tokens, model weights, raw provider output, reviewer personal data and signing secrets.

## Review status

Local T5 qualification is complete through candidate `d64ad4c7e03563c57766b2673b2d734511f0a2b9`: program-status tests, scope guard, contracts, privacy, diff check, JVM 141/141, lint 0 errors/10 warnings and Debug assemble passed. The final receipt-only commit will be pushed as the T5 Draft PR head; local exact-diff review and remote evidence review remain required before T5 is marked DONE.
