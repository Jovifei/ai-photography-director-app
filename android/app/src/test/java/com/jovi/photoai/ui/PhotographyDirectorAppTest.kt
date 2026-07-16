package com.jovi.photoai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotographyDirectorAppTest {
    @Test
    fun homeCameraBack_returnsHome() {
        assertEquals(AppDestination.HOME, cameraReturnDestination(AppDestination.HOME))
    }

    @Test
    fun analysisCameraBack_returnsAnalysis() {
        assertEquals(
            AppDestination.ANALYSIS_DETAIL,
            cameraReturnDestination(AppDestination.ANALYSIS_DETAIL),
        )
    }
}
