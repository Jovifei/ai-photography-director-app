package com.jovi.photoai.domain.pose

enum class PoseState {
    TRACKED,
    NO_PERSON,
    PARTIAL_OR_LOW_CONFIDENCE,
    MULTI_PERSON_UNKNOWN,
    ENGINE_ERROR,
    STALE_RESULT_DROPPED,
    CANCELLED,
}
