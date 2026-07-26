package com.jovi.photoai.reference

/** Presentation-neutral result of the Phase 1 Reference → Director analysis contract. */
data class ReferenceAnalysis(
    val scene: String,
    val backgroundValue: String,
    val lighting: String,
    val composition: String,
    val subjectIntent: String,
    val emotion: String,
    val cameraSuggestion: String,
) {
    init {
        require(
            listOf(
                scene,
                backgroundValue,
                lighting,
                composition,
                subjectIntent,
                emotion,
                cameraSuggestion,
            ).all(String::isNotBlank),
        ) { "Reference analysis fields must all be non-blank" }
    }
}
