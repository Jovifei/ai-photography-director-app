package com.jovi.photoai.data.reference

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceImportViewModelTest {
    @Test
    fun acceptedBatchCount_neverExceedsRemainingCapacity() {
        assertEquals(20, acceptedBatchCount(20, 20))
        assertEquals(3, acceptedBatchCount(8, 3))
        assertEquals(0, acceptedBatchCount(8, 0))
        assertEquals(0, acceptedBatchCount(-1, 20))
    }
}
