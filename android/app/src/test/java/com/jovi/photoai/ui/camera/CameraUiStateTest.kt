package com.jovi.photoai.ui.camera

import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.domain.model.OverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraUiStateTest {
    @Test
    fun initialState_isSafeAndCannotCapture() {
        val state = CameraUiState()

        assertEquals(CameraPermission.UNKNOWN, state.permission)
        assertEquals(CameraRuntime.STOPPED, state.cameraRuntime)
        assertNull(state.selectedReferencePhotoId)
        assertEquals(GuidePanel.NONE, state.selectedGuidePanel)
        assertEquals(OverlayMode.SKELETON, state.overlayMode)
        assertEquals(0, state.captureCount)
        assertFalse(state.canCapture)
    }

    @Test
    fun cameraBecomesReady_onlyAfterPermissionAndStartRequest() {
        val granted = reduceCameraUiState(
            CameraUiState(),
            CameraUiEvent.PermissionObserved(CameraPermission.GRANTED),
        )
        val starting = reduceCameraUiState(granted, CameraUiEvent.CameraStartRequested)
        val ready = reduceCameraUiState(starting, CameraUiEvent.CameraReady)

        assertEquals(CameraRuntime.STARTING, starting.cameraRuntime)
        assertEquals(CameraRuntime.READY, ready.cameraRuntime)
        assertTrue(ready.canCapture)
    }

    @Test
    fun staleCameraReadyCallback_isIgnored() {
        val grantedButStopped = CameraUiState(permission = CameraPermission.GRANTED)

        val result = reduceCameraUiState(grantedButStopped, CameraUiEvent.CameraReady)

        assertSame(grantedButStopped, result)
    }

    @Test
    fun permissionRevocation_stopsCameraAndPreservesReferenceSelection() {
        val ready = capturableState().copy(
            selectedReferencePhotoId = "window-portrait",
            captureInFlight = true,
        )

        val denied = reduceCameraUiState(
            ready,
            CameraUiEvent.PermissionObserved(CameraPermission.DENIED),
        )

        assertEquals(CameraRuntime.STOPPED, denied.cameraRuntime)
        assertEquals("window-portrait", denied.selectedReferencePhotoId)
        assertFalse(denied.captureInFlight)
        assertFalse(denied.canCapture)
    }

    @Test
    fun decodeSuccess_replacesReferenceOnlyForActiveRequest() {
        val original = CameraUiState(selectedReferencePhotoId = "old-photo")
        val decoding = reduceCameraUiState(
            original,
            CameraUiEvent.ReferenceDecodeStarted(requestId = 9L),
        )

        val stale = reduceCameraUiState(
            decoding,
            CameraUiEvent.ReferenceDecodeSucceeded(8L, "stale-photo"),
        )
        val current = reduceCameraUiState(
            stale,
            CameraUiEvent.ReferenceDecodeSucceeded(9L, "new-photo"),
        )

        assertSame(decoding, stale)
        assertEquals("new-photo", current.selectedReferencePhotoId)
        assertSame(ReferenceLoadState.Idle, current.referenceLoadState)
    }

    @Test
    fun decodeFailure_keepsPreviousReferenceAndShowsMessage() {
        val decoding = CameraUiState(
            selectedReferencePhotoId = "old-photo",
            referenceLoadState = ReferenceLoadState.Decoding(4L),
        )

        val failed = reduceCameraUiState(
            decoding,
            CameraUiEvent.ReferenceDecodeFailed(4L),
        )

        assertEquals("old-photo", failed.selectedReferencePhotoId)
        assertSame(ReferenceLoadState.Idle, failed.referenceLoadState)
        assertEquals(CameraUiMessage.REFERENCE_DECODE_FAILED, failed.message)
    }

    @Test
    fun pickerCancellation_doesNotChangeState() {
        val state = CameraUiState(
            selectedReferencePhotoId = "old-photo",
            overlayMode = OverlayMode.REFERENCE,
            captureCount = 3,
        )

        assertSame(
            state,
            reduceCameraUiState(state, CameraUiEvent.ReferencePickCancelled),
        )
    }

    @Test
    fun removingReference_disablesReferenceOverlayButKeepsOtherOverlayModes() {
        val referenceOverlay = CameraUiState(
            selectedReferencePhotoId = "photo",
            overlayMode = OverlayMode.REFERENCE,
        )
        val outlineOverlay = referenceOverlay.copy(overlayMode = OverlayMode.OUTLINE)

        val removedReference = reduceCameraUiState(
            referenceOverlay,
            CameraUiEvent.ReferenceRemoved,
        )
        val removedFromOutline = reduceCameraUiState(
            outlineOverlay,
            CameraUiEvent.ReferenceRemoved,
        )

        assertNull(removedReference.selectedReferencePhotoId)
        assertEquals(OverlayMode.SKELETON, removedReference.overlayMode)
        assertEquals(OverlayMode.OUTLINE, removedFromOutline.overlayMode)
    }

    @Test
    fun referenceOverlay_cannotBeSelectedWithoutReferencePhoto() {
        val state = CameraUiState(overlayMode = OverlayMode.OUTLINE)

        val result = reduceCameraUiState(
            state,
            CameraUiEvent.OverlayModeSelected(OverlayMode.REFERENCE),
        )

        assertSame(state, result)
    }

    @Test
    fun successfulCapture_incrementsCountAndReenablesShutter() {
        val initial = capturableState().copy(captureCount = 2)

        val capturing = reduceCameraUiState(initial, CameraUiEvent.CaptureStarted)
        val saved = reduceCameraUiState(capturing, CameraUiEvent.CaptureSucceeded)

        assertTrue(capturing.captureInFlight)
        assertFalse(capturing.canCapture)
        assertEquals(3, saved.captureCount)
        assertFalse(saved.captureInFlight)
        assertTrue(saved.canCapture)
    }

    @Test
    fun failedCapture_doesNotIncrementCountAndExposesError() {
        val capturing = capturableState().copy(captureInFlight = true, captureCount = 5)

        val failed = reduceCameraUiState(capturing, CameraUiEvent.CaptureFailed)

        assertEquals(5, failed.captureCount)
        assertFalse(failed.captureInFlight)
        assertEquals(CameraUiMessage.CAPTURE_FAILED, failed.message)
    }

    @Test
    fun captureStart_isIgnoredUntilCameraIsCapturable() {
        val notReady = CameraUiState(permission = CameraPermission.GRANTED)

        assertSame(
            notReady,
            reduceCameraUiState(notReady, CameraUiEvent.CaptureStarted),
        )
    }

    @Test
    fun snapshotRoundTrip_restoresDurableStateAndResetsTransientState() {
        val active = capturableState().copy(
            selectedReferencePhotoId = "window-portrait",
            referenceLoadState = ReferenceLoadState.Decoding(12L),
            selectedGuidePanel = GuidePanel.SUBJECT,
            overlayMode = OverlayMode.REFERENCE,
            captureInFlight = true,
            captureCount = 7,
            message = CameraUiMessage.CAPTURE_FAILED,
        )

        val restored = restoreCameraUiState(active.toSnapshot())

        assertEquals("window-portrait", restored.selectedReferencePhotoId)
        assertEquals(GuidePanel.SUBJECT, restored.selectedGuidePanel)
        assertEquals(OverlayMode.REFERENCE, restored.overlayMode)
        assertEquals(7, restored.captureCount)
        assertEquals(CameraPermission.UNKNOWN, restored.permission)
        assertEquals(CameraRuntime.STOPPED, restored.cameraRuntime)
        assertSame(ReferenceLoadState.Idle, restored.referenceLoadState)
        assertFalse(restored.captureInFlight)
        assertNull(restored.message)
    }

    @Test
    fun snapshotRestore_sanitizesInvalidDurableValues() {
        val restored = restoreCameraUiState(
            CameraUiSnapshot(
                selectedReferencePhotoId = " ",
                overlayMode = OverlayMode.REFERENCE,
                captureCount = -10,
            )
        )

        assertNull(restored.selectedReferencePhotoId)
        assertEquals(OverlayMode.SKELETON, restored.overlayMode)
        assertEquals(0, restored.captureCount)
    }

    @Test
    fun unknownSnapshotVersion_fallsBackToFreshState() {
        val restored = restoreCameraUiState(
            CameraUiSnapshot(
                version = 999,
                selectedReferencePhotoId = "photo",
                captureCount = 10,
            )
        )

        assertEquals(CameraUiState(), restored)
    }

    private fun capturableState() = CameraUiState(
        permission = CameraPermission.GRANTED,
        cameraRuntime = CameraRuntime.READY,
    )
}
