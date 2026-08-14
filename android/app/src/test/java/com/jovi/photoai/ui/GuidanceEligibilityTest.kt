package com.jovi.photoai.ui

import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.ui.project.projectShootingActionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEligibilityTest {
    @Test
    fun onlyReadyReferences_canOpenAiGuidance() {
        assertTrue(isRealAiGuidanceReady(PhotoAnalysisStatus.READY))
        listOf(
            PhotoAnalysisStatus.IMPORTED,
            PhotoAnalysisStatus.QUEUED,
            PhotoAnalysisStatus.RUNNING,
            PhotoAnalysisStatus.UNAVAILABLE,
            PhotoAnalysisStatus.FAILED,
            PhotoAnalysisStatus.CANCELLED,
            PhotoAnalysisStatus.EXAMPLE_GUIDANCE,
        ).forEach { status ->
            assertFalse("$status must not guide capture", isRealAiGuidanceReady(status))
        }
    }

    @Test
    fun legacyGuidanceRoutes_areSafelyDowngradedForUnreadyReferences() {
        listOf(AppDestination.DIRECTOR_CARD, AppDestination.CAMERA_DIRECTOR).forEach { requested ->
            assertEquals(
                AppDestination.CAPTURE_ENTRY,
                guardedGuidanceDestination(requested, PhotoAnalysisStatus.IMPORTED),
            )
        }
        assertEquals(
            AppDestination.CAMERA_DIRECTOR,
            guardedGuidanceDestination(AppDestination.CAMERA_DIRECTOR, PhotoAnalysisStatus.READY),
        )
    }

    @Test
    fun unreadyPrimaryReference_hasExplicitManualCaptureCopy() {
        assertEquals(
            "该参考尚未完成真实分析，本次拍摄不会使用 AI 指导。",
            offlineCaptureNotice(PhotoAnalysisStatus.EXAMPLE_GUIDANCE),
        )
        assertEquals("", offlineCaptureNotice(PhotoAnalysisStatus.READY))
        assertEquals("使用主参考进行无 AI 拍摄", projectShootingActionLabel(PhotoAnalysisStatus.IMPORTED))
        assertEquals("使用主参考进入 AI 拍摄", projectShootingActionLabel(PhotoAnalysisStatus.READY))
    }
}
