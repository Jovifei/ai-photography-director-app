package com.jovi.photoai.camera

import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.CloseOnceFrameLease
import com.jovi.photoai.pose.PoseTestFixtures
import com.jovi.photoai.pose.fake.FakePoseEstimator
import com.jovi.photoai.pose.fake.FakePoseScenario
import java.util.concurrent.CountDownLatch
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
}
