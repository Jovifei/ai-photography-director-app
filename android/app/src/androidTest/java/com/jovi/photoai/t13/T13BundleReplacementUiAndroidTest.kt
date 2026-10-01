package com.jovi.photoai.t13

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.p23r.P23RRoomFixture
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real import screen and ViewModel with isolated Room and synthetic guidance only. */
@RunWith(AndroidJUnit4::class)
class T13BundleReplacementUiAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun cancelPreservesOldGuidance_thenExplicitConfirmReplacesIt() {
        fixture { f, incoming ->
            val model = install(f, incoming)
            loadAndBind(model, f)
            openPreview()
            rule.onNodeWithText("发布标识：synthetic_release", substring = true).assertExists()
            rule.onNodeWithText("发布标识：replacement_release", substring = true).assertExists()
            rule.onNodeWithTag("bundle-cancel-replacement").performClick()
            rule.onNodeWithTag("bundle-replacement-preview").assertDoesNotExist()
            runBlocking { assertEquals("synthetic_release", f.repository.activeRecordsForProject(f.project.id).first().single().knowledgeBundleProvenance!!.releaseId) }
            openPreview()
            rule.onNodeWithTag("bundle-confirm-replacement").performClick()
            rule.waitUntil(5000) { model.state.value.appliedCount == 1 }
            runBlocking { assertEquals(incoming.payloadSha256, f.repository.activeRecordsForProject(f.project.id).first().single().knowledgeBundleProvenance!!.payloadSha256) }
        }
    }

    @Test fun confirmationRejectsChangedProvenanceWithoutOverwritingWinner() {
        fixture { f, incoming ->
            val model = install(f, incoming)
            loadAndBind(model, f)
            openPreview()
            val expected = model.state.value.replacementPreview!!.expectedProvenance
            val winnerRaw = incoming.copy(source = incoming.source.copy(releaseId = "winning_release"))
            val winner = winnerRaw.copy(payloadSha256 = canonicalPayloadSha256(winnerRaw))
            runBlocking { assertEquals(KnowledgeBundleApplyResult.Success(1), f.repository.replaceKnowledgeBundle(f.project.id, winner, f.bindings(), expected)) }
            rule.onNodeWithTag("bundle-confirm-replacement").performClick()
            rule.waitUntil(5000) { model.state.value.applyError == KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE }
            rule.onNodeWithTag("bundle-replacement-preview").assertDoesNotExist()
            runBlocking { assertEquals("winning_release", f.repository.activeRecordsForProject(f.project.id).first().single().knowledgeBundleProvenance!!.releaseId) }
        }
    }

    @Test fun activityRecreationRetainsFrozenPreviewAndExplicitConfirmation() {
        fixture { f, incoming ->
            val before = install(f, incoming)
            loadAndBind(before, f)
            openPreview()
            val frozen = before.state.value.replacementPreview
            rule.activityRule.scenario.recreate()
            val after = install(f, incoming)
            assertSame(before, after)
            assertEquals(frozen, after.state.value.replacementPreview)
            rule.onNodeWithTag("bundle-confirm-replacement").performClick()
            rule.waitUntil(5000) { after.state.value.appliedCount == 1 }
        }
    }

    @Test fun partialProviderMetadataCannotBeSelectedOrPreviewed() {
        fixture { f, incoming ->
            val id = f.records.single().photo.id
            val record = runBlocking {
                f.dao.update(f.dao.activeById(id)!!.copy(analysisProviderId = "partial_provider"))
                f.repository.activeRecordsForProject(f.project.id).first().single()
            }
            assertNull(record.analysisProvenance)
            assertTrue(record.hasProviderProvenanceMetadata)
            val model = install(f, incoming)
            rule.runOnUiThread { model.readDocument(Uri.parse("content://synthetic-t13/partial")) }
            rule.waitUntil(5000) { model.state.value.bundle != null }
            rule.onNodeWithTag("bundle-replacement-mode").performScrollTo().performClick()
            rule.onNodeWithContentDescription("将知识条目 1 绑定到项目照片第 1 张").assertDoesNotExist()
            rule.onNodeWithTag("bundle-choose-producer_0").performScrollTo().performClick()
            rule.onNodeWithTag("bundle-target-$id").assertIsNotEnabled()
            rule.onNodeWithText("取消选择").performClick()
            rule.runOnUiThread {
                model.bind("producer_0", id)
                model.previewReplacement(f.project.id, listOf(record))
            }
            assertNull(model.state.value.replacementPreview)
            assertEquals(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE, model.state.value.applyError)
            runBlocking { assertEquals("synthetic_release", f.dao.activeById(id)!!.knowledgeBundleReleaseId) }
        }
    }

    private fun openPreview() {
        rule.onNodeWithText("预览已绑定指导替换").performScrollTo().performClick()
        rule.onNodeWithTag("bundle-replacement-preview").assertExists()
    }

    private fun loadAndBind(model: PhotoKnowledgeBundleImportViewModel, f: P23RRoomFixture) {
        rule.runOnUiThread { model.readDocument(Uri.parse("content://synthetic-t13/bundle")) }
        rule.waitUntil(5000) { model.state.value.bundle != null }
        rule.onNodeWithTag("bundle-replacement-mode").performScrollTo().performClick()
        rule.onNodeWithContentDescription("将知识条目 1 绑定到项目照片第 1 张").performScrollTo().performClick()
        assertEquals(f.records.single().photo.id, model.state.value.bindings["producer_0"])
    }

    private fun install(f: P23RRoomFixture, incoming: PhotoKnowledgeBundle): PhotoKnowledgeBundleImportViewModel {
        lateinit var model: PhotoKnowledgeBundleImportViewModel
        rule.runOnUiThread {
            model = ViewModelProvider(rule.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PhotoKnowledgeBundleImportViewModel(
                    readSelectedDocument = { PhotoKnowledgeBundleParseResult.Success(incoming) },
                    applySelectedBundle = f.repository::applyKnowledgeBundle,
                    replaceSelectedBundle = f.repository::replaceKnowledgeBundle,
                ) as T
            }).get("t13-ui", PhotoKnowledgeBundleImportViewModel::class.java)
            rule.activity.setContent {
                val state by model.state.collectAsState()
                val records by f.repository.activeRecordsForProject(f.project.id).collectAsState(initial = emptyList())
                PhotoDirectorTheme {
                    PhotoKnowledgeBundleImportScreen(f.project, records, state,
                        model::readDocument, model::bind, { model.apply(f.project.id) }, model::reset,
                        { model.tryLeave() }, model::setReplacementMode,
                        { model.previewReplacement(f.project.id, records) }, { model.confirmReplacement(f.project.id) },
                        model::cancelReplacementPreview)
                }
            }
        }
        rule.waitForIdle()
        return model
    }

    private fun fixture(action: (P23RRoomFixture, PhotoKnowledgeBundle) -> Unit) {
        P23RRoomFixture().use { f ->
            runBlocking { f.seed(1); assertEquals(KnowledgeBundleApplyResult.Success(1), f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings())) }
            val raw = f.bundle().copy(source = f.bundle().source.copy(releaseId = "replacement_release"))
            val incoming = raw.copy(payloadSha256 = canonicalPayloadSha256(raw))
            try { action(f, incoming) } finally {
                rule.runOnUiThread { rule.activity.setContent {} }
                rule.waitForIdle()
            }
        }
    }
}
