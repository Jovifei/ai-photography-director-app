package com.jovi.photoai.data.reference

import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSummaryTest {
    @Test
    fun summary_countsOnlyProviderReadyPhotos_asRealAnalysis() {
        val summary = projectSummaryOf(
            records = listOf(
                record("ready", PhotoAnalysisStatus.READY),
                record("example", PhotoAnalysisStatus.EXAMPLE_GUIDANCE),
                record("unavailable", PhotoAnalysisStatus.UNAVAILABLE),
            ),
            failedImportCount = 2,
        )

        assertEquals(3, summary.importedCount)
        assertEquals(1, summary.providerReadyCount)
        assertEquals(1, summary.exampleGuidanceCount)
        assertEquals(1, summary.unavailableCount)
        assertEquals(2, summary.failedImportCount)
        assertEquals(4, summary.excludedCount)
    }

    @Test
    fun summary_combinesOnlyReadyPhotos_andUsesPrimaryForShootFirstPlan() {
        val primary = record("primary", PhotoAnalysisStatus.READY, scene = "室内", prompt = "先拍主参考")
        val summary = projectSummaryOf(
            records = listOf(
                primary,
                record("second", PhotoAnalysisStatus.READY, scene = "室内", prompt = "第二张建议"),
                record("example", PhotoAnalysisStatus.EXAMPLE_GUIDANCE, scene = "海边", prompt = "示例不应进入汇总"),
            ),
            failedImportCount = 0,
            primaryReferenceId = primary.photo.id,
        )

        assertEquals(listOf("场景：室内", "光线：光线", "构图：构图", "主体意图：主体", "情绪：情绪", "机位：机位"), summary.sharedDirections)
        assertEquals(emptyList<String>(), summary.differingDirectionLabels)
        assertEquals("先拍主参考", summary.shootFirstPlan)
    }

    private fun record(
        id: String,
        status: PhotoAnalysisStatus,
        scene: String = "场景",
        prompt: String = "提示",
    ) = ReferenceRecord(
        photo = ReferencePhoto(id, "照片", "仅用于测试", "示例指导", "$id.jpg", 1f),
        bundle = ReferenceBundle(id, scene, "背景", "光线", "构图", "主体", "情绪", "姿态", "机位", prompt, "1"),
        imageFileName = "$id.jpg",
        createdAtEpochMillis = 1L,
        projectId = "project",
        analysisStatus = status,
    )
}
