package com.jovi.photoai.reference

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceFlowReducerTest {
    @Test
    fun importAnalysisDirectorCardAndCamera_formThePhase1ForwardFlow() {
        val analysis = advanceReferenceFlow(ReferenceFlowStage.IMPORT)
        val card = advanceReferenceFlow(analysis)
        val camera = advanceReferenceFlow(card)

        assertEquals(ReferenceFlowStage.ANALYSIS, analysis)
        assertEquals(ReferenceFlowStage.DIRECTOR_CARD, card)
        assertEquals(ReferenceFlowStage.CAMERA_DIRECTOR, camera)
    }

    @Test
    fun backFromCamera_returnsToDirectorCard() {
        assertEquals(
            ReferenceFlowStage.DIRECTOR_CARD,
            backReferenceFlow(ReferenceFlowStage.CAMERA_DIRECTOR),
        )
    }
}
