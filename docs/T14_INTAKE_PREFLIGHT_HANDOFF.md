# T14 synthetic intake integrity preflight

Status: COMPATIBILITY_PREPARATION_ONLY. This is a single supplied-document integrity tool, not Android consumer validation, producer provenance verification, or T14 approval. ready/db_import/t14_authority/producer_origin_verified are always false; consumer_validation is NOT_RUN.

## Manifest v1

The UTF-8 JSON manifest has exactly contract_version, mode, producer_revision, files. contract_version is T14_PREFLIGHT_MANIFEST_V1; mode is SYNTHETIC_CONTRACT_TEST_ONLY. producer_revision is a 40-lowercase-hex caller assertion, not authenticated producer evidence. files contains exactly one object with path, bytes, sha256.

path is a single ASCII leaf name (1–128 chars, starts alphanumeric, subsequent alphanumeric/dot/underscore/hyphen). It cannot be a Windows device name or end in dot; separators, absolute/UNC/drive/traversal paths are rejected. Nested/multiple-file packages are unsupported and rejected rather than reported verified. bytes must be an actual integer equal to raw-document length; sha256 is exactly64 hex chars. CHECKSUMS.sha256 contains exactly one SHA/name record bound to the same leaf and raw bytes. Uppercase hex is accepted and normalized for comparison.

Raw input <=512KiB, manifest <=16KiB, checksums <=4KiB; JSON depth <=64. Strict UTF-8, no BOM, duplicate keys or nonfinite constants; raw JSON root must be an object. This does not prove complete PKB1 schema or guidance semantics. Intentionally invalid future producer vectors need a separately reviewed intake contract; this tool accepts readable positive synthetic documents only.

## CLI

python scripts/t14_bundle_intake_preflight.py vector.json manifest.json CHECKSUMS.sha256

The CLI reads only supplied files, rejects symlink/non-file inputs, binds the actual raw filename, performs bounded reads before decode, and returns exit0 only for successful envelope integrity; rejection returns structured flags=false with exit1. It writes no files, DB rows, READY or permissions and calls no network/provider.

Run python scripts/test_t14_bundle_preflight_contract.py for25 real typed-input/CLI tests. The retained legacy negative entry delegates to this suite. Existing progress tests remain20. Producer test utility uses a consumer-origin frozen synthetic fixture, not producer golden evidence.

T14 remains PENDING_EXTERNAL_GOLDEN_VECTORS. Neither this script nor a manifest can grant runtime, model, real-photo, human, signing or release authority.
