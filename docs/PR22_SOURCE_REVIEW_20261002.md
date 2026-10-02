# PR22 Source Review Record

Date: 2026-10-02

## Review boundary

Candidate:

- PR #22
- head `57081b4aeb5cfeffdfe7460b359c5e3e031b27b3`

This is a remote source inspection record. It is not a replacement for local build, device, or human acceptance.

## Reviewed implementation paths and symbols

### PKB1 consumer contract

Files reviewed:

- `android/app/src/main/java/com/jovi/photoai/data/reference/PhotoKnowledgeBundle.kt`
- `android/app/src/main/java/com/jovi/photoai/data/reference/KnowledgeBundleReplacement.kt`
- `android/app/src/main/java/com/jovi/photoai/data/reference/ReferenceRepository.kt`

Verified invariants:

- parser accepts only exact schema keys;
- contract version is checked;
- UTF-8 and size limits are enforced;
- SHA-256 payload digest is checked before success;
- duplicate reference IDs are rejected;
- provenance keeps producerId/releaseId/origin separate from user claims.

Not reviewed:

- production producer generator implementation;
- external golden vector compatibility.

### T17 offline continuity

Files reviewed:

- `android/app/src/androidTest/java/com/jovi/photoai/t17/T17OfflineGuidedContinuityAndroidTest.kt`
- `android/app/src/androidTest/java/com/jovi/photoai/t17/T17OwnershipPolicyAndroidTest.kt`
- `reports/T17_OFFLINE_GUIDED_CONTINUITY_REPORT.md`

Verified invariants:

- dedicated API35 instrumentation arguments are checked;
- process identity and package ownership are asserted;
- replacement flow checks current bundle provenance;
- deleted-reference path disables unsafe actions;
- capture identity is compared across recovery flow.

Not reviewed:

- execution result from this remote pass;
- physical device camera behavior.

### CameraX and capture

Files reviewed:

- `android/app/src/main/java/com/jovi/photoai/camera/CameraCapability.kt`
- `android/app/src/main/java/com/jovi/photoai/camera/CameraControlFence.kt`
- `android/app/src/main/java/com/jovi/photoai/camera/CameraXManager.kt`
- `android/app/src/main/java/com/jovi/photoai/data/capture/CaptureEngine.kt`
- `android/app/src/main/java/com/jovi/photoai/data/capture/CaptureRepository.kt`

Verified invariants:

- camera capability, control fence, capture engine, and repository remain separated;
- exported capture state is persisted through repository APIs;
- remote review found no reproducible source defect.

Not reviewed:

- runtime thermal/performance behavior;
- physical sensor differences.

## Findings

No reproducible code defect identified in this remote review pass. No product feature was added without source evidence.

## Evidence limits

NOT_RUN:

- Android build/test
- renderer --check
- privacy audit
- API35 device execution
- human photography evaluation
- producer golden vector compatibility
