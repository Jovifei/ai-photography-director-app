package com.jovi.photoai.pose.geometry

/** Sensor-space to canonical-space transform. Canonical coordinates are upright and unmirrored. */
data class PoseCoordinateTransform(
    val rotationDegrees: Int,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotation must be one of 0, 90, 180, 270"
        }
    }

    fun sensorToUpright(point: NormalizedPoint): NormalizedPoint = when (rotationDegrees) {
        0 -> point
        90 -> NormalizedPoint(1.0 - point.y, point.x)
        180 -> NormalizedPoint(1.0 - point.x, 1.0 - point.y)
        270 -> NormalizedPoint(point.y, 1.0 - point.x)
        else -> error("validated rotation")
    }

    fun sensorToCanonical(point: NormalizedPoint, crop: CropGeometry): NormalizedPoint =
        crop.applyToUprightSource(sensorToUpright(point))
}
