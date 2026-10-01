package com.jovi.photoai.t17

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.capture.CaptureRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Pure adversarial inputs to the same guards called immediately before real harness writes. */
@RunWith(AndroidJUnit4::class)
class T17OwnershipPolicyAndroidTest {
    private val c1 = CaptureRecord("C1", "P", "R", 1)
    private val c2 = CaptureRecord("C2", "P", "R", 2)

    @Test fun extraUnrecordedCaptureRejectsBeforeAnyDelete() {
        var deletes = 0
        expectRejection {
            T17OwnershipPolicy.requireExactCaptures(listOf(c1, c2, CaptureRecord("X", "P", "R", 3)), setOf("C1", "C2"), "P", "R")
            deletes++
        }
        assertEquals(0, deletes)
    }

    @Test fun recordedIdWithWrongReferenceRejectsBeforeAnyDelete() {
        var deletes = 0
        expectRejection {
            T17OwnershipPolicy.requireExactCaptures(listOf(c1, c2.copy(referenceId = "OTHER")), setOf("C1", "C2"), "P", "R")
            deletes++
        }
        assertEquals(0, deletes)
    }

    @Test fun recordedIdWithWrongProjectRejectsBeforeAnyDelete() {
        var deletes = 0
        expectRejection {
            T17OwnershipPolicy.requireExactCaptures(listOf(c1, c2.copy(projectId = "OTHER")), setOf("C1", "C2"), "P", "R")
            deletes++
        }
        assertEquals(0, deletes)
    }

    @Test fun exactRecordedAssociationAllowsCleanupBoundary() {
        T17OwnershipPolicy.requireExactCaptures(listOf(c2, c1), setOf("C1", "C2"), "P", "R")
    }

    @Test fun missingCurrentKeyWhenOriginalPresentRejectsWithoutWrite() {
        var writes = 0
        expectRejection {
            T17OwnershipPolicy.preferenceDecision(true, "OWNER", false, null, "R")
            writes++
        }
        assertEquals(0, writes)
    }

    @Test fun foreignReferenceRejectsWithoutWrite() {
        var writes = 0
        expectRejection {
            T17OwnershipPolicy.preferenceDecision(true, "OWNER", true, "FOREIGN", "R")
            writes++
        }
        assertEquals(0, writes)
    }

    @Test fun exactOriginalPresentOrAbsentIsNoop() {
        assertEquals(T17PreferenceDecision.NOOP, T17OwnershipPolicy.preferenceDecision(true, "OWNER", true, "OWNER", "R"))
        assertEquals(T17PreferenceDecision.NOOP, T17OwnershipPolicy.preferenceDecision(false, null, false, null, "R"))
    }

    @Test fun exactOwnedReferenceRestoresOriginalPresentOrAbsent() {
        assertEquals(T17PreferenceDecision.RESTORE_ORIGINAL,
            T17OwnershipPolicy.preferenceDecision(true, "OWNER", true, "R", "R"))
        assertEquals(T17PreferenceDecision.RESTORE_ORIGINAL,
            T17OwnershipPolicy.preferenceDecision(false, null, true, "R", "R"))
    }

    private fun expectRejection(block: () -> Unit) {
        try { block(); fail("Expected ownership rejection") } catch (_: IllegalArgumentException) { }
    }
}
