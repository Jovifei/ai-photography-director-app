package com.jovi.photoai.domain.pose

/** A single person; absent points remain absent and are never interpolated. */
data class PosePerson(
    val points: Map<PoseKeypoint33, PosePoint>,
) {
    init {
        require(points.all { (key, value) -> key == value.keypoint }) {
            "point map keys must match point.keypoint"
        }
    }

    operator fun get(keypoint: PoseKeypoint33): PosePoint? = points[keypoint]

    val presentPointCount: Int
        get() = points.size
}
