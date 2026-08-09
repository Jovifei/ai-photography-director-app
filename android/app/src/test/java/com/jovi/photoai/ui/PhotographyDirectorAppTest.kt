package com.jovi.photoai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotographyDirectorAppTest {
    @Test
    fun cameraDirectorBack_returnsDirectorCard() {
        assertEquals(
            AppDestination.DIRECTOR_CARD,
            cameraReturnDestination(AppDestination.CAMERA_DIRECTOR),
        )
    }

    @Test
    fun directHomeCameraFallback_returnsHome() {
        assertEquals(AppDestination.HOME, cameraReturnDestination(AppDestination.HOME))
    }

    @Test
    fun appDestinations_followImportAnalysisDirectorCardAndCamera() {
        val analysis = referenceNextDestination(AppDestination.IMPORT_REFERENCE)
        val card = referenceNextDestination(analysis)
        val camera = referenceNextDestination(card)

        assertEquals(AppDestination.ANALYSIS_DETAIL, analysis)
        assertEquals(AppDestination.DIRECTOR_CARD, card)
        assertEquals(AppDestination.CAMERA_DIRECTOR, camera)
    }

    @Test
    fun startup_restore_opensLastValidReferenceAnalysis_withoutClearingPreference() {
        val decision = resolveStartupRestore(
            current = AppDestination.HOME,
            hasPersistedActiveReference = true,
            restoredActiveReference = true,
        )

        assertEquals(
            AppDestination.ANALYSIS_DETAIL,
            decision.destination,
        )
        assertFalse(decision.shouldClearPersistedActiveReference)
    }

    @Test
    fun startup_invalidActiveReference_fallsBackToLibrary_andClearsPreference() {
        val decision = resolveStartupRestore(
            current = AppDestination.CAMERA_DIRECTOR,
            hasPersistedActiveReference = true,
            restoredActiveReference = false,
        )

        assertEquals(
            AppDestination.REFERENCE_LIBRARY,
            decision.destination,
        )
        assertTrue(decision.shouldClearPersistedActiveReference)
    }

    @Test
    fun referenceContent_staysHiddenUntilStartupRecoveryIsReady() {
        assertFalse(referenceContentVisible(ReferenceStartupState.RECONCILING))
        assertTrue(referenceContentVisible(ReferenceStartupState.READY))
    }

    @Test
    fun restoredProjectPhoto_returnsToItsProjectBoard() {
        assertEquals(
            AppDestination.PROJECT_BOARD,
            restoredReferenceReturnDestination("project-opaque-id"),
        )
    }
}
