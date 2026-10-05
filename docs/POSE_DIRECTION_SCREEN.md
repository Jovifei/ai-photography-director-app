# Pose direction tap screen

PoseDirectionScreen is the phone consumer for a pose-direction-bundle version 1 document from nightly-photo-intelligence-pipeline. The projects home has a 构图口令 entry. The screen lists compositions. Tapping one stays on this screen and shows that item's stick-figure SVG and spoken direction together. Optional why_it_works lines (composition, light, gaze, clothing) appear under the prompt.

authority must be JSON false. This screen does not set authority, does not mark the bundle ready_for_t14, and is not T14 approval, a golden vector, or a production unlock. An empty or invalid bundle shows a plain failure instead of crashing.

The checked-in file android/app/src/main/assets/pose_direction/synthetic_pose_direction_bundle_v1.json is a synthetic fixture written for this screen. Do not replace it with private photos, private filenames, or a real producer dump.

The parser fails closed when spoken text is missing, the figure is missing, authority is not false, or an id or figure reference is a private path or filename. Accepted ids look like img-01-aaaaaaaa. An optional figure file name must be a relative img-NN-hhhhhhhh.svg and is never a photo path.
