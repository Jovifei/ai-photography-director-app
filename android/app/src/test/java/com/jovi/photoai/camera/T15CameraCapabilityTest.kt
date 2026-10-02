package com.jovi.photoai.camera

import org.junit.Assert.*
import org.junit.Test

class T15CameraCapabilityTest {
    @Test fun confirmedRatioAllowsActualFixedZoomButRejectsInvalidState() {
        assertEquals(2f, validatedConfirmedZoomRatio(2f, 2f, 2f)!!, 0f)
        assertNull(validatedZoomCapability(2f, 2f, 2f))
        assertNull(validatedConfirmedZoomRatio(2f, 2f, 1f))
        assertNull(validatedConfirmedZoomRatio(0f, 2f, 1f))
        assertNull(validatedConfirmedZoomRatio(2f, 1f, 1f))
        assertNull(validatedConfirmedZoomRatio(1f, 2f, Float.NaN))
        assertNull(validatedConfirmedZoomRatio(1f, Float.POSITIVE_INFINITY, 1f))
    }
    @Test fun controlsRequireReadyAndEveryBusyGateClear() {
        assertTrue(cameraControlsAllowed(true, false, false, false, false))
        assertFalse(cameraControlsAllowed(false, false, false, false, false))
        assertFalse(cameraControlsAllowed(true, true, false, false, false))
        assertFalse(cameraControlsAllowed(true, false, true, false, false))
        assertFalse(cameraControlsAllowed(true, false, false, true, false))
        assertFalse(cameraControlsAllowed(true, false, false, false, true))
    }
    @Test fun zoomRequiresFinitePositiveNonFixedRangeAndActualCurrent() {
        assertEquals(ZoomCapability(1f, 4f, 2f), validatedZoomCapability(1f, 4f, 2f))
        for (values in listOf(Triple(0f, 4f, 1f), Triple(1f, 1f, 1f),
            Triple(4f, 1f, 2f), Triple(1f, 4f, .5f), Triple(1f, 4f, 5f),
            Triple(Float.NaN, 4f, 1f), Triple(1f, Float.POSITIVE_INFINITY, 1f),
            Triple(1f, 4f, Float.NaN))) {
            assertNull(validatedZoomCapability(values.first, values.second, values.third))
        }
    }

    @Test fun savedLensResolvesDeterministicallyWithoutInventingCamera() {
        assertEquals(CameraLens.FRONT, resolveCameraLens(CameraLens.FRONT, CameraLens.entries.toSet()))
        assertEquals(CameraLens.BACK, resolveCameraLens(CameraLens.FRONT, setOf(CameraLens.BACK)))
        assertEquals(CameraLens.FRONT, resolveCameraLens(CameraLens.BACK, setOf(CameraLens.FRONT)))
        assertNull(resolveCameraLens(CameraLens.BACK, emptySet()))
    }

    @Test fun restoredZoomClampsAndNonFiniteStartsAtOne() {
        val capability = ZoomCapability(.5f, 4f, 1f)
        assertEquals(.5f, restoredZoomRatio(.1f, capability), 0f)
        assertEquals(4f, restoredZoomRatio(9f, capability), 0f)
        assertEquals(1f, restoredZoomRatio(Float.NaN, capability), 0f)
        assertEquals(2f, restoredZoomRatio(Float.POSITIVE_INFINITY, ZoomCapability(2f, 4f, 2f)), 0f)
    }

    @Test fun zoomFenceRejectsStaleAndUnboundButKeepsExposureIndependent() {
        val fence = CameraControlFence()
        val old = fence.zoomRequest()
        val exposure = fence.exposureRequest()
        val current = fence.zoomRequest()
        assertFalse(fence.currentZoom(old))
        assertTrue(fence.currentZoom(current))
        assertTrue(fence.currentExposure(exposure))
        fence.exposureRequest()
        assertTrue(fence.currentZoom(current))
        fence.invalidate()
        assertFalse(fence.currentZoom(current))
    }

    @Test fun failedRequestedBindRollsBackAndBothFailuresStayFailed() {
        val calls = mutableListOf<CameraLens>()
        val available = CameraLens.entries.toSet()
        assertEquals(LensSwitchResult.Restored(CameraLens.BACK), performLensSwitch(
            CameraLens.BACK, CameraLens.FRONT, available, false) { calls.add(it); it == CameraLens.BACK })
        assertEquals(listOf(CameraLens.FRONT, CameraLens.BACK), calls)
        assertEquals(LensSwitchResult.Failed, performLensSwitch(
            CameraLens.BACK, CameraLens.FRONT, available, false) { false })
        assertEquals(LensSwitchResult.Switched(CameraLens.FRONT), performLensSwitch(
            CameraLens.BACK, CameraLens.FRONT, available, false) { true })
    }

    @Test fun busyUnavailableAndUnchangedNeverBind() {
        val bind: (CameraLens) -> Boolean = { fail("Binding must not run"); false }
        assertEquals(LensSwitchResult.Busy, performLensSwitch(CameraLens.BACK,
            CameraLens.FRONT, CameraLens.entries.toSet(), true, bind))
        assertEquals(LensSwitchResult.Unavailable, performLensSwitch(CameraLens.BACK,
            CameraLens.FRONT, setOf(CameraLens.BACK), false, bind))
        assertEquals(LensSwitchResult.Unchanged, performLensSwitch(CameraLens.BACK,
            CameraLens.BACK, setOf(CameraLens.BACK), false, bind))
    }
}
