package com.jovi.photoai.pose.fake

import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.PoseTestFixtures
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePoseEstimatorTest {
    @Test
    fun scenarios_coverTrackedNoPersonPartialLowConfidenceAndMultiPerson() {
        listOf(
            FakePoseScenario.SinglePerson to PoseState.TRACKED,
            FakePoseScenario.NoPerson to PoseState.NO_PERSON,
            FakePoseScenario.Partial to PoseState.PARTIAL_OR_LOW_CONFIDENCE,
            FakePoseScenario.LowConfidence to PoseState.PARTIAL_OR_LOW_CONFIDENCE,
            FakePoseScenario.MultiPersonUnknown to PoseState.MULTI_PERSON_UNKNOWN,
        ).forEachIndexed { index, (scenario, expected) ->
            val engine = FakePoseEstimator(scenario)
            var state: PoseState? = null
            engine.submit(PoseTestFixtures.frame(id = index.toLong())) { state = it.state }
            assertEquals(expected, state)
            engine.close()
        }
    }

    @Test
    fun delayedCallback_canBeEmittedAfterCancellationForStaleProtectionTests() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback)
        val latch = CountDownLatch(1)
        val submission = engine.submit(PoseTestFixtures.frame()) { latch.countDown() }

        submission.cancel()
        engine.emit(1L)

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(submission.isCancelled)
        engine.close()
    }

    @Test
    fun close_isIdempotentAndDelayedScenarioDoesNotRetainRawFrame() {
        val engine = FakePoseEstimator(FakePoseScenario.Delayed(10L))
        engine.submit(PoseTestFixtures.frame()) {}

        engine.close()
        engine.close()

        assertEquals(1, engine.engineCloseCount)
        assertTrue(engine.isClosed)
    }

    @Test
    fun history_isBoundedAndEachSubmitHasIndependentTokenForReverseCallbacks() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback, historyCapacity = 3)
        val callbacks = mutableListOf<Long>()
        engine.submit(PoseTestFixtures.frame(id = 4L)) { callbacks += it.timestampNs }
        engine.submit(PoseTestFixtures.frame(id = 4L, timestampNs = 2_000_000L)) { callbacks += it.timestampNs }
        engine.submit(PoseTestFixtures.frame(id = 5L, timestampNs = 3_000_000L)) { callbacks += it.timestampNs }

        assertEquals(3, engine.historySize)
        val retained = engine.requestTokens(4L)
        assertEquals(2, retained.size)
        val latest = engine.requestTokens(5L).single()
        engine.emitRequest(latest)
        engine.emitRequest(retained.last())
        engine.emitRequest(retained.first())
        assertEquals(listOf(3_000_000L, 2_000_000L, 4_000_000L), callbacks)
        assertEquals(0, engine.pendingCount)
        assertEquals(3, engine.drainHistory().size)
        assertEquals(0, engine.historySize)
        engine.close()
    }
}
