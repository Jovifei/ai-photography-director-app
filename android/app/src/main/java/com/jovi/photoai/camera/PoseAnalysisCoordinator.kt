package com.jovi.photoai.camera

import com.jovi.photoai.domain.pose.PoseDiagnostics
import com.jovi.photoai.domain.pose.PoseEstimate
import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.CloseOnceFrameLease
import com.jovi.photoai.pose.PoseEstimating
import com.jovi.photoai.pose.PoseInputFrame
import com.jovi.photoai.pose.PoseSubmission
import com.jovi.photoai.pose.metrics.PoseMetricOutcome
import com.jovi.photoai.pose.metrics.PoseMetricsAccumulator
import com.jovi.photoai.pose.metrics.PoseMetricsSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine-neutral owner of frame leases and callback lifetime. It is deliberately
 * not wired into CameraXManager or CameraScreen in AA0-P0.
 */
class PoseAnalysisCoordinator(
    private val engine: PoseEstimating,
    private val timeoutMs: Long = 1_000L,
    private val scheduler: ScheduledExecutorService = daemonScheduler(),
    private val ownsScheduler: Boolean = true,
    private val clockNs: () -> Long = System::nanoTime,
    private val metrics: PoseMetricsAccumulator = PoseMetricsAccumulator(),
) : AutoCloseable {
    private val lock = Any()
    private val pending = mutableMapOf<Long, Pending>()
    private val closed = AtomicBoolean(false)
    private var activeGeneration = 0L

    init {
        require(timeoutMs > 0L)
    }

    val currentGeneration: Long
        get() = synchronized(lock) { activeGeneration }

    fun metricsSnapshot(): PoseMetricsSnapshot = metrics.snapshot()

    /** Returns false when the frame is stale, duplicated, or the coordinator is disposed. */
    fun submit(frame: PoseInputFrame, sink: (PoseEstimate) -> Unit): Boolean {
        val pendingFrame = Pending(
            frameId = frame.frameId,
            generation = frame.generation,
            timestampNs = frame.timestampNs,
            lease = frame.lease,
        )
        val accepted = synchronized(lock) {
            if (closed.get() || frame.generation < activeGeneration || pending.containsKey(frame.frameId)) {
                false
            } else {
                pending[frame.frameId] = pendingFrame
                metrics.recordSubmitted(frame.timestampNs)
                true
            }
        }
        if (!accepted) {
            closeLease(frame)
            metrics.recordDroppedFrame()
            if (frame.generation < currentGeneration) metrics.recordOutcome(PoseMetricOutcome.STALE_DROP)
            return false
        }

        val timeout = scheduler.schedule(
            { onTimeout(frame.frameId, frame.generation, sink) },
            timeoutMs,
            TimeUnit.MILLISECONDS,
        )
        synchronized(lock) {
            pending[frame.frameId]?.timeout = timeout
        }

        try {
            val submission = engine.submit(frame) { estimate ->
                onEngineResult(estimate, sink)
            }
            val shouldCancel = synchronized(lock) {
                val current = pending[frame.frameId]
                if (current == null || current.terminal) {
                    true
                } else {
                    current.submission = submission
                    false
                }
            }
            if (shouldCancel) submission.cancel()
        } catch (throwable: Throwable) {
            val estimate = errorEstimate(frame, "engine submit failed: ${throwable::class.simpleName}")
            finish(frame.frameId, frame.generation, PoseMetricOutcome.ERROR, estimate, sink, deliver = true)
        }
        return true
    }

    /** Invalidates all generations strictly older than [generation]. */
    fun invalidateGeneration(generation: Long) {
        require(generation >= 0L)
        val victims = synchronized(lock) {
            if (generation <= activeGeneration) {
                emptyList()
            } else {
                activeGeneration = generation
                pending.values.filter { it.generation < generation }.onEach {
                    it.terminal = true
                    pending.remove(it.frameId)
                }
            }
        }
        victims.forEach { finalize(it, PoseMetricOutcome.STALE_DROP, null, null, deliver = false) }
        runCatching { engine.invalidateGeneration(generation) }
    }

    fun cancel(frameId: Long): Boolean {
        val victim = synchronized(lock) {
            pending.remove(frameId)?.also { it.terminal = true }
        } ?: return false
        finalize(victim, PoseMetricOutcome.CANCELLED, null, null, deliver = false)
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val victims = synchronized(lock) {
            pending.values.toList().also { victims ->
                victims.forEach { it.terminal = true }
                pending.clear()
            }
        }
        victims.forEach { finalize(it, PoseMetricOutcome.CANCELLED, null, null, deliver = false) }
        runCatching { engine.close() }
        if (ownsScheduler) scheduler.shutdownNow()
    }

    fun dispose() = close()

    private fun onEngineResult(estimate: PoseEstimate, sink: (PoseEstimate) -> Unit) {
        val current = synchronized(lock) { pending[estimate.frameId] }
        if (current == null) {
            metrics.recordOutcome(PoseMetricOutcome.STALE_DROP)
            metrics.recordDroppedFrame()
            return
        }
        if (estimate.generation != current.generation || estimate.generation < currentGeneration) {
            val removed = synchronized(lock) {
                pending.remove(current.frameId)?.also { it.terminal = true }
            }
            if (removed != null) finalize(removed, PoseMetricOutcome.STALE_DROP, null, null, deliver = false)
            return
        }
        val outcome = when (estimate.state) {
            PoseState.TRACKED,
            PoseState.PARTIAL_OR_LOW_CONFIDENCE,
            PoseState.MULTI_PERSON_UNKNOWN,
            -> PoseMetricOutcome.SUCCESS
            PoseState.NO_PERSON -> PoseMetricOutcome.NO_PERSON
            PoseState.ENGINE_ERROR -> PoseMetricOutcome.ERROR
            PoseState.STALE_RESULT_DROPPED -> PoseMetricOutcome.STALE_DROP
            PoseState.CANCELLED -> PoseMetricOutcome.CANCELLED
        }
        finish(estimate.frameId, estimate.generation, outcome, estimate, sink, deliver = outcome != PoseMetricOutcome.STALE_DROP)
    }

    private fun onTimeout(frameId: Long, generation: Long, sink: (PoseEstimate) -> Unit) {
        val current = synchronized(lock) { pending[frameId] } ?: return
        val estimate = errorEstimate(
            frameId = current.frameId,
            generation = current.generation,
            timestampNs = current.timestampNs,
            message = "pose inference timeout",
        )
        finish(frameId, generation, PoseMetricOutcome.ERROR, estimate, sink, deliver = true)
    }

    private fun finish(
        frameId: Long,
        generation: Long,
        outcome: PoseMetricOutcome,
        estimate: PoseEstimate?,
        sink: (PoseEstimate) -> Unit,
        deliver: Boolean,
    ) {
        val current = synchronized(lock) {
            val candidate = pending[frameId]
            if (candidate == null || candidate.generation != generation || candidate.terminal || generation < activeGeneration) {
                null
            } else {
                candidate.terminal = true
                pending.remove(frameId)
                candidate
            }
        } ?: return
        finalize(current, outcome, estimate, sink, deliver)
    }

    private fun finalize(
        current: Pending,
        outcome: PoseMetricOutcome,
        estimate: PoseEstimate?,
        sink: ((PoseEstimate) -> Unit)?,
        deliver: Boolean,
    ) {
        current.timeout?.cancel(false)
        if (outcome != PoseMetricOutcome.SUCCESS && outcome != PoseMetricOutcome.NO_PERSON) {
            current.submission?.cancel()
        }
        closeLease(current.lease)
        val latencyMs = ((clockNs() - current.timestampNs).coerceAtLeast(0L)).toDouble() / 1_000_000.0
        metrics.recordOutcome(outcome, latencyMs = latencyMs, timestampNs = clockNs())
        if (deliver && estimate != null && sink != null) {
            runCatching { sink(estimate) }
        }
    }

    private fun closeLease(frame: PoseInputFrame) = closeLease(frame.lease)

    private fun closeLease(lease: com.jovi.photoai.pose.FrameLease<*>) {
        val closeOnce = lease as? CloseOnceFrameLease<*>
        if (closeOnce != null && closeOnce.closeCallCount > 0) metrics.recordDoubleClose()
        runCatching { lease.close() }
        metrics.recordFrameClosed()
    }

    private fun errorEstimate(frame: PoseInputFrame, message: String): PoseEstimate =
        errorEstimate(frame.frameId, frame.generation, frame.timestampNs, message)

    private fun errorEstimate(frameId: Long, generation: Long, timestampNs: Long, message: String): PoseEstimate =
        PoseEstimate(
            frameId = frameId,
            generation = generation,
            timestampNs = timestampNs,
            engineId = engine.descriptor.id,
            state = PoseState.ENGINE_ERROR,
            diagnostics = PoseDiagnostics(message = message),
        )

    private class Pending(
        val frameId: Long,
        val generation: Long,
        val timestampNs: Long,
        val lease: com.jovi.photoai.pose.FrameLease<*>,
    ) {
        var submission: PoseSubmission? = null
        var timeout: ScheduledFuture<*>? = null
        var terminal: Boolean = false
    }

    private companion object {
        fun daemonScheduler(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "pose-coordinator-timeout").apply { isDaemon = true }
        }
    }
}
