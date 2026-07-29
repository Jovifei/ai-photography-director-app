# UI1 Product Completion and P1B Offline Benchmark Plan

**Plan date:** 2026-07-29
**Status:** `READY_FOR_OWNER_EXECUTION_AUTHORIZATION`
**Product decisions:** [OWNER_PRODUCT_DECISIONS_2026-07-29.md](OWNER_PRODUCT_DECISIONS_2026-07-29.md)

## 1. Verified starting point

- Current `main/origin/main`:
  `3c6be0314669b4ea4bf12c57f366df0c3510a69c`.
- P1A candidate:
  `96372f39cfe6e7ecf71673f6386afc40182e1dcb`.
- Independent result:
  `PASS_PHASE1_5_P1A_FINAL_REMEDIATION_FOR_OWNER`.
- P1A can fast-forward from current main. The complete `main..candidate`
  lineage contains 11 commits; the final remediation delta
  `d10a731..96372f3` contains the independently reported four commits and
  eight files.
- The main worktree currently contains five modified tracked scripts and
  thirteen Owner-owned untracked results. They are outside this plan and must
  remain byte-for-byte unchanged.
- Current product truth: the Reference → Director path exists, but references
  are session-only, analysis is fixed Demo content, Home search/category
  affordances are not functional, and real Picker-result persistence remains
  unverified.
- The Obsidian project notes dated 2026-07-21 are historical support only.
  They still describe G0/AH0-era facts and must not override Git or Reviewer
  evidence.

## 2. Gate 0 — P1A Owner fast-forward merge

This Gate requires a separate Owner execution command naming the candidate.

### Procedure

1. Fetch origin and require local `main`, `origin/main`, and the approved
   merge base to remain
   `3c6be0314669b4ea4bf12c57f366df0c3510a69c`.
2. Resolve the candidate from the remote branch and require it to equal
   `96372f39cfe6e7ecf71673f6386afc40182e1dcb`.
3. Confirm the original P1A review plus the final delta review jointly cover
   the complete `main..candidate` range.
4. Capture path, byte count and SHA-256 for every pre-existing modified or
   untracked Owner file. Stop if any candidate path overlaps that set.
5. Requalify the exact candidate in a clean detached worktree:
   - Phase 1.5 P0 contracts: 75/75;
   - P1A manifest suite: 40/40;
   - r3 Validator: exit 0, 21 approved, 8 quarantine, 0 failures;
   - Android assemble, JVM tests and lint;
   - privacy audit, forbidden-artifact scan and `git diff --check`.
6. In the main worktree run only:
   `git merge --ff-only 96372f39cfe6e7ecf71673f6386afc40182e1dcb`.
7. Recompute the Owner-file snapshot and require exact equality.
8. Push `main` without force and require `origin/main` to equal the candidate.

### Stop conditions

- Main or candidate SHA moved.
- Review coverage for `main..candidate` is incomplete.
- Owner-file overlap or hash drift.
- Any quality, privacy or artifact gate fails.

No merge commit, rebase, squash, amend, stash, clean or reset is permitted.

## 3. Gate 1 — UI1 product usability closure

After Gate 0, create from the merged main:

- Branch: `codex/ui1-product-usability-closure`
- Worktree:
  `E:\project\_worktrees\ai-photography-director-app-ui1`

The destination must not already exist. UI1 does not modify CameraXManager,
the Pose domain, P1A evidence, model/runtime code, Pipeline code or iOS.

### 3.1 Application structure

Replace root-Composable session ownership with:

```kotlin
interface ReferenceRepository {
    fun observeAll(): Flow<List<ReferenceRecord>>
    suspend fun get(id: String): ReferenceRecord?
    suspend fun import(uri: Uri): ReferenceImportResult
    suspend fun delete(id: String)
    suspend fun clearAll()
    suspend fun recoverBrokenRecords(): RecoverySummary
}
```

`ReferenceRecord` contains an ID, title, opaque derived-image key, width,
height, scene category, searchable tags, `ReferenceBundle`, visible source
label, and created/updated timestamps. It contains no original URI, absolute
path, account identifier or device identifier.

Use Room for record persistence and App-private files for derived images.
Repository methods run off the main thread. Because Room and the file system
cannot share one atomic transaction, use an explicit recovery state machine:

- Import writes and fsyncs a temporary image, renames it to its final opaque
  key, then inserts the `ACTIVE` database record. A failed insert immediately
  removes that final file.
- Delete marks the row `DELETE_PENDING`, removes the derived file, then removes
  the row. `DELETE_PENDING` rows are immediately excluded from
  `observeAll()`, `get()` and active-reference restoration. A file-deletion
  failure stays hidden and retryable. Restart resumes any `DELETE_PENDING`
  operation.
- `clearAll()` marks all applicable rows `DELETE_PENDING` in one Room
  transaction, then performs the same idempotent file/row deletion sequence.
- Startup performs a bidirectional reconciliation: remove rows whose required
  file is missing/corrupt, continue pending deletions, delete temporary files,
  and delete every final derived file that is not referenced by an active
  record.

### 3.2 Safe import and retention

- Keep `PickVisualMedia(ImageOnly)` and do not add storage permissions.
- Validate MIME and decoded dimensions before allocation.
- Downsample with a maximum long edge of 2048 px.
- Apply EXIF orientation, then re-encode a metadata-free App-private JPEG.
- Use a random opaque ID and relative image key.
- Surface picker cancellation, unsupported image, decode failure, oversized
  image and write failure as distinct user-facing states.
- Single delete and clear-all require confirmation and remove both record and
  derived file.
- Startup recovery applies the full bidirectional reconciliation above and
  shows one non-sensitive summary without file names or paths.
- Change the current `android:allowBackup="false"` only within UI1 and add
  explicit API 31+ `data-extraction-rules` plus legacy
  `full-backup-content` allowlists. Include only the reference Room database,
  final files below private `references/`, and the named UI1 preferences file.
  Exclude temporary files, Camera captures/cache, logs, diagnostics, evidence,
  original URIs and every unrelated App-private file.
- After restore or device transfer, reconcile database rows and files before
  exposing the library. Do not display a partially restored reference.
- Explain in privacy copy that Android cloud backup is controlled by the
  user's backup account and system settings, while device-to-device transfer
  is controlled by the Android setup/transfer flow. Local deletion immediately
  removes the live App-private record and image. A prior cloud backup may retain
  an older allowlisted copy until Android replaces or expires it under platform
  retention. The App creates no separately managed remote copy.

### 3.3 Navigation and Home behavior

- Root destinations are `灵感` and `拍摄`.
- Analysis and Director Card remain transient flow destinations.
- Home search matches title, scene, lighting, composition and tags using
  normalized, case-insensitive text.
- Scene chips are real filters with selection, result count, clear action and
  an empty-result state.
- Built-in examples and user references use stable distinct IDs; each recent
  card opens its own content.
- Home exposes one primary import CTA. Library and Camera are reached from the
  root navigation rather than duplicate CTAs.
- App restart restores the library and last active reference when the record
  is still valid. A missing active record falls back to the library without a
  crash.

### 3.4 A1 example-guidance flow

- Before P1C, imported records use the existing fixed guidance only under the
  source label `示例指导 · 非图片分析`.
- Import, Analysis, Director Card and guided Camera all repeat the truthful
  source boundary at the point where a user might otherwise infer AI.
- Example output is not placed in a success Provider Envelope.
- A future provider error never silently changes into example guidance.
- Copy must not imply that different imported images produced different
  analysis.

### 3.5 B1 Camera choice flow

When `拍摄` has no active reference, show:

- `选择参考图并拍摄`;
- `无指导直接拍摄`.

Direct capture uses the existing CameraX lifecycle and capture implementation,
but supplies no `ReferenceGuidance`, no environment/subject panel content and
no Demo overlay. It displays `基础拍摄 · 无参考指导`.

Guided capture retains the current Camera Director structure and adds:

- visible 48dp panel handles and first-use explanation;
- unambiguous Back behavior;
- capture-in-flight, success and failure feedback;
- portrait/landscape adaptive layout;
- correct system Insets and TalkBack traversal.

### 3.6 Responsive UI requirements

- Remove fixed content heights that can clip Chinese copy.
- Support 100% and 200% font scale without hidden primary actions.
- Use scrolling or adaptive pane layouts rather than truncating required
  guidance.
- Keep minimum touch targets at 48dp.
- Preserve the established Apple Glass design tokens while improving spacing,
  hierarchy and contrast; do not create a second design system.

### 3.7 UI1 verification

Unit/JVM coverage:

- repository import, CRUD, clear and recovery;
- EXIF rotations 0/90/180/270;
- search normalization and combined filters;
- stable reference IDs and correct recent-card navigation;
- no-reference Camera decision and direct-capture guidance absence;
- A1 source-label and no-silent-fallback rules.

Compose/instrumentation coverage on a dedicated emulator:

- real system Photo Picker result and cancellation using synthetic media;
- corrupt, unsupported, oversized and rotated images;
- process/App restart persistence;
- single delete and clear-all;
- Android backup/restore on a dedicated emulator using an isolated test
  transport: restore the approved database/image/preferences allowlist, prove
  temporary/import/capture artifacts are absent, and run reconciliation before
  displaying records;
- Home/Camera navigation, Back and both Camera choice actions;
- left/right panels, portrait/landscape, 100%/200% font and TalkBack semantics.

A user-data physical phone may receive only a same-signature
`adb install -r`, controlled launch and manual/read-only observation. Do not
run `connected*AndroidTest` or unreviewed instrumentation on it.

Required gates:

- assembleDebug and all JVM/Compose tests pass;
- lint has 0 errors;
- Manifest adds no INTERNET or media-read permission;
- merged Manifest enables backup only through the reviewed explicit
  data-extraction/full-backup allowlists;
- privacy audit and `git diff --check` pass;
- no image, APK, log, database, private path or device identifier is tracked.

Commit groups:

1. `feat(ui1): add persistent private reference repository`
2. `feat(ui1): add functional discovery and camera entry flows`
3. `fix(ui1): close responsive and accessible product usability`
4. `test(ui1): qualify reference persistence and navigation`
5. `docs(ui1): record product usability evidence`

Push only the UI1 branch, then stop for independent review.

## 4. Gate 1.5 — UI1 Owner merge

An independent UI1 PASS does not merge the branch. A separate Owner command
must name the reviewed UI1 candidate SHA.

The merge agent must:

1. fetch origin and verify `origin/main` is still the UI1 branch base;
2. verify the reviewed SHA equals the remote UI1 branch head;
3. re-run the documented build, test, lint, Manifest, privacy and artifact
   gates in a clean detached worktree;
4. snapshot and preserve all Owner-modified/untracked main-worktree files;
5. use only `git merge --ff-only <REVIEWED_UI1_SHA>`;
6. push without force and require `origin/main` to equal the reviewed SHA;
7. stop before P1B, P1C, Pipeline, Pose or release work.

Any SHA drift, review gap, file overlap or failed gate stops the merge.

## 5. Independent Gate P1B — PC offline benchmark

P1B is independent of UI1. The A1/B1/C1 decisions do not themselves authorize
model download or inference. A separate Owner command must authorize either
artifact quarantine only or artifact plus offline runtime.

Planned branch and locations:

- Fixed branch base:
  `96372f39cfe6e7ecf71673f6386afc40182e1dcb` after Gate 0 has made it
  `origin/main`. P1B does not wait for UI1 and must not use a UI1 candidate as
  its base.
- Branch: `codex/phase1-5-p1b-qwen-offline-benchmark`
- Worktree:
  `E:\project\_worktrees\ai-photography-director-app-phase1-5-p1b`
- Download quarantine:
  `E:\Claude_allow\Download\phase1-5-p1b\<run-id>\quarantine`
- Runtime:
  `E:\AI_Tools\Other\LocalLLM\Qwen3VL-P1B`
- Evidence:
  `E:\project\_benchmark_evidence\phase1-5-p1b\<run-id>`

Fixed candidate:

- `Qwen/Qwen3-VL-2B-Instruct`
- revision `89644892e4d85e24eaac8bacfd4f463576704203`
- primary SHA-256
  `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`

The only allowed corpus manifest is:

- path:
  `docs/phase1_5/p1a/public_corpus_manifest.v3.json`
- SHA-256:
  `6BFD43091E598AA056CA1CB2C2956F4D012CB851F9DB4058574296305EBC6AFC`
- expected state: 21 approved, 8 quarantine.

Before model loading, verify the exact 12-file allowlist, repository totals,
LFS OIDs, local hashes, redirects, licenses, malware scan, dependency wheel
lock, offline enforcement and the corpus path/hash/count above. Any mismatch
stops before model loading or inference and produces a fail-closed report.
Use Python 3.12, `transformers==4.57.0`, `qwen-vl-utils==0.0.14`, a fully
hashed wheel lock and `trust_remote_code=false`.

Runtime requires at least 8GB free VRAM, 12GB free RAM and 40GB free disk.
Insufficient headroom stops the run; there is no automatic CPU fallback.
Inference is network-denied and reads only the 21 approved r3 public samples.

The benchmark records strict JSON/Schema success, field completeness,
hallucination review, latency, VRAM/RAM, repeatability and failure taxonomy.
Model files, images, per-sample output and logs remain outside Git. A benchmark
PASS does not authorize Android, Pipeline, cloud, private-image or production
integration.

## 6. Documentation and knowledge synchronization

After P1A is merged and pushed, update the three Obsidian project notes:

- project status `paused` → `active`;
- authoritative main SHA;
- completed Android/UI0/AA0-P0/Phase1/P0/P1A stages;
- UI1 and P1B as next independent Gates;
- AH0 full dual-device, real provider, Pipeline, Pose and iOS as unfinished.

After UI1 merges, update product capability status again and record A1/B1/C1
as implemented rather than merely decided. Obsidian is never updated ahead of
the corresponding Git merge.

Historical implementation/review reports remain immutable. New Gate reports
must distinguish static, emulator, physical-device, runtime and independent
review evidence.

## 7. Overall completion boundary

The next engineering milestone is complete only when:

1. P1A is fast-forward merged with Owner files unchanged.
2. UI1 passes independent review and the real local product flow is usable.
3. Obsidian reflects the merged facts.

P1B may run in parallel only under a separate explicit authorization and never
blocks UI1. Pipeline integration, real VLM App integration, Phase 2 Pose,
second-device Android MVP qualification, iOS and production release remain
later independent Gates.
