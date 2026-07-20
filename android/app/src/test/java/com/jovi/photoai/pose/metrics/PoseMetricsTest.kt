package com.jovi.photoai.pose.metrics

import com.jovi.photoai.pose.FrameReleaseResult
import com.jovi.photoai.pose.FrameReleaseStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMetricsTest {
    @Test
    fun snapshot_usesNearestRankBoundedWindowAndSuccessOnlyFps() {
        val metrics = PoseMetricsAccumulator(latencyWindowCapacity = 3)
        repeat(5) { index -> metrics.recordSubmitted(index.toLong()) }
        metrics.recordOutcome(PoseMetricOutcome.SUCCESS, latencyMs = 10.0, timestampNs = 0L)
        metrics.recordOutcome(PoseMetricOutcome.SUCCESS, latencyMs = 20.0, timestampNs = 1_000_000_000L)
        metrics.recordOutcome(PoseMetricOutcome.ERROR, latencyMs = 30.0, timestampNs = 2_000_000_000L)
        metrics.recordOutcome(PoseMetricOutcome.NO_PERSON, latencyMs = 40.0, timestampNs = 3_000_000_000L)
        metrics.recordOutcome(PoseMetricOutcome.CANCELLED, latencyMs = 50.0, timestampNs = 4_000_000_000L)
        metrics.recordFrameRelease(FrameReleaseResult(FrameReleaseStatus.RELEASED))
        metrics.recordFrameRelease(FrameReleaseResult(FrameReleaseStatus.ALREADY_RELEASED))
        metrics.recordFrameRelease(FrameReleaseResult(FrameReleaseStatus.RELEASE_FAILED, "X"))
        metrics.recordDroppedFrame()

        val snapshot = metrics.snapshot()

        assertEquals(5L, snapshot.count)
        assertEquals(2L, snapshot.success)
        assertEquals(1L, snapshot.error)
        assertEquals(40.0, snapshot.latencyP50Ms!!, 1e-9)
        assertEquals(50.0, snapshot.latencyP95Ms!!, 1e-9)
        assertEquals(1.0, snapshot.effectiveFps!!, 1e-9)
        assertEquals(2L, snapshot.timedSuccessCount)
        assertEquals(1.0 / 5.0, snapshot.errorRate, 1e-9)
        assertEquals(3L, snapshot.latencySampleCount)
        assertEquals(3L, snapshot.latencyWindowCapacity.toLong())
        assertEquals(5L, snapshot.latencyTotalObserved)
        assertEquals(1L, snapshot.frameReleaseSuccessCount)
        assertEquals(1L, snapshot.frameAlreadyReleasedCount)
        assertEquals(1L, snapshot.frameReleaseFailureCount)
        assertEquals(3L, snapshot.frameCloseAttemptCount)
        assertEquals(1L, snapshot.droppedFrameCount)
    }

    @Test
    fun overflow_capacityOneRetainsOnlyNewestSampleAndConcurrentRecordsRemainBounded() {
        val metrics = PoseMetricsAccumulator(latencyWindowCapacity = 1)
        repeat(10_000) { index ->
            metrics.recordSubmitted(index.toLong())
            metrics.recordOutcome(PoseMetricOutcome.SUCCESS, latencyMs = index.toDouble(), timestampNs = index.toLong())
        }

        val snapshot = metrics.snapshot()
        assertEquals(10_000L, snapshot.count)
        assertEquals(10_000L, snapshot.success)
        assertEquals(1L, snapshot.latencySampleCount)
        assertEquals(10_000L, snapshot.latencyTotalObserved)
        assertEquals(9_999.0, snapshot.latencyP50Ms!!, 1e-9)
        assertNull(PoseMetricsAccumulator(latencyWindowCapacity = 5).snapshot().effectiveFps)
    }

    @Test
    fun successWithoutTimestamp_isRejectedBeforeAnyMetricChanges() {
        val metrics = PoseMetricsAccumulator()

        try {
            metrics.recordOutcome(PoseMetricOutcome.SUCCESS)
            fail("expected SUCCESS timestamp contract failure")
        } catch (error: IllegalArgumentException) {
            assertEquals("SUCCESS requires a non-null completion timestamp", error.message)
        }

        val snapshot = metrics.snapshot()
        assertEquals(0L, snapshot.success)
        assertEquals(0L, snapshot.timedSuccessCount)
        assertNull(snapshot.effectiveFps)
    }

    @Test
    fun fps_usesOnlyTimedSuccessAndRejectsReverseOrNegativeClockValues() {
        val metrics = PoseMetricsAccumulator()
        metrics.recordOutcome(PoseMetricOutcome.SUCCESS, timestampNs = 1_000_000_000L)
        metrics.recordOutcome(PoseMetricOutcome.NO_PERSON)
        metrics.recordOutcome(PoseMetricOutcome.ERROR)
        metrics.recordOutcome(PoseMetricOutcome.CANCELLED)
        metrics.recordOutcome(PoseMetricOutcome.STALE_DROP)
        metrics.recordOutcome(PoseMetricOutcome.SUCCESS, timestampNs = 2_000_000_000L)
        assertEquals(1.0, metrics.snapshot().effectiveFps!!, 1e-9)

        try {
            metrics.recordOutcome(PoseMetricOutcome.SUCCESS, timestampNs = 1_500_000_000L)
            fail("expected reverse timestamp rejection")
        } catch (error: IllegalArgumentException) {
            assertEquals("SUCCESS completion timestamps must be non-decreasing", error.message)
        }

        try {
            metrics.recordOutcome(PoseMetricOutcome.SUCCESS, timestampNs = -1L)
            fail("expected negative timestamp rejection")
        } catch (error: IllegalArgumentException) {
            assertEquals("SUCCESS completion timestamp must be non-negative", error.message)
        }
        assertEquals(2L, metrics.snapshot().success)
    }

    @Test
    fun concurrentWritersAndSnapshotReader_areConsistentAndWindowRemainsBounded() {
        val metrics = PoseMetricsAccumulator(latencyWindowCapacity = 5)
        val writers = 4
        val iterations = 1_000
        val start = CyclicBarrier(writers + 1)
        val done = CountDownLatch(writers)
        val readerDone = CountDownLatch(1)
        val snapshots = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(writers + 1)

        repeat(writers) { writer ->
            executor.execute {
                start.await()
                repeat(iterations) { index ->
                    metrics.recordSubmitted((writer * iterations + index).toLong())
                    metrics.recordOutcome(PoseMetricOutcome.ERROR, latencyMs = index.toDouble())
                }
                done.countDown()
            }
        }
        executor.execute {
            try {
                start.await()
                snapshots.incrementAndGet()
                while (done.count > 0) {
                    val snapshot = metrics.snapshot()
                    assertNotNull(snapshot)
                    assertTrue(snapshot.latencySampleCount <= snapshot.latencyWindowCapacity)
                    snapshots.incrementAndGet()
                }
            } finally {
                readerDone.countDown()
            }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(readerDone.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))

        val snapshot = metrics.snapshot()
        assertEquals((writers * iterations).toLong(), snapshot.count)
        assertEquals((writers * iterations).toLong(), snapshot.error)
        assertEquals(5L, snapshot.latencySampleCount)
        assertEquals((writers * iterations).toLong(), snapshot.latencyTotalObserved)
        assertTrue(snapshots.get() > 0)
    }
}
