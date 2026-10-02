package com.jovi.photoai.ui.capture

import com.jovi.photoai.data.capture.CaptureRecord
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import org.junit.Assert.*
import org.junit.Test

class CaptureReferenceContextTest {
    private val project = PhotographyProject("project", "合成项目", null, 0, 1, 1)
    private val capture = CaptureRecord("capture", "project", "reference", 1)
    private val reference = ReferenceRecord(
        ReferencePhoto("reference", "合成图", "测试", "测试", "reference.jpg", 1f),
        ReferenceBundle("reference", "场景", "背景", "光线", "构图", "主体", "情绪", "姿态", "机位", "提示", "1"),
        "reference.jpg", 1, projectId = "project",
    )
    private fun resolve(row: CaptureRecord = capture, refs: List<ReferenceRecord> = listOf(reference),
        projects: List<PhotographyProject> = listOf(project)) = resolveCaptureReferenceContext(row, projects, refs)

    @Test fun directCaptureNeverGuessesReference() {
        val context = resolve(capture.copy(referenceId = null))
        assertFalse(context.canOpen); assertFalse(context.canRetake)
        assertTrue(context.sourceLabel.contains("直接拍摄"))
    }
    @Test fun exactAssociationRequiresExistingMatchingNonNullProject() {
        assertFalse(resolve(capture.copy(projectId = null)).canOpen)
        assertFalse(resolve(projects = emptyList()).canOpen)
        assertFalse(resolve(refs = emptyList()).canOpen)
        assertFalse(resolve(refs = listOf(reference.copy(projectId = "other"))).canOpen)
        assertFalse(resolve(capture.copy(referenceId = "different")).canOpen)
        assertFalse(resolve(refs = listOf(reference, reference)).canOpen)
    }
    @Test fun exampleCanOpenButCannotRetakeAndDoesNotMutate() {
        val context = resolve()
        assertEquals(reference, context.reference); assertTrue(context.canOpen); assertFalse(context.canRetake)
        assertEquals(PhotoAnalysisStatus.EXAMPLE_GUIDANCE, reference.analysisStatus)
    }
    @Test fun bareReadyCannotRetake() {
        assertFalse(resolve(refs = listOf(reference.copy(analysisStatus = PhotoAnalysisStatus.READY))).canRetake)
    }
    @Test fun currentBundleReadyCanRetakeButLaterFailureCannot() {
        val ready = reference.copy(analysisStatus = PhotoAnalysisStatus.READY,
            knowledgeBundleProvenance = KnowledgeBundleProvenance("bundle_001", "ref_001", "pipeline",
                KnowledgeBundleOrigin.PIPELINE, "release_001", "a".repeat(64), 2))
        assertTrue(resolve(refs = listOf(ready)).canRetake)
        assertTrue(resolve(refs = listOf(ready)).sourceLabel.contains("Bundle"))
        assertFalse(resolve(refs = listOf(ready.copy(analysisStatus = PhotoAnalysisStatus.FAILED))).canRetake)
    }
    @Test fun currentProviderReadyCanRetake() {
        val ready = reference.copy(analysisStatus = PhotoAnalysisStatus.READY,
            analysisProvenance = ProviderAnalysisProvenance("provider", ProviderType.LOCAL_SERVICE,
                "model", "revision", "a".repeat(64), "runtime", 2))
        assertTrue(resolve(refs = listOf(ready)).canRetake)
        assertTrue(resolve(refs = listOf(ready)).sourceLabel.contains("分析服务"))
    }
}
