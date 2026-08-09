package com.jovi.photoai.data.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceRecoverySummaryTest {
    @Test
    fun summary_exposesOnlyAggregateRecoveryCount() {
        val summary = ReferenceRecoverySummary(
            removedInvalidRecords = 1,
            completedPendingDeletes = 2,
            removedTemporaryFiles = 3,
            removedOrphanFiles = 4,
        )

        assertEquals(10, summary.recoveredItemCount)
        assertTrue(summary.hasRecoveryNotice)
    }

    @Test
    fun emptySummary_doesNotRequestUserNotification() {
        val summary = ReferenceRecoverySummary()

        assertEquals(0, summary.recoveredItemCount)
        assertFalse(summary.hasRecoveryNotice)
    }

    @Test
    fun pendingInvalidDeletion_isNotReportedAsAlreadyCleaned() {
        val summary = ReferenceRecoverySummary(isolatedInvalidRecordsPendingRetry = 1)

        assertEquals(0, summary.recoveredItemCount)
        assertTrue(summary.hasRecoveryNotice)
    }
}
