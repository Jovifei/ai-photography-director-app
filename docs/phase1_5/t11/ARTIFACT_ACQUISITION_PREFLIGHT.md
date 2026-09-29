# T11 Artifact Acquisition Preflight

**Tracked state:** `BLOCKED_LEGAL_REVIEW`<br>
**Target status:** `ARTIFACT_ACQUISITION_PREFLIGHT_READY — EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED`<br>
**Accepted T10-R3 authority base:** `2ff77cadc8696cbd06a504458da4d58c74aa6133`

This package defines a metadata-only preflight boundary for the exact Qwen artifact already bound by P1B and the separate T10 Owner decision. The tracked manifest records missing external evidence and authorization. It does not claim that artifact acquisition is ready or authorized.

## Frozen authority bindings

T11 binds the separate T10 decision at `docs/phase1_5/p1b/p1b_owner_decision.v1.json` using `UTF8_TEXT_EOL_NORMALIZED_SHA256_V1`, portable SHA-256 `45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9`, and Git blob `74d980e7dccdc67f659836b39126557d91e5460b`. T10 records `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`; its download, runtime, inference, App, Pipeline, and private-media flags remain false.

The P1B readiness record is `phase1-5-p1b-qwen3-vl-2b-authorization-readiness-20260927` at `docs/phase1_5/p1b/p1b_readiness_manifest.v1.json`, with the unchanged frozen manifest SHA-256 `166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a`. P1B's own `authorization.owner_decision` remains `NONE_TRACKED`; the separate T10 record does not rewrite P1B.

The artifact identity is `Qwen/Qwen3-VL-2B-Instruct`, revision `89644892e4d85e24eaac8bacfd4f463576704203`, `model.safetensors`, 4,255,140,312 bytes, LFS SHA-256 `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`. Proposed transport domains are exactly `huggingface.co` and `us.aws.cdn.hf.co`; both remain unverified proposals.

## P1B source identity semantics

The seven `p1a_sources` rows in the tracked manifest are copied exactly from the frozen P1B readiness manifest under `FROZEN_P1B_CHECKOUT_BINDING_V1`. Their declared Windows-checkout byte counts and SHA-256 values remain authoritative and are checked by `scripts/validate_phase1_5_p1b_readiness.py::validate_manifest`. This inherited identity is checkout-representation-sensitive; it is not claimed to be a cross-platform canonical identity.

On this worktree, the P1B validator returns `READY_FOR_OWNER_DECISION`. For the three P1A JSON rows shown below, the LF Git blobs have shorter byte counts because the Windows checkout uses CRLF. Replacing CRLF with LF yields the exact Git-blob bytes. The Git blob OID and EOL-normalized SHA-256 are `NON_AUTHORITY_DIAGNOSTIC_ONLY`; they explain the representation difference and do not replace or amend the frozen P1B tuples. Each is labeled `EOL_ONLY_CHECKOUT_REPRESENTATION_DIFFERENCE`.

| P1A source | Frozen P1B Windows-checkout bytes / SHA-256 | LF Git-blob bytes / SHA-256 (non-authority diagnostic only) |
| --- | --- | --- |
| `docs/phase1_5/p1a/primary_artifact_authorization_draft.v1.json` | 2171 / `7fcab9f1c351f5473a8bde7b2e23185d2888610c27e1fb6d6c4a0e0ab718d5b5` | 2124 / `d3e01c6ec868d4be2d94b92c0a299dad1a4a219b6e6f53f31169cb60354bdc29` |
| `docs/phase1_5/p1a/qwen_candidate_inventory.v1.json` | 4192 / `ba432763ee70321eaac4e0314588701f25a833471030d33fee9dd6ee404bc9a1` | 4099 / `fe365b9f358ef680f81762fb50bbaffb702b0c7b40372af53b3e0f94c01e32cb` |
| `docs/phase1_5/p1a/qwen_weight_license_evidence.v1.json` | 2485 / `08ac3a64b9bf4e189c8955bd8106b37a8deb20d8d44f9be9d131c7392f95adac` | 2438 / `2b377ecb680fbece4be8a2bbddcc2d647924f0452dff39d2bc123de3ad94e3f9` |

The future runbook reference `docs/phase1_5/p1a/QWEN_FUTURE_DOWNLOAD_RUNBOOK.md` is not a P1B `p1a_sources` row. Its base Git blob `d77bb04a74c1be25a135a01b0f02b37d8605e0fe` and LF-blob SHA-256 `44932ea13784446975001fee2717f519c91c72b6abd70855043a55e502d77ab8` are also recorded only as `NON_AUTHORITY_DIAGNOSTIC_ONLY`. A P1B validator failure or any normalized non-EOL source-content drift is `SOURCE_CONTENT_DRIFT — STOP`.

## Current blockers

The current state remains `BLOCKED_LEGAL_REVIEW` with these five blockers:

1. `LEGAL_REVIEW_REQUIRED`
2. `TRANSPORT_VERIFICATION_REQUIRED`
3. `FRESH_METADATA_REVALIDATION_REQUIRED`
4. `QUARANTINE_DESTINATION_REQUIRED`
5. `OWNER_ACQUISITION_AUTHORIZATION_REQUIRED`

Current records remain legal `NOT_LEGAL_APPROVED`, transport `REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED`, fresh metadata `NOT_RUN`, destination `NOT_DECLARED`, and future acquisition Owner decision `NONE_TRACKED`. Both `acquisition_authorized` and `artifact_body_access_authorized` are false.

The five companion schemas describe hypothetical future legal, transport, fresh-metadata, quarantine-destination, and Owner-decision inputs. They are contracts only: this package contains no positive external evidence, no destination record, and no future acquisition decision. Model-card Apache metadata does not establish legal approval. Legal disposition scope is limited to `MODEL_WEIGHT_QUARANTINE_ACQUISITION_ONLY`; the legal schema has no reviewer-identity fields.

The live T11 manifest identity uses `UTF8_TEXT_EOL_NORMALIZED_SHA256_V1`: strict UTF-8 decode, CRLF/lone-CR to LF, no JSON reserialization or other transformation, UTF-8 encode, then SHA-256. The same mode and portable digest are bound in canonical status and any future Owner decision. A raw checkout-byte digest is only `NON_AUTHORITY_DIAGNOSTIC_ONLY`. This live manifest identity is separate from the frozen P1B source tuples.

Future evidence-input fingerprints use UTF-8 JSON with object keys sorted, compact separators, `ensure_ascii=false`, and no trailing newline before SHA-256. These rules make hypothetical Owner bindings deterministic without storing the evidence records in T11.

## State boundary

The tracked state cannot report external evidence complete, acquisition authorized, or artifact-acquisition-ready. A future complete evidence set may qualify for `READY_FOR_OWNER_ACQUISITION_DECISION`; a separately tracked future Owner authorization may qualify only for `READY_FOR_SEPARATE_ACQUISITION_EXECUTION_PLAN`. Neither state is asserted here.

T11 performs no HTTP, HEAD, GET, Range, redirect-follow, Git LFS, metadata refresh, model download, legal conclusion, destination selection, runtime installation or execution, inference, photo/private-media/Pipeline/device access, Android change, signing, release, or merge.
