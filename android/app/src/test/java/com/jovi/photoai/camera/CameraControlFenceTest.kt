package com.jovi.photoai.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraControlFenceTest {
    @Test fun staleFocusAndUnboundExposureCallbacksAreRejected() {
        val fence = CameraControlFence()
        val oldFocus = fence.focusRequest()
        val exposure = fence.exposureRequest()
        val currentFocus = fence.focusRequest()

        assertFalse(fence.currentFocus(oldFocus))
        assertTrue(fence.currentFocus(currentFocus))
        assertTrue(fence.currentExposure(exposure))

        fence.invalidate()
        assertFalse(fence.currentFocus(currentFocus))
        assertFalse(fence.currentExposure(exposure))
        assertTrue(fence.currentFocus(fence.focusRequest()))
    }
}
