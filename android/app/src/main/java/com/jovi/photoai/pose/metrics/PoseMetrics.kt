package com.jovi.photoai.pose.metrics

import kotlin.math.ceil

enum class PoseMetricOutcome {
    SUCCESS,
    NO_PERSON,
    ERROR,
    STALE_DROP,
    CANCELLED,
}

data class PoseMetricsSnapshot(
    val count: Long,
    val success: Long,
    val noPerson: Long,
    val error: Long,
    val staleDrop: Long,
    val cancelled: Long,
    val latencyP50Ms: Double?,
    val latencyP95Ms: Double?,
    val effectiveFps: Double?,
    val errorRate: Double,
    val frameCloseCount: Long,
    val doubleCloseCount: Long,
    val droppedFrameCount: Long,
)

/** Bounded, privacy-safe aggregate; it never stores frame ids, points, paths, or raw frames. */
class PoseMetricsAccumulator {
    private val lock = Any()
    private var count = 0L
    private var success = 0L
    private var noPerson = 0L
    private var error = 0L
    private var staleDrop = 0L
    private var cancelled = 0L
    private var frameCloseCount = 0L
    private var doubleCloseCount = 0L
    private var droppedFrameCount = 0L
    private val latenciesMs = mutableListOf<Double>()
    private var firstTimestampNs: Long? = null
    private var lastTimestampNs: Long? = null

    fun recordSubmitted(timestampNs: Long) = synchronized(lock) {
        count++
        updateTime(timestampNs)
    }

    fun recordOutcome(outcome: PoseMetricOutcome, latencyMs: Double? = null, timestampNs: Long? = null) = synchronized(lock) {
        when (outcome) {
            PoseMetricOutcome.SUCCESS -> success++
            PoseMetricOutcome.NO_PERSON -> noPerson++
            PoseMetricOutcome.ERROR -> error++
            PoseMetricOutcome.STALE_DROP -> staleDrop++
            PoseMetricOutcome.CANCELLED -> cancelled++
        }
        if (latencyMs != null && latencyMs.isFinite() && latencyMs >= 0.0) latenciesMs += latencyMs
        if (timestampNs != null) updateTime(timestampNs)
    }

    fun recordFrameClosed() = synchronized(lock) { frameCloseCount++ }

    fun recordDoubleClose() = synchronized(lock) { doubleCloseCount++ }

    fun recordDroppedFrame() = synchronized(lock) { droppedFrameCount++ }

    fun snapshot(): PoseMetricsSnapshot = synchronized(lock) {
        PoseMetricsSnapshot(
            count = count,
            success = success,
            noPerson = noPerson,
            error = error,
            staleDrop = staleDrop,
            cancelled = cancelled,
            latencyP50Ms = percentile(0.50),
            latencyP95Ms = percentile(0.95),
            effectiveFps = effectiveFps(),
            errorRate = if (count == 0L) 0.0 else error.toDouble() / count.toDouble(),
            frameCloseCount = frameCloseCount,
            doubleCloseCount = doubleCloseCount,
            droppedFrameCount = droppedFrameCount,
        )
    }

    private fun updateTime(timestampNs: Long) {
        require(timestampNs >= 0)
        if (firstTimestampNs == null) firstTimestampNs = timestampNs
        lastTimestampNs = timestampNs
    }

    private fun percentile(fraction: Double): Double? {
        if (latenciesMs.isEmpty()) return null
        val sorted = latenciesMs.sorted()
        val rank = ceil(fraction * sorted.size.toDouble()).toInt().coerceAtLeast(1)
        return sorted[rank - 1]
    }

    private fun effectiveFps(): Double? {
        val first = firstTimestampNs ?: return null
        val last = lastTimestampNs ?: return null
        val elapsedSeconds = (last - first).toDouble() / 1_000_000_000.0
        return if (elapsedSeconds <= 0.0) null else count.toDouble() / elapsedSeconds
    }
}
