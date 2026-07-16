package com.jovi.photoai.ui.design

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Converts an actual runtime Compose [Color] to the integer sRGB representation used by contrast
 * math. Visual camera-chrome tokens remain exclusively in [AppColors].
 */
fun Color.toChromeRgba(): ChromeRgba = ChromeRgba(
    red = (red * 255f).roundToInt().coerceIn(0, 255),
    green = (green * 255f).roundToInt().coerceIn(0, 255),
    blue = (blue * 255f).roundToInt().coerceIn(0, 255),
    alpha = alpha,
)

/** Small, pure sRGB contrast and alpha-compositing helper. */
data class ChromeRgba(val red: Int, val green: Int, val blue: Int, val alpha: Float = 1f) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255)
        require(alpha in 0f..1f)
    }
}

object CameraChromeContrast {
    fun composite(foreground: ChromeRgba, background: ChromeRgba): ChromeRgba {
        val alpha = foreground.alpha + background.alpha * (1f - foreground.alpha)
        if (alpha == 0f) return ChromeRgba(0, 0, 0, 0f)
        fun channel(fg: Int, bg: Int): Int =
            (((fg * foreground.alpha) + (bg * background.alpha * (1f - foreground.alpha))) /
                alpha).roundToInt().coerceIn(0, 255)
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

    fun relativeLuminance(color: ChromeRgba): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
