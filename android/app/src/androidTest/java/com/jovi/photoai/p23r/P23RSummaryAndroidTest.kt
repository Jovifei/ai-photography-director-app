package com.jovi.photoai.p23r

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.AnalysisAttempt
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ProviderAnalysisProvenance
import com.jovi.photoai.data.reference.ProviderAnalysisResult
import com.jovi.photoai.data.reference.ProviderType
import com.jovi.photoai.data.reference.PersistedProjectSummary
import com.jovi.photoai.data.reference.ProjectSummaryRequest
import com.jovi.photoai.data.reference.ProjectSummaryStatus
import com.jovi.photoai.data.reference.ReadySummaryInput
import com.jovi.photoai.data.reference.ReferenceAnalysisRequest
import com.jovi.photoai.data.reference.ReferenceEntity
import com.jovi.photoai.data.reference.SafeProviderErrorCode
import com.jovi.photoai.data.reference.toEntity
import com.jovi.photoai.data.reference.toRecord
import com.jovi.photoai.data.reference.withAnalysisResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P23RSummaryAndroidTest {
    @Test fun bundleOnlyLegacySummary_isHiddenAndDeletedByReadValidation() = runBlocking {
        P23RRoomFixture().use { fixture ->
            fixture.seed(1)
            val applied = fixture.repository.applyKnowledgeBundle(fixture.project.id, fixture.bundle(), fixture.bindings())
            assertTrue(applied is KnowledgeBundleApplyResult.Success)
            fixture.dao.upsertSummary(
                PersistedProjectSummary(
                    projectId = fixture.project.id,
                    status = ProjectSummaryStatus.SUCCESS,
                    readyCount = 1,
                    failedCount = 0,
                    inputDigest = "stale-bundle-only-digest",
                    modelId = "legacy-provider-model",
                ).toEntity(),
            )

            assertNull(fixture.repository.projectSummary(fixture.project.id))
            assertNull(fixture.dao.summaryByProject(fixture.project.id))
        }
    }

    @Test fun providerReadySetChange_invalidatesSummaryFlowAndRead() = runBlocking {
        P23RRoomFixture().use { fixture ->
            fixture.seed(2)
            persistProviderReady(fixture, 0)
            fixture.dao.upsertSummary(validProviderSummary(fixture).toEntity())

            assertSummaryInvalidates(fixture) {
                val current = fixture.dao.activeById(fixture.records[1].photo.id)!!
                fixture.dao.update(current.withAnalysisResult(providerResult(current.id, current.toRecord().bundle.copy(scene = "provider-second"))))
            }
        }
    }

    @Test fun failedCountChange_invalidatesSummaryFlowAndRead() = runBlocking {
        P23RRoomFixture().use { fixture ->
            fixture.seed(2)
            persistProviderReady(fixture, 0)
            fixture.dao.upsertSummary(validProviderSummary(fixture).toEntity())

            assertSummaryInvalidates(fixture) {
                val current = fixture.dao.activeById(fixture.records[1].photo.id)!!
                fixture.dao.update(current.withAnalysisResult(
                    ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_UNAVAILABLE, retryable = true),
                ))
            }
        }
    }

    @Test fun inputDigestChange_invalidatesSummaryFlowAndRead() = runBlocking {
        P23RRoomFixture().use { fixture ->
            fixture.seed(1)
            persistProviderReady(fixture, 0)
            fixture.dao.upsertSummary(validProviderSummary(fixture).toEntity())

            assertSummaryInvalidates(fixture) {
                val current = fixture.dao.activeById(fixture.records.single().photo.id)!!
                val changed = current.toRecord().bundle.copy(scene = "provider-changed")
                fixture.dao.update(current.withAnalysisResult(providerResult(current.id, changed)))
            }
        }
    }

    private suspend fun persistProviderReady(fixture: P23RRoomFixture, index: Int) {
        val record = fixture.dao.activeById(fixture.records[index].photo.id)!!
        val attempt = AnalysisAttempt(record.id, "summary-provider-$index")
        assertTrue(fixture.repository.markAnalysisQueued(attempt))
        assertTrue(fixture.repository.markAnalysisRunning(attempt))
        assertNotNull(fixture.repository.persistAnalysis(
            ReferenceAnalysisRequest(record.id),
            providerResult(record.id, record.toRecord().bundle.copy(scene = "provider-${record.id}")),
            attempt,
        ))
    }

    private suspend fun validProviderSummary(fixture: P23RRoomFixture): PersistedProjectSummary {
        val rows = fixture.dao.activeByProjectOnce(fixture.project.id).map(ReferenceEntity::toRecord)
        val ready = rows.filter {
            it.analysisStatus == PhotoAnalysisStatus.READY &&
                it.analysisProvenance != null && it.knowledgeBundleProvenance == null
        }.map { ReadySummaryInput(it.photo.id, it.bundle) }
        val failed = rows.count {
            it.analysisStatus == PhotoAnalysisStatus.FAILED || it.analysisStatus == PhotoAnalysisStatus.UNAVAILABLE
        }
        val request = ProjectSummaryRequest(fixture.project.id, ready, failed)
        return PersistedProjectSummary(
            projectId = fixture.project.id,
            status = ProjectSummaryStatus.SUCCESS,
            readyCount = ready.size,
            failedCount = failed,
            inputDigest = request.inputDigest,
            modelId = "provider-model",
        )
    }

    private fun providerResult(referenceId: String, bundle: com.jovi.photoai.reference.ReferenceBundle) =
        ProviderAnalysisResult.Ready(
            bundle = bundle.copy(referenceId = referenceId),
            provenance = ProviderAnalysisProvenance(
                providerId = "synthetic-provider",
                providerType = ProviderType.ON_DEVICE,
                modelId = "synthetic-model",
                modelRevision = "synthetic-revision",
                modelArtifactSha256 = "a".repeat(64),
                runtimeId = "synthetic-runtime",
                startedAtEpochMillis = 1L,
                completedAtEpochMillis = 2L,
            ),
        )

    private suspend fun assertSummaryInvalidates(fixture: P23RRoomFixture, mutate: suspend () -> Unit) {
        val projectId = fixture.project.id
        assertEquals("provider-model", fixture.repository.projectSummaryForProject(projectId).first()?.modelId)
        val sawValid = CompletableDeferred<Unit>()
        val sawNull = CompletableDeferred<Unit>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            fixture.repository.projectSummaryForProject(projectId).collect { summary ->
                if (summary == null) sawNull.complete(Unit) else sawValid.complete(Unit)
            }
        }
        try {
            withTimeout(5_000) { sawValid.await() }
            mutate()
            withTimeout(5_000) { sawNull.await() }
        } finally {
            collector.cancelAndJoin()
        }
        assertNull(fixture.repository.projectSummary(projectId))
        assertNull(fixture.dao.summaryByProject(projectId))
    }
}
