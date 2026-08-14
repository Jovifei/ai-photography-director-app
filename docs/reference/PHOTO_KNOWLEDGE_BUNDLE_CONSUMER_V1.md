# Photo Knowledge Bundle Consumer v1

**Status:** `APP_LOCAL_INTEGRATION_READINESS_ONLY`
**Version:** `1.0`

This document defines the app-side acceptance boundary for a future approved Photo Knowledge Bundle. It is deliberately image-free and transport-free. It does **not** modify `shared-contract/`, authorize a Pipeline connection, start a local service, read a sibling worktree, or add a runtime adapter.

The machine-readable acceptance shape is [`photo_knowledge_bundle_consumer.v1.schema.json`](photo_knowledge_bundle_consumer.v1.schema.json). Its synthetic-only fixture is [`fixtures/photo_knowledge_bundle_consumer.v1.json`](fixtures/photo_knowledge_bundle_consumer.v1.json).

## Allowed payload

The root object has exactly five fields. Unknown fields are rejected.

| Field | Allowed content | Consumer rule |
| --- | --- | --- |
| `contract_version` | Exact string `"1.0"` | Reject an unknown version. |
| `bundle_id` | Opaque token | Never treat it as a file, user, device, URI, EXIF value, or media hash. |
| `source` | `origin`, opaque `producer_id`, opaque `release_id` | Identifies only an approved local-service or Pipeline release; it contains no endpoint, account, token, or model data. |
| `integrity` | `SHA-256` plus an aggregate payload digest | A future authorized adapter must verify an agreed canonical digest before adaptation; this P21 static gate validates format only. A digest is not a media locator. |
| `references` | 1–20 opaque `reference_id` values and structured photography guidance | Validate every field before any display or adaptation. |

Each `photography` object contains only `scene`, `background_story`, `lighting`, `composition`, `subject_intent`, `emotion`, textual `pose_template`, `camera_position`, and `director_prompt`. `pose_template` is a textual creative intention, never biometric data, keypoints, skeleton coordinates, or live Pose output.

## Explicitly forbidden

The v1 schema has `additionalProperties: false` at every level. Therefore it accepts no image bytes, thumbnails, Base64, URI, URL, file path, filename, EXIF, GPS, account, user identifier, device identifier, database key, credential, pairing code, token, certificate, raw provider prompt, model identifier, model revision, model weight, or raw provider output. The structured `director_prompt` field is an approved user-visible photography instruction, not an upstream raw prompt.

Opaque-token syntax protects against obvious URI/path/email forms but cannot prove how an upstream producer generated an identifier. Any future producer must separately prove that IDs are not derived from private media, account, device, location, filename, URI, EXIF, or media-hash data.

## Consumer behavior and ownership

1. A future app adapter receives an already-approved in-memory payload from an explicitly authorized integration boundary; it never scans a sibling repository, database, filesystem, output directory, or network location for bundles.
2. The adapter validates the exact contract version, root/nested fields, identifier syntax, digest format, one-to-twenty cardinality, duplicate reference IDs, and unsafe transport-like text before mapping to the existing app-local `ReferenceBundle`.
3. Any validation or integrity failure is `PIPELINE_BUNDLE_INCOMPATIBLE`/unavailable. The consumer must not guess missing photography guidance, create a `READY` result, or substitute a Demo bundle.
4. This contract grants no permission to persist an upstream payload, synchronize automatically, launch a local service, configure LAN/TLS/firewall settings, or access a user photo.

## Compatibility and hand-off

`ReferenceBundle v1` and `ProviderAnalysisEnvelope v1` remain unchanged. This is a narrow pre-adapter consumer gate for a future local Qwen service or Nightly Pipeline release; it is not a replacement for their respective provider and cross-repository approval gates.

Before any implementation consumes a non-synthetic bundle, obtain separate authorization for exactly one of these paths:

1. private-LAN local Qwen service activation and its provider qualification; or
2. Nightly Pipeline release approval, shared-contract/ADR synchronization, and an approved transfer mechanism.

The offline compatibility test is `python scripts/test_photo_knowledge_bundle_consumer_contract.py`. It reads only the schema and synthetic fixture in this repository.
