package com.jovi.photoai.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraControlFenceTest {
    @Test fun pauseInvalidationRejectsAllPendingControlsButFreshTicketsWork() {
        val fence = CameraControlFence()
        val focus = fence.focusRequest()
        val exposure = fence.exposureRequest()
        val zoom = fence.zoomRequest()
        fence.invalidate()
        assertFalse(fence.currentFocus(focus))
        assertFalse(fence.currentExposure(exposure))
        assertFalse(fence.currentZoom(zoom))
        assertTrue(fence.currentFocus(fence.focusRequest()))
        assertTrue(fence.currentExposure(fence.exposureRequest()))
        assertTrue(fence.currentZoom(fence.zoomRequest()))
    }

    @Test fun userReadinessRequiresForegroundPermissionOpenAndStreaming() {
        assertTrue(cameraInteractionReady(true, true, true, true))
        assertFalse(cameraInteractionReady(false, true, true, true))
        assertFalse(cameraInteractionReady(true, false, true, true))
        assertFalse(cameraInteractionReady(true, true, false, true))
        assertFalse(cameraInteractionReady(true, true, true, false))
    }
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
