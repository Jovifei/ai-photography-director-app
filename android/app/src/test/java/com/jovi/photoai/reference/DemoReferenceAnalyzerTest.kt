package com.jovi.photoai.reference

import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoReferenceAnalyzerTest {
    @Test
    fun sameUri_producesDeterministicClearlyLabelledDemoAnalysis() {
        val first = DemoReferenceAnalyzer.analyze("reference-1", "content://picker/image-1")
        val second = DemoReferenceAnalyzer.analyze("reference-1", "content://picker/image-1")

        assertEquals(first, second)
        assertEquals("Demo Analysis", DemoReferenceAnalyzer.SOURCE_LABEL)
        assertTrue(first.poseTemplate.contains("不进行实时骨架检测"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankUri_isRejectedBeforeAnyAnalysis() {
        DemoReferenceAnalyzer.analyze("reference-1", "")
    }
}
