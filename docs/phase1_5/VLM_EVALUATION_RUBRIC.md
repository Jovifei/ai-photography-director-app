# VLM Photography Evaluation Rubric

**Status:** `PROPOSED_OWNER_THRESHOLD — NOT_MEASURED`.

Each human-scored criterion receives 0, 1, or 2 points: 0 = wrong/unsafe/empty, 1 = partly useful or materially uncertain, 2 = accurate, specific, and supported. Report a 0–100 normalized score from 36 points; never use it as a false factual-confidence percentage.

| # | Criterion | Primary verifier |
| ---: | --- | --- |
| 1–3 | Scene correctness; background-story plausibility; lighting direction | Human reviewer |
| 4–6 | Lighting quality; composition description; subject intent | Human reviewer |
| 7–10 | Emotion interpretation; pose advice; camera position; director-prompt executability | Human reviewer |
| 11–12 | Advice specificity; advice does not contradict the image | Human reviewer |
| 13–17 | Severe hallucination; honest uncertainty; natural Chinese; photographic professionalism; repetition/empty language | Human reviewer |
| 18 | Schema legality, required fields, bounds, source/version consistency | Automated validator |

Automatic checks also measure required-field coverage, invalid output, timeout/OOM, P50/P95 latency, VRAM peak, deterministic replay, and repeated-run consistency. They cannot score photographic truth.

## Severe hallucination

Flag a severe hallucination when output asserts a contradicted or unsupported subject count, scene/event, identity, exact location, safety claim, live measurement, or biographical fact; presents a speculative story/emotion as observed fact; or emits an unsafe recommendation contrary to visible evidence. Any privacy claim or license claim without provenance is also a hard-stop review finding.

## Review protocol and proposed gates

Blind the provider/model name for human quality scoring; preserve it in the separate Envelope for audit. Two reviewers score independently. Use the per-criterion median; a difference above one point or any severe-hallucination flag routes to a third reviewer and written adjudication.

| Measure | Classification | Proposed Owner review threshold |
| --- | --- | --- |
| Schema valid / required field rate | HARD GATE | 100% on accepted outputs |
| Privacy or license violation | HARD GATE | 0 occurrences |
| Severe hallucination rate | HARD GATE | ≤ 1% and no unresolved critical case |
| Director usefulness median | Comparative | ≥ 1.5 / 2 |
| Lighting / composition usefulness | Comparative | ≥ 1.5 / 2 each |
| Timeout / OOM | Comparative | Report distribution; Owner sets deployment ceiling |
| P50/P95, VRAM, determinism, consistency | Comparative | Report by exact candidate/runtime; no P0 claim |

These are review proposals, not approved launch thresholds and not evidence that any candidate passed.
