package com.jovi.photoai.data.reference

import com.jovi.photoai.reference.ReferenceBundle
import java.security.MessageDigest

internal data class ReadySummaryInput(
    val referenceId: String,
    val bundle: ReferenceBundle,
)

internal data class ProjectSummaryRequest(
    val projectId: String,
    val readyItems: List<ReadySummaryInput>,
    val failedCount: Int,
    val inputDigest: String = readyItems.digest(),
) {
    init {
        require(projectId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
        require(readyItems.isNotEmpty() && readyItems.size <= MAX_PROJECT_PHOTOS)
        require(failedCount >= 0)
    }
}

internal sealed interface ProjectSummaryResult {
    data class Ready(
        val summary: PersistedProjectSummary,
    ) : ProjectSummaryResult

    data class Failed(val errorCode: SafeProviderErrorCode) : ProjectSummaryResult
}

internal interface ProjectSummaryProvider {
    suspend fun summarize(request: ProjectSummaryRequest): ProjectSummaryResult
}

internal object UnconfiguredProjectSummaryProvider : ProjectSummaryProvider {
    override suspend fun summarize(request: ProjectSummaryRequest): ProjectSummaryResult =
        ProjectSummaryResult.Failed(SafeProviderErrorCode.PROVIDER_NOT_CONFIGURED)
}

internal fun List<ReadySummaryInput>.digest(): String {
    val canonical = sortedBy { it.referenceId }.joinToString("\n") { item ->
        listOf(
            item.referenceId,
            item.bundle.scene,
            item.bundle.backgroundStory,
            item.bundle.lighting,
            item.bundle.composition,
            item.bundle.subjectIntent,
            item.bundle.emotion,
            item.bundle.poseTemplate,
            item.bundle.cameraPosition,
            item.bundle.directorPrompt,
            item.bundle.version,
        ).joinToString("\u001f")
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
