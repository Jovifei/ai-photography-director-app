package com.jovi.photoai.pose

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Single-owner frame resource contract. The release lambda runs at most once. */
interface FrameLease<out T> : AutoCloseable {
    val value: T
    val isClosed: Boolean

    override fun close()
}

class CloseOnceFrameLease<T>(
    override val value: T,
    private val release: (T) -> Unit,
) : FrameLease<T> {
    private val closed = AtomicBoolean(false)
    private val closeCalls = AtomicInteger(0)
    private val releases = AtomicInteger(0)

    override val isClosed: Boolean
        get() = closed.get()

    val closeCallCount: Int
        get() = closeCalls.get()

    val releaseCount: Int
        get() = releases.get()

    override fun close() {
        closeCalls.incrementAndGet()
        if (closed.compareAndSet(false, true)) {
            releases.incrementAndGet()
            release(value)
        }
    }
}
