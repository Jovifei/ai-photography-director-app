package com.jovi.photoai.domain.pose

/**
 * The app-owned 33-point vocabulary. The explicit canonical index is the only
 * index exposed to adapters; no SDK enum ordinal is part of this contract.
 */
enum class PoseKeypoint33(
    val canonicalIndex: Int,
    val sdkName: String,
) {
    NOSE(0, "NOSE"),
    LEFT_EYE_INNER(1, "LEFT_EYE_INNER"),
    RIGHT_EYE_INNER(2, "RIGHT_EYE_INNER"),
    LEFT_EYE(3, "LEFT_EYE"),
    RIGHT_EYE(4, "RIGHT_EYE"),
    LEFT_EYE_OUTER(5, "LEFT_EYE_OUTER"),
    RIGHT_EYE_OUTER(6, "RIGHT_EYE_OUTER"),
    LEFT_EAR(7, "LEFT_EAR"),
    RIGHT_EAR(8, "RIGHT_EAR"),
    LEFT_MOUTH(9, "LEFT_MOUTH"),
    RIGHT_MOUTH(10, "RIGHT_MOUTH"),
    LEFT_SHOULDER(11, "LEFT_SHOULDER"),
    RIGHT_SHOULDER(12, "RIGHT_SHOULDER"),
    LEFT_ELBOW(13, "LEFT_ELBOW"),
    RIGHT_ELBOW(14, "RIGHT_ELBOW"),
    LEFT_WRIST(15, "LEFT_WRIST"),
    RIGHT_WRIST(16, "RIGHT_WRIST"),
    LEFT_PINKY(17, "LEFT_PINKY"),
    RIGHT_PINKY(18, "RIGHT_PINKY"),
    LEFT_INDEX(19, "LEFT_INDEX"),
    RIGHT_INDEX(20, "RIGHT_INDEX"),
    LEFT_THUMB(21, "LEFT_THUMB"),
    RIGHT_THUMB(22, "RIGHT_THUMB"),
    LEFT_HIP(23, "LEFT_HIP"),
    RIGHT_HIP(24, "RIGHT_HIP"),
    LEFT_KNEE(25, "LEFT_KNEE"),
    RIGHT_KNEE(26, "RIGHT_KNEE"),
    LEFT_ANKLE(27, "LEFT_ANKLE"),
    RIGHT_ANKLE(28, "RIGHT_ANKLE"),
    LEFT_HEEL(29, "LEFT_HEEL"),
    RIGHT_HEEL(30, "RIGHT_HEEL"),
    LEFT_FOOT_INDEX(31, "LEFT_FOOT_INDEX"),
    RIGHT_FOOT_INDEX(32, "RIGHT_FOOT_INDEX");

    companion object {
        val canonicalOrder: List<PoseKeypoint33> = entries.sortedBy(PoseKeypoint33::canonicalIndex)

        fun fromCanonicalIndex(index: Int): PoseKeypoint33? =
            canonicalOrder.firstOrNull { it.canonicalIndex == index }
    }
}
