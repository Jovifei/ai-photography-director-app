package com.jovi.photoai.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test

class CameraChromeContrastTest {
    @Test
    fun normalCameraChromeText_meetsWcagOnWhiteAndBlackPreviewFrames() {
        listOf(CameraChromeContrast.whitePreview, CameraChromeContrast.blackPreview).forEach { preview ->
            val surface = CameraChromeContrast.composite(CameraChromeContrast.surface, preview)
            assertTrue(CameraChromeContrast.contrastRatio(CameraChromeContrast.text, surface) >= 4.5)
            assertTrue(CameraChromeContrast.contrastRatio(CameraChromeContrast.secondaryText, surface) >= 4.5)
        }
    }

    @Test
    fun largeCameraChromeIcon_meetsWcagOnWhiteAndBlackPreviewFrames() {
        listOf(CameraChromeContrast.whitePreview, CameraChromeContrast.blackPreview).forEach { preview ->
            val surface = CameraChromeContrast.composite(CameraChromeContrast.surface, preview)
            assertTrue(CameraChromeContrast.contrastRatio(CameraChromeContrast.text, surface) >= 3.0)
        }
    }
}
