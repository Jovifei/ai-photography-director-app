# Phase 1.5 P1 Model Artifact Authorization Draft

**Status:** `NEEDS_OWNER_CHOICE — DOWNLOAD FORBIDDEN`.

One completed, Owner-approved record is required for every model artifact before any download. This P0 document grants no download, framework installation, GPU use, inference, Docker, WSL2, or network/API operation.

| Required authorization record | Required evidence |
| --- | --- |
| Exact model ID and immutable revision | Official model page and revision/commit resolved at authorization time. |
| Official URL and allowed domains | Explicit domain allowlist; no mirrors or implicit redirects. |
| Code and weight licenses | Separate primary-source texts; commercial use and redistribution disposition. |
| Artifact inventory | File count, total bytes, published hashes, and expected filenames. |
| Quarantine and verification | Isolated download destination, malware/license review, local SHA-256, and mismatch stop rule. |
| Promotion | Named approver, immutable manifest, signed/recorded promotion, rollback version. |
| Runtime | Exact runtime/container/CUDA version, Windows/WSL2 decision, CPU/GPU and VRAM limits. |
| Privacy | No-private-image rule, retention/deletion behavior, logs prohibited from carrying media identifiers. |

The artifact is rejected if any authoritative fact is missing, a weight license conflicts with intended use, hashes do not match, the selected runtime exceeds the approved environment, or the P1 test corpus/authorization is absent. No “latest” tag is acceptable.
