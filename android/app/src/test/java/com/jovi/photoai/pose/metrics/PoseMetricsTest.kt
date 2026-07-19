package com.jovi.photoai.pose.metrics

import com.jovi.photoai.pose.FrameReleaseResult
import com.jovi.photoai.pose.FrameReleaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(2.0, snapshot.effectiveFps!!, 1e-9)
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
}
