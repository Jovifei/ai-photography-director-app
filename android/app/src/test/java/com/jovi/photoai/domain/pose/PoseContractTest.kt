package com.jovi.photoai.domain.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseContractTest {
    @Test
    fun canonicalSchema_hasExactly33ExplicitNamesAndIndexes() {
        val expected = listOf(
            "NOSE", "LEFT_EYE_INNER", "RIGHT_EYE_INNER", "LEFT_EYE", "RIGHT_EYE",
            "LEFT_EYE_OUTER", "RIGHT_EYE_OUTER", "LEFT_EAR", "RIGHT_EAR", "LEFT_MOUTH",
            "RIGHT_MOUTH", "LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW",
            "LEFT_WRIST", "RIGHT_WRIST", "LEFT_PINKY", "RIGHT_PINKY", "LEFT_INDEX",
            "RIGHT_INDEX", "LEFT_THUMB", "RIGHT_THUMB", "LEFT_HIP", "RIGHT_HIP",
            "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE", "LEFT_HEEL",
            "RIGHT_HEEL", "LEFT_FOOT_INDEX", "RIGHT_FOOT_INDEX",
        )

        assertEquals(33, PoseKeypoint33.entries.size)
        assertEquals(expected, PoseKeypoint33.canonicalOrder.map { it.name })
        assertEquals((0..32).toList(), PoseKeypoint33.canonicalOrder.map { it.canonicalIndex })
        assertEquals(PoseKeypoint33.LEFT_SHOULDER, PoseKeypoint33.fromCanonicalIndex(11))
        assertNull(PoseKeypoint33.fromCanonicalIndex(33))
    }

    @Test
    fun missingPointsRemainNullAndOutOfFrameIsNotClamped() {
        val point = PosePoint(
            keypoint = PoseKeypoint33.NOSE,
            xNorm = 1.2,
            yNorm = -0.1,
            visibility = null,
            presence = null,
            inFrameLikelihood = null,
        )
        val person = PosePerson(mapOf(PoseKeypoint33.NOSE to point))

        assertNull(person[PoseKeypoint33.LEFT_EYE])
        assertTrue(point.isInFrame.not())
        assertTrue(point.isOccludedOrUncertain.not())
        assertNull(point.visibility)
    }

    @Test
    fun estimateStatesDoNotConflateNoPersonWithEngineError() {
        val noPerson = PoseEstimate(1L, 0L, 1L, PoseEngineId.FAKE, PoseState.NO_PERSON)
        val multiPersonUnknown = PoseEstimate(1L, 0L, 1L, PoseEngineId.FAKE, PoseState.MULTI_PERSON_UNKNOWN)
        val error = PoseEstimate(
            frameId = 1L,
            generation = 0L,
            timestampNs = 1L,
            engineId = PoseEngineId.FAKE,
            state = PoseState.ENGINE_ERROR,
            diagnostics = PoseDiagnostics(message = "timeout"),
        )

        assertEquals(PoseState.NO_PERSON, noPerson.state)
        assertEquals(PoseState.ENGINE_ERROR, error.state)
        assertNull(noPerson.person)
        assertNull(multiPersonUnknown.person)
    }
}
