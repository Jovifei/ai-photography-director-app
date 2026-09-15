package com.jovi.photoai.p23r

import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Coordinator + actual Repository/Room, with a synthetic no-image Provider only. */
@RunWith(AndroidJUnit4::class)
class P23RCoordinatorAndroidTest {
    @Test fun bundleImportedWhileAnotherPhotoRuns_isSkippedWhenOldSnapshotResumes() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(2)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val provider = object : ReferenceAnalysisProvider {
                override suspend fun analyze(request: ReferenceAnalysisRequest): ProviderAnalysisResult {
                    calls.add(request.referenceId)
                    started.complete(Unit)
                    release.await()
                    return ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.PROVIDER_UNAVAILABLE)
                }
            }
            val job = async { PhotoAnalysisCoordinator(f.repository, provider).analyzeProject(f.project.id) }
            try {
                withTimeout(5000) { started.await() }
                val secondBinding = listOf(KnowledgeBundleBinding("producer_0", f.records[1].photo.id))
                assertEquals(KnowledgeBundleApplyResult.Success(1), f.repository.applyKnowledgeBundle(f.project.id, f.bundle(1), secondBinding))
                release.complete(Unit)
                withTimeout(5000) { job.await() }
                assertEquals(listOf(f.records[0].photo.id), calls)
                val second = f.dao.activeById(f.records[1].photo.id)!!
                assertEquals("READY", second.analysisStatus)
                assertEquals("synthetic_bundle", second.knowledgeBundleId)
            } finally { release.complete(Unit); job.cancelAndJoin() }
        }
    }

    @Test fun cancellationCleansOnlyClaimedAttempt_andLeavesUnvisitedRowsImported() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(2)
            val started = CompletableDeferred<Unit>()
            val provider = object : ReferenceAnalysisProvider {
                override suspend fun analyze(request: ReferenceAnalysisRequest): ProviderAnalysisResult {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            val job = launch { PhotoAnalysisCoordinator(f.repository, provider).analyzeProject(f.project.id) }
            try {
                withTimeout(5000) { started.await() }
                job.cancelAndJoin()
                val first = f.dao.activeById(f.records[0].photo.id)!!
                assertEquals("CANCELLED", first.analysisStatus)
                assertNull(first.analysisAttemptId)
                assertEquals("IMPORTED", f.dao.activeById(f.records[1].photo.id)!!.analysisStatus)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun lostClaimAcknowledgement_stillReleasesTheKnownAttemptToken() = runBlocking {
        P23RRoomFixture(afterAnalysisQueueCommit = { throw SQLiteException("synthetic claim acknowledgement failure") }).use { f ->
            f.seed(1)
            var calls = 0
            val provider = object : ReferenceAnalysisProvider {
                override suspend fun analyze(request: ReferenceAnalysisRequest): ProviderAnalysisResult {
                    calls++
                    return ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.PROVIDER_UNAVAILABLE)
                }
            }
            val error = runCatching { PhotoAnalysisCoordinator(f.repository, provider).analyzeProject(f.project.id) }.exceptionOrNull()
            assertNotNull(error)
            assertEquals(0, calls)
            val row = f.dao.activeById(f.records.single().photo.id)!!
            assertEquals("CANCELLED", row.analysisStatus)
            assertNull(row.analysisAttemptId)
        }
    }
}
