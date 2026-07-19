package com.jovi.photoai.pose.geometry

/** Normalized crop rectangle in the upright source image. */
data class CropGeometry(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(listOf(left, top, right, bottom).all(Double::isFinite))
        require(left >= 0.0 && top >= 0.0 && right <= 1.0 && bottom <= 1.0)
        require(right > left && bottom > top) { "crop rectangle must have positive area" }
    }

    val width: Double
        get() = right - left

    val height: Double
        get() = bottom - top

    fun applyToUprightSource(point: NormalizedPoint): NormalizedPoint =
        NormalizedPoint((point.x - left) / width, (point.y - top) / height)

    companion object {
        fun fullFrame(): CropGeometry = CropGeometry(0.0, 0.0, 1.0, 1.0)

        fun centerCrop(sourceAspectRatio: Double, targetAspectRatio: Double): CropGeometry {
            require(sourceAspectRatio.isFinite() && targetAspectRatio.isFinite())
            require(sourceAspectRatio > 0.0 && targetAspectRatio > 0.0)
            return if (sourceAspectRatio > targetAspectRatio) {
                val visibleWidth = targetAspectRatio / sourceAspectRatio
                val left = (1.0 - visibleWidth) / 2.0
                CropGeometry(left, 0.0, left + visibleWidth, 1.0)
            } else {
                val visibleHeight = sourceAspectRatio / targetAspectRatio
                val top = (1.0 - visibleHeight) / 2.0
                CropGeometry(0.0, top, 1.0, top + visibleHeight)
            }
        }
    }
}
