# T3 reference comparison and camera controls

Base: reviewed P25U `7b2c09d189b598e1517af784f06d89eb4ea62320` on isolated branch `codex/t3-reference-camera-controls`. Scope is one Android Gate.

1. Director camera receives only the selected primary reference's ID, title and app-private JPEG basename. Show a small card outside the shutter area and a fit-scaled expanded image. Back closes the image first; failures show retry/return without exposing a Picker URI. Basic capture has no reference card.
2. CameraX binding retains its `Camera` for tap-focus and capability-derived exposure index. The PreviewView maps tap coordinates. Ignore results from superseded requests and unbound cameras; unavailable hardware leaves capture usable. Commit exposure when the slider is released; show confirmed EV and revert a failed request.
3. Start with failing focused tests for behavior and stale-result handling. Build and run on a verified API 35 emulator by explicit serial. Test the actual root route, capture/export regressions, layouts, and lifecycle recovery. Keep runtime evidence outside Git, with source SHA and log hashes; mark real-device visual/optical checks pending.
4. Verify origin, privacy audit and clean diff before non-force push. Create a separate Draft PR based on PR #6's branch; do not merge, sign a new key, use private media, run Provider/LAN/Pipeline, or touch a physical device. Independent review is required for the final candidate.

Stop on a failing safety invariant, unavailable exact emulator, or regression that cannot be isolated. Roll back only T3 commits on the isolated branch; preserve Owner and P25U state.
