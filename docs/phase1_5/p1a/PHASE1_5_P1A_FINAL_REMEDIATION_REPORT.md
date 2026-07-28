# Phase 1.5-P1A final remediation evidence

## Scope and lineage

- Branch: `codex/phase1-5-p1a-corpus-artifact-authorization`
- P1A implementation base: `d10a73143ab8b4701be630e397b09af43af004c9`
- Approved main during this remediation: `3c6be0314669b4ea4bf12c57f366df0c3510a69c`
- This remediation changes corpus/license evidence, schemas, validators, tests, and this report only. It does not add Android model runtime code, pose providers, network inference, owner media, or model weights.

## B1 — Qwen weight-license evidence

The final metadata-only evidence root is identified by
`model-license-evidence-20260728T170208Z`. It contains exact-revision model-card,
Hub API/tree, license semantics, Apache-2.0 text, Qwen code-license, transport, and
evidence manifest records. The evidence records the exact model revision
`89644892e4d85e24eaac8bacfd4f463576704203`, primary artifact size
`4255140312`, LFS SHA-256
`7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`,
and repository inventory (`12` files, `4266648961` bytes).

`model_body_downloaded=false`. This is not legal approval: `NOT_LEGAL_APPROVED`
remains in force and download, runtime, inference, and app-integration authorization
are all `false`.

## B2/B5 — fail-closed r3 public corpus

The final r3 evidence root is `public-corpus-20260729T015100Z-r3`.

- Approved: 21 public samples.
- Quarantined: 8 samples, including `p1a-006` (bedroom/private-interior content
  conflicts with its earlier manual review) and `p1a-025` (the source's conditional
  reuse/contact terms do not close a Public Domain claim).
- Added: `p1a-030` (reviewed no-person public Warsaw library interior), `p1a-031`
  (NASA public-domain adult half-body portrait), and `p1a-032` (NASA
  public-domain adult full-body portrait).
- Public Domain samples `p1a-012`, `p1a-027`, `p1a-028`, and `p1a-029` now carry
  exact-revision template and institution/right-statement evidence. `p1a-028`
  retains its explicit Alexander Turnbull Library credit condition.

No owner media was accessed. Source and sanitized public images remain outside Git.

## B3 — v3 binding contract

`public_corpus_manifest.v3.schema.json` requires exactly one of each evidence type
per approved sample: `COMMONS_API`, `SOURCE_PAGE_RECORD`, `LICENSE_EVIDENCE`,
`TRANSPORT`, and `MANUAL_VISUAL_REVIEW`. Each binding carries safe relative path,
byte count, and SHA-256. Unknown fields, duplicate types/paths, traversal paths, and
missing types fail validation.

The validator checks root containment, JSON encoding, bytes/hashes, Commons/source
identity, license and Public Domain basis, transport, manual-review consistency,
image integrity/metadata stripping, duplicate page/revision/URL/content, quarantine,
and deterministic aggregate hashing. Reports use stable, redacted failure descriptions
and do not echo absolute input paths.

## Verification record

| Gate | Result |
|---|---|
| Complete r3 CLI validation | PASS; exit 0; 21 approved; 0 failures |
| CLI missing arguments | PASS; exit 2 |
| Damaged-evidence CLI copy | PASS; exit 1 with a redacted report |
| v3 schema/manifest/negative/integration tests | PASS; 40/40 |
| Phase1.5-P0 contract suite | PASS; 75/75 |
| Android Debug build | PASS |
| Android JVM tests | PASS; 91 tests, 0 failures, 0 errors, 0 skipped |
| Android lintDebug | PASS; 0 errors (13 warnings retained) |
| Privacy audit | PASS |
| `git diff --check` | PASS |
| Tracked forbidden-asset scan | PASS; no image, APK/AAB, model weight, database, or keystore added |
| Owner untracked boundary | PASS; all 13 pre/post path, size, and SHA-256 entries identical |

## Independent-review handoff

This P1A branch is ready only for independent delta review. It does **not** authorize
P1B model download/runtime work, Android integration, UI1 work, pipeline integration,
or a merge to `main`.
