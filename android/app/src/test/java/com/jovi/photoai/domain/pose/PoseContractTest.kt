package com.jovi.photoai.domain.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun staleAndCancelledTerminalEstimates_areValidNoPersonStates() {
        listOf(PoseState.STALE_RESULT_DROPPED, PoseState.CANCELLED).forEach { state ->
            val estimate = PoseEstimate(
                frameId = 1L,
                generation = 0L,
                timestampNs = 1L,
                engineId = PoseEngineId.FAKE,
                state = state,
            )
            assertEquals(state, estimate.state)
            assertNull(estimate.person)
            assertEquals(0, estimate.diagnostics.pointCount)
        }
    }

    @Test
    fun estimateStateInvariant_matrixRejectsInvalidAndAcceptsCanonicalTrackedPartial() {
        val point = PosePoint(PoseKeypoint33.NOSE, 0.5, 0.5)
        val person = PosePerson(mapOf(PoseKeypoint33.NOSE to point))
        val emptyPerson = PosePerson(emptyMap())

        assertEquals(PoseState.TRACKED, estimate(PoseState.TRACKED, person, 1).state)
        assertEquals(PoseState.PARTIAL_OR_LOW_CONFIDENCE, estimate(PoseState.PARTIAL_OR_LOW_CONFIDENCE, person, 1).state)

        assertInvalid(PoseState.TRACKED, null, 0, "TRACKED requires a non-null person")
        assertInvalid(PoseState.TRACKED, emptyPerson, 0, "TRACKED requires at least one canonical point")
        assertInvalid(PoseState.PARTIAL_OR_LOW_CONFIDENCE, null, 0, "PARTIAL_OR_LOW_CONFIDENCE requires a non-null person")
        assertInvalid(
            PoseState.PARTIAL_OR_LOW_CONFIDENCE,
            emptyPerson,
            0,
            "PARTIAL_OR_LOW_CONFIDENCE requires at least one canonical point",
        )
        PoseState.entries.filter { it != PoseState.TRACKED && it != PoseState.PARTIAL_OR_LOW_CONFIDENCE }.forEach { state ->
            assertInvalid(state, person, 1, "$state must not carry a person")
            assertInvalid(state, null, 1, "$state requires zero diagnostic points")
        }
        assertInvalid(PoseState.TRACKED, person, 0, "diagnostics.pointCount must match person.presentPointCount")
    }

    private fun estimate(state: PoseState, person: PosePerson?, pointCount: Int): PoseEstimate = PoseEstimate(
        frameId = 1L,
        generation = 0L,
        timestampNs = 1L,
        engineId = PoseEngineId.FAKE,
        state = state,
        person = person,
        diagnostics = PoseDiagnostics(pointCount = pointCount),
    )

    private fun assertInvalid(state: PoseState, person: PosePerson?, pointCount: Int, message: String) {
        try {
            estimate(state, person, pointCount)
            fail("expected IllegalArgumentException for $state")
        } catch (error: IllegalArgumentException) {
            assertEquals(message, error.message)
        }
    }
}
