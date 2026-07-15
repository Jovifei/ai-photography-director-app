package com.jovi.photoai.domain.model

/** A compact, presentation-neutral analysis of one reference photo. */
data class PhotoAnalysis(
    val referencePhotoId: String,
    val summary: String,
    val environment: String,
    val composition: String,
    val lighting: String,
    val pose: String,
    val story: String,
    val colorGrading: String,
    val onsitePlan: String,
    val confidence: Float,
) {
    init {
        require(referencePhotoId.isNotBlank()) { "Reference photo id must not be blank" }
        require(
            listOf(summary, environment, composition, lighting, pose, story, colorGrading, onsitePlan)
                .all(String::isNotBlank),
        ) { "Every analysis module must contain presentation content" }
        require(confidence in 0f..1f) { "Analysis confidence must be between 0 and 1" }
    }
}
