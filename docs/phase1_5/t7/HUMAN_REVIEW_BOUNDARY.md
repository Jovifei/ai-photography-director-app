# Human review readiness boundary

T7 prepares a provider-neutral review protocol. It does not perform human review, authorize reviewers, open images, ingest private media, or approve launch thresholds.

## Current status

`EVALUATION_GOVERNANCE_PACKET_READY — HUMAN_REVIEW_NOT_AUTHORIZED`

The existing 18-criterion rubric remains `PROPOSED_OWNER_THRESHOLD — NOT_MEASURED`. Criteria 1–17 are future human-scored criteria with values 0, 1 or 2; criterion 18 is automated contract/schema validation. A normalized 0–100 value is a quality aggregate and must never be called confidence or probability.

## Future packet boundary

Future packets use only opaque sample/candidate identifiers and reviewer slots `R1`, `R2`, `R3`. They must not contain reviewer names, emails, account or employee IDs, device IDs, image bytes, image paths, Photo Picker/content URIs, EXIF, thumbnails, private-media URLs, credentials or tokens. T7 tracks no packet records.

Two independent reviewers are the normal starting state. A difference greater than one point on a criterion or any severe-hallucination flag requires R3 and bounded adjudication. Missing required adjudication leaves the result unresolved. Proposed thresholds remain unapproved.

## Gate separation

Provider/model provenance belongs in the existing Envelope contract, not in the quality score. T7 does not qualify Qwen, claim Pipeline compatibility, authorize human editorial execution, or alter Bundle/Envelope/error-policy semantics. Real photos, devices, LAN, signing, release and main merge remain closed.
