package com.jovi.photoai.pose.fake

import com.jovi.photoai.domain.pose.PoseDiagnostics
import com.jovi.photoai.domain.pose.PoseEngineDescriptor
import com.jovi.photoai.domain.pose.PoseEngineId
import com.jovi.photoai.domain.pose.PoseEstimate
import com.jovi.photoai.domain.pose.PoseKeypoint33
import com.jovi.photoai.domain.pose.PosePerson
import com.jovi.photoai.domain.pose.PosePoint
import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.PoseEstimating
import com.jovi.photoai.pose.PoseInputFrame
import com.jovi.photoai.pose.PoseSubmission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic test/debug engine. It is never wired into the product Camera Director. */
class FakePoseEstimator(
    private val scenario: FakePoseScenario,
    private val scheduler: ScheduledExecutorService = daemonScheduler(),
    private val ownsScheduler: Boolean = true,
) : PoseEstimating {
    override val descriptor = PoseEngineDescriptor(
        id = PoseEngineId.FAKE,
        displayName = "Fake Pose Engine",
        version = "p0",
    )

    private val closed = AtomicBoolean(false)
    private val active = ConcurrentHashMap<Long, Pending>()
    private val history = ConcurrentHashMap<Long, Pending>()
    private val closeCount = AtomicInteger(0)
    private val cancelCount = AtomicInteger(0)

    val isClosed: Boolean
        get() = closed.get()

    val engineCloseCount: Int
        get() = closeCount.get()

    val cancellationCount: Int
        get() = cancelCount.get()

    val pendingCount: Int
        get() = active.size

    override fun submit(frame: PoseInputFrame, callback: (PoseEstimate) -> Unit): PoseSubmission {
        check(!closed.get()) { "fake engine is closed" }
        val pending = Pending(frame.frameId, frame.generation, frame.timestampNs, callback)
        active[frame.frameId] = pending
        history[frame.frameId] = pending
        when (val selected = scenario) {
            FakePoseScenario.SinglePerson,
            FakePoseScenario.NoPerson,
            FakePoseScenario.Partial,
            FakePoseScenario.LowConfidence,
            FakePoseScenario.MultiPersonUnknown,
            -> emit(frame.frameId)
            is FakePoseScenario.EngineError -> {
                active.remove(frame.frameId)
                throw IllegalStateException(selected.message)
            }
            is FakePoseScenario.Delayed -> scheduler.schedule(
                { emit(frame.frameId) },
                selected.delayMs,
                TimeUnit.MILLISECONDS,
            )
            FakePoseScenario.NeverCallback -> Unit
        }
        return Submission(pending)
    }

    /** Emits a callback even after cancellation/close, proving coordinator stale protection. */
    fun emit(frameId: Long) {
        val pending = history[frameId] ?: return
        active.remove(frameId)
        pending.callback(estimate(pending))
    }

    fun emitAllReversed() = history.keys.toList().sortedDescending().forEach(::emit)

    override fun invalidateGeneration(generation: Long) = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeCount.incrementAndGet()
        active.clear()
        if (ownsScheduler) scheduler.shutdownNow()
    }

    private fun estimate(pending: Pending): PoseEstimate {
        val state = when (scenario) {
            FakePoseScenario.SinglePerson -> PoseState.TRACKED
            FakePoseScenario.NoPerson -> PoseState.NO_PERSON
            FakePoseScenario.Partial,
            FakePoseScenario.LowConfidence,
            -> PoseState.PARTIAL_OR_LOW_CONFIDENCE
            FakePoseScenario.MultiPersonUnknown -> PoseState.MULTI_PERSON_UNKNOWN
            is FakePoseScenario.EngineError,
            is FakePoseScenario.Delayed,
            FakePoseScenario.NeverCallback,
            -> PoseState.TRACKED
        }
        val person = when (state) {
            PoseState.NO_PERSON,
            PoseState.MULTI_PERSON_UNKNOWN,
            -> null
            else -> canonicalPerson(partial = scenario == FakePoseScenario.Partial)
        }
        return PoseEstimate(
            frameId = pending.frameId,
            generation = pending.generation,
            timestampNs = pending.timestampNs,
            engineId = descriptor.id,
            state = state,
            person = person,
            diagnostics = PoseDiagnostics(pointCount = person?.presentPointCount ?: 0),
        )
    }

    private fun canonicalPerson(partial: Boolean): PosePerson {
        val points = PoseKeypoint33.canonicalOrder
            .filterNot { partial && it.canonicalIndex % 3 == 0 }
            .associateWith { keypoint ->
                PosePoint(
                    keypoint = keypoint,
                    xNorm = 0.5 + (keypoint.canonicalIndex % 5) * 0.01,
                    yNorm = 0.2 + (keypoint.canonicalIndex % 7) * 0.08,
                    visibility = if (scenario == FakePoseScenario.LowConfidence) 0.1 else 0.95,
                    presence = if (scenario == FakePoseScenario.LowConfidence) 0.1 else 0.95,
                    inFrameLikelihood = if (scenario == FakePoseScenario.LowConfidence) 0.1 else 0.95,
                    isOccludedOrUncertain = scenario == FakePoseScenario.LowConfidence,
                )
            }
        return PosePerson(points)
    }

    private inner class Submission(private val pending: Pending) : PoseSubmission {
        private val cancelled = AtomicBoolean(false)

        override val isCancelled: Boolean
            get() = cancelled.get()

        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) {
                cancelCount.incrementAndGet()
                active.remove(pending.frameId)
            }
        }
    }

    private data class Pending(
        val frameId: Long,
        val generation: Long,
        val timestampNs: Long,
        val callback: (PoseEstimate) -> Unit,
    )

    private companion object {
        fun daemonScheduler(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fake-pose-engine").apply { isDaemon = true }
        }
    }
}
