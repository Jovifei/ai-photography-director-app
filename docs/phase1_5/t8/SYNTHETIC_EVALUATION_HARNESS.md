# T8 synthetic evaluation evidence harness

Status: `SYNTHETIC_EVALUATION_HARNESS_READY — REAL_EVALUATION_NOT_AUTHORIZED`.

This package executes only contract-only JSON fixtures. Every case is marked `CONTRACT_ONLY_SYNTHETIC_NO_PROVIDER_EXECUTED`; no model, image, media, human reviewer, Pipeline, device or network is used. SUCCESS fixtures are projected to a blinded candidate and passed through the T7 R1/R2/R3 rules. FAILED, CANCELLED and Pipeline-incompatible fixtures are never quality-scored.

`METRICS_READY_OWNER_THRESHOLD_NOT_APPROVED` means deterministic evidence flow only. It is not Provider PASS, Qwen qualification, Pipeline compatibility, human acceptance, confidence or release readiness.

Commands:

```text
python scripts/run_phase1_5_t8_synthetic_evaluation.py
python scripts/run_phase1_5_t8_synthetic_evaluation.py --require-real-provider
python scripts/run_phase1_5_t8_synthetic_evaluation.py --require-human-review
python scripts/run_phase1_5_t8_synthetic_evaluation.py --require-pipeline
python scripts/test_phase1_5_t8_synthetic_evaluation.py
```
