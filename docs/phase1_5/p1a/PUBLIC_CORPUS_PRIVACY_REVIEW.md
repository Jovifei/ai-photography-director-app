# P1A Public Corpus Privacy Review

Status: `PASS_WITH_QUARANTINE`.

Manual review approved 20 public, non-private evaluation scenes. The set covers city night, street, beach, forest, architecture, and commercial cafe interiors; it contains no owner media and no downloaded user gallery material.

The review excludes private domestic space, suspected minors, uncertain-age portraiture, crowds that cannot be safely classified, nudity, medical or religious context, political activity, readable personal IDs, plates, addresses, or sensitive scenes. Commercial interiors are treated as public settings; any visible adults are assessed as incidental subjects and no identity claim is made.

Sanitization is a re-encode to JPEG followed by SHA-256 verification. The evidence metadata records `source_gps_present` and the final manifest requires `exif_removed=true` and `gps_removed=true` for every approved image. No screenshot, owner photo, account data, or absolute local path is stored in Git.

Future additions require the same source-license review, manual safety review, EXIF/GPS removal, and fail-closed quarantine decision. This review does not authorize model inference, cloud upload, training, or product bundling.
