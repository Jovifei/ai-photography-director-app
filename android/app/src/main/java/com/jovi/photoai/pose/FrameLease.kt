package com.jovi.photoai.pose

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The result of one idempotent attempt to release a frame resource. */
enum class FrameReleaseStatus {
    RELEASED,
    ALREADY_RELEASED,
    RELEASE_FAILED,
}

data class FrameReleaseResult(
    val status: FrameReleaseStatus,
    val exceptionClassName: String? = null,
)

/**
 * Single-owner frame resource contract. [releaseOnce] is terminal: even when the
 * underlying release throws, the lease is closed and a retry is never attempted.
 * [close] delegates to [releaseOnce] and deliberately does not rethrow release
 * failures; callers that need the outcome must call [releaseOnce] directly.
 */
interface FrameLease<out T> : AutoCloseable {
    val value: T
    val isClosed: Boolean

    fun releaseOnce(): FrameReleaseResult

    override fun close() {
        releaseOnce()
    }
}

class CloseOnceFrameLease<T>(
    override val value: T,
    private val release: (T) -> Unit,
) : FrameLease<T> {
    private val terminal = AtomicBoolean(false)
    private val closeAttempts = AtomicInteger(0)
    private val successfulReleases = AtomicInteger(0)
    private val alreadyReleased = AtomicInteger(0)
    private val releaseFailures = AtomicInteger(0)

    override val isClosed: Boolean
        get() = terminal.get()

    val closeAttemptCount: Int
        get() = closeAttempts.get()

    val successfulReleaseCount: Int
        get() = successfulReleases.get()

    val alreadyReleasedCount: Int
        get() = alreadyReleased.get()

    val releaseFailureCount: Int
        get() = releaseFailures.get()

    /** Compatibility aliases retained for existing AA0 tests. */
    val closeCallCount: Int
        get() = closeAttemptCount

    val releaseCount: Int
        get() = successfulReleaseCount

    override fun releaseOnce(): FrameReleaseResult {
        closeAttempts.incrementAndGet()
        if (!terminal.compareAndSet(false, true)) {
            alreadyReleased.incrementAndGet()
            return FrameReleaseResult(FrameReleaseStatus.ALREADY_RELEASED)
        }
        return try {
            release(value)
            successfulReleases.incrementAndGet()
            FrameReleaseResult(FrameReleaseStatus.RELEASED)
        } catch (throwable: Throwable) {
            releaseFailures.incrementAndGet()
            FrameReleaseResult(
                status = FrameReleaseStatus.RELEASE_FAILED,
                exceptionClassName = throwable::class.qualifiedName ?: throwable::class.simpleName,
            )
        }
    }
}
