package com.jovi.photoai.domain.model

/**
 * Android-free description of a reference photo.
 *
 * [imageAssetKey] is an opaque lookup key. It deliberately is not an Android Uri so domain
 * state and JVM tests do not depend on framework types or persist access to private media.
 */
data class ReferencePhoto(
    val id: String,
    val title: String,
    val description: String,
    val sourceLabel: String,
    val imageAssetKey: String,
    val aspectRatio: Float,
) {
    init {
        require(id.isNotBlank()) { "Reference photo id must not be blank" }
        require(title.isNotBlank()) { "Reference photo title must not be blank" }
        require(imageAssetKey.isNotBlank()) { "Reference photo asset key must not be blank" }
        require(aspectRatio > 0f && aspectRatio.isFinite()) {
            "Reference photo aspect ratio must be finite and positive"
        }
    }
}
