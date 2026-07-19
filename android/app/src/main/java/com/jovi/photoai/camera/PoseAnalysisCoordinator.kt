package com.jovi.photoai.camera

import com.jovi.photoai.domain.pose.PoseDiagnostics
import com.jovi.photoai.domain.pose.PoseEstimate
import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.FrameLease
import com.jovi.photoai.pose.FrameReleaseStatus
import com.jovi.photoai.pose.PoseEstimating
import com.jovi.photoai.pose.PoseInputFrame
import com.jovi.photoai.pose.PoseSubmission
import com.jovi.photoai.pose.metrics.PoseMetricOutcome
import com.jovi.photoai.pose.metrics.PoseMetricsAccumulator
import com.jovi.photoai.pose.metrics.PoseMetricsSnapshot
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine-neutral owner of frame leases and callback lifetime. It is deliberately
 * not wired into CameraXManager or CameraScreen in AA0-P0.
 *
 * Each accepted frame receives an opaque request token. Neither frame id nor
 * generation is used as the pending primary key, so late callbacks and timeout
 * tasks from an older request cannot affect a newer request reusing the same id.
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
    private val lifecycleGate = Any()
    private val pending = mutableMapOf<Long, Pending>()
    private val activeFrameTokens = mutableMapOf<Long, Long>()
    private val closed = AtomicBoolean(false)
    private var activeGeneration = 0L
    private var nextRequestToken = 1L

    init {
        require(timeoutMs > 0L)
    }

    val currentGeneration: Long
        get() = synchronized(lock) { activeGeneration }

    fun metricsSnapshot(): PoseMetricsSnapshot = metrics.snapshot()

    /** Returns false for stale, duplicate, or disposed input. */
    fun submit(frame: PoseInputFrame, sink: (PoseEstimate) -> Unit): Boolean {
        val acceptedAtNs = clockNs().also { require(it >= 0L) { "clock must be non-negative" } }
        val pendingFrame: Pending?
        val rejectAsStale: Boolean
        synchronized(lock) {
            rejectAsStale = frame.generation < activeGeneration
            if (closed.get() || rejectAsStale || activeFrameTokens.containsKey(frame.frameId)) {
                pendingFrame = null
            } else {
                val token = nextRequestToken++
                pendingFrame = Pending(
                    requestToken = token,
                    frameId = frame.frameId,
                    generation = frame.generation,
                    acceptedAtNs = acceptedAtNs,
                    lease = frame.lease,
                )
                pending[token] = pendingFrame
                activeFrameTokens[frame.frameId] = token
                metrics.recordSubmitted(acceptedAtNs)
            }
        }
        if (pendingFrame == null) {
            releaseRejected(frame.lease)
            metrics.recordDroppedFrame()
            if (rejectAsStale) metrics.recordOutcome(PoseMetricOutcome.STALE_DROP)
            return false
        }

        val token = pendingFrame.requestToken
        val timeout = try {
            scheduler.schedule(
                { onTimeout(token, pendingFrame.generation, sink) },
                timeoutMs,
                TimeUnit.MILLISECONDS,
            )
        } catch (rejection: RejectedExecutionException) {
            metrics.recordSchedulerRejection()
            metrics.recordInfrastructureError()
            failBeforeEngineSubmit(pendingFrame, sink, "timeout scheduler rejected request")
            return true
        } catch (runtime: RuntimeException) {
            metrics.recordSchedulerRejection()
            metrics.recordInfrastructureError()
            failBeforeEngineSubmit(pendingFrame, sink, "timeout scheduler failed: ${runtime::class.simpleName}")
            return true
        }

        val cancelUnboundTimeout = synchronized(lock) {
            val current = pending[token]
            if (current === pendingFrame && !current.terminal) {
                current.timeout = timeout
                false
            } else {
                true
            }
        }
        if (cancelUnboundTimeout) timeout.cancel(false)

        // Serialize the close-vs-submit boundary. The engine is never called after
        // close() has acquired this gate and closed the engine.
        synchronized(lifecycleGate) {
            val canSubmit = synchronized(lock) { pending[token] === pendingFrame && !pendingFrame.terminal && !closed.get() }
            if (!canSubmit) return true
            try {
                val submission = engine.submit(frame) { estimate ->
                    onEngineResult(token, pendingFrame.generation, estimate, sink)
                }
                val cancelReturnedSubmission = synchronized(lock) {
                    val current = pending[token]
                    if (current === pendingFrame && !current.terminal) {
                        current.submission = submission
                        false
                    } else {
                        true
                    }
                }
                if (cancelReturnedSubmission) submission.cancel()
            } catch (throwable: Throwable) {
                metrics.recordInfrastructureError()
                val estimate = errorEstimate(frame, "engine submit failed: ${throwable::class.simpleName}")
                finishToken(token, PoseMetricOutcome.ERROR, estimate, sink, deliver = true)
            }
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
                pending.values.filter { it.generation < generation }.also { stale ->
                    stale.forEach { removeTerminalLocked(it) }
                }
            }
        }
        victims.forEach { finalize(it, PoseMetricOutcome.STALE_DROP, null, null, deliver = false) }
        synchronized(lifecycleGate) {
            if (!closed.get()) runCatching { engine.invalidateGeneration(generation) }
        }
    }

    fun cancel(frameId: Long): Boolean {
        val token = synchronized(lock) { activeFrameTokens[frameId] } ?: return false
        return finishToken(token, PoseMetricOutcome.CANCELLED, null, null, deliver = false)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val victims = synchronized(lock) {
            pending.values.toList().also { all -> all.forEach(::removeTerminalLocked) }
        }
        victims.forEach { finalize(it, PoseMetricOutcome.CANCELLED, null, null, deliver = false) }
        synchronized(lifecycleGate) { runCatching { engine.close() } }
        if (ownsScheduler) scheduler.shutdownNow()
    }

    fun dispose() = close()

    private fun onEngineResult(
        token: Long,
        expectedGeneration: Long,
        estimate: PoseEstimate,
        sink: (PoseEstimate) -> Unit,
    ) {
        val current = synchronized(lock) { pending[token] }
        if (current == null) {
            metrics.recordOutcome(PoseMetricOutcome.STALE_DROP)
            metrics.recordDroppedFrame()
            return
        }
        if (estimate.frameId != current.frameId || estimate.generation != expectedGeneration ||
            estimate.generation != current.generation || estimate.generation < currentGeneration
        ) {
            finishToken(token, PoseMetricOutcome.STALE_DROP, null, null, deliver = false)
            return
        }
        val outcome = when (estimate.state) {
            PoseState.TRACKED,
            PoseState.PARTIAL_OR_LOW_CONFIDENCE,
            -> PoseMetricOutcome.SUCCESS
            PoseState.MULTI_PERSON_UNKNOWN -> PoseMetricOutcome.MULTI_PERSON_UNKNOWN
            PoseState.NO_PERSON -> PoseMetricOutcome.NO_PERSON
            PoseState.ENGINE_ERROR -> PoseMetricOutcome.ERROR
            PoseState.STALE_RESULT_DROPPED -> PoseMetricOutcome.STALE_DROP
            PoseState.CANCELLED -> PoseMetricOutcome.CANCELLED
        }
        finishToken(token, outcome, estimate, sink, deliver = outcome != PoseMetricOutcome.STALE_DROP)
    }

    private fun onTimeout(token: Long, generation: Long, sink: (PoseEstimate) -> Unit) {
        val current = synchronized(lock) { pending[token] } ?: return
        if (current.generation != generation) return
        val estimate = errorEstimate(
            frameId = current.frameId,
            generation = current.generation,
            timestampNs = current.acceptedAtNs,
            message = "pose inference timeout",
        )
        finishToken(token, PoseMetricOutcome.ERROR, estimate, sink, deliver = true)
    }

    private fun failBeforeEngineSubmit(current: Pending, sink: (PoseEstimate) -> Unit, message: String) {
        val estimate = errorEstimate(current.frameId, current.generation, current.acceptedAtNs, message)
        finishToken(current.requestToken, PoseMetricOutcome.ERROR, estimate, sink, deliver = true)
    }

    private fun finishToken(
        token: Long,
        outcome: PoseMetricOutcome,
        estimate: PoseEstimate?,
        sink: ((PoseEstimate) -> Unit)?,
        deliver: Boolean,
    ): Boolean {
        val current = synchronized(lock) {
            pending[token]?.takeUnless { it.terminal }?.also { removeTerminalLocked(it) }
        } ?: return false
        finalize(current, outcome, estimate, sink, deliver)
        return true
    }

    private fun finalize(
        current: Pending,
        outcome: PoseMetricOutcome,
        estimate: PoseEstimate?,
        sink: ((PoseEstimate) -> Unit)?,
        deliver: Boolean,
    ) {
        current.timeout?.cancel(false)
        if (outcome != PoseMetricOutcome.SUCCESS && outcome != PoseMetricOutcome.NO_PERSON &&
            outcome != PoseMetricOutcome.MULTI_PERSON_UNKNOWN
        ) {
            current.submission?.cancel()
        }
        val releaseResult = current.lease.releaseOnce()
        metrics.recordFrameRelease(releaseResult)
        if (releaseResult.status == FrameReleaseStatus.RELEASE_FAILED) metrics.recordInfrastructureError()
        val completionNs = clockNs().coerceAtLeast(current.acceptedAtNs)
        val latencyMs = (completionNs - current.acceptedAtNs).toDouble() / 1_000_000.0
        metrics.recordOutcome(outcome, latencyMs = latencyMs, timestampNs = completionNs)
        if (deliver && estimate != null && sink != null) runCatching { sink(estimate) }
    }

    private fun releaseRejected(lease: FrameLease<*>) {
        metrics.recordFrameRelease(lease.releaseOnce())
    }

    private fun removeTerminalLocked(current: Pending) {
        current.terminal = true
        pending.remove(current.requestToken)
        if (activeFrameTokens[current.frameId] == current.requestToken) activeFrameTokens.remove(current.frameId)
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
        val requestToken: Long,
        val frameId: Long,
        val generation: Long,
        val acceptedAtNs: Long,
        val lease: FrameLease<*>,
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
