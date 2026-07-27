# Phase 1.5 P1A Corpus and Artifact Evidence Remediation

Status: `P1A_REMEDIATION_READY_FOR_INDEPENDENT_DELTA_REVIEW`.

This document records corrective evidence only. It does not authorize a model
download, runtime installation, inference, Android integration, cloud use, or
product promotion.

## Candidate lineage

- Base/main: `3c6be0314669b4ea4bf12c57f366df0c3510a69c`.
- Rejected candidate: `42f9686d8e37df99997f6a82c44d0d610f056fc2`.
- Parent corpus ID: `phase1-5-p1a-public-corpus-20260726T121500Z`.
- New corpus ID: `phase1-5-p1a-public-corpus-20260727T150809Z-r2`.
- New external evidence root ID: `public-corpus-20260727T150809Z-r2`.
- The parent root is immutable and was not overwritten, deleted, or re-labelled.

## Independent-review findings and corrections

| Finding | Correction | Result |
| --- | --- | --- |
| B1: Qwen exact weight-license evidence was insufficient | Captured exact-revision README, Hub API, tree, Hub license-field semantics, Apache terms, and separate Qwen code-license evidence without requesting an artifact | `WEIGHT_LICENSE_EVIDENCE_READY_FOR_INDEPENDENT_REVIEW`; not legal approval |
| B2: age/no-person claims were not fail closed | Introduced `NONE`, `VISIBLE_DOCUMENTED_ADULT`, and rejected `VISIBLE_AGE_UNCERTAIN`; re-reviewed content and quarantined `p1a-010` and `p1a-015` | Approved human samples have nonvisual adult evidence |
| B3: validator was not independently executable | Replaced it with a three-argument CLI that validates bytes, hashes, image decode, metadata, license evidence, age, quarantine, categories and aggregate | Exit `0` for r2; report is non-sensitive and path-free |
| B4: no true single-person-full-body sample | Added `p1a-025`, a one-person full-length image with an official title documenting age 82 | Full-body requirement is explicitly tested |
| B5: attribution and source evidence incomplete | Added five offline review files per approved sample, fresh source revision records, licence-text snapshots, corrected quarantine attribution for `p1a-008` and `p1a-023`, and mojibake rejection | 20 approved samples have reviewable license evidence |

## Corpus r2 disposition

- Approved: 20.
- Quarantined: 6.
- Required old approved records moved to r2 quarantine: `p1a-008`, `p1a-019`, `p1a-023`, `p1a-024`.
- Additional fail-closed quarantine: `p1a-010` (title/visual mismatch) and `p1a-015` (not every person has adult evidence).
- Additions: `p1a-025` true single-person full body; `p1a-026` no-person cafe interior; `p1a-027` adult window/lighting scene; `p1a-028` documented adult occlusion scene; `p1a-029` exactly two documented adult astronauts.
- Recovered after a completed visual review: `p1a-006`, an empty public window scene, now covering indoor window, backlight, side light and negative space.
- `no_person` is present only where `human_presence=NONE`. Distant or uncertain people are not treated as no-person.

Every approved sample has the following external, UTF-8, offline-reviewable files:

1. `metadata/<id>/commons_api.json`
2. `metadata/<id>/source_page_record.json`
3. `metadata/<id>/license_evidence.json`
4. `metadata/<id>/transport.json`
5. `metadata/<id>/manual_visual_review.json`

The external root also contains CC0 1.0, CC BY 4.0 and Commons public-domain policy snapshots. Public-domain samples retain item-specific basis; CC-BY validation requires author, title, page, license, modification and recommended attribution.

## Coverage and aggregate integrity

All 24 required categories have a true approved sample mapping in
`category_coverage.v2.json`, including `single_person_full_body`,
`two_people`, `indoor_window`, `backlight`, `side_light`, `no_person`, and
`negative_space`.

Aggregate SHA-256: `20ef4759fd1caa097dbafe4b9c81194b38595782aff4dc2caca4b1b3b7ad0008`.

The algorithm sorts approved sample IDs and hashes the UTF-8 compact JSON
projection containing source/sanitized hashes, source revision, license ID,
license-evidence hash, human-presence state and sorted category tags. It has no
timestamp or local path input.

## Qwen license evidence

- Model: `Qwen/Qwen3-VL-2B-Instruct`.
- Revision: `89644892e4d85e24eaac8bacfd4f463576704203`.
- Basis: `MODEL_CARD_METADATA_AT_IMMUTABLE_REVISION`.
- Publisher assertion: official Hub organization `Qwen`.
- Exact README YAML and exact revision API both declare `apache-2.0`.
- The exact model tree contains no local `LICENSE`, `NOTICE`, or `COPYING`; no repository file is substituted for weight-license scope.
- `license_link` is absent and no commercial-restriction marker was found in the exact README.
- Qwen3-VL code-license text is recorded as separate code evidence only.
- External evidence root ID: `model-license-evidence-20260727T152339Z`.
- Download, runtime, inference and app-integration authorization: all `false`.

## Validation record

The exact corpus gate is:

```text
python -B scripts/validate_phase1_5_p1a_corpus.py --manifest docs/phase1_5/p1a/public_corpus_manifest.v2.json --external-root <r2-root> --report-json <external-report>
```

The r2 invocation exited `0` with 0 failures. `test_phase1_5_p1a_manifests.py`
has 14 offline tests covering the required CLI, schema, safe reports, source and
sanitized hash failures, decode, EXIF, duplicate content, human/age, quarantine,
full-body, attribution, mojibake, aggregate, traversal and absolute-path cases.

## Boundaries retained

No source image, sanitized image, APK, database, model, model pointer, secret,
private path, Owner media, account data or device data is tracked by Git. No
artifact GET, Range, or HEAD request was performed. No model, runtime, GPU,
container, cloud API, Android source, CameraX, UI0, Pose provider, pipeline,
P1B, main merge or product release was entered.
