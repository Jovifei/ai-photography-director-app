package com.jovi.photoai.camera

internal data class CameraRequest(val generation: Long, val sequence: Long)

/** Rejects callbacks from an unbound camera or a superseded control request. */
internal class CameraControlFence {
    private var generation = 0L
    private var focus = 0L
    private var exposure = 0L
    private var zoom = 0L

    fun invalidate() { generation++ }
    fun focusRequest() = CameraRequest(generation, ++focus)
    fun exposureRequest() = CameraRequest(generation, ++exposure)
    fun zoomRequest() = CameraRequest(generation, ++zoom)
    fun currentFocus(request: CameraRequest) = request.generation == generation && request.sequence == focus
    fun currentExposure(request: CameraRequest) = request.generation == generation && request.sequence == exposure
    fun currentZoom(request: CameraRequest) = request.generation == generation && request.sequence == zoom
}
