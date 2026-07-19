package com.jovi.photoai.aa0

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.camera.PoseAnalysisCoordinator
import com.jovi.photoai.pose.CloseOnceFrameLease
import com.jovi.photoai.pose.PoseImageFormat
import com.jovi.photoai.pose.PoseInputFrame
import com.jovi.photoai.pose.PoseLensFacing
import com.jovi.photoai.pose.fake.FakePoseEstimator
import com.jovi.photoai.pose.fake.FakePoseScenario
import com.jovi.photoai.pose.geometry.CropGeometry
import com.jovi.photoai.pose.geometry.PreviewGeometry
import com.jovi.photoai.ui.pose.PoseDebugOverlayContentDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Compile-safe P0 Android skeleton; no camera, model, or UI0 runtime wiring. */
@RunWith(AndroidJUnit4::class)
class PoseContractAndroidTest {
    @Test
    fun frameLeaseAndroidProcessStillClosesExactlyOnce() {
        val lease = CloseOnceFrameLease(Unit) {}

        lease.close()
        lease.close()

        assertEquals(1, lease.releaseCount)
    }

    @Test
    fun coordinatorDispose_closesPendingInAndroidProcess() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val lease = CloseOnceFrameLease(Unit) {}
        val coordinator = PoseAnalysisCoordinator(engine)

        coordinator.submit(frame(lease)) {}
        coordinator.dispose()

        assertEquals(1, lease.releaseCount)
        assertEquals(1, engine.engineCloseCount)
    }

    @Test
    fun debugOverlaySemantics_areExplicitAndNotDemoOverlay() {
        assertTrue(PoseDebugOverlayContentDescription.startsWith("Pose Debug"))
        assertTrue(PoseDebugOverlayContentDescription.contains("非产品功能"))
    }

    private fun frame(lease: CloseOnceFrameLease<Unit>) = PoseInputFrame(
        frameId = 1L,
        generation = 0L,
        timestampNs = 1L,
        imageFormat = PoseImageFormat.YUV_420,
        width = 100,
        height = 100,
        cropRect = CropGeometry.fullFrame(),
        rotationDegrees = 0,
        lensFacing = PoseLensFacing.BACK,
        lease = lease,
        previewGeometry = PreviewGeometry.letterbox(100.0, 100.0, 100.0, 100.0),
    )
}
