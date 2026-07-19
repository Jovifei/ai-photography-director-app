package com.jovi.photoai.domain.pose

data class PoseWorldPoint(
    val xMeters: Double,
    val yMeters: Double,
    val zMeters: Double,
) {
    init {
        require(xMeters.isFinite() && yMeters.isFinite() && zMeters.isFinite()) {
            "world coordinates must be finite"
        }
    }
}

/** A single canonical point with engine-specific confidence fields kept separate. */
data class PosePoint(
    val keypoint: PoseKeypoint33,
    val xNorm: Double,
    val yNorm: Double,
    val zImageScale: Double? = null,
    val worldMeters: PoseWorldPoint? = null,
    val visibility: Double? = null,
    val presence: Double? = null,
    val inFrameLikelihood: Double? = null,
    val isInFrame: Boolean = xNorm in 0.0..1.0 && yNorm in 0.0..1.0,
    val isOccludedOrUncertain: Boolean = false,
) {
    init {
        require(xNorm.isFinite() && yNorm.isFinite()) { "normalized coordinates must be finite" }
        require(zImageScale == null || zImageScale.isFinite()) { "zImageScale must be finite" }
        requireConfidenceRange("visibility", visibility)
        requireConfidenceRange("presence", presence)
        requireConfidenceRange("inFrameLikelihood", inFrameLikelihood)
        require(isInFrame == (xNorm in 0.0..1.0 && yNorm in 0.0..1.0)) {
            "isInFrame must match the un-clamped normalized coordinates"
        }
    }

    private fun requireConfidenceRange(name: String, value: Double?) {
        require(value == null || (value.isFinite() && value in 0.0..1.0)) {
            "$name must be null or within 0..1"
        }
    }
}
