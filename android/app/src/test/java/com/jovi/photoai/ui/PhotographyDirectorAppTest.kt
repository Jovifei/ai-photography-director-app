package com.jovi.photoai.ui

import org.junit.Assert.assertEquals
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
}
