# AA0-P0 Pose Contract and Lifecycle Foundation

Status: implementation foundation only; real Pose engines and model assets are not authorized in P0.

## Scope and ownership

AA0-P0 defines an engine-neutral contract for a future pose adapter. The canonical domain lives under `domain/pose`, the adapter boundary under `pose`, and the coordinator under `camera/PoseAnalysisCoordinator.kt`. The coordinator is not wired into `CameraXManager`, `CameraScreen`, or UI0.

The current UI0 `DemoOverlay` remains a product-demo artifact. `PoseDebugOverlay` is an independent diagnostic component with the explicit label `Pose Debug / 非产品功能`; it consumes only `PoseEstimate` and is not enabled by any production screen.

## Canonical 33 points

`PoseKeypoint33` is an app-owned enum with explicit `canonicalIndex` and `sdkName` values. Adapters must map their SDK names explicitly; SDK array ordinals are not part of the contract. The order is:

`NOSE`, `LEFT_EYE_INNER`, `RIGHT_EYE_INNER`, `LEFT_EYE`, `RIGHT_EYE`, `LEFT_EYE_OUTER`, `RIGHT_EYE_OUTER`, `LEFT_EAR`, `RIGHT_EAR`, `LEFT_MOUTH`, `RIGHT_MOUTH`, `LEFT_SHOULDER`, `RIGHT_SHOULDER`, `LEFT_ELBOW`, `RIGHT_ELBOW`, `LEFT_WRIST`, `RIGHT_WRIST`, `LEFT_PINKY`, `RIGHT_PINKY`, `LEFT_INDEX`, `RIGHT_INDEX`, `LEFT_THUMB`, `RIGHT_THUMB`, `LEFT_HIP`, `RIGHT_HIP`, `LEFT_KNEE`, `RIGHT_KNEE`, `LEFT_ANKLE`, `RIGHT_ANKLE`, `LEFT_HEEL`, `RIGHT_HEEL`, `LEFT_FOOT_INDEX`, `RIGHT_FOOT_INDEX`.

`PosePoint` preserves normalized x/y, optional z/world coordinates, and each engine-provided visibility/presence/in-frame likelihood separately. No cross-engine confidence is invented. Missing points are absent from `PosePerson.points` and return `null`; they are never interpolated.

## Pose states and engine boundary

`PoseState` distinguishes `NO_PERSON` from `ENGINE_ERROR`, represents uncertain/partial output, and uses `MULTI_PERSON_UNKNOWN` without selecting a target person. `STALE_RESULT_DROPPED` and `CANCELLED` are terminal lifecycle states. `PoseEstimating` has no MediaPipe, ML Kit, MoveNet, Compose, Activity, or CameraX types and returns a cancellable `PoseSubmission`.

## Frame ownership and lifecycle

`FrameLease` is the only owner-facing resource contract. `CloseOnceFrameLease` uses an atomic compare-and-set; repeated and concurrent `close()` calls may be observed, but the underlying release lambda executes once. `PoseAnalysisCoordinator` owns each submitted lease and closes it on success, no-person, engine error, callback exception, timeout, cancellation, stale generation, duplicate frame rejection, and dispose.

Coordinator terminal transitions are serialized under a lock. A pending token is removed before a terminal callback is delivered, so duplicate or late callbacks cannot update the sink. `invalidateGeneration(generation)` monotonically invalidates older generations; old callbacks are dropped and never reach the result sink. `close()` invalidates all pending work, closes the engine once, and ignores late callbacks. The timeout scheduler is injectable and the coordinator never waits for inference results.

## Coordinate contract

The canonical point space is:

- normalized x/y, top-left origin, x right, y down;
- already cropped and rotated upright;
- never mirrored;
- out-of-frame values are preserved and marked, not clamped;
- front-camera mirroring occurs only at display projection;
- mirroring never swaps LEFT/RIGHT semantic labels.

`PoseCoordinateTransform` owns sensor-space rotation (clockwise 0/90/180/270) and applies a normalized upright crop. `PoseCoordinateMapper` then projects canonical points into the resolved `PreviewGeometry`; it does not re-apply sensor rotation or crop. This separation prevents double transforms.

`PreviewGeometry` resolves either center-crop (`max(viewport/source)`) or letterbox (`min(viewport/source)`) into one scale and one x/y offset. The future runtime decision is `UseCaseGroup + shared ViewPort`; runtime wiring is deferred to AA0-P1 and current UI0 does not claim shared ViewPort proof.

## Fake engine and metrics

`FakePoseEstimator` supports complete single-person, no-person, partial, low-confidence, multi-person-unknown, engine error, delay, never-callback, cancellation, late callback, reversed emission, and close scenarios. It stores only frame metadata and callbacks for tests; it is not a product result and is not connected to Camera Director.

`PoseMetricsAccumulator` stores only aggregate counters and bounded latency samples. It reports count, success, no-person, error, stale drop, cancelled, nearest-rank P50/P95 latency, effective FPS, error rate, frame close count, double-close count, and dropped-frame count. It has no raw frames, point sequences, device identifiers, paths, or account data.

## Explicit non-goals

P0 does not add MediaPipe, ML Kit, MoveNet, LiteRT, TFLite, or TensorFlow; does not download `pose_landmarker_lite.task` or any model; does not create adapters; does not modify CameraX/UI0/Pipeline/iOS; and does not enter AA1.
