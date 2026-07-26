package com.jovi.photoai.reference

/** The four concise recommendation groups shown before Camera Director. */
data class DirectorCard(
    val environment: String,
    val subject: String,
    val emotion: String,
    val camera: String,
) {
    init {
        require(listOf(environment, subject, emotion, camera).all(String::isNotBlank)) {
            "Director card fields must all be non-blank"
        }
    }
}

data class ReferenceGuidanceItem(
    val title: String,
    val detail: String,
) {
    init {
        require(title.isNotBlank()) { "Reference guidance title must not be blank" }
        require(detail.isNotBlank()) { "Reference guidance detail must not be blank" }
    }
}

/** Read-only presentation model for Camera Director; it does not contain live Pose data. */
data class CameraDirectorGuidance(
    val referenceTitle: String,
    val sourceLabel: String,
    val centerHint: String,
    val environment: List<ReferenceGuidanceItem>,
    val subject: List<ReferenceGuidanceItem>,
) {
    init {
        require(referenceTitle.isNotBlank()) { "Reference title must not be blank" }
        require(sourceLabel.isNotBlank()) { "Source label must not be blank" }
        require(centerHint.isNotBlank()) { "Center hint must not be blank" }
        require(environment.isNotEmpty()) { "Environment guidance must not be empty" }
        require(subject.isNotEmpty()) { "Subject guidance must not be empty" }
    }
}
