package com.jovi.photoai.pose.metrics

import com.jovi.photoai.pose.FrameReleaseResult
import com.jovi.photoai.pose.FrameReleaseStatus
import java.util.ArrayDeque
import kotlin.math.ceil

enum class PoseMetricOutcome {
    SUCCESS,
    NO_PERSON,
    MULTI_PERSON_UNKNOWN,
    ERROR,
    STALE_DROP,
    CANCELLED,
}

data class PoseMetricsSnapshot(
    val count: Long,
    val success: Long,
    val noPerson: Long,
    val multiPersonUnknown: Long,
    val error: Long,
    val staleDrop: Long,
    val cancelled: Long,
    val latencyP50Ms: Double?,
    val latencyP95Ms: Double?,
    val latencySampleCount: Long,
    val latencyWindowCapacity: Int,
    val latencyTotalObserved: Long,
    val timedSuccessCount: Long,
    val effectiveFps: Double?,
    val errorRate: Double,
    val frameReleaseSuccessCount: Long,
    val frameAlreadyReleasedCount: Long,
    val frameReleaseFailureCount: Long,
    val frameCloseAttemptCount: Long,
    val schedulerRejectionCount: Long,
    val infrastructureErrorCount: Long,
    val droppedFrameCount: Long,
) {
    /** Historical names are retained as read-only aliases, with clarified semantics. */
    val frameCloseCount: Long get() = frameReleaseSuccessCount
    val doubleCloseCount: Long get() = frameAlreadyReleasedCount
}

/**
 * Thread-safe bounded aggregate. Only the newest [latencyWindowCapacity] valid
 * latency samples are retained; counters remain total observations and never
 * retain frame ids, points, paths, or raw frames.
 */
class PoseMetricsAccumulator(
    val latencyWindowCapacity: Int = DEFAULT_LATENCY_WINDOW_CAPACITY,
) {
    init {
        require(latencyWindowCapacity > 0) { "latencyWindowCapacity must be positive" }
    }

    private val lock = Any()
    private var count = 0L
    private var success = 0L
    private var noPerson = 0L
    private var multiPersonUnknown = 0L
    private var error = 0L
    private var staleDrop = 0L
    private var cancelled = 0L
    private var frameReleaseSuccessCount = 0L
    private var frameAlreadyReleasedCount = 0L
    private var frameReleaseFailureCount = 0L
    private var frameCloseAttemptCount = 0L
    private var schedulerRejectionCount = 0L
    private var infrastructureErrorCount = 0L
    private var droppedFrameCount = 0L
    private var latencyTotalObserved = 0L
    private val latencyWindow = ArrayDeque<Double>(latencyWindowCapacity)
    private var firstSuccessCompletionNs: Long? = null
    private var lastSuccessCompletionNs: Long? = null
    private var timedSuccessCount = 0L

    /** [acceptedAtNs] is validated for clock-domain consistency but not used for FPS. */
    fun recordSubmitted(acceptedAtNs: Long) = synchronized(lock) {
        require(acceptedAtNs >= 0L) { "acceptedAtNs must be non-negative" }
        count++
    }

    /** [completionTimestampNs] must come from the same injected monotonic clock as acceptance. */
    fun recordOutcome(
        outcome: PoseMetricOutcome,
        latencyMs: Double? = null,
        timestampNs: Long? = null,
    ) = synchronized(lock) {
        when (outcome) {
            PoseMetricOutcome.SUCCESS -> {
                val completionNs = requireNotNull(timestampNs) {
                    "SUCCESS requires a non-null completion timestamp"
                }
                require(completionNs >= 0L) {
                    "SUCCESS completion timestamp must be non-negative"
                }
                require(lastSuccessCompletionNs == null || completionNs >= lastSuccessCompletionNs!!) {
                    "SUCCESS completion timestamps must be non-decreasing"
                }
                success++
                timedSuccessCount++
                firstSuccessCompletionNs = firstSuccessCompletionNs ?: completionNs
                lastSuccessCompletionNs = completionNs
            }
            PoseMetricOutcome.NO_PERSON -> noPerson++
            PoseMetricOutcome.MULTI_PERSON_UNKNOWN -> multiPersonUnknown++
            PoseMetricOutcome.ERROR -> error++
            PoseMetricOutcome.STALE_DROP -> staleDrop++
            PoseMetricOutcome.CANCELLED -> cancelled++
        }
        if (latencyMs != null && latencyMs.isFinite() && latencyMs >= 0.0) {
            latencyTotalObserved++
            if (latencyWindow.size == latencyWindowCapacity) latencyWindow.removeFirst()
            latencyWindow.addLast(latencyMs)
        }
    }

    fun recordFrameRelease(result: FrameReleaseResult) = synchronized(lock) {
        frameCloseAttemptCount++
        when (result.status) {
            FrameReleaseStatus.RELEASED -> frameReleaseSuccessCount++
            FrameReleaseStatus.ALREADY_RELEASED -> frameAlreadyReleasedCount++
            FrameReleaseStatus.RELEASE_FAILED -> frameReleaseFailureCount++
        }
    }

    fun recordSchedulerRejection() = synchronized(lock) { schedulerRejectionCount++ }

    fun recordInfrastructureError() = synchronized(lock) { infrastructureErrorCount++ }

    fun recordFrameClosed() = recordFrameRelease(FrameReleaseResult(FrameReleaseStatus.RELEASED))

    fun recordDoubleClose() = recordFrameRelease(FrameReleaseResult(FrameReleaseStatus.ALREADY_RELEASED))

    fun recordDroppedFrame() = synchronized(lock) { droppedFrameCount++ }

    fun snapshot(): PoseMetricsSnapshot = synchronized(lock) {
        PoseMetricsSnapshot(
            count = count,
            success = success,
            noPerson = noPerson,
            multiPersonUnknown = multiPersonUnknown,
            error = error,
            staleDrop = staleDrop,
            cancelled = cancelled,
            latencyP50Ms = percentile(0.50),
            latencyP95Ms = percentile(0.95),
            latencySampleCount = latencyWindow.size.toLong(),
            latencyWindowCapacity = latencyWindowCapacity,
            latencyTotalObserved = latencyTotalObserved,
            timedSuccessCount = timedSuccessCount,
            effectiveFps = effectiveFps(),
            errorRate = if (count == 0L) 0.0 else error.toDouble() / count.toDouble(),
            frameReleaseSuccessCount = frameReleaseSuccessCount,
            frameAlreadyReleasedCount = frameAlreadyReleasedCount,
            frameReleaseFailureCount = frameReleaseFailureCount,
            frameCloseAttemptCount = frameCloseAttemptCount,
            schedulerRejectionCount = schedulerRejectionCount,
            infrastructureErrorCount = infrastructureErrorCount,
            droppedFrameCount = droppedFrameCount,
        )
    }

    private fun percentile(fraction: Double): Double? {
        if (latencyWindow.isEmpty()) return null
        val sorted = latencyWindow.toList().sorted()
        val rank = ceil(fraction * sorted.size.toDouble()).toInt().coerceAtLeast(1)
        return sorted[rank - 1]
    }

    private fun effectiveFps(): Double? {
        val first = firstSuccessCompletionNs ?: return null
        val last = lastSuccessCompletionNs ?: return null
        val elapsedSeconds = (last - first).toDouble() / 1_000_000_000.0
        return if (timedSuccessCount < 2L || elapsedSeconds <= 0.0) {
            null
        } else {
            (timedSuccessCount - 1L).toDouble() / elapsedSeconds
        }
    }

    private companion object {
        const val DEFAULT_LATENCY_WINDOW_CAPACITY = 2_048
    }
}
