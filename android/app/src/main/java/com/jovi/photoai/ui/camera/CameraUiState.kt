package com.jovi.photoai.ui.camera

import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.domain.model.GuidanceItem
import com.jovi.photoai.domain.model.OverlayMode
import com.jovi.photoai.domain.model.selectHighestPriorityGuidance

private const val CAMERA_UI_SNAPSHOT_VERSION = 2
private const val EDGE_GESTURE_THRESHOLD_DP = 56f
private const val EDGE_GESTURE_WIDTH_DP = 32f

enum class CameraPermission {
    UNKNOWN,
    DENIED,
    GRANTED,
}

enum class CameraRuntime {
    STOPPED,
    STARTING,
    READY,
    FAILED,
}

sealed interface ReferenceLoadState {
    data object Idle : ReferenceLoadState

    data class Decoding(val requestId: Long) : ReferenceLoadState {
        init {
            require(requestId >= 0L) { "Decode request id must not be negative" }
        }
    }
}

enum class CameraUiMessage {
    CAMERA_UNAVAILABLE,
    REFERENCE_DECODE_FAILED,
    CAPTURE_FAILED,
}

/**
 * Pure runtime UI state. Android permission, CameraX, PreviewView, Context and Uri stay outside.
 * Guidance is injected from the navigation layer and intentionally omitted from durable snapshots.
 */
data class CameraUiState(
    val permission: CameraPermission = CameraPermission.UNKNOWN,
    val cameraRuntime: CameraRuntime = CameraRuntime.STOPPED,
    val selectedReferencePhotoId: String? = null,
    val referenceLoadState: ReferenceLoadState = ReferenceLoadState.Idle,
    val selectedGuidePanel: GuidePanel = GuidePanel.NONE,
    val overlayMode: OverlayMode = OverlayMode.SKELETON,
    val gridVisible: Boolean = true,
    val currentGuidance: GuidanceItem? = null,
    val captureInFlight: Boolean = false,
    val captureCount: Int = 0,
    val message: CameraUiMessage? = null,
) {
    val canCapture: Boolean
        get() = permission == CameraPermission.GRANTED &&
            cameraRuntime == CameraRuntime.READY &&
            !captureInFlight
}

sealed interface CameraUiEvent {
    data class PermissionObserved(val permission: CameraPermission) : CameraUiEvent
    data object CameraStartRequested : CameraUiEvent
    data object CameraReady : CameraUiEvent
    data object CameraFailed : CameraUiEvent

    data class ReferenceSelected(val referencePhotoId: String) : CameraUiEvent
    data class ReferenceDecodeStarted(val requestId: Long) : CameraUiEvent
    data class ReferenceDecodeSucceeded(
        val requestId: Long,
        val referencePhotoId: String,
    ) : CameraUiEvent
    data class ReferenceDecodeFailed(val requestId: Long) : CameraUiEvent
    data object ReferencePickCancelled : CameraUiEvent
    data object ReferenceRemoved : CameraUiEvent

    data class GuidanceUpdated(val guidance: GuidanceItem?) : CameraUiEvent
    data class GuidePanelSelected(val panel: GuidePanel) : CameraUiEvent
    data object ClosePanel : CameraUiEvent
    data class OverlayModeSelected(val mode: OverlayMode) : CameraUiEvent
    data object GridToggled : CameraUiEvent
    data class GridVisibilityChanged(val visible: Boolean) : CameraUiEvent

    data object CaptureStarted : CameraUiEvent
    data object CaptureSucceeded : CameraUiEvent
    data object CaptureFailed : CameraUiEvent
    data object MessageConsumed : CameraUiEvent
}

/** Maps injected plan guidance to the one honest runtime hint; empty input is safe. */
fun cameraGuidanceFor(items: Iterable<GuidanceItem>): GuidanceItem? =
    selectHighestPriorityGuidance(items)

/** Shared pure conversion for tests and density-aware edge gesture handling. */
fun edgeGestureThresholdPx(density: Float): Float {
    require(density > 0f) { "Density must be positive" }
    return EDGE_GESTURE_THRESHOLD_DP * density
}

/** The 32dp edge zone remains defined in dp even when tests need its pixel size. */
fun edgeGestureWidthPx(density: Float): Float {
    require(density > 0f) { "Density must be positive" }
    return EDGE_GESTURE_WIDTH_DP * density
}

/**
 * Opens only for an inward horizontal drag that reaches the shared threshold.
 * A vertical component at or beyond the threshold is treated as a non-edge gesture.
 */
fun shouldOpenEdgeGuide(
    panel: GuidePanel,
    horizontalDragPx: Float,
    verticalDragPx: Float,
    thresholdPx: Float,
): Boolean {
    if (thresholdPx <= 0f || kotlin.math.abs(verticalDragPx) >= thresholdPx) return false
    return when (panel) {
        GuidePanel.ENVIRONMENT -> horizontalDragPx >= thresholdPx
        GuidePanel.SUBJECT -> horizontalDragPx <= -thresholdPx
        GuidePanel.NONE -> false
    }
}

/** A panel consumes Back before the navigation layer is allowed to leave Camera Director. */
fun cameraBackClosesPanel(state: CameraUiState): Boolean =
    state.selectedGuidePanel != GuidePanel.NONE

fun reduceCameraUiState(state: CameraUiState, event: CameraUiEvent): CameraUiState =
    when (event) {
        is CameraUiEvent.PermissionObserved -> when (event.permission) {
            CameraPermission.GRANTED -> state.copy(permission = CameraPermission.GRANTED)
            CameraPermission.UNKNOWN,
            CameraPermission.DENIED,
            -> state.copy(
                permission = event.permission,
                cameraRuntime = CameraRuntime.STOPPED,
                captureInFlight = false,
            )
        }

        CameraUiEvent.CameraStartRequested -> {
            if (state.permission != CameraPermission.GRANTED) state
            else state.copy(cameraRuntime = CameraRuntime.STARTING, message = null)
        }

        CameraUiEvent.CameraReady -> {
            if (
                state.permission != CameraPermission.GRANTED ||
                state.cameraRuntime != CameraRuntime.STARTING
            ) state
            else state.copy(cameraRuntime = CameraRuntime.READY, message = null)
        }

        CameraUiEvent.CameraFailed -> {
            if (state.permission != CameraPermission.GRANTED) state
            else state.copy(
                cameraRuntime = CameraRuntime.FAILED,
                captureInFlight = false,
                message = CameraUiMessage.CAMERA_UNAVAILABLE,
            )
        }

        is CameraUiEvent.ReferenceSelected -> {
            if (event.referencePhotoId.isBlank()) state
            else state.copy(
                selectedReferencePhotoId = event.referencePhotoId,
                referenceLoadState = ReferenceLoadState.Idle,
                message = null,
            )
        }

        is CameraUiEvent.ReferenceDecodeStarted -> state.copy(
            referenceLoadState = ReferenceLoadState.Decoding(event.requestId),
            message = null,
        )

        is CameraUiEvent.ReferenceDecodeSucceeded -> {
            if (!state.isActiveDecode(event.requestId) || event.referencePhotoId.isBlank()) state
            else state.copy(
                selectedReferencePhotoId = event.referencePhotoId,
                referenceLoadState = ReferenceLoadState.Idle,
                message = null,
            )
        }

        is CameraUiEvent.ReferenceDecodeFailed -> {
            if (!state.isActiveDecode(event.requestId)) state
            else state.copy(
                referenceLoadState = ReferenceLoadState.Idle,
                message = CameraUiMessage.REFERENCE_DECODE_FAILED,
            )
        }

        CameraUiEvent.ReferencePickCancelled -> state

        CameraUiEvent.ReferenceRemoved -> state.copy(
            selectedReferencePhotoId = null,
            referenceLoadState = ReferenceLoadState.Idle,
            overlayMode = if (state.overlayMode == OverlayMode.REFERENCE) {
                OverlayMode.SKELETON
            } else {
                state.overlayMode
            },
            message = null,
        )

        is CameraUiEvent.GuidanceUpdated -> state.copy(currentGuidance = event.guidance)

        is CameraUiEvent.GuidePanelSelected -> state.copy(selectedGuidePanel = event.panel)

        CameraUiEvent.ClosePanel -> {
            if (state.selectedGuidePanel == GuidePanel.NONE) state
            else state.copy(selectedGuidePanel = GuidePanel.NONE)
        }

        is CameraUiEvent.OverlayModeSelected -> {
            if (event.mode == OverlayMode.REFERENCE && state.selectedReferencePhotoId == null) state
            else state.copy(overlayMode = event.mode)
        }

        CameraUiEvent.GridToggled -> state.copy(gridVisible = !state.gridVisible)

        is CameraUiEvent.GridVisibilityChanged -> state.copy(gridVisible = event.visible)

        CameraUiEvent.CaptureStarted -> {
            if (!state.canCapture) state
            else state.copy(captureInFlight = true, message = null)
        }

        CameraUiEvent.CaptureSucceeded -> {
            if (!state.captureInFlight) state
            else state.copy(
                captureInFlight = false,
                captureCount = if (state.captureCount == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    state.captureCount + 1
                },
                message = null,
            )
        }

        CameraUiEvent.CaptureFailed -> {
            if (!state.captureInFlight) state
            else state.copy(
                captureInFlight = false,
                message = CameraUiMessage.CAPTURE_FAILED,
            )
        }

        CameraUiEvent.MessageConsumed -> state.copy(message = null)
    }

private fun CameraUiState.isActiveDecode(requestId: Long): Boolean =
    (referenceLoadState as? ReferenceLoadState.Decoding)?.requestId == requestId

/** Only durable pure Kotlin values are saved; hardware and injected guidance are intentionally omitted. */
data class CameraUiSnapshot(
    val version: Int = CAMERA_UI_SNAPSHOT_VERSION,
    val selectedReferencePhotoId: String? = null,
    val selectedGuidePanel: GuidePanel = GuidePanel.NONE,
    val overlayMode: OverlayMode = OverlayMode.SKELETON,
    val gridVisible: Boolean = true,
    val captureCount: Int = 0,
)

fun CameraUiState.toSnapshot(): CameraUiSnapshot = CameraUiSnapshot(
    selectedReferencePhotoId = selectedReferencePhotoId,
    selectedGuidePanel = selectedGuidePanel,
    overlayMode = overlayMode,
    gridVisible = gridVisible,
    captureCount = captureCount,
)

fun restoreCameraUiState(snapshot: CameraUiSnapshot?): CameraUiState {
    if (snapshot == null || snapshot.version != CAMERA_UI_SNAPSHOT_VERSION) {
        return CameraUiState()
    }

    val referencePhotoId = snapshot.selectedReferencePhotoId?.takeIf(String::isNotBlank)
    val restoredOverlay = if (
        snapshot.overlayMode == OverlayMode.REFERENCE && referencePhotoId == null
    ) {
        OverlayMode.SKELETON
    } else {
        snapshot.overlayMode
    }

    return CameraUiState(
        selectedReferencePhotoId = referencePhotoId,
        selectedGuidePanel = snapshot.selectedGuidePanel,
        overlayMode = restoredOverlay,
        gridVisible = snapshot.gridVisible,
        captureCount = snapshot.captureCount.coerceAtLeast(0),
    )
}
