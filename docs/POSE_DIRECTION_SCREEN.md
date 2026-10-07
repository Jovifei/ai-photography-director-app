# Pose direction tap screen

PoseDirectionScreen is the phone consumer for a pose-direction-bundle version 1 document from nightly-photo-intelligence-pipeline. The projects home has a 构图口令 entry. The screen lists compositions. Tapping one stays on this screen and shows that item's stick-figure SVG and spoken direction together. Optional why_it_works lines (composition, light, gaze, clothing) appear under the prompt.

From a composition detail, **用此构图拍摄** carries that selection into the existing direct-capture shoot flow (`DIRECT_CAPTURE`). On the CameraX shoot UI (and on retake via `CAMERA_DIRECTOR` when a selection is still held), a compact overlay shows the same spoken_direction and stick-figure SVG together. The overlay is Compose-only chrome; it does not change CameraX binding or capture. If no pose is selected, shoot behaves as before: no panel, no fake direction, no crash.

authority must be JSON false. This screen does not set authority, does not mark the bundle ready_for_t14, and is not T14 approval, a golden vector, or a production unlock. An empty or invalid bundle shows a plain failure instead of crashing.

## Bundles on device

- **Product default:** `android/app/src/main/assets/pose_direction/sample_pose_direction_bundle_v1.json` — sanitized 18-item non-authority sample (hash ids + spoken_direction + inline stick-figure SVG + optional why_it_works). No photo bytes, private paths, or EXIF. img-08 is excluded. Still authority=false and not T14.
- **Unit-test fixture:** `android/app/src/main/assets/pose_direction/synthetic_pose_direction_bundle_v1.json` — tiny 2-item synthetic kept for parser/ViewModel tests. Do not replace either file with private photos or private filenames.

`PoseDirectionScreen` opens `POSE_DIRECTION_ASSET` (the sample). Optional local developer loading: `PoseDirectionDocumentLoader` can read an external pose-direction-bundle JSON path when explicitly supplied at composition time. Missing, blank, unreadable, or oversized files fail closed and fall back to the sample asset. Do not hardcode private `F:\` paths into committed defaults.

The parser fails closed when spoken text is missing, the figure is missing, authority is not false, or an id or figure reference is a private path or filename. Accepted ids look like img-01-aaaaaaaa. An optional figure file name must be a relative img-NN-hhhhhhhh.svg and is never a photo path.
