package com.jovi.photoai.pose.geometry

data class NormalizedPoint(
    val x: Double,
    val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "normalized coordinates must be finite" }
    }

    val outOfFrame: Boolean
        get() = x !in 0.0..1.0 || y !in 0.0..1.0
}

data class ViewportPoint(
    val xPx: Double,
    val yPx: Double,
    val outOfViewport: Boolean,
) {
    init {
        require(xPx.isFinite() && yPx.isFinite()) { "viewport coordinates must be finite" }
    }
}
