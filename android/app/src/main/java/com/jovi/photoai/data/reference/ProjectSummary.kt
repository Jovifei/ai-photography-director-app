package com.jovi.photoai.data.reference

import org.json.JSONArray
import org.json.JSONObject

internal enum class ProjectSummaryStatus { SUCCESS, UNAVAILABLE, INVALIDATED }

internal data class StrongestReference(
    val referenceId: String,
    val reason: String,
)

internal data class PersistedProjectSummary(
    val projectId: String,
    val status: ProjectSummaryStatus,
    val readyCount: Int,
    val failedCount: Int,
    val inputDigest: String,
    val summaryVersion: String? = null,
    val modelId: String? = null,
    val modelRevision: String? = null,
    val modelArtifactSha256: String? = null,
    val runtimeId: String? = null,
    val commonSceneDirection: String? = null,
    val commonLightingDirection: String? = null,
    val commonCompositionDirection: String? = null,
    val commonSubjectDirection: String? = null,
    val differences: List<String> = emptyList(),
    val strongestReferences: List<StrongestReference> = emptyList(),
    val recommendedPrimaryReference: String? = null,
    val photographerActionSummary: String? = null,
    val updatedAtEpochMillis: Long = 0L,
)

internal fun PersistedProjectSummary.toEntity(): ProjectSummaryEntity = ProjectSummaryEntity(
    projectId = projectId,
    status = status.name,
    readyCount = readyCount,
    failedCount = failedCount,
    inputDigest = inputDigest,
    summaryVersion = summaryVersion,
    modelId = modelId,
    modelRevision = modelRevision,
    modelArtifactSha256 = modelArtifactSha256,
    runtimeId = runtimeId,
    commonSceneDirection = commonSceneDirection,
    commonLightingDirection = commonLightingDirection,
    commonCompositionDirection = commonCompositionDirection,
    commonSubjectDirection = commonSubjectDirection,
    differences = JSONArray(differences).toString(),
    strongestReferences = JSONArray(strongestReferences.map { JSONObject().put("reference_id", it.referenceId).put("reason", it.reason) }).toString(),
    recommendedPrimaryReference = recommendedPrimaryReference,
    photographerActionSummary = photographerActionSummary,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun ProjectSummaryEntity.toPersistedSummary(): PersistedProjectSummary = PersistedProjectSummary(
    projectId = projectId,
    status = runCatching { ProjectSummaryStatus.valueOf(status) }.getOrDefault(ProjectSummaryStatus.UNAVAILABLE),
    readyCount = readyCount,
    failedCount = failedCount,
    inputDigest = inputDigest,
    summaryVersion = summaryVersion,
    modelId = modelId,
    modelRevision = modelRevision,
    modelArtifactSha256 = modelArtifactSha256,
    runtimeId = runtimeId,
    commonSceneDirection = commonSceneDirection,
    commonLightingDirection = commonLightingDirection,
    commonCompositionDirection = commonCompositionDirection,
    commonSubjectDirection = commonSubjectDirection,
    differences = runCatching {
        val values = JSONArray(differences.orEmpty())
        (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList()),
    strongestReferences = runCatching {
        val values = JSONArray(strongestReferences.orEmpty())
        (0 until values.length()).mapNotNull { index ->
            values.optJSONObject(index)?.let { StrongestReference(it.optString("reference_id"), it.optString("reason")) }
        }
    }.getOrDefault(emptyList()),
    recommendedPrimaryReference = recommendedPrimaryReference,
    photographerActionSummary = photographerActionSummary,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

/** A deterministic, metadata-only view. It never starts a new image-analysis operation. */
internal data class ProjectSummarySnapshot(
    val importedCount: Int,
    val providerReadyCount: Int,
    val knowledgeBundleReadyCount: Int,
    val exampleGuidanceCount: Int,
    val unavailableCount: Int,
    val failedImportCount: Int,
    val sharedDirections: List<String>,
    val differingDirectionLabels: List<String>,
    val shootFirstPlan: String?,
) {
    val guidanceReadyCount: Int
        get() = providerReadyCount + knowledgeBundleReadyCount

    val excludedCount: Int
        get() = (importedCount - guidanceReadyCount).coerceAtLeast(0) + failedImportCount
}

internal fun projectSummaryOf(
    records: List<ReferenceRecord>,
    failedImportCount: Int,
    primaryReferenceId: String? = null,
): ProjectSummarySnapshot {
    val readyRecords = records.filter {
        it.analysisStatus == PhotoAnalysisStatus.READY &&
            (it.analysisProvenance != null || it.knowledgeBundleProvenance != null)
    }
    val directions = listOf(
        "场景" to { record: ReferenceRecord -> record.bundle.scene },
        "光线" to { record: ReferenceRecord -> record.bundle.lighting },
        "构图" to { record: ReferenceRecord -> record.bundle.composition },
        "主体意图" to { record: ReferenceRecord -> record.bundle.subjectIntent },
        "情绪" to { record: ReferenceRecord -> record.bundle.emotion },
        "机位" to { record: ReferenceRecord -> record.bundle.cameraPosition },
    )
    val sharedDirections = directions.mapNotNull { (label, valueOf) ->
        val values = readyRecords.map(valueOf)
        values.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= 2 }
            ?.let { "$label：${it.key}" }
    }
    val differingDirectionLabels = directions.mapNotNull { (label, valueOf) ->
        label.takeIf { readyRecords.map(valueOf).distinct().size > 1 }
    }
    val shootFirstPlan = readyRecords
        .firstOrNull { it.photo.id == primaryReferenceId }
        ?.bundle
        ?.directorPrompt
        ?: readyRecords.firstOrNull()?.bundle?.directorPrompt

    return ProjectSummarySnapshot(
        importedCount = records.size,
        providerReadyCount = readyRecords.count { it.analysisProvenance != null },
        knowledgeBundleReadyCount = readyRecords.count { it.knowledgeBundleProvenance != null },
        exampleGuidanceCount = records.count { it.analysisStatus == PhotoAnalysisStatus.EXAMPLE_GUIDANCE },
        unavailableCount = records.count {
        it.analysisStatus == PhotoAnalysisStatus.FAILED ||
            it.analysisStatus == PhotoAnalysisStatus.UNAVAILABLE
    },
        failedImportCount = failedImportCount,
        sharedDirections = sharedDirections,
        differingDirectionLabels = differingDirectionLabels,
        shootFirstPlan = shootFirstPlan,
    )
}
