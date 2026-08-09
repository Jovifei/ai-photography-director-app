package com.jovi.photoai.data.reference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceLibraryPreferencesTest {
    @Test
    fun opaqueReferenceId_acceptsOnlyBoundedIdentifierCharacters() {
        assertTrue(ReferenceLibraryPreferences.isSafeOpaqueReferenceId("f47ac10b-58cc-4372-a567-0e02b2c3d479"))
        assertTrue(ReferenceLibraryPreferences.isSafeOpaqueReferenceId("reference_01"))
        assertFalse(ReferenceLibraryPreferences.isSafeOpaqueReferenceId(""))
        assertFalse(ReferenceLibraryPreferences.isSafeOpaqueReferenceId("../private/photo"))
        assertFalse(ReferenceLibraryPreferences.isSafeOpaqueReferenceId("content://media/picker/1"))
    }
}
