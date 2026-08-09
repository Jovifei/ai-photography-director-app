package com.jovi.photoai.data.reference

import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle

internal const val MAX_PROJECT_PHOTOS = 20
internal const val LEGACY_PROJECT_ID = "legacy-project-v1"

/** Example guidance is explicitly not a real-analysis success. */
enum class PhotoAnalysisStatus {
    EXAMPLE_GUIDANCE,
    IMPORTED,
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELLED,
    UNAVAILABLE,
}

/** Project metadata deliberately contains no source-media or device identity. */
internal data class PhotographyProject(
    val id: String,
    val title: String,
    val primaryReferenceId: String?,
    val failedImportCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(failedImportCount >= 0) { "Failed import count must not be negative" }
    }
}

/**
 * Durable, app-private reference metadata. The image key is a filename only: a Photo Picker Uri,
 * original filename, EXIF data, source path, and any cloud identifier are deliberately excluded.
 */
internal data class ReferenceRecord(
    val photo: ReferencePhoto,
    val bundle: ReferenceBundle,
    val imageFileName: String,
    val createdAtEpochMillis: Long,
    val projectId: String = LEGACY_PROJECT_ID,
    val ordinal: Int = 0,
    val analysisStatus: PhotoAnalysisStatus = PhotoAnalysisStatus.EXAMPLE_GUIDANCE,
    val safeAnalysisErrorCode: String? = null,
    val analysisProvenance: ProviderAnalysisProvenance? = null,
)

internal fun ReferenceRecord.requireSafeImageFileName() {
    require(imageFileName.matches(Regex("^[a-zA-Z0-9_-]+\\.jpg$"))) {
        "Reference image filename must be a private JPEG basename"
    }
}
