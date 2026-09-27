package com.jovi.photoai.ui.home

import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceSearchTest {
    private val reference = SearchableReference(
        id = "r1",
        title = "窗边柔光人像",
        scene = "窗边",
        lighting = "柔和侧光",
        composition = "人物位于右侧三分线",
        tags = setOf("人像", "留白"),
    )

    @Test
    fun `search matches title scene lighting composition and tags`() {
        listOf("柔光", "窗边", "三分线", "留白").forEach { query ->
            assertTrue(reference.matches(query, sceneFilter = null))
        }
    }

    @Test
    fun `scene filter combines with search`() {
        assertTrue(reference.matches("人像", sceneFilter = "窗边"))
        assertFalse(reference.matches("人像", sceneFilter = "街道"))
        assertFalse(reference.matches("夜景", sceneFilter = "窗边"))
    }

    private val projectRecords = listOf(
        record("r1", "窗边肖像", "室内", "本机 Qwen", "p1", PhotoAnalysisStatus.READY),
        record("r2", "城市夜色", "街道", "离线知识包", "p2", PhotoAnalysisStatus.READY),
        record("r3", "窗边等待", "室内", "示例指导", "p1", PhotoAnalysisStatus.EXAMPLE_GUIDANCE),
    )
    private val projectTitles = mapOf("p1" to "小满人像", "p2" to "夜间街拍")

    @Test
    fun `project library query searches title scene source and project title across projects`() {
        val expectations = mapOf(
            "肖像" to listOf("r1"),
            "街道" to listOf("r2"),
            "qWEN" to listOf("r1"),
            "夜间街拍" to listOf("r2"),
            "  小满  " to listOf("r1", "r3"),
        )
        expectations.forEach { (query, expected) ->
            assertEquals(expected, filteredIds(ProjectReferenceFilters(query = query)))
        }
    }

    @Test
    fun `project library filters combine scene source status and project exactly`() {
        assertEquals(
            listOf("r1"),
            filteredIds(
                ProjectReferenceFilters(
                    query = "窗边",
                    scene = "室内",
                    source = "本机 Qwen",
                    status = PhotoAnalysisStatus.READY,
                    projectId = "p1",
                ),
            ),
        )
        assertEquals(emptyList<String>(), filteredIds(ProjectReferenceFilters(scene = "室")))
        assertEquals(emptyList<String>(), filteredIds(ProjectReferenceFilters(projectId = "p2", status = PhotoAnalysisStatus.FAILED)))
        assertEquals(listOf("r2"), filteredIds(ProjectReferenceFilters(source = "离线知识包", status = PhotoAnalysisStatus.READY)))
    }

    @Test
    fun `project library projection preserves record order and unknown project identity`() {
        val items = filterProjectReferences(projectRecords, mapOf("p1" to "小满人像"), ProjectReferenceFilters())
        assertEquals(listOf("r1", "r2", "r3"), items.map { it.record.photo.id })
        assertEquals(listOf("小满人像", null, "小满人像"), items.map { it.projectTitle })
        assertEquals(emptyList<String>(),
            filterProjectReferences(projectRecords, emptyMap(), ProjectReferenceFilters(query = "夜间街拍"))
                .map { it.record.photo.id })
    }

    private fun filteredIds(filters: ProjectReferenceFilters): List<String> =
        filterProjectReferences(projectRecords, projectTitles, filters).map { it.record.photo.id }

    private fun record(
        id: String,
        title: String,
        scene: String,
        source: String,
        projectId: String,
        status: PhotoAnalysisStatus,
    ): ReferenceRecord = ReferenceRecord(
        photo = ReferencePhoto(id, title, "合成描述", source, "opaque-$id", 1f),
        bundle = ReferenceBundle(
            referenceId = id,
            scene = scene,
            backgroundStory = "背景",
            lighting = "光线",
            composition = "构图",
            subjectIntent = "人物",
            emotion = "情绪",
            poseTemplate = "姿态",
            cameraPosition = "机位",
            directorPrompt = "指导",
            version = ReferenceBundle.CURRENT_VERSION,
        ),
        imageFileName = "$id.jpg",
        createdAtEpochMillis = 1L,
        projectId = projectId,
        analysisStatus = status,
    )
}
