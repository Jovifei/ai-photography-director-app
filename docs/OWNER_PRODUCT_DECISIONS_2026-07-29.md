# Owner Product Decisions — UI1

**Decision date:** 2026-07-29
**Owner:** Jovi
**Status:** `LOCKED_FOR_UI1_PLANNING`

This record fixes the product behavior that was still ambiguous after the
Phase 1.5-P1A independent review. It is a forward-looking UI1 decision record,
not historical P1A evidence and not authorization to merge, download a model,
run inference, or enter production integration.

## Decision A1 — analysis before a real VLM exists

Until a separately qualified real provider is integrated, an imported
reference may continue through the Director flow using **explicit example
guidance**.

Required behavior:

- Every affected surface displays `示例指导 · 非图片分析`.
- The import screen states before continuation that the selected image does
  not change the example guidance.
- Analysis, Director Card, and Camera Director must not use `AI 分析完成`,
  `真实分析`, `本地模型`, `Pipeline` or equivalent provenance.
- Example guidance cannot be wrapped in a successful
  `ProviderAnalysisEnvelope`.
- A failed or unavailable future provider may offer an explicit user-selected
  example fallback, but must never silently replace a real result with Demo
  content.
- The selected image itself may be displayed and stored under Decision C1;
  only the interpretation remains an example.

## Decision B1 — entering Camera without a reference

Selecting the root `拍摄` destination without an active reference opens a
choice surface rather than an empty Camera Director.

The surface contains two first-class actions:

1. `选择参考图并拍摄` — select an existing reference or import a new one,
   then enter Camera Director with reference guidance.
2. `无指导直接拍摄` — enter the existing CameraX capture experience with
   no reference, no example analysis, and no subject/environment guidance.

Required behavior:

- Direct capture is visibly labelled `基础拍摄 · 无参考指导`.
- No placeholder or Demo guidance is injected into direct capture.
- Camera permission, capture, Back and lifecycle behavior are shared with the
  existing CameraX path.
- CameraXManager and the frozen Pose domain are not modified by this product
  decision.

## Decision C1 — local reference retention

An imported reference is retained in App-private storage until the user
deletes it.

Required behavior:

- The system Photo Picker remains image-only and requires no broad media-read
  permission.
- The original Picker URI is used only during import and is not persisted.
- Import creates a bounded, orientation-corrected, metadata-free derived image
  in App-private storage.
- Persistent records may store an opaque ID, user-visible title, opaque
  relative image key, derived-image width/height, structured analysis Bundle,
  source label, category/tags and created/updated timestamps. They do not store
  the original Picker URI, an absolute private path or image metadata.
- The library supports single-item deletion and `清空全部`.
- Deleting a record deletes its derived image and local analysis record.
- Missing/corrupt files and orphaned temporary files are recovered
  fail-closed without crashing or exposing a stale record.
- Uninstalling or clearing App data removes the private library through normal
  Android behavior.

### C1 backup and device-transfer decision

The Owner allows the private reference library to participate in Android
Auto Backup and device-to-device transfer.

The allowlist is limited to:

- the Room reference-library database;
- metadata-free derived reference images under the dedicated private
  `references/` directory;
- UI1 preferences required to restore the active reference and first-use
  guidance state.

The following remain excluded:

- original Photo Picker URIs and source media;
- temporary import files and recovery markers;
- Camera capture cache;
- logs, diagnostics, screenshots and test evidence;
- credentials, device identifiers and absolute paths.

Restore must run the same database/file reconciliation as normal startup before
showing any reference. An incomplete or corrupt restore is cleaned fail-closed.
The product privacy copy must explain that Android cloud backup is controlled
by the user's backup account and system settings, while device-to-device
transfer is controlled by the Android setup/transfer flow. Local deletion
immediately removes the live App-private record and derived image. A previously
retained cloud backup may contain an older allowlisted copy until Android
replaces or expires it under the platform's retention behavior. The App creates
no independently managed remote copy.

## Acceptance summary

UI1 does not pass unless all three decisions are simultaneously true:

- example guidance is unmistakably non-AI;
- Camera can be used without a reference and without fabricated guidance;
- imported references survive process/App restart until explicitly deleted.
- an Android backup/transfer restores only the approved private-library
  allowlist and passes post-restore reconciliation.
