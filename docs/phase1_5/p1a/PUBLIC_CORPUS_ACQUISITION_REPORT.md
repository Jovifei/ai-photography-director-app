# P1A Public Corpus Acquisition Report

Status: `CORPUS_ACQUIRED_AND_VERIFIED` for the approved evaluation set.

The acquisition attempt resolved 24 public Wikimedia Commons samples. Network requests wrote first to the host-approved download staging area and were copied only after validation into the external evidence root identified by `public-corpus-20260726T121500Z`. Git contains neither source images nor sanitized images.

| Measure | Result |
| --- | --- |
| Acquisition target | 24 |
| Public samples acquired | 24 |
| Approved samples | 20 |
| Quarantined samples | 4 |
| Minimum requirement | 20 |
| Accepted licenses | CC0, Public Domain, CC BY 4.0 |
| Source | Wikimedia Commons pages and upload URLs |

For every acquired item the external metadata records the Commons page, original/API-selected download URL, final response URL, redirect chain, MIME, byte size, magic/decode result, dimensions, source SHA-256, sanitized SHA-256, license, attribution, and GPS result. Original-size fetches were subject to Commons rate limiting for some assets; where the Commons API selected an official thumbnail representation, the manifest calls that out instead of claiming it was an original.

All final images decoded successfully, were within the 20 MB / 10,000 pixel edge / 60 MP limits, and were re-encoded as JPEG without EXIF. The 20 approved records and their relative evidence paths are immutable inputs to `public_corpus_manifest.json`.

The four rejected candidates are retained only in the external `quarantine/` area: a private-bedroom view, a street crowd with age uncertainty, a face-obscured portrait with age uncertainty, and an historical dual portrait whose adult status could not be confirmed to the required standard. They are excluded from the manifest and from all evaluation use.
