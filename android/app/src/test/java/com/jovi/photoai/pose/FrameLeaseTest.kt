package com.jovi.photoai.pose

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameLeaseTest {
    @Test
    fun repeatedClose_releasesUnderlyingValueExactlyOnce() {
        var releases = 0
        val lease = CloseOnceFrameLease("frame") { releases++ }

        lease.close()
        lease.close()

        assertTrue(lease.isClosed)
        assertEquals(2, lease.closeCallCount)
        assertEquals(1, lease.releaseCount)
        assertEquals(1, releases)
        assertEquals(FrameReleaseStatus.ALREADY_RELEASED, lease.releaseOnce().status)
        assertEquals(2, lease.alreadyReleasedCount)
    }

    @Test
    fun concurrentClose_releasesUnderlyingValueExactlyOnce() {
        var releases = 0
        val lease = CloseOnceFrameLease(Unit) { synchronized(this) { releases++ } }
        val executor = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(100)

        repeat(100) {
            executor.execute {
                lease.close()
                done.countDown()
            }
        }
        done.await()
        executor.shutdownNow()

        assertEquals(1, releases)
        assertEquals(1, lease.releaseCount)
        assertEquals(100, lease.closeCallCount)
    }

    @Test
    fun releaseFailure_isTerminalAndReportedWithoutRetry() {
        var attempts = 0
        val lease = CloseOnceFrameLease(Unit) {
            attempts++
            error("release failed")
        }

        val first = lease.releaseOnce()
        val second = lease.releaseOnce()

        assertEquals(FrameReleaseStatus.RELEASE_FAILED, first.status)
        assertEquals("java.lang.IllegalStateException", first.exceptionClassName)
        assertEquals(FrameReleaseStatus.ALREADY_RELEASED, second.status)
        assertTrue(lease.isClosed)
        assertEquals(1, attempts)
        assertEquals(1, lease.releaseFailureCount)
        assertEquals(1, lease.alreadyReleasedCount)
    }

    @Test
    fun preclosedAndConcurrentReleaseProduceOneTerminalResult() {
        val lease = CloseOnceFrameLease(Unit) {}
        val first = lease.releaseOnce()
        assertEquals(FrameReleaseStatus.RELEASED, first.status)
        assertFalse(lease.releaseOnce().status == FrameReleaseStatus.RELEASED)

        val concurrent = CloseOnceFrameLease(Unit) {}
        val executor = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(100)
        repeat(100) {
            executor.execute {
                concurrent.releaseOnce()
                done.countDown()
            }
        }
        assertTrue(done.await(1, java.util.concurrent.TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(1, concurrent.successfulReleaseCount)
        assertEquals(100, concurrent.closeAttemptCount)
    }
}
