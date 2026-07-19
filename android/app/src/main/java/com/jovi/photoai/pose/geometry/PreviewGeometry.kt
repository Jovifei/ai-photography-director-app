package com.jovi.photoai.pose.geometry

enum class PreviewScaleType {
    CENTER_CROP,
    LETTERBOX,
}

/** Resolved scale/offset snapshot shared by a future Preview and overlay path. */
data class PreviewGeometry(
    val sourceWidthPx: Double,
    val sourceHeightPx: Double,
    val viewportWidthPx: Double,
    val viewportHeightPx: Double,
    val scale: Double,
    val offsetXPx: Double,
    val offsetYPx: Double,
    val scaleType: PreviewScaleType,
) {
    init {
        require(listOf(
            sourceWidthPx,
            sourceHeightPx,
            viewportWidthPx,
            viewportHeightPx,
            scale,
            offsetXPx,
            offsetYPx,
        ).all(Double::isFinite))
        require(sourceWidthPx > 0.0 && sourceHeightPx > 0.0)
        require(viewportWidthPx > 0.0 && viewportHeightPx > 0.0 && scale > 0.0)
    }

    fun project(point: NormalizedPoint): ViewportPoint {
        val x = point.x * sourceWidthPx * scale + offsetXPx
        val y = point.y * sourceHeightPx * scale + offsetYPx
        return ViewportPoint(
            xPx = x,
            yPx = y,
            outOfViewport = x !in 0.0..viewportWidthPx || y !in 0.0..viewportHeightPx,
        )
    }

    companion object {
        fun centerCrop(sourceWidthPx: Double, sourceHeightPx: Double, viewportWidthPx: Double, viewportHeightPx: Double): PreviewGeometry =
            resolve(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx, PreviewScaleType.CENTER_CROP)

        fun letterbox(sourceWidthPx: Double, sourceHeightPx: Double, viewportWidthPx: Double, viewportHeightPx: Double): PreviewGeometry =
            resolve(sourceWidthPx, sourceHeightPx, viewportWidthPx, viewportHeightPx, PreviewScaleType.LETTERBOX)

        private fun resolve(
            sourceWidthPx: Double,
            sourceHeightPx: Double,
            viewportWidthPx: Double,
            viewportHeightPx: Double,
            scaleType: PreviewScaleType,
        ): PreviewGeometry {
            require(sourceWidthPx > 0.0 && sourceHeightPx > 0.0)
            require(viewportWidthPx > 0.0 && viewportHeightPx > 0.0)
            val scale = if (scaleType == PreviewScaleType.CENTER_CROP) {
                maxOf(viewportWidthPx / sourceWidthPx, viewportHeightPx / sourceHeightPx)
            } else {
                minOf(viewportWidthPx / sourceWidthPx, viewportHeightPx / sourceHeightPx)
            }
            return PreviewGeometry(
                sourceWidthPx = sourceWidthPx,
                sourceHeightPx = sourceHeightPx,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                scale = scale,
                offsetXPx = (viewportWidthPx - sourceWidthPx * scale) / 2.0,
                offsetYPx = (viewportHeightPx - sourceHeightPx * scale) / 2.0,
                scaleType = scaleType,
            )
        }
    }
}
