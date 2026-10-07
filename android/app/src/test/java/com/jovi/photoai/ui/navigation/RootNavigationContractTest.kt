package com.jovi.photoai.ui.navigation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RootNavigationContractTest {
    @Test
    fun rootSections_putPoseFirst_thenInspirationStub_thenRecords() {
        assertArrayEquals(
            arrayOf(RootSection.POSE, RootSection.INSPIRATION, RootSection.RECORDS),
            RootSection.entries.toTypedArray(),
        )
    }

    @Test
    fun poseIsDefaultProductRootLabel() {
        assertEquals("POSE", RootSection.POSE.name)
    }
}
