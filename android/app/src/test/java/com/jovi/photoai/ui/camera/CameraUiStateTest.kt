package com.jovi.photoai.ui.camera

import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.domain.model.GuidanceItem
import com.jovi.photoai.domain.model.GuidancePriority
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
        assertTrue(state.gridVisible)
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
        assertTrue(restored.gridVisible)
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

    @Test
    fun runtimeCameraHint_usesTheDemoPlanSafetyGuidance() {
        val guidance = cameraGuidanceFor(DemoContentRepository.defaultShootingPlan.guidance)
        val state = reduceCameraUiState(CameraUiState(), CameraUiEvent.GuidanceUpdated(guidance))

        assertEquals("确认人物脚下安全", state.currentGuidance?.title)
        assertEquals(GuidancePriority.SAFETY, state.currentGuidance?.priority)
    }

    @Test
    fun runtimeCameraHint_isPriorityOrderedAndTieStable() {
        val firstSafety = guidance("first", GuidancePriority.SAFETY)
        val laterSafety = guidance("later", GuidancePriority.SAFETY)
        val framing = guidance("framing", GuidancePriority.FRAMING)

        assertSame(firstSafety, cameraGuidanceFor(listOf(framing, firstSafety, laterSafety)))
        assertSame(firstSafety, cameraGuidanceFor(listOf(firstSafety, framing, laterSafety)))
        assertNull(cameraGuidanceFor(emptyList()))
    }

    @Test
    fun edgeGestureThreshold_isDensityAwareAndUsesSharedDirectionRules() {
        assertEquals(56f, edgeGestureThresholdPx(1f), 0f)
        assertEquals(112f, edgeGestureThresholdPx(2f), 0f)
        assertEquals(168f, edgeGestureThresholdPx(3f), 0f)
        assertEquals(32f, edgeGestureWidthPx(1f), 0f)
        assertEquals(64f, edgeGestureWidthPx(2f), 0f)

        val threshold = edgeGestureThresholdPx(2f)
        assertFalse(shouldOpenEdgeGuide(GuidePanel.ENVIRONMENT, threshold - 1f, 0f, threshold))
        assertTrue(shouldOpenEdgeGuide(GuidePanel.ENVIRONMENT, threshold, 0f, threshold))
        assertTrue(shouldOpenEdgeGuide(GuidePanel.SUBJECT, -threshold, 0f, threshold))
        assertFalse(shouldOpenEdgeGuide(GuidePanel.ENVIRONMENT, -threshold, 0f, threshold))
        assertFalse(shouldOpenEdgeGuide(GuidePanel.SUBJECT, threshold, 0f, threshold))
        assertFalse(shouldOpenEdgeGuide(GuidePanel.ENVIRONMENT, 0f, threshold, threshold))
        assertFalse(shouldOpenEdgeGuide(GuidePanel.ENVIRONMENT, threshold, threshold, threshold))
        assertFalse(shouldOpenEdgeGuide(GuidePanel.NONE, threshold, 0f, threshold))
    }

    @Test
    fun guidePanelGridAndBackAreAllReducerDriven() {
        val environment = reduceCameraUiState(
            CameraUiState(),
            CameraUiEvent.GuidePanelSelected(GuidePanel.ENVIRONMENT),
        )
        val subject = reduceCameraUiState(
            environment,
            CameraUiEvent.GuidePanelSelected(GuidePanel.SUBJECT),
        )
        val closed = reduceCameraUiState(subject, CameraUiEvent.ClosePanel)
        val gridToggled = reduceCameraUiState(closed, CameraUiEvent.GridToggled)

        assertEquals(GuidePanel.ENVIRONMENT, environment.selectedGuidePanel)
        assertTrue(cameraBackClosesPanel(environment))
        assertEquals(GuidePanel.SUBJECT, subject.selectedGuidePanel)
        assertEquals(GuidePanel.NONE, closed.selectedGuidePanel)
        assertFalse(cameraBackClosesPanel(closed))
        assertFalse(gridToggled.gridVisible)
    }

    @Test
    fun overlayModeSwitch_usesTheSameReducerAsRuntimeChrome() {
        val withReference = reduceCameraUiState(
            CameraUiState(),
            CameraUiEvent.ReferenceSelected("window-portrait"),
        )
        val outline = reduceCameraUiState(
            withReference,
            CameraUiEvent.OverlayModeSelected(OverlayMode.OUTLINE),
        )
        val reference = reduceCameraUiState(
            outline,
            CameraUiEvent.OverlayModeSelected(OverlayMode.REFERENCE),
        )

        assertEquals(OverlayMode.OUTLINE, outline.overlayMode)
        assertEquals(OverlayMode.REFERENCE, reference.overlayMode)
    }

    @Test
    fun snapshotRoundTrip_restoresGridAndNeverPersistsInjectedGuidance() {
        val state = CameraUiState(
            gridVisible = false,
            currentGuidance = guidance("safety", GuidancePriority.SAFETY),
        )

        val restored = restoreCameraUiState(state.toSnapshot())

        assertFalse(restored.gridVisible)
        assertNull(restored.currentGuidance)
    }

    private fun capturableState() = CameraUiState(
        permission = CameraPermission.GRANTED,
        cameraRuntime = CameraRuntime.READY,
    )

    private fun guidance(id: String, priority: GuidancePriority) = GuidanceItem(
        id = id,
        panel = GuidePanel.ENVIRONMENT,
        priority = priority,
        title = id,
        instruction = id,
    )
}
