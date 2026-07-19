package com.jovi.photoai.pose.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseCoordinateMapperTest {
    private val epsilon = 1e-9

    @Test
    fun sensorRotation_coversAllQuarterTurns() {
        val source = NormalizedPoint(0.0, 0.0)

        assertPoint(PoseCoordinateTransform(0).sensorToUpright(source), 0.0, 0.0)
        assertPoint(PoseCoordinateTransform(90).sensorToUpright(source), 1.0, 0.0)
        assertPoint(PoseCoordinateTransform(180).sensorToUpright(source), 1.0, 1.0)
        assertPoint(PoseCoordinateTransform(270).sensorToUpright(source), 0.0, 1.0)
    }

    @Test
    fun centerCrop_supportsWideSquareAndTallInputsWithoutClamping() {
        val wide = CropGeometry.centerCrop(sourceAspectRatio = 2.0, targetAspectRatio = 1.0)
        val tall = CropGeometry.centerCrop(sourceAspectRatio = 0.5, targetAspectRatio = 1.0)
        val square = CropGeometry.centerCrop(sourceAspectRatio = 1.0, targetAspectRatio = 1.0)

        assertEquals(0.25, wide.left, epsilon)
        assertEquals(0.0, wide.applyToUprightSource(NormalizedPoint(0.25, 0.5)).x, epsilon)
        assertEquals(0.25, tall.top, epsilon)
        assertEquals(0.5, square.applyToUprightSource(NormalizedPoint(0.5, 0.5)).x, epsilon)
        assertTrue(wide.applyToUprightSource(NormalizedPoint(0.0, 0.5)).outOfFrame)
    }

    @Test
    fun viewport_letterboxAndCenterCropExposeScaleAndOffsets() {
        val letterbox = PreviewGeometry.letterbox(100.0, 50.0, 100.0, 100.0)
        val letterboxedCenter = letterbox.project(NormalizedPoint(0.5, 0.5))
        assertEquals(50.0, letterboxedCenter.xPx, epsilon)
        assertEquals(50.0, letterboxedCenter.yPx, epsilon)
        assertFalse(letterboxedCenter.outOfViewport)

        val centerCrop = PreviewGeometry.centerCrop(100.0, 50.0, 100.0, 100.0)
        val croppedLeft = centerCrop.project(NormalizedPoint(0.0, 0.5))
        assertEquals(-50.0, croppedLeft.xPx, epsilon)
        assertTrue(croppedLeft.outOfViewport)
    }

    @Test
    fun frontMirror_isDisplayOnlyAndDoesNotSwapSemanticPoint() {
        val geometry = PreviewGeometry.letterbox(100.0, 100.0, 100.0, 100.0)
        val point = NormalizedPoint(0.25, 0.5)

        val rear = PoseCoordinateMapper.canonicalToViewport(point, geometry, mirrorForDisplay = false)
        val front = PoseCoordinateMapper.canonicalToViewport(point, geometry, mirrorForDisplay = true)

        assertEquals(25.0, rear.xPx, epsilon)
        assertEquals(75.0, front.xPx, epsilon)
        assertEquals(rear.yPx, front.yPx, epsilon)
    }

    @Test
    fun cornersCenterAndOutOfFrame_arePreservedWithEpsilon() {
        val geometry = PreviewGeometry.letterbox(200.0, 100.0, 400.0, 400.0)
        val points = listOf(
            NormalizedPoint(0.0, 0.0),
            NormalizedPoint(1.0, 0.0),
            NormalizedPoint(0.0, 1.0),
            NormalizedPoint(1.0, 1.0),
            NormalizedPoint(0.5, 0.5),
            NormalizedPoint(-0.1, 1.1),
        )

        assertEquals(6, points.map { geometry.project(it) }.size)
        assertFalse(geometry.project(points[4]).outOfViewport)
        assertTrue(points.last().outOfFrame)
    }

    @Test
    fun composition_nonFullCropRotationCenterCropViewportAndFrontMirror_hasHandCalculatedResults() {
        val crop = CropGeometry.centerCrop(sourceAspectRatio = 4.0 / 3.0, targetAspectRatio = 1.0)
        val sensorPoint = NormalizedPoint(0.2, 0.7)
        val portrait = PreviewGeometry.centerCrop(100.0, 100.0, 50.0, 100.0)
        val wide = PreviewGeometry.centerCrop(100.0, 100.0, 200.0, 100.0)

        val at90 = PoseCoordinateMapper.sensorToViewport(
            sensorPoint,
            PoseCoordinateTransform(90),
            crop,
            portrait,
            mirrorForDisplay = true,
        )
        // 90° => (0.3, 0.2); crop => (7/30, 0.2); front mirror => (23/30, 0.2).
        assertEquals(51.66666666666667, at90.xPx, epsilon)
        assertEquals(20.0, at90.yPx, epsilon)

        val at270 = PoseCoordinateMapper.sensorToViewport(
            sensorPoint,
            PoseCoordinateTransform(270),
            crop,
            wide,
            mirrorForDisplay = true,
        )
        // 270° => (0.7, 0.8); crop => (23/30, 0.8); front mirror => (7/30, 0.8).
        assertEquals(46.66666666666667, at270.xPx, epsilon)
        assertEquals(110.0, at270.yPx, epsilon)
        assertTrue(at270.outOfViewport)
    }
}

private fun assertPoint(actual: NormalizedPoint, expectedX: Double, expectedY: Double) {
    assertEquals(expectedX, actual.x, 1e-9)
    assertEquals(expectedY, actual.y, 1e-9)
}
