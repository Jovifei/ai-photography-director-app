# Local main branch reconciliation and docs maintenance

The E-drive `main` worktree was 86 commits behind `origin/main`. It is now fast-forwarded to `1b776ba9932a7fdc96112c8cb85c7258f3f7d6af`. Before that update, path-collision checks showed none between the 179 remote changed paths and the six tracked Owner edits or twelve untracked Docs/tasks paths; all Owner work remains unchanged in E.

This clean consolidation candidate starts from current `origin/main`. The independently reviewed T17 head `96ec9b3f0270636000acbea163a7b64e5ca5dffc` is a descendant by121 commits and contains the T3–T17 staged application stack. This local integration retains a full merge ancestry. PR #21 is still Draft; no GitHub main merge is implied.

Preserve branch semantics while classifying refs:

- P20/P21, P23A/C/D, P23B landing, and P24 work already in `origin/main` should not be re-applied.
- The exact reviewed T17 stack can be consolidated locally with its upstream history.
- PR #1 is stale/open although its head is already reachable from main; PR #3/#4 are closed/merged.
- PR #2 safe-landing preflight and its local-validation tip, UI0's isolated runtime-fix commit, and the P24 docs-refresh branch have commits outside both current main and T17. Inspect and validate each separately before inclusion or closing.
- PRs #5/#6 and the T3–T17 PR stack stay visible as Draft/review records until their own gates and remote review disposition are resolved.

The Owner-edited E-drive P1.5 scripts/tests remain an independent patch. `python scripts/test_phase1_5_contracts.py` returned75/75 PASS, and the privacy audit passed in E. Incorporate only after testing that exact patch against the integrated branch. The ten untracked Owner Docs are preserved and should be reviewed/categorized before any public commit or knowledge-base mirror.

At completion, document the source head, branch/PR inventory, local test totals, privacy/Docs results and remaining human/external gates. A clean local consolidation candidate or Draft PR does not itself authorize public merge, real-photo/provider/network tests, signing, or release.

## Execution checklist (2026-10-02)

- [x] Confirm latest `origin/main` SHA and preserve the dirty E-drive Owner worktree.
- [x] Merge the reviewed T3–T17 stack onto the latest-main integration branch.
- [x] Apply the six Owner-edited tracked files to the candidate through a clean three-way patch.
- [x] Classify P23B, P24 docs-refresh, UI0 and the older T11 plan by current target, exact SHA and local test status.
- [x] Update README, current Android status, Gate matrix, branch inventory, task ledger and correction lesson.
- [x] Run local P1.5/PKB1, Python, JVM, Debug/AndroidTest builds, Debug lint, and APK metadata verification.
- [x] Final `prepush_privacy_audit.py` and `git diff --check` pass on the candidate.
- [x] Commit the staged code/docs and push the Draft PR #22 non-force; PR remains open and unmerged.
- [ ] Re-run C2C Doctor and obtain overall exact-SHA ChatGPT review after the local host security block is resolved.
- [ ] API 35 instrumentation remains `NOT_RUN` because no local AVD exists; Release lint remains `BLOCKED` by missing signing input.

## Review

Local JVM is `PASS 159/159`; Debug and AndroidTest builds plus Debug lint are `PASS`. Python unittest is `247 PASS / 1 SKIP / 1 FAIL` because an external P1A evidence directory is absent; signing and local API 35 remain separate blockers. The complete review and branch table are in `docs/LOCAL_MAINLINE_RECONCILIATION_20261002.md`.
