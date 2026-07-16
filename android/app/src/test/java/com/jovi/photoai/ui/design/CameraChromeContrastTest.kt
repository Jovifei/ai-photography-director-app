package com.jovi.photoai.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraChromeContrastTest {
    @Test
    fun runtimeCameraChromeTokens_areConvertedWithoutIndependentVisualValues() {
        val surface = AppColors.CameraChromeSurface.toChromeRgba()

        assertEquals(AppColors.CameraChromeSurface.red, surface.red / 255f, 0.0001f)
        assertEquals(AppColors.CameraChromeSurface.green, surface.green / 255f, 0.0001f)
        assertEquals(AppColors.CameraChromeSurface.blue, surface.blue / 255f, 0.0001f)
        assertEquals(AppColors.CameraChromeSurface.alpha, surface.alpha, 0f)
        assertEquals(AppColors.CameraChromeText.alpha, AppColors.CameraChromeText.toChromeRgba().alpha, 0f)
        assertEquals(
            AppColors.CameraChromeSecondaryText.alpha,
            AppColors.CameraChromeSecondaryText.toChromeRgba().alpha,
            0f,
        )
        assertEquals(AppColors.CameraChromeDisabled.alpha, AppColors.CameraChromeDisabled.toChromeRgba().alpha, 0f)
    }

    @Test
    fun runtimeCameraChromeText_meetsWcagAcrossRepresentativePreviewFrames() {
        representativePreviewFrames().forEach { (name, preview) ->
            val renderedSurface = renderedRuntimeSurface(preview)
            assertTrue(
                "$name primary text contrast",
                CameraChromeContrast.contrastRatio(AppColors.CameraChromeText.toChromeRgba(), renderedSurface) >= 4.5,
            )
            assertTrue(
                "$name secondary text contrast",
                CameraChromeContrast.contrastRatio(
                    AppColors.CameraChromeSecondaryText.toChromeRgba(),
                    renderedSurface,
                ) >= 4.5,
            )
        }
    }

    @Test
    fun runtimeCameraChromeDisabledContent_meetsLargeContentThresholdAcrossPreviewFrames() {
        representativePreviewFrames().forEach { (name, preview) ->
            assertTrue(
                "$name disabled content contrast",
                CameraChromeContrast.contrastRatio(
                    AppColors.CameraChromeDisabled.toChromeRgba(),
                    renderedRuntimeSurface(preview),
                ) >= 3.0,
            )
        }
    }

    @Test
    fun alphaCompositing_handlesTransparentOpaqueAndHalfAlphaEdges() {
        val background = ChromeRgba(12, 34, 56)
        assertEquals(background, CameraChromeContrast.composite(ChromeRgba(200, 100, 50, 0f), background))
        assertEquals(
            ChromeRgba(200, 100, 50),
            CameraChromeContrast.composite(ChromeRgba(200, 100, 50), background),
        )
        assertEquals(
            ChromeRgba(128, 0, 0),
            CameraChromeContrast.composite(ChromeRgba(255, 0, 0, 0.5f), ChromeRgba(0, 0, 0)),
        )
        assertEquals(
            ChromeRgba(0, 0, 0, 0f),
            CameraChromeContrast.composite(ChromeRgba(255, 255, 255, 0f), ChromeRgba(0, 0, 0, 0f)),
        )
    }

    @Test
    fun contrastMath_handlesBlackWhiteEqualityAndSrgbTransferEdges() {
        val black = ChromeRgba(0, 0, 0)
        val white = ChromeRgba(255, 255, 255)
        assertEquals(21.0, CameraChromeContrast.contrastRatio(black, white), 0.0001)
        assertEquals(1.0, CameraChromeContrast.contrastRatio(black, black), 0.0001)
        assertEquals(0.0, CameraChromeContrast.relativeLuminance(black), 0.0001)
        assertEquals(1.0, CameraChromeContrast.relativeLuminance(white), 0.0001)
        assertEquals(10.0 / 255.0 / 12.92, CameraChromeContrast.relativeLuminance(ChromeRgba(10, 10, 10)), 0.0001)
        assertEquals(
            Math.pow((11.0 / 255.0 + 0.055) / 1.055, 2.4),
            CameraChromeContrast.relativeLuminance(ChromeRgba(11, 11, 11)),
            0.0001,
        )
    }

    private fun renderedRuntimeSurface(preview: ChromeRgba): ChromeRgba =
        CameraChromeContrast.composite(AppColors.CameraChromeSurface.toChromeRgba(), preview)

    private fun representativePreviewFrames(): List<Pair<String, ChromeRgba>> = listOf(
        "white" to ChromeRgba(255, 255, 255),
        "black" to ChromeRgba(0, 0, 0),
        "mid-gray" to ChromeRgba(128, 128, 128),
    )
}
