package com.jovi.photoai.ui.capture

import com.jovi.photoai.data.capture.CaptureRecord
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.ui.isRealAiGuidanceReady

/** A current association lookup, never a snapshot of guidance at capture time. */
internal data class CaptureReferenceContext(
    val reference: ReferenceRecord? = null,
    val sourceLabel: String,
    val statusLabel: String,
    val unavailableReason: String? = null,
) {
    val canOpen: Boolean get() = reference != null
    val canRetake: Boolean get() = reference?.let {
        isRealAiGuidanceReady(it.analysisStatus, it.analysisProvenance != null, it.knowledgeBundleProvenance != null)
    } == true
}

internal fun resolveCaptureReferenceContext(
    capture: CaptureRecord,
    projects: List<PhotographyProject>,
    references: List<ReferenceRecord>,
): CaptureReferenceContext {
    fun missing(reason: String) = CaptureReferenceContext(sourceLabel = "关联参考图不可用", statusLabel = reason, unavailableReason = reason)
    val id = capture.referenceId ?: return CaptureReferenceContext(sourceLabel = "直接拍摄 · 无关联参考图", statusLabel = "未记录参考图关联")
    val projectId = capture.projectId ?: return missing("拍摄项目关联已失效")
    if (projects.none { it.id == projectId }) return missing("拍摄项目已不存在")
    val reference = references.singleOrNull { it.photo.id == id } ?: return missing("关联参考图已不存在")
    if (reference.projectId != projectId) return missing("参考图与拍摄项目不匹配")
    val source = when {
        reference.knowledgeBundleProvenance != null -> "当前来源：${reference.photo.sourceLabel} · 知识 Bundle · ${reference.knowledgeBundleProvenance.producerId} / ${reference.knowledgeBundleProvenance.releaseId}"
        reference.analysisProvenance != null -> "当前来源：${reference.photo.sourceLabel} · 分析服务 · ${reference.analysisProvenance.providerId} / ${reference.analysisProvenance.modelRevision}"
        else -> "当前来源：${reference.photo.sourceLabel} · 导入参考图／示例指导"
    }
    val trusted = reference.analysisProvenance != null || reference.knowledgeBundleProvenance != null
    val status = when (reference.analysisStatus) {
        PhotoAnalysisStatus.READY -> if (trusted) "当前指导可用" else "当前指导缺少可信来源"
        PhotoAnalysisStatus.EXAMPLE_GUIDANCE -> "当前为示例指导"
        PhotoAnalysisStatus.IMPORTED -> "当前尚未分析"
        PhotoAnalysisStatus.QUEUED -> "当前等待分析"
        PhotoAnalysisStatus.RUNNING -> "当前正在分析"
        PhotoAnalysisStatus.FAILED -> "当前分析失败"
        PhotoAnalysisStatus.CANCELLED -> "当前分析已取消"
        PhotoAnalysisStatus.UNAVAILABLE -> "当前指导不可用"
    }
    return CaptureReferenceContext(reference, source, status)
}
