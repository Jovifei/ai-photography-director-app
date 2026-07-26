# VLM Architecture Decision

**Recommendation:** `RECOMMEND_APPROVE` a Pipeline-first *evaluation and producer* investigation, subject to separate Owner authorization. It does not authorize Pipeline integration. Qwen/InternVL-class models are not candidates for direct Android embedding in this phase.

| Architecture | Privacy / UX / offline | Latency / package / hardware | Operations / safety / testability | Current disposition |
| --- | --- | --- | --- | --- |
| ARCH-A: Nightly Pipeline offline producer | Strongest boundary: no user image in App; user receives an image-free Bundle. Not interactive. | Batch latency; no Android model/package; producer hardware external. | Release approval, provenance, corpus and integrity can be audited; current Pipeline mapping remains blocked. | `RECOMMEND_APPROVE` as first evaluation route, not integration. |
| ARCH-B: PC local service on controlled LAN | Raw user image would leave Android; consent and local-network proof required. Potentially interactive. | LAN/service latency; model and service reside on PC, not APK. | Requires auth, discovery, TLS/trust, outage, deletion, monitoring, and device/service test matrix. | `NEEDS_OWNER_CHOICE`. |
| ARCH-C: Android to cloud backend | Highest data-transfer, consent, account, cost, retention, and outage burden. | Network-dependent; no APK model footprint. | Requires backend security and processor contracts; cloud quality benchmark only after separate approval. | `NEEDS_OWNER_CHOICE`. |
| ARCH-D: Android on-device VLM | No external transfer but package/device/thermal/accessibility burden. | Candidate weights and runtimes must fit actual mobile hardware; 12 GB PC VRAM is irrelevant to app packaging. | Needs artifact, runtime, battery, memory, performance, rollback, and device qualification. | `RECOMMEND_REJECT` for Phase 1.5 P0. |

## Data flows and retention

```text
ARCH-A: approved non-private corpus -> offline Pipeline -> validated Bundle + Envelope -> reviewed export -> App consumer
ARCH-B: explicit Picker -> temporary Android decode -> authenticated local service -> validated Bundle + Envelope -> App
ARCH-C: explicit Picker -> approved backend/cloud processor -> validated Bundle + Envelope -> App
ARCH-D: explicit Picker -> temporary device decode -> on-device runtime -> validated Bundle + Envelope -> App
```

P0 performs none of these flows. In each future path, raw originals/thumbnails are memory-only by default, no raw media identifier is logged, Bundle/Envelope retention remains zero until approved, and deletion clears temporary decode/cache before reporting completion. ARCH-B/C additionally require network and provider retention proofs; ARCH-A must prevent producer-side source/derivative metadata from entering the consumer payload.

## UX transition contract (planning only)

Future UI states are `Demo`, `Real analysis available`, `Analysis queued`, `Analyzing`, `Analysis timeout`, `Provider unavailable`, `Invalid output`, `Privacy blocked`, `Unsupported image`, `Result with uncertainty`, `Result from Pipeline`, `Stale result`, `Retry`, and `Fall back to Demo`.

Every state must visibly disclose exactly one source: `Demo`, `Local VLM`, `Pipeline`, `Cloud (future)`, or `Unavailable`. Retry is only offered when taxonomy says retryable. “Fall back to Demo” is explicit and never a silent replacement for an unsuccessful real analysis. This document does not modify UI0, CameraX, or the current Phase 1 product flow.
