package com.jovi.photoai.ui

import com.jovi.photoai.data.reference.PhotoAnalysisStatus

/** P21 truthfulness boundary: only a verified Provider result may guide capture. */
internal fun isRealAiGuidanceReady(status: PhotoAnalysisStatus?): Boolean =
    status == PhotoAnalysisStatus.READY

internal fun guardedGuidanceDestination(
    requested: AppDestination,
    status: PhotoAnalysisStatus?,
): AppDestination = when {
    requested in setOf(AppDestination.DIRECTOR_CARD, AppDestination.CAMERA_DIRECTOR) &&
        !isRealAiGuidanceReady(status) -> AppDestination.CAPTURE_ENTRY
    else -> requested
}

internal fun offlineCaptureNotice(status: PhotoAnalysisStatus?): String = when (status) {
    PhotoAnalysisStatus.READY -> ""
    else -> "该参考尚未完成真实分析，本次拍摄不会使用 AI 指导。"
}
