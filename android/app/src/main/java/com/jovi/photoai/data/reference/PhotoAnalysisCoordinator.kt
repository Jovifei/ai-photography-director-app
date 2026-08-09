package com.jovi.photoai.data.reference

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class PhotoAnalysisRunSummary(
    val requestedCount: Int,
    val readyCount: Int,
    val failedCount: Int,
    val unavailableCount: Int,
    val cancelled: Boolean,
)

/** Runs one photo at a time and persists each result before moving to the next photo. */
internal class PhotoAnalysisCoordinator(
    private val repository: ReferenceRepository,
    private val provider: ReferenceAnalysisProvider,
    private val summaryProvider: ProjectSummaryProvider = UnconfiguredProjectSummaryProvider,
) {
    suspend fun analyzeProject(projectId: String): PhotoAnalysisRunSummary = withContext(Dispatchers.IO) {
        val records = repository.activeRecordsForProject(projectId).first()
        records.filter { it.analysisStatus != PhotoAnalysisStatus.READY && it.analysisStatus != PhotoAnalysisStatus.EXAMPLE_GUIDANCE }
            .forEach { repository.markAnalysisQueued(it.photo.id) }
        var ready = 0
        var failed = 0
        var unavailable = 0
        try {
            records.filter { it.analysisStatus != PhotoAnalysisStatus.READY && it.analysisStatus != PhotoAnalysisStatus.EXAMPLE_GUIDANCE }
                .forEach { record ->
                val request = ReferenceAnalysisRequest(record.photo.id)
                repository.markAnalysisRunning(record.photo.id)
                val result = provider.analyze(request)
                val persisted = repository.persistAnalysis(request, result) ?: return@forEach
                when (persisted) {
                    is ProviderAnalysisResult.Ready -> ready += 1
                    is ProviderAnalysisResult.Failed -> failed += 1
                    is ProviderAnalysisResult.Unavailable -> unavailable += 1
                }
            }
            val current = repository.activeRecordsForProject(projectId).first()
            val readyInputs = current.filter { it.analysisStatus == PhotoAnalysisStatus.READY }.map {
                ReadySummaryInput(it.photo.id, it.bundle)
            }
            if (readyInputs.isNotEmpty()) {
                val failedCount = current.count { it.analysisStatus == PhotoAnalysisStatus.FAILED || it.analysisStatus == PhotoAnalysisStatus.UNAVAILABLE }
                val summaryRequest = ProjectSummaryRequest(projectId, readyInputs, failedCount)
                when (val summary = summaryProvider.summarize(summaryRequest)) {
                    is ProjectSummaryResult.Ready -> repository.persistProjectSummary(summary.summary)
                    is ProjectSummaryResult.Failed -> repository.persistProjectSummary(
                        PersistedProjectSummary(
                            projectId = projectId,
                            status = ProjectSummaryStatus.UNAVAILABLE,
                            readyCount = readyInputs.size,
                            failedCount = failedCount,
                            inputDigest = summaryRequest.inputDigest,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            } else {
                repository.invalidateProjectSummary(projectId)
            }
            PhotoAnalysisRunSummary(records.size, ready, failed, unavailable, cancelled = false)
        } catch (_: CancellationException) {
            repository.cancelOutstandingAnalysis(projectId)
            PhotoAnalysisRunSummary(records.size, ready, failed, unavailable, cancelled = true)
        }
    }
}
