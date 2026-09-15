package com.jovi.photoai.data.reference

import kotlinx.coroutines.CancellationException

/** A returned value is an acknowledgement. An exception does not prove rollback. */
internal sealed interface CommitOutcome<out T> {
    data class Acknowledged<T>(val value: T) : CommitOutcome<T>
    object Unknown : CommitOutcome<Nothing>
}

/** Includes transaction finalization, not just the body. Never consumes cancellation. */
internal suspend fun <T> observeCommitOutcome(operation: suspend () -> T): CommitOutcome<T> = try {
    CommitOutcome.Acknowledged(operation())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    CommitOutcome.Unknown
}
