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
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Deterministic test/debug engine. It is never wired into the product Camera Director. */
class FakePoseEstimator(
    private val scenario: FakePoseScenario,
    private val scheduler: ScheduledExecutorService = daemonScheduler(),
    private val ownsScheduler: Boolean = true,
    private val historyCapacity: Int = DEFAULT_HISTORY_CAPACITY,
) : PoseEstimating {
    init {
        require(historyCapacity > 0) { "historyCapacity must be positive" }
    }

    override val descriptor = PoseEngineDescriptor(
        id = PoseEngineId.FAKE,
        displayName = "Fake Pose Engine",
        version = "p0",
    )

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val nextToken = AtomicLong(1L)
    private val active = mutableMapOf<Long, Pending>()
    private val history = ArrayDeque<Pending>(historyCapacity)
    private val closeCount = AtomicInteger(0)
    private val cancelCount = AtomicInteger(0)

    val isClosed: Boolean
        get() = closed.get()

    val engineCloseCount: Int
        get() = closeCount.get()

    val cancellationCount: Int
        get() = cancelCount.get()

    val pendingCount: Int
        get() = synchronized(lock) { active.size }

    val historySize: Int
        get() = synchronized(lock) { history.size }

    val activeRequestTokens: List<Long>
        get() = synchronized(lock) { active.keys.toList().sorted() }

    override fun submit(frame: PoseInputFrame, callback: (PoseEstimate) -> Unit): PoseSubmission {
        val pending = synchronized(lock) {
            check(!closed.get()) { "fake engine is closed" }
            val request = Pending(nextToken.getAndIncrement(), frame.frameId, frame.generation, frame.timestampNs, callback)
            request.submission = Submission(request)
            active[request.requestToken] = request
            if (history.size == historyCapacity) history.removeFirst()
            history.addLast(request)
            request
        }
        when (val selected = scenario) {
            FakePoseScenario.SinglePerson,
            FakePoseScenario.NoPerson,
            FakePoseScenario.Partial,
            FakePoseScenario.LowConfidence,
            FakePoseScenario.MultiPersonUnknown,
            -> emitRequest(pending.requestToken)
            is FakePoseScenario.EngineError -> {
                synchronized(lock) { active.remove(pending.requestToken) }
                throw IllegalStateException(selected.message)
            }
            is FakePoseScenario.Delayed -> runCatching {
                scheduler.schedule(
                    { emitRequest(pending.requestToken) },
                    selected.delayMs,
                    TimeUnit.MILLISECONDS,
                )
            }.onFailure {
                synchronized(lock) { active.remove(pending.requestToken) }
            }
            FakePoseScenario.NeverCallback -> Unit
        }
        return pending.submission!!
    }

    /** Emits the newest retained request for [frameId], including after cancel. */
    fun emit(frameId: Long) {
        val token = synchronized(lock) { history.lastOrNull { it.frameId == frameId }?.requestToken } ?: return
        emitRequest(token)
    }

    /** Emits one exact retained request, allowing old/reverse callback tests. */
    fun emitRequest(requestToken: Long) {
        val pending = synchronized(lock) {
            history.firstOrNull { it.requestToken == requestToken }?.also { active.remove(requestToken) }
        } ?: return
        pending.callback(estimate(pending))
    }

    /** Explicit name for tests that model a callback arriving after close(). */
    fun emitHistoricalRequest(requestToken: Long) = emitRequest(requestToken)

    fun requestTokens(frameId: Long): List<Long> = synchronized(lock) {
        history.filter { it.frameId == frameId }.map { it.requestToken }
    }

    fun emitAllReversed() = synchronized(lock) { history.map { it.requestToken }.sortedDescending() }
        .forEach(::emitRequest)

    fun clearHistory() = synchronized(lock) { history.clear() }

    fun drainHistory(): List<Long> = synchronized(lock) {
        history.map { it.requestToken }.also { history.clear() }
    }

    override fun invalidateGeneration(generation: Long) = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeCount.incrementAndGet()
        synchronized(lock) {
            active.values.toList().forEach { it.submission?.cancel() }
            active.clear()
        }
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
                synchronized(lock) { active.remove(pending.requestToken) }
            }
        }
    }

    private data class Pending(
        val requestToken: Long,
        val frameId: Long,
        val generation: Long,
        val timestampNs: Long,
        val callback: (PoseEstimate) -> Unit,
    ) {
        var submission: PoseSubmission? = null
    }

    private companion object {
        const val DEFAULT_HISTORY_CAPACITY = 128

        fun daemonScheduler(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fake-pose-engine").apply { isDaemon = true }
        }
    }
}
