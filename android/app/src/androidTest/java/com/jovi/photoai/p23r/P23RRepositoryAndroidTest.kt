package com.jovi.photoai.p23r

import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.withTransaction
import com.jovi.photoai.data.reference.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P23RRepositoryAndroidTest {
    @Test fun bundleWinsAgainstStaleSnapshot_andCannotBeDemoted() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(1)
            val oldRow = f.dao.activeById(f.records.single().photo.id)!!
            assertEquals("IMPORTED", oldRow.analysisStatus)
            assertEquals(KnowledgeBundleApplyResult.Success(1), f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
            val attempt = AnalysisAttempt(oldRow.id, "late_attempt")
            assertFalse(f.repository.markAnalysisQueued(attempt))
            assertFalse(f.repository.markAnalysisRunning(attempt))
            assertNull(f.repository.persistAnalysis(ReferenceAnalysisRequest(oldRow.id), unavailable(), attempt))
            val actual = f.dao.activeById(oldRow.id)!!
            assertEquals("READY", actual.analysisStatus)
            assertEquals("synthetic_bundle", actual.knowledgeBundleId)
            assertNull(actual.analysisAttemptId)
        }
    }

    @Test fun analysisWinsFirst_bundleCannotOverwriteQueuedOrRunning() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(1)
            val attempt = AnalysisAttempt(f.records.single().photo.id, "analysis_winner")
            assertTrue(f.repository.markAnalysisQueued(attempt))
            assertRejected(f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
            assertTrue(f.repository.markAnalysisRunning(attempt))
            assertRejected(f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
            assertNotNull(f.repository.persistAnalysis(ReferenceAnalysisRequest(attempt.referenceId), unavailable(), attempt))
            assertNull(f.dao.activeById(attempt.referenceId)!!.analysisAttemptId)
        }
    }

    @Test fun cancelledAttempt_cannotWriteIntoNewerRunningRetry() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(1)
            val first = AnalysisAttempt(f.records.single().photo.id, "first")
            val second = first.copy(attemptId = "second")
            assertTrue(f.repository.markAnalysisQueued(first))
            assertTrue(f.repository.markAnalysisRunning(first))
            f.repository.cancelAnalysisAttempt(first)
            assertTrue(f.repository.markAnalysisQueued(second))
            assertTrue(f.repository.markAnalysisRunning(second))
            f.repository.cancelAnalysisAttempt(first)
            assertNull(f.repository.persistAnalysis(ReferenceAnalysisRequest(first.referenceId), unavailable(), first))
            assertEquals("second", f.dao.activeById(first.referenceId)!!.analysisAttemptId)
            assertNotNull(f.repository.persistAnalysis(ReferenceAnalysisRequest(second.referenceId), unavailable(), second))
        }
    }

    @Test fun concurrentClaimAndBundleCommit_haveExactlyOneWinner() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(1)
            val gate = CompletableDeferred<Unit>()
            val attempt = AnalysisAttempt(f.records.single().photo.id, "concurrent")
            val claim = async(Dispatchers.Default) { gate.await(); f.repository.markAnalysisQueued(attempt) }
            val imported = async(Dispatchers.Default) { gate.await(); f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()) }
            gate.complete(Unit)
            val claimed = claim.await()
            val applied = imported.await()
            val actual = f.dao.activeById(attempt.referenceId)!!
            if (claimed) {
                assertRejected(applied)
                assertEquals("QUEUED", actual.analysisStatus)
                assertNull(actual.knowledgeBundleId)
            } else {
                assertEquals(KnowledgeBundleApplyResult.Success(1), applied)
                assertEquals("READY", actual.analysisStatus)
                assertEquals("synthetic_bundle", actual.knowledgeBundleId)
            }
        }
    }

    @Test fun secondTargetRejected_noEarlierRowWasWritten() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(2)
            assertTrue(f.repository.markAnalysisQueued(AnalysisAttempt(f.records[1].photo.id, "busy")))
            val before = f.dao.activeById(f.records[0].photo.id)
            assertRejected(f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
            assertEquals(before, f.dao.activeById(f.records[0].photo.id))
        }
    }

    @Test fun sqliteAbortOnSecondUpdate_rollsBackButIsNotMislabelledAsKnownFailure() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(2)
            val before = f.dao.activeByProjectOnce(f.project.id)
            withContext(Dispatchers.IO) {
                f.database.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER synthetic_fail BEFORE UPDATE ON reference_records " +
                        "WHEN NEW.id = 'synthetic_1' AND NEW.knowledgeBundleId IS NOT NULL " +
                        "BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END",
                )
            }
            assertSame(KnowledgeBundleApplyResult.OutcomeUnknown, f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
            assertEquals(before, f.dao.activeByProjectOnce(f.project.id))
        }
    }

    @Test fun exceptionAfterCommit_isUnknown_andRetryCannotOverwriteReady() = runBlocking {
        val failOnce = AtomicBoolean(true)
        P23RRoomFixture(afterKnowledgeBundleCommit = {
            if (failOnce.getAndSet(false)) throw SQLiteException("synthetic lost acknowledgement")
        }).use { f ->
            f.seed(1)
            val outcome = f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings())
            assertSame(KnowledgeBundleApplyResult.OutcomeUnknown, outcome)
            assertEquals("READY", f.dao.activeById(f.records.single().photo.id)!!.analysisStatus)
            assertRejected(f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
        }
    }

    @Test fun cancellationAfterCommit_isRethrown_notConvertedToRetryableFailure() = runBlocking {
        P23RRoomFixture(afterKnowledgeBundleCommit = { throw CancellationException("synthetic acknowledgement cancelled") }).use { f ->
            f.seed(1)
            val caught = try {
                f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings())
                null
            } catch (error: CancellationException) { error }
            assertNotNull(caught)
            assertEquals("READY", f.dao.activeById(f.records.single().photo.id)!!.analysisStatus)
        }
    }

    @Test fun deletedTarget_rejectsLateProviderResultWithoutReinsertion() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(1)
            val attempt = AnalysisAttempt(f.records.single().photo.id, "delete_race")
            assertTrue(f.repository.markAnalysisQueued(attempt))
            assertTrue(f.repository.markAnalysisRunning(attempt))
            f.dao.markDeletePending(listOf(attempt.referenceId))
            assertNull(f.repository.persistAnalysis(ReferenceAnalysisRequest(attempt.referenceId), unavailable(), attempt))
            assertNull(f.dao.activeById(attempt.referenceId))
            assertEquals("DELETE_PENDING", f.dao.allOnce().single().storageState)
        }
    }

    private fun unavailable() = ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.PROVIDER_UNAVAILABLE)
    private fun assertRejected(value: KnowledgeBundleApplyResult) = assertEquals(
        KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE), value,
    )
}
