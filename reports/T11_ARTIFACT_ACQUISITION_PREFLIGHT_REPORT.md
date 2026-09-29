# T11 Artifact Acquisition Preflight Report

**T11 base:** `2ff77cadc8696cbd06a504458da4d58c74aa6133`<br>
**Branch:** `codex/t11-artifact-acquisition-preflight-20260929`<br>
**Stage status:** `ARTIFACT_ACQUISITION_PREFLIGHT_READY — EXTERNAL_EVIDENCE_AND_AUTHORIZATION_REQUIRED`<br>
**Current blocker:** `BLOCKED_LEGAL_REVIEW`

The final candidate SHA is the exact head of the Draft PR stacked on PR #15 and is recorded in the C2C execution handoff. The canonical status keeps top-level `current_stage=PRODUCT_READY_PROMOTION_GATE_READY_REAL_READY_PROMOTION_NOT_AUTHORIZED` and stores T11 as a nested preflight projection, so the existing T10 validator remains compatible.

## Authority and artifact identity

T10 remains `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`, hash mode `UTF8_TEXT_EOL_NORMALIZED_SHA256_V1`, portable decision SHA-256 `45e462982d9fafd6444805529138d4fcc082d310dff52e9522e5c6adf233ece9`, and Owner-decision Git blob `74d980e7dccdc67f659836b39126557d91e5460b`. The frozen P1B manifest remains SHA-256 `166211e0fd1790e85a4e86bd4d270cc3cefccccbc04502d20f2584dfcf752a6a`, with `authorization.owner_decision=NONE_TRACKED`; the T10 record remains separate.

The exact candidate is `Qwen/Qwen3-VL-2B-Instruct`, revision `89644892e4d85e24eaac8bacfd4f463576704203`, file `model.safetensors`, 4,255,140,312 bytes, LFS SHA-256 `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`. Proposed transport domains remain `huggingface.co` and `us.aws.cdn.hf.co`; neither is verified.

## Frozen P1B source semantics

The seven T6 `p1a_sources` tuples are copied exactly under `FROZEN_P1B_CHECKOUT_BINDING_V1`; their existing P1B validator remains authoritative. `cross_platform_canonical=false` makes the byte representation limit explicit. Git blob OIDs and EOL-normalized SHA-256 values are `NON_AUTHORITY_DIAGNOSTIC_ONLY`. Proven CRLF-to-LF differences are labeled `EOL_ONLY_CHECKOUT_REPRESENTATION_DIFFERENCE`, not content drift. A P1B validator failure or non-EOL mutation is `SOURCE_CONTENT_DRIFT_STOP`. No P1A/P1B record or validator was modified or repaired.

## Current blockers and authority

| Evidence | Current status |
|---|---|
| Legal disposition | `NOT_LEGAL_APPROVED` / `LEGAL_REVIEW_REQUIRED` |
| Transport chain | `REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED` / `TRANSPORT_VERIFICATION_REQUIRED` |
| Fresh immutable metadata | `NOT_RUN` / `FRESH_METADATA_REVALIDATION_REQUIRED` |
| Quarantine destination | `NOT_DECLARED` / `QUARANTINE_DESTINATION_REQUIRED` |
| Future acquisition Owner decision | `NONE_TRACKED` / `OWNER_ACQUISITION_AUTHORIZATION_REQUIRED` |

Artifact acquisition, artifact-body access, network, runtime, inference, App, Pipeline and private-media authorization remain false. No positive legal, transport, metadata, destination or acquisition decision record is tracked. Hypothetical positive inputs exist only as temporary unit-test objects.

## Verification

| Check | Result |
|---|---|
| T11 unit tests | `29/29 PASS` |
| T10 unit tests | `12/12 PASS` |
| P1B unit tests | `11/11 PASS` |
| T9 unit tests | `13/13 PASS` |
| T8/T7 tests | `15/15 PASS` |
| Program status tests | `8/8 PASS` |
| Contract checks | `PASS` |
| T10/P1B/T9/T8/T7/status validators | `PASS` |
| T11 preflight validator | `BLOCKED_LEGAL_REVIEW` as expected |
| P1B execution authority probe | `BLOCKED_OWNER_AUTHORIZATION_REQUIRED` |
| Privacy audit | `PASS` |
| Five T11 authority probes | `5/5 BLOCKED_AS_EXPECTED` |
| Android bounded JVM smoke | `BUILD SUCCESSFUL`; `:app:testDebugUnitTest`, 27 tasks executed, no emulator/device |
| Exact-base scope guard | `PENDING_FINAL_QUALIFICATION` |

The Android test emitted only the existing Kapt warning that language version 2.0 falls back to 1.9; there were no test or build errors.

## Boundary

T11 issues no HTTP, HEAD, GET or Range request, follows no redirect, refreshes no external metadata, accesses no model body or Git LFS, reaches no legal conclusion, selects no actual destination, and performs no runtime, inference, photo/private-media, Pipeline, device, Android, signing, release or merge action. The next authorized stage is `EXTERNAL_LEGAL_TRANSPORT_DESTINATION_EVIDENCE_OR_OWNER_DEFER`; it does not authorize T11 to collect evidence or acquire the artifact.
