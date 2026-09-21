package com.jovi.photoai.p25t

import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.p23r.P23RRoomFixture
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses actual import ViewModel and isolated Room; rows have synthetic metadata, no real photos. */
@RunWith(AndroidJUnit4::class)
class P25TBundleMappingAndroidTest {
    @get:Rule val rule = createComposeRule()

    @Test fun visualSelectionUsesExplicitTargetAndDoesNotStealOtherBinding() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(4) }
            val store = ViewModelStore()
            val visible = mutableStateOf(true)
            val model = PhotoKnowledgeBundleImportViewModel(
                readSelectedDocument = { PhotoKnowledgeBundleParseResult.Success(fixture.bundle()) },
                applySelectedBundle = fixture.repository::applyKnowledgeBundle,
            )
            rule.runOnUiThread { store.put("mapping", model) }
            rule.setContent {
                if (visible.value) PhotoDirectorTheme {
                    val state by model.state.collectAsState()
                    PhotoKnowledgeBundleImportScreen(fixture.project, fixture.records, state,
                        model::readDocument, model::bind, { model.apply(fixture.project.id) }, model::reset, {})
                }
            }
            try {
                rule.runOnIdle { model.readDocument(Uri.parse("content://synthetic-p25t/bundle")) }
                rule.waitUntil(5000) { model.state.value.bundle != null }
                val target = fixture.records[2].photo.id
                rule.onNodeWithTag("bundle-choose-producer_0").performScrollTo().performClick()
                rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$target"))
                rule.onNodeWithTag("bundle-target-$target").performClick()
                rule.runOnIdle { assertEquals(target, model.state.value.bindings["producer_0"]) }
                rule.onNodeWithTag("bundle-choose-producer_1").performScrollTo().performClick()
                rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$target"))
                rule.onNodeWithTag("bundle-target-$target").assertIsNotEnabled()
                rule.onNodeWithText("取消选择").performClick()
                rule.onNodeWithTag("bundle-unbind-producer_0").performScrollTo().performClick()
                rule.runOnIdle { assertTrue(model.state.value.bindings.isEmpty()) }
            } finally {
                rule.runOnIdle { visible.value = false; store.clear() }
                rule.waitForIdle()
            }
        }
    }

    @Test fun detailDialogExposesAllNineFields() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            rule.setContent { PhotoDirectorTheme {
                PhotoKnowledgeBundleImportScreen(fixture.project, fixture.records,
                    KnowledgeBundleImportUiState(bundle = fixture.bundle()), {}, { _, _ -> }, {}, {}, {})
            } }
            rule.onNodeWithTag("bundle-details-producer_0").performScrollTo().performClick()
            for (label in listOf("场景", "背景", "光线", "构图", "主体意图", "情绪", "姿态意图", "机位", "拍摄指令")) {
                rule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            }
            rule.onNodeWithText("关闭指导详情").performClick()
        }
    }

    @Test fun busyStateDisablesPickerDetailsAndUnbind() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            val state = KnowledgeBundleImportUiState(bundle = fixture.bundle(),
                bindings = mapOf("producer_0" to fixture.records.single().photo.id), isApplying = true)
            rule.setContent { PhotoDirectorTheme {
                PhotoKnowledgeBundleImportScreen(fixture.project, fixture.records, state, {}, { _, _ -> }, {}, {}, {})
            } }
            rule.onNodeWithTag("bundle-choose-producer_0").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("bundle-details-producer_0").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithTag("bundle-unbind-producer_0").performScrollTo().assertIsNotEnabled()
        }
    }

    @Test fun foreignProjectRowsCannotEnableCommitOrBecomeVisibleTargets() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            val foreign = fixture.records.single().copy(projectId = "other-project")
            val state = KnowledgeBundleImportUiState(bundle = fixture.bundle(),
                bindings = mapOf("producer_0" to foreign.photo.id))
            rule.setContent { PhotoDirectorTheme {
                PhotoKnowledgeBundleImportScreen(fixture.project, listOf(foreign), state, {}, { _, _ -> }, {}, {}, {})
            } }
            rule.onNodeWithTag("bundle-choose-producer_0").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithText("确认全部绑定并导入").performScrollTo().assertIsNotEnabled()
        }
    }
}
