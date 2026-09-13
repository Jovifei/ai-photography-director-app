package com.jovi.photoai.data.reference

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class PhotoAnalysisRunSummary(
    val requestedCount: Int,
    val readyCount: Int,
    val failedCount: Int,
    val unavailableCount: Int,
    val cancelled: Boolean,
)

/** Only the current durable attempt may advance a row or persist a response. */
internal class PhotoAnalysisCoordinator(
    private val repository: ReferenceRepository,
    private val provider: ReferenceAnalysisProvider,
    private val summaryProvider: ProjectSummaryProvider = UnconfiguredProjectSummaryProvider,
) {
    suspend fun analyzeProject(projectId: String): PhotoAnalysisRunSummary = withContext(Dispatchers.IO) {
        val records = repository.activeRecordsForProject(projectId).first()
        var ready = 0
        var failed = 0
        var unavailable = 0
        for (record in records) {
            currentCoroutineContext().ensureActive()
            // No pre-queued array: every claim observes the latest committed row.
            val attempt = AnalysisAttempt(record.photo.id, UUID.randomUUID().toString())
            try {
                // Keep the token before the suspend call: even a lost claim acknowledgement
                // can be cleaned up by this attempt in finally.
                if (!repository.markAnalysisQueued(attempt)) continue
                if (!repository.markAnalysisRunning(attempt)) continue
                val request = ReferenceAnalysisRequest(record.photo.id)
                val result = provider.analyze(request)
                currentCoroutineContext().ensureActive()
                when (repository.persistAnalysis(request, result, attempt)) {
                    is ProviderAnalysisResult.Ready -> ready++
                    is ProviderAnalysisResult.Failed -> failed++
                    is ProviderAnalysisResult.Unavailable -> unavailable++
                    null -> Unit // deleted, cancelled or superseded; do not fabricate success
                }
            } finally {
                withContext(NonCancellable) { repository.cancelAnalysisAttempt(attempt) }
            }
        }
        val current = repository.activeRecordsForProject(projectId).first()
        // Offline Bundle v1 does not authorize a Provider semantic summary.
        val readyInputs = current.filter {
            it.analysisStatus == PhotoAnalysisStatus.READY && it.analysisProvenance != null &&
                it.knowledgeBundleProvenance == null
        }.map { ReadySummaryInput(it.photo.id, it.bundle) }
        if (readyInputs.isNotEmpty()) {
            val failedCount = current.count { it.analysisStatus == PhotoAnalysisStatus.FAILED || it.analysisStatus == PhotoAnalysisStatus.UNAVAILABLE }
            val summaryRequest = ProjectSummaryRequest(projectId, readyInputs, failedCount)
            when (val summary = summaryProvider.summarize(summaryRequest)) {
                is ProjectSummaryResult.Ready -> repository.persistProjectSummary(summary.summary)
                is ProjectSummaryResult.Failed -> repository.persistProjectSummary(
                    PersistedProjectSummary(
                        projectId = projectId, status = ProjectSummaryStatus.UNAVAILABLE,
                        readyCount = readyInputs.size, failedCount = failedCount,
                        inputDigest = summaryRequest.inputDigest, updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        } else {
            repository.invalidateProjectSummary(projectId)
        }
        PhotoAnalysisRunSummary(records.size, ready, failed, unavailable, cancelled = false)
    }
}
