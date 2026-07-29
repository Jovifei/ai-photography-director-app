package com.jovi.photoai.data.reference

import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import org.junit.Assert.assertThrows
import org.junit.Test

class ReferenceRecordTest {
    @Test
    fun `private image key accepts only a jpeg basename`() {
        record("reference-1.jpg").requireSafeImageFileName()
    }

    @Test
    fun `private image key rejects a path`() {
        assertThrows(IllegalArgumentException::class.java) {
            record("../outside.jpg").requireSafeImageFileName()
        }
    }

    @Test
    fun `private image key rejects non jpeg content`() {
        assertThrows(IllegalArgumentException::class.java) {
            record("reference-1.png").requireSafeImageFileName()
        }
    }

    private fun record(fileName: String) = ReferenceRecord(
        photo = ReferencePhoto("id", "title", "description", "source", "private/$fileName", 1f),
        bundle = ReferenceBundle(
            "id", "scene", "story", "lighting", "composition", "subject", "emotion", "pose", "camera", "prompt", "1.0",
        ),
        imageFileName = fileName,
        createdAtEpochMillis = 1L,
    )
}
