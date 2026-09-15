package com.jovi.photoai.ui

import com.jovi.photoai.data.reference.PhotoAnalysisStatus

/** A READY flag alone is insufficient: a durable, validated provenance must also exist. */
internal fun isRealAiGuidanceReady(
    status: PhotoAnalysisStatus?,
    hasProviderProvenance: Boolean,
    hasKnowledgeBundleProvenance: Boolean,
): Boolean = status == PhotoAnalysisStatus.READY && (hasProviderProvenance || hasKnowledgeBundleProvenance)

internal fun guardedGuidanceDestination(
    requested: AppDestination,
    status: PhotoAnalysisStatus?,
    hasProviderProvenance: Boolean,
    hasKnowledgeBundleProvenance: Boolean,
): AppDestination = when {
    requested in setOf(AppDestination.DIRECTOR_CARD, AppDestination.CAMERA_DIRECTOR) &&
        !isRealAiGuidanceReady(status, hasProviderProvenance, hasKnowledgeBundleProvenance) -> AppDestination.CAPTURE_ENTRY
    else -> requested
}

internal fun offlineCaptureNotice(
    status: PhotoAnalysisStatus?,
    hasProviderProvenance: Boolean = false,
    hasKnowledgeBundleProvenance: Boolean = false,
): String = if (isRealAiGuidanceReady(status, hasProviderProvenance, hasKnowledgeBundleProvenance)) {
    ""
} else {
    "该参考尚未完成真实分析，本次拍摄不会使用 AI 指导。"
}
