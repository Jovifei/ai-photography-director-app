# P1B Provider/Qwen authorization readiness

Status: `READY_FOR_OWNER_DECISION`  
T6 type: metadata-only, non-runtime, fail-closed preparation  
T5 base: `0b04abdc57a3fa332689cfe664f9bcb997160e5e`

This packet binds the existing P1A evidence for a future Owner decision. It does not download a model, install a runtime, open a network connection, read an image, access private media, change Android, or authorize Pipeline integration.

## What is bound

- Primary candidate: `Qwen/Qwen3-VL-2B-Instruct`
- Immutable revision: `89644892e4d85e24eaac8bacfd4f463576704203`
- Primary weight: `model.safetensors`, expected 4,255,140,312 bytes, LFS SHA-256 `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`
- P1A metadata, license evidence, corpus r3 and App contracts are bound by repository-relative paths and SHA-256 in [`p1b_readiness_manifest.v1.json`](p1b_readiness_manifest.v1.json).
- Corpus r3 is `21 approved / 8 quarantined`; Owner media is prohibited.

P1A currently records `NOT_LEGAL_APPROVED`, `REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED`, and all download/runtime/inference/App authorization flags false. Those states are preserved.

## Owner decision boundary

The companion schema supports only a future decision record. Valid conceptual decisions are:

- `DEFER`
- `REJECT_PRIMARY`
- `AUTHORIZE_ARTIFACT_QUARANTINE_ONLY`

Even the quarantine-only option must bind the exact model, immutable revision, artifact, expected bytes/hash and this readiness manifest. It must not authorize runtime, inference, App integration, Pipeline, private media or public release. T6 tracks no positive Owner decision.

## Qualification commands

```text
python scripts/validate_phase1_5_p1b_readiness.py
python scripts/validate_phase1_5_p1b_readiness.py --require-execution-authority
python scripts/test_phase1_5_p1b_readiness.py
python scripts/test_phase1_5_contracts.py
python scripts/prepush_privacy_audit.py
git diff --check
```

Normal mode must return `READY_FOR_OWNER_DECISION`. The execution-authority probe must fail closed with `BLOCKED_OWNER_AUTHORIZATION_REQUIRED`; that non-zero result is a required T6 PASS condition. The validator has no HTTP, socket, downloader, Git LFS or model-framework capability.

## Explicit non-results

`READY_FOR_OWNER_DECISION` is metadata coherence only. It is not legal approval, artifact acquisition, runtime readiness, inference quality, Android integration, Qwen qualification, LAN readiness, Pipeline provenance, physical-device evidence, signing or release authorization.
