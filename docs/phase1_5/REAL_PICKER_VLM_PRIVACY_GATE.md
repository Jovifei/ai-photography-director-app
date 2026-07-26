# Real Photo Picker to VLM Privacy Gate

**Current state:** `REAL_PICKER_E2E = NOT_VERIFIED`.

No real Picker, private image, model, network, or analysis flow is exercised in Phase 1.5 P0. A later gate must prove all of the following before any user image reaches a provider:

1. User performs the explicit selection; the app does not browse a gallery or request broad media permission.
2. URI access and expiry are handled predictably; decoding is temporary, orientation-aware, size-bounded, cancellable, and memory-bounded.
3. Raw original and thumbnail are not persisted; raw bytes, URI, EXIF/location, and image-derived identifiers are not written to logs or Envelope.
4. Consent identifies the exact processor and data route. A cloud route additionally needs a separately approved backend, authentication, retention, deletion, incident, and cost design.
5. Error/cancel/retry clears temporary decode state. Provider failure never silently changes to Demo output.
6. User can delete any retained Bundle/Envelope permitted by a future retention decision; no hidden cache survives deletion.

## P0 data-flow decisions

For this phase, all raw-image, thumbnail, Bundle, and Envelope retention is **zero** because none is produced or persisted. Safe planning/test logs may contain no image/URI/path/account/device/location content. Any later non-zero retention requires Owner approval and a source-specific deletion proof.

Future safe test paths are limited to Owner-provided non-private images, app-generated MediaStore test images, or clearly licensed public images. They are not authorized by this document.
