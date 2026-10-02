package com.jovi.photoai.t12

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.MainActivity
import com.jovi.photoai.ui.capture.*
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.p25u.P25UCaptureFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class T12CaptureUiAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun directCaptureShowsHonestAssociationAndDisabledActions() {
        P25UCaptureFixture().use { fixture ->
            val row = fixture.ready(referenceId = null)
            rule.activity.runOnUiThread {
                rule.activity.setContent { PhotoDirectorTheme {
                    CaptureLibraryContent(CaptureLibraryUiState(ready = true, records = listOf(row), selectedId = row.id),
                        mapOf("test-project" to "合成项目"), { fixture.files.verify(it) }, {}, {}, {}, {}, {}, {}, {},
                        referenceContexts = mapOf(row.id to resolveCaptureReferenceContext(row, emptyList(), emptyList())),
                        onOpenReference = {}, onRetake = {})
                } }
            }
            rule.onNodeWithText("直接拍摄 · 无关联参考图").performScrollTo().assertExists()
            rule.onNodeWithTag("capture-open-reference").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("capture-retake").performScrollTo().assertIsNotEnabled()
            rule.activity.runOnUiThread { rule.activity.setContent {} }
            rule.waitForIdle()
        }
    }
    @Test fun linkedListAndDetailShowCurrentTitleStatusAndDispatchExactCapture() {
        P25UCaptureFixture().use { fixture ->
            val row = fixture.ready()
            val reference = reference().copy(analysisStatus = PhotoAnalysisStatus.READY,
                knowledgeBundleProvenance = KnowledgeBundleProvenance("bundle_001", "ref_001", "pipeline",
                    KnowledgeBundleOrigin.PIPELINE, "release_001", "a".repeat(64), 2))
            val context = resolveCaptureReferenceContext(row, listOf(project()), listOf(reference))
            val state = mutableStateOf(CaptureLibraryUiState(ready = true, records = listOf(row)))
            val busy = mutableStateOf(false)
            var opened: String? = null
            var retaken: String? = null
            rule.activity.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
                CaptureLibraryContent(state.value, mapOf("test-project" to "合成项目"), { fixture.files.verify(it) }, {},
                    { state.value = state.value.copy(selectedId = it) }, {}, {}, {}, {}, {},
                    referenceContexts = mapOf(row.id to context), onOpenReference = { opened = it },
                    onRetake = { retaken = it }, navigationInFlight = busy.value)
            } } }
            rule.onNodeWithText("当前参考图：第 1 张 · 合成参考图").assertExists()
            rule.onNodeWithText("当前指导可用").assertExists()
            rule.onNodeWithTag("capture-${row.id}").performClick()
            rule.onNodeWithText(context.sourceLabel).performScrollTo().assertExists()
            rule.onNodeWithTag("capture-open-reference").performScrollTo().performClick()
            rule.runOnIdle { assertEquals(row.id, opened) }
            rule.onNodeWithTag("capture-retake").performScrollTo().performClick()
            rule.runOnIdle { assertEquals(row.id, retaken); busy.value = true }
            rule.onNodeWithTag("capture-open-reference").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("capture-retake").performScrollTo().assertIsNotEnabled()
            dispose()
        }
    }

    @Test fun missingReferenceAndBareReadyCannotRetake() {
        P25UCaptureFixture().use { fixture ->
            val row = fixture.ready()
            val context = mutableStateOf(resolveCaptureReferenceContext(row, listOf(project()), emptyList()))
            rule.activity.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
                CaptureLibraryContent(CaptureLibraryUiState(ready = true, records = listOf(row), selectedId = row.id),
                    mapOf("test-project" to "合成项目"), { fixture.files.verify(it) }, {}, {}, {}, {}, {}, {}, {},
                    referenceContexts = mapOf(row.id to context.value), onOpenReference = {}, onRetake = {})
            } } }
            rule.onNodeWithText("关联参考图已不存在").performScrollTo().assertExists()
            rule.onNodeWithTag("capture-open-reference").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("capture-retake").performScrollTo().assertIsNotEnabled()
            rule.runOnIdle { context.value = resolveCaptureReferenceContext(row, listOf(project()),
                listOf(reference().copy(analysisStatus = PhotoAnalysisStatus.READY))) }
            rule.onNodeWithText("当前指导缺少可信来源").performScrollTo().assertExists()
            rule.onNodeWithTag("capture-open-reference").performScrollTo().assertIsEnabled()
            rule.onNodeWithTag("capture-retake").performScrollTo().assertIsNotEnabled()
            dispose()
        }
    }

    private fun dispose() {
        rule.activity.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
    }
    private fun project() = PhotographyProject("test-project", "合成项目", null, 0, 1, 1)
    private fun reference() = ReferenceRecord(
        ReferencePhoto("test-reference", "合成参考图", "测试来源", "测试", "test-reference.jpg", 1f),
        ReferenceBundle("test-reference", "场景", "背景", "光线", "构图", "主体", "情绪", "姿态", "机位", "提示", "1"),
        "test-reference.jpg", 1, projectId = "test-project")
}
