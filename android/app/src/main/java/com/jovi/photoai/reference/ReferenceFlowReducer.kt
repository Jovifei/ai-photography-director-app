package com.jovi.photoai.reference

/** Pure Phase 1 flow used by the root navigator and JVM tests. */
enum class ReferenceFlowStage {
    IMPORT,
    ANALYSIS,
    DIRECTOR_CARD,
    CAMERA_DIRECTOR,
}

fun advanceReferenceFlow(stage: ReferenceFlowStage): ReferenceFlowStage = when (stage) {
    ReferenceFlowStage.IMPORT -> ReferenceFlowStage.ANALYSIS
    ReferenceFlowStage.ANALYSIS -> ReferenceFlowStage.DIRECTOR_CARD
    ReferenceFlowStage.DIRECTOR_CARD -> ReferenceFlowStage.CAMERA_DIRECTOR
    ReferenceFlowStage.CAMERA_DIRECTOR -> ReferenceFlowStage.CAMERA_DIRECTOR
}

fun backReferenceFlow(stage: ReferenceFlowStage): ReferenceFlowStage = when (stage) {
    ReferenceFlowStage.CAMERA_DIRECTOR -> ReferenceFlowStage.DIRECTOR_CARD
    ReferenceFlowStage.DIRECTOR_CARD -> ReferenceFlowStage.ANALYSIS
    ReferenceFlowStage.ANALYSIS -> ReferenceFlowStage.IMPORT
    ReferenceFlowStage.IMPORT -> ReferenceFlowStage.IMPORT
}
