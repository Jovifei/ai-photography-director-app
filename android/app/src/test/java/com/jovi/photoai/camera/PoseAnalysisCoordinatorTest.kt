package com.jovi.photoai.camera

import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.CloseOnceFrameLease
import com.jovi.photoai.pose.PoseTestFixtures
import com.jovi.photoai.pose.fake.FakePoseEstimator
import com.jovi.photoai.pose.fake.FakePoseScenario
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseAnalysisCoordinatorTest {
    @Test
    fun success_closesLeaseExactlyOnceAndDeliversTracked() {
        val engine = FakePoseEstimator(FakePoseScenario.SinglePerson)
        val lease = CloseOnceFrameLease(Unit) {}
        val result = mutableListOf<PoseState>()
        val latch = CountDownLatch(1)
        val coordinator = PoseAnalysisCoordinator(engine, timeoutMs = 200)

        assertTrue(coordinator.submit(PoseTestFixtures.frame(lease = lease)) {
            result += it.state
            latch.countDown()
        })
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertEquals(listOf(PoseState.TRACKED), result)
        assertEquals(1, lease.releaseCount)
        assertEquals(1L, coordinator.metricsSnapshot().success)
        coordinator.close()
        assertEquals(1, engine.engineCloseCount)
    }

    @Test
    fun noPerson_isNotEngineError() {
        val engine = FakePoseEstimator(FakePoseScenario.NoPerson)
        val states = mutableListOf<PoseState>()
        val coordinator = PoseAnalysisCoordinator(engine)

        coordinator.submit(PoseTestFixtures.frame()) { states += it.state }

        assertEquals(listOf(PoseState.NO_PERSON), states)
        assertEquals(1L, coordinator.metricsSnapshot().noPerson)
        assertEquals(0L, coordinator.metricsSnapshot().error)
        coordinator.close()
    }

    @Test
    fun engineThrow_deliversErrorAndStillClosesLease() {
        val engine = FakePoseEstimator(FakePoseScenario.EngineError())
        val lease = CloseOnceFrameLease(Unit) {}
        val states = mutableListOf<PoseState>()
        val coordinator = PoseAnalysisCoordinator(engine)

        coordinator.submit(PoseTestFixtures.frame(lease = lease)) { states += it.state }

        assertEquals(listOf(PoseState.ENGINE_ERROR), states)
        assertEquals(1, lease.releaseCount)
        assertEquals(1L, coordinator.metricsSnapshot().error)
        coordinator.close()
        assertEquals(1, engine.engineCloseCount)
    }

    @Test
    fun callbackException_doesNotLeakLeaseOrEscapeSubmit() {
        val engine = FakePoseEstimator(FakePoseScenario.SinglePerson)
        val lease = CloseOnceFrameLease(Unit) {}
        val coordinator = PoseAnalysisCoordinator(engine)

        val accepted = coordinator.submit(PoseTestFixtures.frame(lease = lease)) {
            error("consumer failure")
        }

        assertTrue(accepted)
        assertEquals(1, lease.releaseCount)
        coordinator.close()
    }

    @Test
    fun timeout_closesLeaseAndReportsEngineError() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val lease = CloseOnceFrameLease(Unit) {}
        val states = mutableListOf<PoseState>()
        val latch = CountDownLatch(1)
        val coordinator = PoseAnalysisCoordinator(engine, timeoutMs = 20)

        coordinator.submit(PoseTestFixtures.frame(lease = lease)) {
            states += it.state
            latch.countDown()
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(listOf(PoseState.ENGINE_ERROR), states)
        assertEquals(1, lease.releaseCount)
        coordinator.close()
    }

    @Test
    fun cancel_thenLateCallback_isDroppedAndLeaseClosedOnce() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val lease = CloseOnceFrameLease(Unit) {}
        var callbacks = 0
        val coordinator = PoseAnalysisCoordinator(engine)
        val frame = PoseTestFixtures.frame(lease = lease)

        assertTrue(coordinator.submit(frame) { callbacks++ })
        assertTrue(coordinator.cancel(frame.frameId))
        engine.emit(frame.frameId)

        assertEquals(0, callbacks)
        assertEquals(1, lease.releaseCount)
        assertEquals(1L, coordinator.metricsSnapshot().cancelled)
        coordinator.close()
    }

    @Test
    fun invalidateGeneration_dropsOldAndAcceptsNewGeneration() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val oldLease = CloseOnceFrameLease(Unit) {}
        val newLease = CloseOnceFrameLease(Unit) {}
        val states = mutableListOf<PoseState>()
        val coordinator = PoseAnalysisCoordinator(engine)
        val oldFrame = PoseTestFixtures.frame(id = 1L, generation = 0L, lease = oldLease)
        val newFrame = PoseTestFixtures.frame(id = 2L, generation = 1L, lease = newLease)

        coordinator.submit(oldFrame) { states += it.state }
        coordinator.invalidateGeneration(1L)
        engine.emit(oldFrame.frameId)
        assertEquals(1, oldLease.releaseCount)
        assertEquals(0, states.size)

        assertTrue(coordinator.submit(newFrame) { states += it.state })
        engine.emit(newFrame.frameId)

        assertEquals(listOf(PoseState.TRACKED), states)
        assertEquals(1, newLease.releaseCount)
        assertEquals(2L, coordinator.metricsSnapshot().staleDrop)
        coordinator.close()
    }

    @Test
    fun dispose_closesPendingIgnoresLateCallbackAndClosesEngineOnce() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val lease = CloseOnceFrameLease(Unit) {}
        var callbacks = 0
        val coordinator = PoseAnalysisCoordinator(engine)
        val frame = PoseTestFixtures.frame(lease = lease)

        assertTrue(coordinator.submit(frame) { callbacks++ })
        coordinator.close()
        coordinator.close()
        engine.emit(frame.frameId)

        assertEquals(0, callbacks)
        assertEquals(1, lease.releaseCount)
        assertEquals(1, engine.engineCloseCount)
        assertFalse(coordinator.submit(PoseTestFixtures.frame(id = 2L)) {})
    }

    @Test
    fun duplicateFrameId_closesIncomingLeaseAndKeepsOriginalPending() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val firstLease = CloseOnceFrameLease(Unit) {}
        val duplicateLease = CloseOnceFrameLease(Unit) {}
        val coordinator = PoseAnalysisCoordinator(engine)
        val first = PoseTestFixtures.frame(id = 9L, lease = firstLease)
        val duplicate = PoseTestFixtures.frame(id = 9L, lease = duplicateLease)

        assertTrue(coordinator.submit(first) {})
        assertFalse(coordinator.submit(duplicate) {})
        assertEquals(0, firstLease.releaseCount)
        assertEquals(1, duplicateLease.releaseCount)
        coordinator.close()
        assertEquals(1, firstLease.releaseCount)
    }

    @Test
    fun oldCallbackAndTimeout_cannotAffectReusedFrameId() {
        val scheduler = ManualScheduler()
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val firstLease = CloseOnceFrameLease(Unit) {}
        val secondLease = CloseOnceFrameLease(Unit) {}
        val states = mutableListOf<PoseState>()
        val coordinator = PoseAnalysisCoordinator(engine, scheduler = scheduler, ownsScheduler = false)

        assertTrue(coordinator.submit(PoseTestFixtures.frame(id = 7L, lease = firstLease)) { states += it.state })
        val firstToken = engine.requestTokens(7L).single()
        engine.emitRequest(firstToken)
        assertEquals(1, firstLease.releaseCount)

        assertTrue(coordinator.submit(PoseTestFixtures.frame(id = 7L, lease = secondLease)) { states += it.state })
        val secondToken = engine.requestTokens(7L).last()
        scheduler.futures.first().fire()
        assertEquals(0, secondLease.releaseCount)
        engine.emitRequest(firstToken)
        assertEquals(0, secondLease.releaseCount)
        engine.emitRequest(secondToken)
        assertEquals(2, states.size)
        assertEquals(1, secondLease.releaseCount)
        coordinator.close()
    }

    @Test
    fun synchronousCallback_cancelsTimeoutAndCallbackTimeoutRaceDeliversOnce() {
        val scheduler = ManualScheduler()
        val engine = FakePoseEstimator(FakePoseScenario.SinglePerson)
        val lease = CloseOnceFrameLease(Unit) {}
        var callbacks = 0
        val coordinator = PoseAnalysisCoordinator(engine, scheduler = scheduler, ownsScheduler = false)

        assertTrue(coordinator.submit(PoseTestFixtures.frame(lease = lease)) { callbacks++ })
        assertTrue(scheduler.futures.single().isCancelled)
        assertEquals(1, callbacks)
        assertEquals(1, lease.releaseCount)
        scheduler.futures.single().fire()
        assertEquals(1, callbacks)
        coordinator.close()
    }

    @Test
    fun schedulerRejection_failClosesLeaseWithoutCallingEngine() {
        val scheduler = ManualScheduler(reject = true)
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val lease = CloseOnceFrameLease(Unit) {}
        val states = mutableListOf<PoseState>()
        val coordinator = PoseAnalysisCoordinator(engine, scheduler = scheduler, ownsScheduler = false)

        assertTrue(coordinator.submit(PoseTestFixtures.frame(lease = lease)) { states += it.state })
        assertEquals(listOf(PoseState.ENGINE_ERROR), states)
        assertEquals(1, lease.releaseCount)
        assertEquals(0, engine.pendingCount)
        assertEquals(1L, coordinator.metricsSnapshot().schedulerRejectionCount)
        assertEquals(1L, coordinator.metricsSnapshot().error)
        coordinator.close()
    }

    @Test
    fun releaseFailure_isTerminalAndDistinctFromSuccessfulRelease() {
        val engine = FakePoseEstimator(FakePoseScenario.SinglePerson)
        val lease = CloseOnceFrameLease(Unit) { error("release failure") }
        var callbacks = 0
        val coordinator = PoseAnalysisCoordinator(engine)

        assertTrue(coordinator.submit(PoseTestFixtures.frame(lease = lease)) { callbacks++ })

        val snapshot = coordinator.metricsSnapshot()
        assertEquals(1, callbacks)
        assertEquals(1L, snapshot.frameReleaseFailureCount)
        assertEquals(0L, snapshot.frameReleaseSuccessCount)
        assertEquals(1L, snapshot.infrastructureErrorCount)
        coordinator.close()
    }

    private class ManualScheduler(private val reject: Boolean = false) : ScheduledThreadPoolExecutor(1) {
        val futures = mutableListOf<ManualFuture>()

        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
            if (reject) throw RejectedExecutionException("manual rejection")
            return ManualFuture(command).also { futures += it }
        }
    }

    private class ManualFuture(private val action: Runnable) : ScheduledFuture<Unit> {
        private var cancelled = false
        private var fired = false

        fun fire() {
            fired = true
            action.run()
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            if (cancelled || fired) return false
            cancelled = true
            return true
        }

        override fun isCancelled(): Boolean = cancelled
        override fun isDone(): Boolean = cancelled || fired
        override fun get(): Unit = Unit
        override fun get(timeout: Long, unit: TimeUnit): Unit = Unit
        override fun getDelay(unit: TimeUnit): Long = 0L
        override fun compareTo(other: java.util.concurrent.Delayed): Int = 0
    }
}
