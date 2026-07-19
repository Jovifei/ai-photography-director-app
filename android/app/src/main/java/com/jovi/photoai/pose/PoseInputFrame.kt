package com.jovi.photoai.pose

import com.jovi.photoai.pose.geometry.CropGeometry
import com.jovi.photoai.pose.geometry.PreviewGeometry

enum class PoseImageFormat {
    YUV_420,
    RGBA_8888,
    JPEG,
    UNKNOWN,
}

enum class PoseLensFacing {
    BACK,
    FRONT,
    UNKNOWN,
}

/** Immutable metadata passed to an engine; raw frames never enter UI state or persistence. */
data class PoseInputFrame(
    val frameId: Long,
    val generation: Long,
    val timestampNs: Long,
    val imageFormat: PoseImageFormat,
    val width: Int,
    val height: Int,
    val cropRect: CropGeometry,
    val rotationDegrees: Int,
    val lensFacing: PoseLensFacing,
    val lease: FrameLease<*>,
    val previewGeometry: PreviewGeometry,
) {
    init {
        require(frameId >= 0)
        require(generation >= 0)
        require(timestampNs >= 0)
        require(width > 0 && height > 0)
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }
}
