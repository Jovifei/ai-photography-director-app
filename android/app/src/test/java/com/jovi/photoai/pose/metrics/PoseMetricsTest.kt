package com.jovi.photoai.pose.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoseMetricsTest {
    @Test
    fun snapshot_calculatesNearestRankPercentilesFpsAndErrorRate() {
        val metrics = PoseMetricsAccumulator()
        metrics.recordSubmitted(0L)
        metrics.recordSubmitted(1_000_000_000L)
        metrics.recordSubmitted(2_000_000_000L)
        metrics.recordOutcome(PoseMetricOutcome.SUCCESS, latencyMs = 10.0)
        metrics.recordOutcome(PoseMetricOutcome.SUCCESS, latencyMs = 20.0)
        metrics.recordOutcome(PoseMetricOutcome.ERROR, latencyMs = 30.0)
        metrics.recordFrameClosed()
        metrics.recordDoubleClose()
        metrics.recordDroppedFrame()

        val snapshot = metrics.snapshot()

        assertEquals(3L, snapshot.count)
        assertEquals(2L, snapshot.success)
        assertEquals(1L, snapshot.error)
        assertEquals(20.0, snapshot.latencyP50Ms!!, 1e-9)
        assertEquals(30.0, snapshot.latencyP95Ms!!, 1e-9)
        assertEquals(1.5, snapshot.effectiveFps!!, 1e-9)
        assertEquals(1.0 / 3.0, snapshot.errorRate, 1e-9)
        assertEquals(1L, snapshot.frameCloseCount)
        assertEquals(1L, snapshot.doubleCloseCount)
        assertEquals(1L, snapshot.droppedFrameCount)
    }

    @Test
    fun emptyMetrics_haveNoLatencyOrFpsAndNoRawDataSurface() {
        val snapshot = PoseMetricsAccumulator().snapshot()

        assertEquals(0L, snapshot.count)
        assertNull(snapshot.latencyP50Ms)
        assertNull(snapshot.latencyP95Ms)
        assertNull(snapshot.effectiveFps)
    }
}
