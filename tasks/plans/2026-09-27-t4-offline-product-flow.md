# T4 offline product flow: stage execution plan

Date: 2026-09-27. Remote planning task: `c2c_a91f` in the bound `Ai相机` ChatGPT Project. The remote revision at iteration 1 supersedes its narrower initial plan. Codex owns implementation and verification. This is the current T4 stage plan; earlier G0, AA0, P20, P25T and T3 plans are historical evidence for their own gates.

## Objective and exact base

Close the existing offline Android journey from project and reference selection through Director, Camera, private capture library, and explicit system export. T4 must also expose one all-project reference library with real search/filter and project ownership, plus persistent project naming. Keep the user informed of whether guidance came from a validated reference Bundle, an explicit example, or no guidance. Fix only gaps demonstrated by the T4 audit.

- Base: `codex/t3-reference-camera-controls@14a56f49e1220eb8139bf7280c747124118fdb21`, stacked on Draft PR #6. T3 source qualification and independent review passed on their recorded exact SHAs; T4 requires its own evidence and review.
- Worktree: isolated `codex/t4-offline-flow-20260927` from that exact base. The Owner's dirty `main` and another task's uncommitted roadmap cleanup stay separate.
- Hard boundary: synthetic media and dedicated API 35 emulator only. No real photos, Qwen/LAN, Pipeline runtime, Pose, physical device, signing, public release, or main merge.

## Stage work packages

### M0. Freeze baseline and reconcile plans

- [x] Verify origin, full T3 SHA, clean new worktree, and remote T3 head after fetch.
- [x] Record current CameraX version, permissions, project navigation owners, T3 camera controls and relevant tests from this branch. Treat old main and Obsidian status statements as historical.
- [x] Confirm reviewed T3 SHA and ancestry; the isolated branch remained separate from Owner work.

### M1. Audit the whole offline path before coding

- [x] Trace project creation, reference selection and provenance, READY-only summary, Director entry, Camera choice, T3 controls, capture persistence, capture library, and system export. Record each reachable entry and back route.
- [ ] Check this state matrix against implementation and tests:

| State | Required outcome |
| --- | --- |
| READY reference with matching validated Bundle v1 | Guided Camera with correct source and T3 controls |
| Explicit built-in example | Guidance clearly labelled example throughout; never Provider SUCCESS |
| No primary/reference | Choice between selecting a reference and direct Camera |
| FAILED, UNAVAILABLE, provider not configured | No fabricated AI guidance; direct Camera where offered |
| Bundle ID/version/required-field mismatch | Reject before guidance; no partial state |
| Missing/stale primary | Safe project/selection recovery without stale guidance |
| Direct Camera | No reference, subject or environment guidance |

- [x] Inspect semantics, 200% font, portrait/landscape reachability, error and cancel recovery, and project naming. Note verified gaps only. Search existing tests before adding one.
- [x] Concrete gaps at exact T3: no Home entry for the all-project reference library; library does not search/filter/show owner or return to owner; root `searchQuery` is unused; project creation silently assigns a number and there is no persisted rename API; no T4 E2E qualifier. The active-reference snapshot also needs a live-transition scenario test before any fix.

### M2. Implement the smallest integration repair

- [x] Add a Home entry for all-project reference browsing while retaining a distinct capture-selection mode. The library shows owning project, truthful source/state, real search + P25T-required filters, and separate empty-library / zero-results states.
- [x] Use the existing persisted project title column for explicit create name and rename. Reject blank names; preserve current title limit; verify rename in the library and after process/repository reload. No new table or migration.
- [x] Open a browsed reference in its owning project context and return coherently by stable IDs. Deleted/missing owner or reference recovers safely; capture picker retains its existing behavior.
- [ ] Reuse existing navigation and camera state. If needed, add one narrow presentation adapter for direct, validated, example and unavailable guidance; keep schema and storage contracts unchanged.
- [x] Preserve CameraX lifecycle and T3 focus/exposure/zoom ownership. The compact landscape chrome position was adjusted after an observed overlap; CameraXManager was untouched.
- [ ] Keep the private capture and export recovery protocol intact; do not infer success from an external URI or a transient callback.
- [ ] Stop and re-plan if a repair requires Room migration, Bundle/Provider/error-policy changes, new permissions, dependency upgrades or a broader architecture rewrite.

### M3. Verify on synthetic inputs

- [x] Existing guidance/READY-only/T3 JVM suite passed; added pure tests for cross-project library filtering and isolated root-flow tests for Bundle ID mismatch, FAILED→READY→UNAVAILABLE, live guidance removal, deleted primary and the next reference's direct route. The replacement exact-candidate qualification remains pending.
- [x] Pure tests cover all-project search and combined scene/source/status/project filters; Room-backed test covers rename persistence and invalid targets.
- [x] T4-specific API 35 instrumentation covers two synthetic projects, Home→library, search/filter/source/owner, project naming/rename, 200% font and landscape. Existing guided/direct Camera and capture/export tests passed separately; final exact-candidate qualification remains pending.
- [x] Dedicated API 35 emulator flow: no-primary/direct entry; synthetic validated Bundle/guided entry; reference switch and back navigation; non-READY/unavailable; capture/library/export where changed; TalkBack/large font/rotation where affected. The final qualifier also covered live Bundle mismatch, READY→UNAVAILABLE, deleted guided primary and next-reference fallback.
- [x] Run exact-candidate Debug build, full JVM suite, relevant Android tests, lint, contract tests, privacy audit and `git diff --check`. Save commands, exit codes and short evidence outside Git; label unrun checks accurately.
- [x] Assert no INTERNET or broad media permissions, model/Pose dependency, private/generated media, Pipeline contract mutation, or unrelated roadmap cleanup in the diff.

### M4. Evidence, review and delivery

- [x] Write a T4 result with exact T3 base and T4 source SHAs, test counts, emulator qualification, permissions/dependency diff, and explicit external gates in `docs/handoff/T4_STAGE_STATE_20260927.json`.
- [x] Commit the bounded T4 changes after verification. Run `python scripts/prepush_privacy_audit.py` before the non-force push.
- [x] Stack Draft PR #8 on T3 Draft PR #7, and obtain independent review of the exact T3..T4 source delta; findings were repaired and the final SHA requalified.
- [x] Stop at T4 synthetic/API35 PASS for Owner landing decision. Do not infer physical, human, provider, signing, or release PASS.

## Technology follow-up, separate from T4 implementation

- CameraX official releases and Android camera samples: compare newer Compose viewfinder, lifecycle, focus/exposure and device fallback in a future ADR; no T4 upgrade without a concrete defect and device regression plan.
- LiteRT-LM, LiteRT samples, AI Edge Gallery, ExecuTorch and MMPose: research runtime/license/model-weight/privacy/Android 11 compatibility in future gates. Existing MediaPipe target CPU hang and ML Kit Pose beta remain unresolved; no Pose integration in T4.
- Later gated tracks: Provider/Qwen real validation at 1→5→20 photos; 20-item human editorial rights/privacy decisions; real Pipeline exporter via Bundle Contract; DPAPI signing identity, physical-device/pilot and Owner release decisions. Keep these tracks distinct.

## Review

T4 reviewed source `a69ede68f3e54e5ab006dbfd7a65c33040590f93` passed the clean qualification run `412699bebfe344d5b9aeca1b68135090` and exact-SHA independent read-only review. The qualification recorded JVM 141/141, T4 Android 7/7, T3 Android 7/7, root 2/2, P25U camera 1/1, export 3/3, recovery prepare/verify/cleanup 1/1 each, build/lint/contract/privacy/diff PASS and 16 hashed outputs. Draft PR #8 remains unmerged. Earlier failed exploratory verification and host-memory attempts are retained as diagnostic history, not promoted to PASS. The C2C remote connector reads the dirty Owner `main`, so remote evidence review cannot substitute for the local exact-diff review.
