package com.jovi.photoai.data.reference

import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle

/**
 * Durable, app-private reference metadata. The image key is a filename only: a Photo Picker Uri,
 * original filename, EXIF data, source path, and any cloud identifier are deliberately excluded.
 */
data class ReferenceRecord(
    val photo: ReferencePhoto,
    val bundle: ReferenceBundle,
    val imageFileName: String,
    val createdAtEpochMillis: Long,
)

internal fun ReferenceRecord.requireSafeImageFileName() {
    require(imageFileName.matches(Regex("^[a-zA-Z0-9_-]+\\.jpg$"))) {
        "Reference image filename must be a private JPEG basename"
    }
}
