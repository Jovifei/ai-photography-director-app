package com.jovi.photoai.domain.pose

data class PoseEstimate(
    val frameId: Long,
    val generation: Long,
    val timestampNs: Long,
    val engineId: PoseEngineId,
    val state: PoseState,
    val person: PosePerson? = null,
    val diagnostics: PoseDiagnostics = PoseDiagnostics(),
) {
    init {
        require(frameId >= 0) { "frameId must be non-negative" }
        require(generation >= 0) { "generation must be non-negative" }
        require(timestampNs >= 0) { "timestampNs must be non-negative" }
        when (state) {
            PoseState.TRACKED -> {
                require(person != null) { "TRACKED requires a non-null person" }
                require(person.presentPointCount > 0) { "TRACKED requires at least one canonical point" }
                require(diagnostics.pointCount == person.presentPointCount) {
                    "diagnostics.pointCount must match person.presentPointCount"
                }
            }
            PoseState.PARTIAL_OR_LOW_CONFIDENCE -> {
                require(person != null) { "PARTIAL_OR_LOW_CONFIDENCE requires a non-null person" }
                require(person.presentPointCount > 0) {
                    "PARTIAL_OR_LOW_CONFIDENCE requires at least one canonical point"
                }
                require(diagnostics.pointCount == person.presentPointCount) {
                    "diagnostics.pointCount must match person.presentPointCount"
                }
            }
            else -> {
                require(person == null) { "$state must not carry a person" }
                require(diagnostics.pointCount == 0) { "$state requires zero diagnostic points" }
            }
        }
    }
}
