package com.jovi.photoai.data.reference

/**
 * Main-thread-confined operation gate. Tickets make late completions harmless even when a
 * document provider cannot promptly cancel blocking I/O. Never reset an in-flight Room apply:
 * cancellation of a UI operation is not proof that a database transaction rolled back.
 * This gate carries no URI, photo, raw JSON, or durable state.
 */
internal class KnowledgeBundleImportSession {
    private enum class Phase { IDLE, READING, APPLYING }
    private var phase = Phase.IDLE
    private var generation = 0L

    fun beginRead(): Long? {
        if (phase == Phase.APPLYING) return null
        phase = Phase.READING
        return ++generation
    }

    fun finishRead(ticket: Long): Boolean {
        if (ticket != generation || phase != Phase.READING) return false
        phase = Phase.IDLE
        return true
    }

    fun beginApply(): Long? {
        if (phase != Phase.IDLE) return null
        phase = Phase.APPLYING
        return ++generation
    }

    fun finishApply(ticket: Long): Boolean {
        if (ticket != generation || phase != Phase.APPLYING) return false
        phase = Phase.IDLE
        return true
    }

    fun reset(): Boolean {
        if (phase == Phase.APPLYING) return false
        generation++
        phase = Phase.IDLE
        return true
    }
}

/** Complete means an exact, nonempty, one-to-one mapping; a non-null value is not sufficient. */
internal fun isCompleteKnowledgeBundleMapping(producerIds: List<String>, bindings: Map<String, String>): Boolean =
    producerIds.isNotEmpty() && producerIds.distinct().size == producerIds.size &&
        bindings.keys == producerIds.toSet() && bindings.values.all { it.isNotBlank() } &&
        bindings.values.toSet().size == bindings.size

/** Tapping a selected target unbinds it. Never silently steal another item's confirmed target. */
internal fun toggleKnowledgeBundleBinding(
    producerIds: List<String>,
    bindings: Map<String, String>,
    producerId: String,
    localId: String,
): Map<String, String> {
    if (producerId !in producerIds || localId.isBlank()) return bindings
    if (bindings[producerId] == localId) return bindings - producerId
    if (bindings.any { (producer, local) -> producer != producerId && local == localId }) return bindings
    return bindings + (producerId to localId)
}
