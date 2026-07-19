package com.jovi.photoai.pose

import com.jovi.photoai.pose.geometry.CropGeometry
import com.jovi.photoai.pose.geometry.PreviewGeometry

internal object PoseTestFixtures {
    fun frame(
        id: Long = 1L,
        generation: Long = 0L,
        lease: FrameLease<*> = CloseOnceFrameLease(Unit) {},
        timestampNs: Long = id * 1_000_000L,
    ): PoseInputFrame = PoseInputFrame(
        frameId = id,
        generation = generation,
        timestampNs = timestampNs,
        imageFormat = PoseImageFormat.YUV_420,
        width = 100,
        height = 100,
        cropRect = CropGeometry.fullFrame(),
        rotationDegrees = 0,
        lensFacing = PoseLensFacing.BACK,
        lease = lease,
        previewGeometry = PreviewGeometry.letterbox(100.0, 100.0, 100.0, 100.0),
    )
}
