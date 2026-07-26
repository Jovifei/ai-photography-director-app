# Qwen Runtime Preflight Plan

Status: `NOT_EXECUTED`.

P1A authorizes no runtime. Before any future inference, the Owner must approve a separate controlled preflight:

1. Confirm the exact primary revision and verified local artifact hash in quarantine.
2. Confirm the intended runtime package, version, platform, CPU/GPU policy, and license without installing unapproved packages.
3. Measure available VRAM/RAM and reserve headroom for model, image preprocessing, KV cache, and process overhead.
4. Run only public-corpus, offline, no-network probes with explicit time and memory limits.
5. Validate JSON against the Phase 1.5 contract, record no image bytes or personal metadata in logs, and fail closed on malformed outputs.
6. Report performance, memory, failure modes, and privacy boundary to the Owner before any Android or Pipeline decision.

The publisher documentation notes Transformers 4.57.0 or later for Qwen3-VL usage, but this is compatibility metadata only; it does not qualify a runtime, Windows support, GPU fit, accuracy, or privacy posture.
