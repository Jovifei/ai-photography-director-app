package com.jovi.photoai.pose.fake

import com.jovi.photoai.domain.pose.PoseState
import com.jovi.photoai.pose.PoseTestFixtures
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val token = engine.run {
            submit(PoseTestFixtures.frame()) {}
            requestTokens(1L).single()
        }

        engine.close()
        engine.close()

        assertEquals(1, engine.engineCloseCount)
        assertTrue(engine.isClosed)
        assertEquals(0, engine.pendingCount)
        assertEquals(1, engine.historySize)
        engine.emitHistoricalRequest(token)
        engine.clearHistory()
        assertEquals(0, engine.historySize)
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

    @Test
    fun submitAndClose_areAtomicAndCloseRetainsOnlyExplicitlyClearableHistory() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback, historyCapacity = 16)
        val firstSubmission = engine.submit(PoseTestFixtures.frame(id = 10L)) {}
        val firstToken = engine.requestTokens(10L).single()
        engine.close()

        assertTrue(firstSubmission.isCancelled)
        assertEquals(0, engine.pendingCount)
        assertTrue(engine.isClosed)
        engine.emitHistoricalRequest(firstToken)
        assertEquals(0, engine.pendingCount)

        try {
            engine.submit(PoseTestFixtures.frame(id = 11L)) {}
            throw AssertionError("closed Fake accepted a new request")
        } catch (error: IllegalStateException) {
            assertEquals("fake engine is closed", error.message)
        }
        assertEquals(0, engine.pendingCount)
        assertEquals(1, engine.historySize)
        assertEquals(1, engine.drainHistory().size)
        assertEquals(0, engine.historySize)
    }

    @Test
    fun oneHundredSubmitCloseRaces_leaveNoActiveRequestsWithoutSleep() {
        val engine = FakePoseEstimator(FakePoseScenario.NeverCallback, historyCapacity = 32)
        val parties = 101
        val barrier = CyclicBarrier(parties)
        val done = CountDownLatch(parties)
        val accepted = AtomicInteger(0)
        val rejected = AtomicInteger(0)
        val executor: ExecutorService = Executors.newFixedThreadPool(parties)

        repeat(100) { index ->
            executor.execute {
                barrier.await()
                try {
                    engine.submit(PoseTestFixtures.frame(id = index.toLong())) {}
                    accepted.incrementAndGet()
                } catch (error: IllegalStateException) {
                    assertEquals("fake engine is closed", error.message)
                    rejected.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        executor.execute {
            barrier.await()
            engine.close()
            done.countDown()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(0, engine.pendingCount)
        assertTrue(engine.historySize <= 32)
        assertEquals(100, accepted.get() + rejected.get())
        assertFalse(engine.activeRequestTokens.isNotEmpty())
    }
}
