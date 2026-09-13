package com.jovi.photoai.data.reference

/** Internal durable fencing identity; never a Provider/Bundle field or a media locator. */
internal data class AnalysisAttempt(val referenceId: String, val attemptId: String) {
    init { require(referenceId.isNotBlank() && attemptId.isNotBlank()) }
}

/** Call only with a fresh row read inside the same Room transaction as the write. */
internal object AnalysisAttemptPolicy {
    fun canClaim(status: String, hasKnowledge: Boolean, currentAttemptId: String?): Boolean =
        !hasKnowledge && currentAttemptId == null &&
            status in setOf("IMPORTED", "FAILED", "UNAVAILABLE", "CANCELLED")

    fun owns(
        status: String,
        requiredStatus: String,
        hasKnowledge: Boolean,
        currentAttemptId: String?,
        attemptId: String,
    ): Boolean = !hasKnowledge && attemptId.isNotBlank() &&
        status == requiredStatus && currentAttemptId == attemptId
}
