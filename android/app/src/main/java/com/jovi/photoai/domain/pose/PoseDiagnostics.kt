package com.jovi.photoai.domain.pose

data class PoseDiagnostics(
    val processingDurationMs: Double? = null,
    val message: String? = null,
    val pointCount: Int = 0,
) {
    init {
        require(processingDurationMs == null || processingDurationMs.isFinite()) {
            "processingDurationMs must be finite"
        }
        require(pointCount >= 0)
    }
}
