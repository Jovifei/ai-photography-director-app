# Non-private Evaluation Corpus Plan

**Status:** `PLANNED — NO_IMAGE_DOWNLOADED`.

The manifest at `fixtures/vlm_evaluation_corpus_manifest.v1.json` contains 20 planned cases covering urban night, indoor window light, seaside, forest, street, café, architecture, backlight, side light, low light, zero/one/two people, full/half/close framing, action, occlusion, complex background, and minimal background. It is schema-bound by `schemas/vlm_evaluation_corpus_manifest.v1.schema.json`.

Every entry is deliberately `PLANNED`, has `sha256: "UNKNOWN"`, no selected official source/license, and `owner_approval_required: true`. These are honest placeholders, not downloaded images or invented hashes.

## Acquisition requirements

Only an Owner-approved, non-private source with a directly recorded official URL, asset license, commercial-evaluation permission, redistribution permission, local computed SHA-256, and retention decision may move a sample to acquired. Allowed categories are official public licensed material, official synthetic material, or owner-supplied explicitly non-private material.

Prohibited inputs include private photos, social-media scraping, Douyin/TikTok material, bystander images, children, unprovenanced images, and any image with uncertain rights. Acquisition is a future, separately authorized operation; this P0 commit has no image assets.
