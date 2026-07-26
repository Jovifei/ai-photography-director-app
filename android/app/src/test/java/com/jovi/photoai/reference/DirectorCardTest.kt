package com.jovi.photoai.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorCardTest {
    private val bundle = DemoReferenceAnalyzerFixture.bundle

    @Test
    fun directorCard_hasFourActionableSections() {
        val card = bundle.toDirectorCard()

        assertTrue(listOf(card.environment, card.subject, card.emotion, card.camera).all(String::isNotBlank))
        assertTrue(card.subject.contains(bundle.poseTemplate))
        assertTrue(card.camera.contains(bundle.cameraPosition))
    }

    @Test
    fun cameraGuidance_isReferenceBasedAndNotRealtimePose() {
        val guidance = bundle.toCameraDirectorGuidance("导入参考图 1")
        val items = bundle.toDirectorCard().toGuidanceItems()

        assertEquals("Demo Analysis · 非实时 Pose", guidance.sourceLabel)
        assertEquals(5, guidance.environment.size)
        assertEquals(3, guidance.subject.size)
        assertEquals(4, items.size)
        assertTrue(items.all { it.instruction.isNotBlank() })
    }
}

internal object DemoReferenceAnalyzerFixture {
    val bundle: ReferenceBundle = com.jovi.photoai.data.demo.DemoReferenceAnalyzer.analyze(
        referenceId = "reference-1",
        imageUri = "content://picker/image-1",
    )
}
