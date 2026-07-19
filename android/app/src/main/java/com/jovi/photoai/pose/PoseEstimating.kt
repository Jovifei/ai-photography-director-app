package com.jovi.photoai.pose

import com.jovi.photoai.domain.pose.PoseEngineDescriptor
import com.jovi.photoai.domain.pose.PoseEstimate

interface PoseSubmission : AutoCloseable {
    val isCancelled: Boolean

    fun cancel()

    override fun close() = cancel()
}

/** Engine-neutral callback API; no CameraX, Compose, or SDK types cross this boundary. */
interface PoseEstimating : AutoCloseable {
    val descriptor: PoseEngineDescriptor

    fun submit(
        frame: PoseInputFrame,
        callback: (PoseEstimate) -> Unit,
    ): PoseSubmission

    fun invalidateGeneration(generation: Long)

    override fun close()
}
