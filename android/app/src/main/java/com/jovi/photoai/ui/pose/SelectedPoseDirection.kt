package com.jovi.photoai.ui.pose

import com.jovi.photoai.pose.StickFigureGraphic

/**
 * Composition snapshot carried into shoot / retake.
 * Null selection means the camera behaves as before (no fake direction).
 */
internal data class SelectedPoseDirection(
    val id: String,
    val spokenDirection: String,
    val graphic: StickFigureGraphic?,
    val figureNote: String? = null,
)
