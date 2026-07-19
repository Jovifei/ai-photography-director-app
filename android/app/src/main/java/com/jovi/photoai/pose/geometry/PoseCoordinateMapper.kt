package com.jovi.photoai.pose.geometry

/** Canonical-to-display mapper. Mirroring happens here only; LEFT/RIGHT semantics never swap. */
object PoseCoordinateMapper {
    fun canonicalToViewport(
        point: NormalizedPoint,
        previewGeometry: PreviewGeometry,
        mirrorForDisplay: Boolean,
    ): ViewportPoint {
        val displayPoint = if (mirrorForDisplay) {
            NormalizedPoint(1.0 - point.x, point.y)
        } else {
            point
        }
        return previewGeometry.project(displayPoint)
    }

    fun sensorToViewport(
        sensorPoint: NormalizedPoint,
        transform: PoseCoordinateTransform,
        crop: CropGeometry,
        previewGeometry: PreviewGeometry,
        mirrorForDisplay: Boolean,
    ): ViewportPoint = canonicalToViewport(
        point = transform.sensorToCanonical(sensorPoint, crop),
        previewGeometry = previewGeometry,
        mirrorForDisplay = mirrorForDisplay,
    )
}
