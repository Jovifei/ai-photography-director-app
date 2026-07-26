# Phase 1 Git Scope Evidence

**Scope:** AA0_PHASE1_REFERENCE_DIRECTOR_EVIDENCE_REMEDIATION
**Branch:** `codex/phase1-reference-director-mvp`

## Exact review relationship

| Field | Exact value |
| --- | --- |
| Base / `git merge-base HEAD main` | `8a4006b0266d371b3e05d69fbec08642b5ad4518` |
| Reviewed implementation candidate | `2bbe3c42879e5c2d0d43cded8104d2c78edf1e4d` |
| Remediation test commit | `2c86c5e68551135ee30cd1ec0690a9766c6e1258` |
| Documentation/evidence remediation commit | `059be675c149fcfe195a8702044b987574e14002` |
| `git rev-parse HEAD` before finalization | `059be675c149fcfe195a8702044b987574e14002` |
| Branch | `codex/phase1-reference-director-mvp` |

The reviewed candidate remains the immutable `2bbe3c42879e5c2d0d43cded8104d2c78edf1e4d`. The instrumentation and documentation/evidence remediation commits are recorded separately and are not relabeled as the reviewed candidate.

## Reviewed candidate range

Command range: `8a4006b0266d371b3e05d69fbec08642b5ad4518...2bbe3c42879e5c2d0d43cded8104d2c78edf1e4d`

- Commit count: **6**
- Diff statistic: **25 files changed, 1,104 insertions(+), 108 deletions(-)**
- `git diff --check` result: **exit 0, no output**

### Full candidate commits

1. `ef08f60cf3008bd66553c1580c8f565a6e9779fc` — `feat(reference): add reference photo domain`
2. `5d7352a350052c18cf6fd6dd49963a64bcc3eb90` — `feat(reference): add demo director analyzer`
3. `a83ae225d816b9475d6faa53b5707e37351be919` — `feat(reference): add director card UI flow`
4. `2fc15776aab36208e0d915ae7b1b041ad1df4513` — `docs(reference): add phase1 implementation report`
5. `afe24717846e0dd364e213bd8c1aca8a5d3c7c1f` — `docs(reference): normalize phase1 report formatting`
6. `2bbe3c42879e5c2d0d43cded8104d2c78edf1e4d` — `docs(reference): refresh phase1 artifact evidence`

Candidate scope comprises Reference domain types, local Demo analyzer, Reference/Director UI routing and presentation, unit tests, app-local Bundle draft, and Phase 1 documentation.

## Evidence remediation delta

Command range: `2bbe3c42879e5c2d0d43cded8104d2c78edf1e4d...059be675c149fcfe195a8702044b987574e14002`

- Commit count: **2**
- Diff statistic: **5 files changed, 249 insertions(+), 2 deletions(-)**
- `git diff --check` result: **exit 0, no output**
- Change class: Phase 1 Compose instrumentation/debug-test support plus documentation/evidence correction. No production feature source changed.

The remediation commits are:

- `2c86c5e68551135ee30cd1ec0690a9766c6e1258` — `test(phase1): add reference director instrumentation evidence`
- `059be675c149fcfe195a8702044b987574e14002` — `docs(phase1): fix evidence baseline and verification records`

## Ownership and exclusion boundary

Pre-existing user-owned untracked recovery documents, `reports/CODEX_CLI_*`, and `tasks/` are outside both Git ranges. They were neither staged nor edited by this remediation. No image, APK, model, database, secret, account data, device serial, or user media is included in the repository scope.
