package com.jovi.photoai.ui.design

/** Small dependency-free sRGB contrast helper for stable camera chrome tokens. */
data class ChromeRgba(val red: Int, val green: Int, val blue: Int, val alpha: Float = 1f) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255)
        require(alpha in 0f..1f)
    }
}

object CameraChromeContrast {
    val surface = ChromeRgba(255, 255, 255, 0.92f)
    val text = ChromeRgba(29, 29, 31)
    val secondaryText = ChromeRgba(81, 81, 86)
    val disabled = ChromeRgba(110, 110, 115)
    val whitePreview = ChromeRgba(255, 255, 255)
    val blackPreview = ChromeRgba(0, 0, 0)

    fun composite(foreground: ChromeRgba, background: ChromeRgba): ChromeRgba {
        val alpha = foreground.alpha + background.alpha * (1f - foreground.alpha)
        fun channel(fg: Int, bg: Int): Int =
            (((fg * foreground.alpha) + (bg * background.alpha * (1f - foreground.alpha))) /
                alpha).toInt().coerceIn(0, 255)
        return ChromeRgba(
            red = channel(foreground.red, background.red),
            green = channel(foreground.green, background.green),
            blue = channel(foreground.blue, background.blue),
            alpha = alpha,
        )
    }

    fun contrastRatio(foreground: ChromeRgba, background: ChromeRgba): Double {
        val light = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val dark = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (light + 0.05) / (dark + 0.05)
    }

    private fun relativeLuminance(color: ChromeRgba): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
