# Photo Knowledge Bundle Consumer v1

**Status:** `APP_LOCAL_OFFLINE_IMPORT_CONTRACT_ACTIVE`
**Version:** `1.0`

This document defines the app-side acceptance boundary for a user-selected, approved Photo Knowledge Bundle. It is deliberately image-free and transport-free. It does **not** modify `shared-contract/`, authorize a Pipeline connection, start a local service, read a sibling worktree, or authorize a real Pipeline artifact.

The machine-readable acceptance shape is [`photo_knowledge_bundle_consumer.v1.schema.json`](photo_knowledge_bundle_consumer.v1.schema.json). Its synthetic-only fixture is [`fixtures/photo_knowledge_bundle_consumer.v1.json`](fixtures/photo_knowledge_bundle_consumer.v1.json).

## Allowed payload

The root object has exactly five fields. Unknown fields are rejected.

| Field | Allowed content | Consumer rule |
| --- | --- | --- |
| `contract_version` | Exact string `"1.0"` | Reject an unknown version. |
| `bundle_id` | Opaque token | Never treat it as a file, user, device, URI, EXIF value, or media hash. |
| `source` | `origin`, opaque `producer_id`, opaque `release_id` | Identifies only an approved local-service or Pipeline release; it contains no endpoint, account, token, or model data. |
| `integrity` | `SHA-256` plus an aggregate payload digest | Verify the PKB1 canonical payload digest before preview, mapping, persistence, or adaptation. A digest is not a media locator. |
| `references` | 1–20 opaque `reference_id` values and structured photography guidance | Validate every field before any display or adaptation. |

Each `photography` object contains only `scene`, `background_story`, `lighting`, `composition`, `subject_intent`, `emotion`, textual `pose_template`, `camera_position`, and `director_prompt`. `pose_template` is a textual creative intention, never biometric data, keypoints, skeleton coordinates, or live Pose output.

Identifiers must match `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`. Each photography field must be non-blank, single-line, free of ISO control characters and transport/path-like text, and at most 800 Unicode code points; `director_prompt` may contain at most 1,200 Unicode code points. The whole selected UTF-8 document is limited to 512 KiB. A UTF-8 BOM, malformed UTF-8, unknown field, duplicate `reference_id`, or mismatched digest is rejected.

## PKB1 canonical payload digest

`integrity.payload_sha256` is the lowercase hexadecimal SHA-256 of the following canonical byte sequence. The `integrity` object itself is excluded so the digest is not recursive.

1. Start with the five ASCII bytes `PKB1\n`.
2. Append the name and then the value of every field listed below. Encode each token independently as:

   ```text
   <decimal UTF-8 byte length>:<raw UTF-8 bytes>\n
   ```

   The decimal length has no sign or leading padding. Length is measured in bytes, not characters. No Unicode normalization, trimming, escaping, locale conversion, or newline conversion is applied.
3. Use this exact order:
   - `contract_version`
   - `bundle_id`
   - `source.origin`
   - `source.producer_id`
   - `source.release_id`
   - `references.count`, whose value is the base-10 array length
4. Preserve `references` array order. For index `i` starting at zero, append these exact names and their values:
   - `references[i].reference_id`
   - `references[i].photography.scene`
   - `references[i].photography.background_story`
   - `references[i].photography.lighting`
   - `references[i].photography.composition`
   - `references[i].photography.subject_intent`
   - `references[i].photography.emotion`
   - `references[i].photography.pose_template`
   - `references[i].photography.camera_position`
   - `references[i].photography.director_prompt`
5. Hash the complete byte sequence once with SHA-256. Consumers may accept uppercase hexadecimal input but compare the decoded digest bytes and persist the normalized lowercase value.

Changing any included value or reference order without recomputing the digest must fail closed.

## Explicitly forbidden

The v1 schema has `additionalProperties: false` at every level. Therefore it accepts no image bytes, thumbnails, Base64, URI, URL, file path, filename, EXIF, GPS, account, user identifier, device identifier, database key, credential, pairing code, token, certificate, raw provider prompt, model identifier, model revision, model weight, or raw provider output. The structured `director_prompt` field is an approved user-visible photography instruction, not an upstream raw prompt.

Opaque-token syntax protects against obvious URI/path/email forms but cannot prove how an upstream producer generated an identifier. Any future producer must separately prove that IDs are not derived from private media, account, device, location, filename, URI, EXIF, or media-hash data.

## Consumer behavior and ownership

1. The app receives a document explicitly selected by the user through the Android system document picker. It reads a bounded in-memory copy, does not retain the selected URI or persist URI permission, and never scans a sibling repository, database, filesystem, output directory, or network location for bundles.
2. The consumer validates the exact contract version, root/nested fields, identifier syntax, PKB1 digest, one-to-twenty cardinality, duplicate reference IDs, and unsafe transport-like text before preview or mapping to the existing app-local `ReferenceBundle`.
3. Any validation or integrity failure is `PIPELINE_BUNDLE_INCOMPATIBLE`/unavailable. The consumer must not guess missing photography guidance, create a `READY` result, or substitute a Demo bundle.
4. Every bundle item requires an explicit one-to-one user-confirmed mapping to a reference already inside the current project. The consumer must not infer a mapping from array order, filename, URI, path, EXIF, or media hash, and must not overwrite an existing `READY` record.
5. After one atomic successful import, the app persists only the approved structured guidance and image-free provenance needed to revalidate its origin. It does not persist the source JSON or document URI.
6. Bundle v1 contains per-photo guidance only. It does not contain or authorize a project-level semantic summary or recommended primary reference; the user selects the primary reference manually.
7. This contract grants no permission to synchronize automatically, launch a local service, configure LAN/TLS/firewall settings, access a user photo outside the existing project, or consume an unapproved real artifact.

## Compatibility and hand-off

`ReferenceBundle v1` and `ProviderAnalysisEnvelope v1` remain unchanged. Bundle provenance makes no model, provider-response, or project-summary claim. This consumer contract is not a replacement for the local-provider or cross-repository approval gates.

Before any implementation consumes a non-synthetic bundle, obtain separate authorization for exactly one of these paths:

1. private-LAN local Qwen service activation and its provider qualification; or
2. Nightly Pipeline release approval, shared-contract/ADR synchronization, and an approved transfer mechanism.

The offline compatibility test is `python scripts/test_photo_knowledge_bundle_consumer_contract.py`. It reads only the schema and synthetic fixture in this repository, independently recomputes the PKB1 digest, and proves that a payload mutation with the original digest is rejected.
