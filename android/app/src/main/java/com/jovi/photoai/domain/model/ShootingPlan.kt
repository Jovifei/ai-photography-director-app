package com.jovi.photoai.domain.model

data class ShootingPlan(
    val id: String,
    val title: String,
    val objective: String,
    val referencePhotoId: String,
    val analysis: PhotoAnalysis,
    val guidance: List<GuidanceItem>,
    val defaultOverlayMode: OverlayMode,
) {
    init {
        require(id.isNotBlank()) { "Shooting plan id must not be blank" }
        require(title.isNotBlank()) { "Shooting plan title must not be blank" }
        require(referencePhotoId == analysis.referencePhotoId) {
            "Shooting plan and analysis must refer to the same photo"
        }
        require(guidance.map(GuidanceItem::id).distinct().size == guidance.size) {
            "Guidance ids must be unique within a shooting plan"
        }
    }
}
