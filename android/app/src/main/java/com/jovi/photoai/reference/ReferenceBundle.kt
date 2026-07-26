package com.jovi.photoai.reference

/**
 * App-local Phase 1 Producer → Consumer draft. It is intentionally separate from the frozen
 * cross-repository shared-contract snapshot until the Pipeline owner approves a synchronized ADR.
 */
data class ReferenceBundle(
    val referenceId: String,
    val scene: String,
    val backgroundStory: String,
    val lighting: String,
    val composition: String,
    val subjectIntent: String,
    val emotion: String,
    val poseTemplate: String,
    val cameraPosition: String,
    val directorPrompt: String,
    val version: String,
) {
    init {
        require(
            listOf(
                referenceId,
                scene,
                backgroundStory,
                lighting,
                composition,
                subjectIntent,
                emotion,
                poseTemplate,
                cameraPosition,
                directorPrompt,
                version,
            ).all(String::isNotBlank),
        ) { "Reference bundle fields must all be non-blank" }
    }

    companion object {
        const val CURRENT_VERSION = "1.0"

        /** JSON field names required by the app-local draft schema. */
        val requiredSchemaFields: Set<String> = setOf(
            "reference_id",
            "scene",
            "background_story",
            "lighting",
            "composition",
            "subject_intent",
            "emotion",
            "pose_template",
            "camera_position",
            "director_prompt",
            "version",
        )
    }
}
