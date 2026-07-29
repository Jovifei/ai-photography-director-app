# UI1 Photo Picker Import and Product Usability Closure

## Scope and baseline

- Approved base: `bdb04f344241149b32f9412dc23807d6c47a2179`
- Candidate branch: `codex/ui1-product-usability-closure`
- Evidence run: `ui1-picker-recovery/20260729T144927Z` (repository-external)
- Physical-device automation: not performed.

This closure implements immediate Photo Picker import. The app accepts one image explicitly
selected by the user, copies it while the transient grant is valid, and retains only a private,
metadata-free JPEG derivative plus safe reference metadata. The app does not retain or log the
Picker URI.

## Root-cause conclusion

Classification: **E — IMPORT_STATE_LIFECYCLE_BUG**.

The pre-recovery UI held the Picker URI in Compose state and attempted media work after the
result callback/recomposition boundary. It also contained overlapping experimental decoder paths.
The repaired flow reads the source only in the callback-triggered ViewModel job, uses one bounded
stream path, and performs all later work on app-private files. Both qualified emulator environments
successfully exercised the real system Picker, so neither URI grant nor provider defect remains a
blocking classification.

## Privacy and persistence contract

- Contract: `PickVisualMedia(ImageOnly)`; one explicit user selection only.
- Source URI: transient only; absent from Room, saved state, logs, UI state, and final media state.
- Private derivative: opaque `*.jpg`, EXIF/metadata-free, maximum edge 2048 px.
- Import parts: app cache only; cache is excluded from Android backup.
- Durable data: final reference JPEGs, Room database, and the explicit UI1 preference allowlist.
- Forbidden permissions verified absent: `INTERNET`, `READ_MEDIA_IMAGES`,
  `READ_EXTERNAL_STORAGE`, and `WRITE_EXTERNAL_STORAGE`.

## Qualified environments

| Environment | Qualification | Result |
| --- | --- | --- |
| Dedicated Android 15 / API 35 emulator | qemu virtual device, no user media used | system Picker and diagnostic suite passed |
| Dedicated Android 14 / API 34 emulator | independently created, no snapshot, no user media used | system Picker and diagnostic suite passed |

No device serial, user account, user image, original display name, or Picker URI is present in
this report or in Git.

## Verification summary

| Area | Result |
| --- | --- |
| JPEG and PNG direct MediaStore decode before import | PASS |
| EXIF 90/180/270 correction and derived orientation | PASS |
| 2048 px derivative scaling and metadata removal | PASS |
| Corrupt, MIME-mismatched, and controlled oversize source | PASS — safe failure, no final file or staging residue |
| Real Picker result to private preview | PASS on both qualified emulators |
| Cancel / return cleanup | PASS — no retained test reference after cleanup |
| Room persistence, restart, delete, clear, missing/corrupt/orphan recovery | PASS |
| Home search, scene filtering, root navigation, Reference Library, Direct/Guided Camera | covered by the UI1 product test suite |
| User-data physical phone media automation | NOT RUN — intentionally prohibited |

## Scope declarations

- No private photo was read.
- No gallery permission or network permission was added.
- No model, VLM, Pipeline, Pose provider, or P1B work was started.
- CameraXManager and the Pose Domain were not modified.
- Main was not merged or modified.

