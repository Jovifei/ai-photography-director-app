package com.jovi.photoai.camera

enum class CameraLens { BACK, FRONT }

data class ZoomCapability(val minRatio: Float, val maxRatio: Float, val currentRatio: Float)

fun cameraControlsAllowed(cameraReady: Boolean, uiCaptureInFlight: Boolean,
    libraryCaptureInFlight: Boolean, bindingPending: Boolean, zoomPending: Boolean): Boolean =
    cameraReady && !uiCaptureInFlight && !libraryCaptureInFlight && !bindingPending && !zoomPending

fun validatedZoomCapability(minRatio: Float, maxRatio: Float, currentRatio: Float): ZoomCapability? =
    if (minRatio.isFinite() && maxRatio.isFinite() && currentRatio.isFinite() &&
        minRatio > 0f && minRatio < maxRatio && currentRatio in minRatio..maxRatio)
        ZoomCapability(minRatio, maxRatio, currentRatio) else null

internal fun validatedConfirmedZoomRatio(minRatio: Float, maxRatio: Float, currentRatio: Float): Float? =
    if (minRatio.isFinite() && maxRatio.isFinite() && currentRatio.isFinite() &&
        minRatio > 0f && minRatio <= maxRatio && currentRatio in minRatio..maxRatio)
        currentRatio else null

fun resolveCameraLens(saved: CameraLens, available: Set<CameraLens>): CameraLens? =
    saved.takeIf { it in available } ?: CameraLens.BACK.takeIf { it in available }
        ?: CameraLens.FRONT.takeIf { it in available }

fun restoredZoomRatio(saved: Float, capability: ZoomCapability): Float =
    (if (saved.isFinite()) saved else 1f).coerceIn(capability.minRatio, capability.maxRatio)

internal sealed interface LensSwitchResult {
    data class Switched(val lens: CameraLens) : LensSwitchResult
    data class Restored(val lens: CameraLens) : LensSwitchResult
    data object Unavailable : LensSwitchResult
    data object Busy : LensSwitchResult
    data object Failed : LensSwitchResult
    data object Unchanged : LensSwitchResult
}

internal fun performLensSwitch(previous: CameraLens, requested: CameraLens,
    available: Set<CameraLens>, busy: Boolean, bind: (CameraLens) -> Boolean): LensSwitchResult {
    if (busy) return LensSwitchResult.Busy
    if (requested !in available) return LensSwitchResult.Unavailable
    if (previous == requested) return LensSwitchResult.Unchanged
    if (runCatching { bind(requested) }.getOrDefault(false)) return LensSwitchResult.Switched(requested)
    return if (previous in available && runCatching { bind(previous) }.getOrDefault(false))
        LensSwitchResult.Restored(previous) else LensSwitchResult.Failed
}
