# AA0-P0 Pose Contract and Lifecycle Foundation

Status: remediation candidate; engine-neutral contract only. No production pose SDK, model, adapter, CameraX wiring, or UI integration is authorized in P0.

## Scope and ownership

The canonical domain is under `domain/pose`, the engine boundary under `pose`, and the lifecycle owner is `camera/PoseAnalysisCoordinator.kt`. The coordinator is not wired into `CameraXManager`, `CameraScreen`, UI0, Pipeline, iOS, AA1, or any production pose runtime.

## B1: canonical state invariant

`PoseKeypoint33` has exactly 33 app-owned names and explicit indices. `TRACKED` and `PARTIAL_OR_LOW_CONFIDENCE` require a non-null `PosePerson` with at least one point, and `PoseDiagnostics.pointCount` must equal `person.presentPointCount`. `NO_PERSON`, `MULTI_PERSON_UNKNOWN`, `ENGINE_ERROR`, `STALE_RESULT_DROPPED`, and `CANCELLED` require a null person and zero diagnostic points. Constructor failures have state-specific messages and are covered by the state matrix tests.

## B2-B4: request identity, timeout, and scheduler failure

Every accepted submit receives an opaque monotonic request token. The pending map and both callback/timeout closures use that token as the primary identity; frame id and generation remain metadata validation only. A duplicate in-flight frame id is rejected while the original remains active, but the id can be reused after terminal cleanup. Old callbacks and old timeout tasks therefore cannot affect a newer request.

Pending ownership is inserted before scheduling. The timeout future is bound under the lifecycle lock; if a synchronous callback, cancellation, invalidation, or `close()` terminalizes the request first, the newly returned future is cancelled immediately. The engine submission is then attempted only while the request remains active. Scheduler rejection or any other scheduler runtime failure removes the token, closes the lease, records infrastructure/scheduler metrics, delivers one `ENGINE_ERROR`, and returns `true` because ownership had already been accepted. A rejected input returns `false` and is released immediately.

## B5: frame release result

`FrameLease.releaseOnce()` returns `RELEASED`, `ALREADY_RELEASED`, or `RELEASE_FAILED`. The underlying release is attempted once; a throwing release is terminal, closed, never retried, and carries the exception class name. `close()` delegates to `releaseOnce()` without rethrowing. The coordinator records the returned result directly, including distinct success, already-released, failure, and total close-attempt counters. Callback, timeout, cancellation, generation invalidation, scheduler failure, duplicate rejection, and dispose paths all converge on this result.

## B6: bounded metrics and one clock domain

`PoseMetricsAccumulator` has a constructor-fixed latency ring (default capacity 2048). It reports the retained `latencySampleCount`, `latencyWindowCapacity`, and total valid `latencyTotalObserved`; P50/P95 use nearest-rank over the retained window, so high volume cannot grow memory. Coordinator acceptance and completion use the same injected monotonic clock; external frame timestamps are metadata only. Effective Pose FPS is `SUCCESS / valid success-completion wall-clock`; `NO_PERSON`, multi-person-unknown, errors, stale drops, and cancellations are excluded. Metrics contain no frame ids, points, raw frames, paths, device identifiers, or account data.

## B7: deterministic fake and geometry

`FakePoseEstimator` creates an independent request record/token per submit, retains only bounded metadata/callback history, supports clear/drain, and can manually trigger a specific old/new request in forward or reverse order, including after cancellation/close. No sleeps are needed for lifecycle tests. Geometry tests include a hand-calculated composition of non-full crop, 90°/270° rotation, portrait/wide center-crop viewport, and front-display mirror. The resulting claim is only `GEOMETRY_PURE_CONTRACT_VERIFIED`; it is not a CameraX/ViewPort runtime claim.

## B8: evidence boundary

JVM tests, Android compilation, and direct instrumentation validate the contract and lifecycle harness, not a real pose engine. `connectedDebugAndroidTest` is reported separately and may be blocked by Gradle UTP gRPC startup. P0 physical evidence requires direct `am instrument` on an available physical device after installing the exact current APK and test APK, hashing pulled copies outside Git, and recording a redacted run id. Emulator-only or historical device results never substitute for current physical evidence.

## Coordinate contract

Canonical points are normalized, top-left, upright, cropped, and never mirrored. Out-of-frame values are preserved and marked rather than clamped. `PoseCoordinateTransform` owns sensor rotation and crop; `PoseCoordinateMapper` owns display projection and optional front-camera mirroring without swapping semantic LEFT/RIGHT labels. `PreviewGeometry` resolves center-crop or letterbox scale/offset. Shared CameraX `ViewPort` wiring is deferred to AA0-P1.

## Explicit non-goals

P0 does not add MediaPipe, ML Kit, MoveNet, LiteRT, TFLite, TensorFlow, model assets, or downloads; does not modify CameraX/UI0/Pipeline; does not create iOS; and does not enter AA1.
