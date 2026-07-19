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
        require(state == PoseState.TRACKED || state == PoseState.PARTIAL_OR_LOW_CONFIDENCE || person == null) {
            "terminal/no-person estimates must not carry a person"
        }
        require(diagnostics.pointCount == (person?.presentPointCount ?: 0)) {
            "diagnostics point count must match the canonical person"
        }
    }
}
