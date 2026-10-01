package com.jovi.photoai.t12

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.CaptureRepository
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.p25u.P25UCaptureFixture
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real root, persisted association and integrity-checked synthetic PKB1; no producer release claim. */
@RunWith(AndroidJUnit4::class)
class T12CaptureNavigationAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun bundleCaptureOpensCurrentReference() = fixture { _, _, capture ->
        openCapture(capture)
        clickWhenEnabled("capture-open-reference")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("项目照片 · 第 1 张").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("capture-library").assertDoesNotExist()
        rule.onNodeWithText("项目照片 · 第 1 张").assertExists()
    }

    @Test fun bundleCaptureRetakesWithTrustedCurrentReference() = fixture { _, _, capture ->
        openCapture(capture)
        clickWhenEnabled("capture-retake")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Camera Director").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("capture-library").assertDoesNotExist()
        rule.onNodeWithText("Camera Director").assertExists()
    }

    @Test fun deletedReferenceLeavesOriginalAndDisablesBothActions() = fixture { repository, id, capture ->
        runBlocking { repository.delete(id) }
        openCapture(capture)
        rule.onNodeWithTag("capture-open-reference").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("capture-retake").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("capture-library").assertExists()
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { assertTrue(CaptureRepository.get(app).previewFile(capture).isFile) }
    }

    private fun clickWhenEnabled(tag: String) {
        // Capture and reference Room Flows publish independently; a rendered button may still be disabled.
        try {
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag(tag).fetchSemanticsNodes().any { isEnabled().matches(it) }
            }
        } catch (timeout: AssertionError) {
            rule.onNodeWithTag("capture-library", useUnmergedTree = true).printToLog("T12_CONTEXT_DIAGNOSTIC")
            throw timeout
        }
        rule.onNodeWithTag(tag).performScrollTo().assertIsEnabled().performClick()
    }

    private fun openCapture(capture: com.jovi.photoai.data.capture.CaptureRecord) {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("查看全部成片").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("查看全部成片").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("capture-${capture.id}").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("capture-${capture.id}").performScrollTo().performClick()
    }

    private fun fixture(action: (ReferenceRepository, String, com.jovi.photoai.data.capture.CaptureRecord) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = ReferenceRepository.create(app)
        val captures = CaptureRepository.get(app)
        var projectId: String? = null
        var captureId: String? = null
        var referenceId: String? = null
        SyntheticPickerMediaFactory(app).use { media ->
            try {
                val pair = runBlocking {
                    val project = repository.createProject("T12 合成 ${UUID.randomUUID()}")
                    projectId = project.id
                    val result = repository.importIntoProject(media.jpeg().uri, project.id) as ReferenceImportResult.Success
                    val ref = result.record
                    referenceId = ref.photo.id
                    val bundle = PhotoKnowledgeBundle("1.0", "t12_bundle", KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE,
                        "synthetic_t12", "not_for_release"), "0".repeat(64), listOf(PhotoKnowledgeBundleItem("t12_ref",
                        KnowledgeBundlePhotography("合成场景", "背景", "光线", "构图", "主体", "情绪", "姿态", "机位", "合成指导"))))
                    assertEquals(KnowledgeBundleApplyResult.Success(1), repository.applyKnowledgeBundle(project.id,
                        bundle.copy(payloadSha256 = canonicalPayloadSha256(bundle)), listOf(KnowledgeBundleBinding("t12_ref", ref.photo.id))))
                    captures.prepare()
                    val reservation = captures.reserve(UUID.randomUUID().toString().replace("-", ""), project.id, ref.photo.id)
                    captureId = reservation.record.id
                    P25UCaptureFixture.writeJpeg(reservation.output)
                    ref.photo.id to captures.complete(reservation.record.id)
                }
                runBlocking {
                    val persisted = captures.records.first().single { it.id == pair.second.id }
                    assertEquals(projectId, persisted.projectId)
                    assertEquals(pair.first, persisted.referenceId)
                    assertNotNull(repository.project(requireNotNull(projectId)))
                    val current = requireNotNull(repository.activeRecord(pair.first))
                    assertEquals(PhotoAnalysisStatus.READY, current.analysisStatus)
                    assertNotNull(current.knowledgeBundleProvenance)
                }
                action(repository, pair.first, pair.second)
            } finally {
                rule.onNodeWithTag("capture-library").let { node -> if (rule.onAllNodesWithTag("capture-library").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("继续操作").performClick() }
                runBlocking {
                    captureId?.let { captures.delete(it) }
                    projectId?.let { repository.deleteProject(it) }
                }
                referenceId?.let { ReferenceLibraryPreferences(app).clearLastActiveReferenceId(it) }
            }
        }
    }
}
