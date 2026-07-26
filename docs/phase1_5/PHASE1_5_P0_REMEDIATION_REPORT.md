# Phase 1.5 P0 Contract Semantics and Owner Path Remediation Report

**Status:** `REMEDIATED_AWAITING_DELTA_REVIEW`
**Reviewer base:** `9b14019d8675e49f960943306045e54752c102a3`
**Review range:** `9b14019d8675e49f960943306045e54752c102a3..NEW_FINAL_SHA` (the handoff, not this document, supplies the final SHA).

## 1. Scope and four Reviewer blockings

This remediation closes only the four requested P0 contract blockers. It does not implement a VLM, Android Provider, Pipeline integration, model download, GPU path, media access, network call, or Phase 1.5-P1 work.

| Blocking | Remediation result |
| --- | --- |
| Candidate captured `tasks/todo.md` | Removed from the candidate tree. The main worktree Owner file was not read, copied, staged, or modified. |
| URI-like `reference_id` and whitespace text accepted | Opaque-token and non-blank schema rules added; semantic validation rejects leading/trailing whitespace. |
| Envelope status/metadata/time semantics incomplete | SUCCESS, FAILED, CANCELLED, error policy, safe-message, and wall-clock order invariants are enforced. |
| Offline tests missed reproduced counterexamples | Schema and semantic negative cases now cover every reported counterexample and identify the enforcement layer. |

## 2. Owner path boundary

The candidate-only commit removes the tracked `tasks/todo.md`; `git ls-files -- tasks/todo.md` is empty in the candidate afterward. Main-worktree Owner untracked evidence is external and content-free: 13 regular files before and after remediation, with identical relative paths, byte counts, and SHA-256 values. The records contain no file body and no `tasks/todo.md` text.

## 3. ReferenceBundle v1 semantics

`reference_id` is now exactly `^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$`. This preserves `demo-reference-1` while rejecting URI schemes, path separators, email syntax, whitespace, CR/LF, and colon-containing values.

**SCHEMA_ENFORCED:** bounded opaque-token syntax and bounded single-line text with at least one non-whitespace character.

**PRODUCER_POLICY_ONLY:** a producer must use an approved random/opaque ID generator and must not derive the token directly from account, device, location, filename, URI, or media-hash data. Syntax cannot prove data provenance.

Every user-visible Bundle text field rejects empty, whitespace-only, tab-only, and CR/LF content. The semantic validator deliberately rejects leading/trailing whitespace; it does not trim and then misrepresent mutated text as original provider output.

## 4. Provider Envelope v1 semantics

| Status | Required invariant |
| --- | --- |
| `SUCCESS` | Valid Bundle v1; `output_schema_version="1.0"`; `error=null`; `retryable=false`; non-empty model ID/revision/runtime ID; 64-hex artifact hash; Bundle and Envelope reference IDs match. |
| `FAILED` | `bundle=null`; `output_schema_version=null`; error is required and not `USER_CANCELLED`; retry/preserve/log/action must equal the machine-readable policy. |
| `CANCELLED` | `bundle=null`; `output_schema_version=null`; `USER_CANCELLED`; `retryable=false`; `STOP`; no safe event log; diagnostic upload false. |

The schema blocks state-shape violations where possible. `scripts/phase1_5_contract_semantics.py` defensively checks cross-field rules, model metadata, reference equality, safe user messages, timezone awareness, time order, non-negative latency, and policy consistency. It returns structured `{path, code, message}` errors and exits non-zero through its CLI. It only uses Python standard library facilities and local JSON files.

`started_at_utc` / `completed_at_utc` are timezone-aware audit timestamps; `completed_at_utc >= started_at_utc` is required. `latency_ms` is a non-negative provider monotonic-clock duration and is not required to equal the wall-clock delta.

## 5. Error policy and logging boundary

`error_policy.v1.json` is the single machine-readable mapping for all 18 frozen taxonomy codes. It declares retryability, reference preservation, safe event logging, `allow_diagnostic_upload=false`, product action, cleanup behavior, and Demo fallback policy. Tests compare that one JSON source against the Envelope enum and the human taxonomy document; no independent retry mapping remains in tests.

Provider failure never auto-substitutes Demo output. `EXPLICIT_DEMO_FALLBACK` can only occur outside a Provider Envelope after an explicit user choice and with a visible `Demo Analysis` label; it cannot be a SUCCESS result.

Safe messages are single-line/non-blank and are rejected if they carry obvious stack traces, local absolute paths, Photo Picker URIs, bearer/API-key material, or device-serial fields. Raw exceptions are not contractual output.

## 6. Offline evidence

The contract suite checks canonical and alias Bundle schemas, Provider Envelope, and corpus Manifest schemas with `Draft202012Validator.check_schema`, unique `$id` values, local `$ref` resolution, positive fixtures, and distinct `SCHEMA_REJECTION` / `SEMANTIC_REJECTION` outputs.

It covers all Reviewer counterexamples: URI/path/email/blank IDs; whitespace/tab/empty/newline/edge-space text; unknown fields/versions; completed-before-started, naive timestamps, and negative latency; every required SUCCESS metadata violation; FAILED policy mismatches; CANCELLED timeout/retry/bundle/error violations; invalid error codes; unsafe messages; and diagnostic-upload attempts.

The same script passed from repository root, `scripts/`, and an arbitrary temporary cwd with identical output: 75 `PASS` lines and normalized output SHA-256 `ad201cb694ece2ebf7621a1c818b8ecbb1a709b6d675782ef16ef0719e585da0`. It uses Python `3.14.2`, `jsonschema==4.26.0`, and `referencing==0.37.0`; no install command, network access, model, or image is involved.

## 7. Pipeline and Android boundaries

`PIPELINE_APP_BUNDLE_CONTRACT.md` is marked `SUPERSEDED_PLANNING_DRAFT`; the current entry points are the app-local v1 schema and `PIPELINE_APP_FIELD_MAPPING.md`. `PIPELINE_DIRECT_INTEGRATION = BLOCKED_BY_CONTRACT_AND_STAGE_EVIDENCE` remains true.

Android source, Gradle, Manifest, CameraX, UI0, and the Pipeline repository are unchanged. Android regression from the isolated worktree: `assembleDebug` PASS; JVM 91/91, 0 failures/errors/skipped; Lint 0 errors and 13 warnings (`DataExtractionRules`, `GradleDependency`, `MissingApplicationIcon`, `ModifierParameter`, `OldTargetApi`).

## 8. Final P0 gates

- Contract tests: PASS in all three cwd modes.
- Privacy/range audit: PASS before the documentation commit: `python scripts/prepush_privacy_audit.py` passed and `git diff --check` exited 0. The final `git diff --check <reviewer-base>..HEAD` gate is executed again after the commit and before push.
- Unauthorized work remains absent: no model download/inference, GPU, Docker/WSL2 inference, private-photo access, Cloud API, network implementation, Android Provider, Pipeline implementation/integration, P1, Pose, Phase 2, AA1, or main merge.
