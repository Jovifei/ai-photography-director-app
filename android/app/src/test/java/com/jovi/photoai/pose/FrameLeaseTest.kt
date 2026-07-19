package com.jovi.photoai.pose

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
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
}
