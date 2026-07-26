# Phase 1.5 P0 VLM Intelligence Qualification

**Review state:** `SUPERSEDED_BY_PHASE1_5_P0_REMEDIATION_REPORT_FOR_DELTA_REVIEW`.

This is the pre-remediation qualification record. The Reviewer subsequently identified owner-path, identifier/text, Provider Envelope, and counterexample-evidence gaps. The current delta evidence is `PHASE1_5_P0_REMEDIATION_REPORT.md`; neither document authorizes P1, a Provider, model download/inference, or Pipeline integration.

## Historical P0 decision before remediation

`PASS_PHASE1_5_P0_PIPELINE_FIRST_FOR_OWNER_DECISION`

P0 has frozen an app-local, Demo-compatible Bundle v1; separated Provider Envelope; error and uncertainty contracts; a no-image evaluation corpus plan; owner-review rubric; official desk qualification of Qwen and InternVL routes; architecture/data-flow options; Pipeline mapping; and a no-download authorization draft. The recommended first implementation investigation is an offline Pipeline producer evaluation, not a live Pipeline integration.

This PASS is limited to P0 contract and qualification design. It does **not** authorize model download/inference, GPU, framework installation, Docker/WSL2, user-image access, Picker E2E, network/API, Android Provider/UI/CameraX change, Pipeline modification/integration, Pose, AA1, Phase 2, persistence, or release.

## Evidence and gates

- Canonical `ReferenceBundle v1` preserves the existing string `"1.0"` Demo and rejects unknown fields/versions, excessive text, and multiline input.
- `ProviderAnalysisEnvelope v1` has strict success/failure/cancelled semantics and rejects URI/path/credential fields.
- Offline contract tests validate both fixtures and the existing Demo fixture. They do not prove a runtime VLM.
- The Pipeline audit found a planned/fixture-only producer with locked stages and no direct safe map for several required app fields. Integration remains blocked.
- Qwen 2B and 4B FP8 are official-source candidates for a future authorization review; 12 GB suitability remains an unmeasured runtime claim. InternVL needs further immutable artifact/license evidence.

## Stop boundary

Main remains unchanged; this isolated worktree contains only contracts, fixtures, tests, plans, and qualification records. The required stop message after independent verification is:

`PHASE1_5_P0_VLM_QUALIFICATION_COMPLETE_AWAITING_INDEPENDENT_REVIEW`
