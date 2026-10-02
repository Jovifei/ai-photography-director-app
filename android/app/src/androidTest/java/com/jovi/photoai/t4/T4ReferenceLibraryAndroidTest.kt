package com.jovi.photoai.t4

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.Density
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.home.ProjectReferenceFilters
import com.jovi.photoai.ui.home.filterProjectReferences
import com.jovi.photoai.ui.reference.ReferenceLibraryEntry
import com.jovi.photoai.ui.reference.ReferenceLibraryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class T4ReferenceLibraryAndroidTest {
    @get:Rule val rule = createComposeRule()

    @Test fun allProjectSearchAndSceneFilterChangeResultsAndPreserveOwner() {
        val records = listOf(
            record("a", "窗边参考", "窗边", "示例指导 · 非图片分析", "project-a", PhotoAnalysisStatus.IMPORTED),
            record("b", "街道参考", "街道", "合成离线知识包", "project-b", PhotoAnalysisStatus.READY),
        )
        val titles = mapOf("project-a" to "甲项目", "project-b" to "乙项目")
        var openedProject: String? = null
        var openedReference: String? = null
        var selectedScene: String? = null
        rule.setContent {
            var filters by androidx.compose.runtime.remember { mutableStateOf(ProjectReferenceFilters()) }
            val entries = records.map { row ->
                ReferenceLibraryEntry(row.photo, row.bundle, row.imageFileName,
                    row.projectId, titles[row.projectId], row.analysisStatus)
            }
            val visibleIds = filterProjectReferences(records, titles, filters).map { it.record.photo.id }.toSet()
            PhotoDirectorTheme {
                ReferenceLibraryScreen(
                    entries = entries,
                    visibleEntries = entries.filter { it.photo.id in visibleIds },
                    filters = filters,
                    onFiltersChange = { filters = it; selectedScene = it.scene },
                    onBack = {}, onImportReference = {}, onDeleteReference = {}, onClearAll = {},
                    onOpenReference = { openedReference = it },
                    onOpenProject = { openedProject = it },
                )
            }
        }

        rule.onNodeWithText("窗边参考").assertExists()
        rule.onNodeWithText("街道参考").assertExists()
        rule.onNodeWithTag("reference-filter-场景-街道").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("街道", selectedScene) }
        rule.onNodeWithText("窗边参考").assertDoesNotExist()
        rule.onNodeWithText("街道参考").assertExists()
        rule.onNodeWithTag("reference-filter-场景-all").performScrollTo().performClick()
        rule.onNodeWithTag("reference-library-search").performTextInput("乙项目")
        rule.onNodeWithText("窗边参考").assertDoesNotExist()
        rule.onNodeWithText("街道参考").assertExists()
        rule.onNodeWithText("打开所属项目").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("project-b", openedProject) }
        rule.onNodeWithText("街道参考").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("b", openedReference) }

    }

    @Test fun libraryActionsRemainReachableAtDoubleFontScale() {
        val row = record("large", "大字参考", "室内", "示例指导 · 非图片分析", "project-large", PhotoAnalysisStatus.IMPORTED)
        val entry = ReferenceLibraryEntry(row.photo, row.bundle, row.imageFileName,
            row.projectId, "大字项目", row.analysisStatus)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                PhotoDirectorTheme {
                    ReferenceLibraryScreen(
                        entries = listOf(entry), onBack = {}, onImportReference = {},
                        onOpenReference = {}, onDeleteReference = {}, onClearAll = {},
                        onOpenProject = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("reference-library-search").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("打开所属项目").performScrollTo().assertIsDisplayed()
    }

    private fun record(
        id: String, title: String, scene: String, source: String, projectId: String,
        status: PhotoAnalysisStatus,
    ): ReferenceRecord {
        val photo = ReferencePhoto(id, title, "synthetic", source, "private/$id.jpg", 1f)
        val bundle = ReferenceBundle(id, scene, "背景", "光线", "构图", "主体", "情绪", "姿态", "机位", "指导", "1.0")
        return ReferenceRecord(photo, bundle, "$id.jpg", 1L, projectId, analysisStatus = status)
    }
}
