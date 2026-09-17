package com.jovi.photoai.p23d

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.AnalysisAttempt
import com.jovi.photoai.data.reference.LEGACY_PROJECT_ID
import com.jovi.photoai.data.reference.PersistedProjectSummary
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ProjectSummaryRequest
import com.jovi.photoai.data.reference.ProjectSummaryStatus
import com.jovi.photoai.data.reference.ProviderAnalysisProvenance
import com.jovi.photoai.data.reference.ProviderAnalysisResult
import com.jovi.photoai.data.reference.ProviderType
import com.jovi.photoai.data.reference.ReadySummaryInput
import com.jovi.photoai.data.reference.ReferenceAnalysisRequest
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic-only durable-state harness. The prepare and verify methods MUST be run as two
 * separate instrumentation invocations with an external `am force-stop com.jovi.photoai`
 * between them. This class does not claim that Activity recreation is OS process death.
 */
@RunWith(AndroidJUnit4::class)
class P23DReadyStateProcessDeathAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun prepareProviderReadySummaryState_forExternalForceStop() = runBlocking {
        resetFixture()
        val repository = ReferenceRepository.create(context)
        val preferences = ReferenceLibraryPreferences(context)
        val imported = SyntheticPickerMediaFactory(context).use { media ->
            val result = repository.importFromPicker(
                media.jpeg(displayName = "p23d-provider-ready.jpg").uri,
            )
            assertTrue(result is ReferenceImportResult.Success)
            (result as ReferenceImportResult.Success).record
        }

        val attempt = AnalysisAttempt(imported.photo.id, "p23d-provider-attempt")
        assertTrue(repository.markAnalysisQueued(attempt))
        assertTrue(repository.markAnalysisRunning(attempt))
        val readyResult = ProviderAnalysisResult.Ready(
            bundle = imported.bundle.copy(
                referenceId = imported.photo.id,
                scene = PROVIDER_SCENE,
                directorPrompt = PROVIDER_PROMPT,
            ),
            provenance = ProviderAnalysisProvenance(
                providerId = PROVIDER_ID,
                providerType = ProviderType.ON_DEVICE,
                modelId = MODEL_ID,
                modelRevision = MODEL_REVISION,
                modelArtifactSha256 = "a".repeat(64),
                runtimeId = RUNTIME_ID,
                startedAtEpochMillis = 100L,
                completedAtEpochMillis = 125L,
            ),
        )
        assertNotNull(
            repository.persistAnalysis(
                ReferenceAnalysisRequest(imported.photo.id),
                readyResult,
                attempt,
            ),
        )

        val ready = requireNotNull(repository.activeRecord(imported.photo.id))
        assertEquals(PhotoAnalysisStatus.READY, ready.analysisStatus)
        assertEquals(PROVIDER_ID, ready.analysisProvenance?.providerId)
        assertNull(ready.knowledgeBundleProvenance)

        val summaryRequest = ProjectSummaryRequest(
            projectId = ready.projectId,
            readyItems = listOf(ReadySummaryInput(ready.photo.id, ready.bundle)),
            failedCount = 0,
        )
        repository.persistProjectSummary(
            PersistedProjectSummary(
                projectId = ready.projectId,
                status = ProjectSummaryStatus.SUCCESS,
                readyCount = 1,
                failedCount = 0,
                inputDigest = summaryRequest.inputDigest,
                summaryVersion = "p23d-synthetic-summary-v1",
                modelId = SUMMARY_MODEL_ID,
                modelRevision = "synthetic-summary-revision",
                modelArtifactSha256 = "b".repeat(64),
                runtimeId = "p23d-summary-runtime",
                commonSceneDirection = PROVIDER_SCENE,
                recommendedPrimaryReference = ready.photo.id,
                photographerActionSummary = "Synthetic durable-state summary.",
                updatedAtEpochMillis = 200L,
            ),
        )
        assertTrue(repository.setPrimaryReference(ready.projectId, ready.photo.id))
        preferences.saveLastActiveReferenceId(ready.photo.id)

        val persistedSummary = repository.projectSummary(ready.projectId)
        assertNotNull(persistedSummary)
        assertEquals(SUMMARY_MODEL_ID, persistedSummary?.modelId)
        assertEquals(summaryRequest.inputDigest, persistedSummary?.inputDigest)
        assertEquals(ready.photo.id, repository.project(ready.projectId)?.primaryReferenceId)
    }

    @Test
    fun verifyProviderReadySummaryState_afterExternalOsProcessRestart() = runBlocking {
        val preferences = ReferenceLibraryPreferences(context)
        val referenceId = requireNotNull(preferences.lastActiveReferenceId()) {
            "P23D_PREPARE_STATE_MISSING"
        }

        // Exercise the real startup reconciliation path in the restarted process before reading.
        ActivityScenario.launch(MainActivity::class.java).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }

        val repository = ReferenceRepository.create(context)
        val ready = requireNotNull(repository.activeRecord(referenceId))
        assertEquals(PhotoAnalysisStatus.READY, ready.analysisStatus)
        assertEquals("本机 VLM", ready.photo.sourceLabel)
        assertEquals(PROVIDER_SCENE, ready.bundle.scene)
        assertEquals(PROVIDER_PROMPT, ready.bundle.directorPrompt)
        assertNull(ready.knowledgeBundleProvenance)
        val provenance = requireNotNull(ready.analysisProvenance)
        assertEquals(PROVIDER_ID, provenance.providerId)
        assertEquals(ProviderType.ON_DEVICE, provenance.providerType)
        assertEquals(MODEL_ID, provenance.modelId)
        assertEquals(MODEL_REVISION, provenance.modelRevision)
        assertEquals(RUNTIME_ID, provenance.runtimeId)
        assertEquals("a".repeat(64), provenance.modelArtifactSha256)

        val expectedDigest = ProjectSummaryRequest(
            projectId = ready.projectId,
            readyItems = listOf(ReadySummaryInput(ready.photo.id, ready.bundle)),
            failedCount = 0,
        ).inputDigest
        val summary = requireNotNull(repository.projectSummary(ready.projectId))
        assertEquals(ProjectSummaryStatus.SUCCESS, summary.status)
        assertEquals(1, summary.readyCount)
        assertEquals(0, summary.failedCount)
        assertEquals(expectedDigest, summary.inputDigest)
        assertEquals(SUMMARY_MODEL_ID, summary.modelId)
        assertEquals(ready.photo.id, summary.recommendedPrimaryReference)
        assertEquals(SUMMARY_MODEL_ID, repository.projectSummaryForProject(ready.projectId).first()?.modelId)
        assertEquals(ready.photo.id, repository.project(ready.projectId)?.primaryReferenceId)
        assertEquals(ready.photo.id, preferences.lastActiveReferenceId())
    }

    @Test
    fun cleanupDurableReadyFixture() = runBlocking {
        resetFixture()
        val repository = ReferenceRepository.create(context)
        assertTrue(repository.activeRecordsForProject(LEGACY_PROJECT_ID).first().isEmpty())
        assertNull(repository.projectSummary(LEGACY_PROJECT_ID))
        assertNull(ReferenceLibraryPreferences(context).lastActiveReferenceId())
    }

    private suspend fun resetFixture() {
        val repository = ReferenceRepository.create(context)
        repository.clearAll()
        repository.invalidateProjectSummary(LEGACY_PROJECT_ID)
        repository.setPrimaryReference(LEGACY_PROJECT_ID, null)
        ReferenceLibraryPreferences(context).clearLastActiveReferenceId()
    }

    private companion object {
        const val PROVIDER_ID = "p23d-synthetic-provider"
        const val MODEL_ID = "p23d-synthetic-model"
        const val MODEL_REVISION = "p23d-synthetic-revision"
        const val RUNTIME_ID = "p23d-synthetic-runtime"
        const val SUMMARY_MODEL_ID = "p23d-synthetic-summary-model"
        const val PROVIDER_SCENE = "P23D synthetic durable portrait scene"
        const val PROVIDER_PROMPT = "Keep the synthetic subject aligned with the tested light direction."
    }
}
