package com.jovi.photoai.p23r

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Actual screen + actual ViewModel + isolated Room. Only the selected document is synthetic. */
@RunWith(AndroidJUnit4::class)
class P23RBundleUiAndroidTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val left = AtomicInteger()
    private lateinit var leaveCallback: () -> Unit

    @Test fun sameFrameSystemBackAndOldClickCallback_doNotLeaveDuringApply() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            val model = install(fixture)
            selectFixture(model)
            composeRule.runOnUiThread {
                model.bind("producer_0", fixture.records.single().photo.id)
                model.apply(fixture.project.id)
                // No idle/wait/recomposition between apply and the old callback/system Back.
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
                leaveCallback()
                assertEquals(0, left.get())
            }
            try {
                composeRule.waitUntil(5000) { model.state.value.appliedCount == 1 }
            } catch (error: Throwable) {
                throw AssertionError("apply did not finish; state=${model.state.value}", error)
            }
            runBlocking {
                assertEquals("READY", fixture.dao.activeById(fixture.records.single().photo.id)!!.analysisStatus)
            }
            composeRule.runOnUiThread { leaveCallback(); assertEquals(1, left.get()) }
            dispose()
        }
    }

    @Test fun longSourceAndDigest_areReachableAtOneAndTwoTimesFontScale() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            val initial = fixture.bundle().let { b ->
                val changed = b.copy(source = b.source.copy(producerId = "p".repeat(128), releaseId = "r".repeat(128)))
                changed.copy(payloadSha256 = canonicalPayloadSha256(changed))
            }
            for (scale in listOf(1f, 2f)) {
                val model = install(fixture, initial, scale)
                selectFixture(model)
                val digest = composeRule.onNodeWithText("SHA-256：${initial.payloadSha256}")
                digest.performScrollTo().assertIsDisplayed()
                val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
                val textBounds = digest.fetchSemanticsNode().boundsInRoot
                assertTrue(textBounds.left >= rootBounds.left - 1f)
                assertTrue(textBounds.right <= rootBounds.right + 1f)
                composeRule.onNodeWithText("已绑定 0 / 1 条").performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithText("确认全部绑定并导入").performScrollTo().assertIsNotEnabled()
            }
            dispose()
        }
    }

    @Test fun activityRecreation_retainsRealViewModelMappingAndCanCommit() {
        P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            val before = install(fixture)
            selectFixture(before)
            composeRule.runOnUiThread { before.bind("producer_0", fixture.records.single().photo.id) }
            composeRule.activityRule.scenario.recreate()
            val after = install(fixture)
            assertSame(before, after)
            assertEquals(fixture.records.single().photo.id, after.state.value.bindings["producer_0"])
            composeRule.onNodeWithText("确认全部绑定并导入").performScrollTo().performClick()
            composeRule.waitUntil(5000) { after.state.value.appliedCount == 1 }
            dispose()
        }
    }

    private fun selectFixture(model: PhotoKnowledgeBundleImportViewModel) {
        composeRule.runOnUiThread { model.readDocument(Uri.parse("content://synthetic-p23r/ui")) }
        composeRule.waitUntil(5000) { model.state.value.bundle != null }
    }

    private fun install(
        fixture: P23RRoomFixture,
        document: PhotoKnowledgeBundle = fixture.bundle(),
        fontScale: Float = 1f,
    ): PhotoKnowledgeBundleImportViewModel {
        lateinit var model: PhotoKnowledgeBundleImportViewModel
        composeRule.runOnUiThread {
            model = ViewModelProvider(composeRule.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PhotoKnowledgeBundleImportViewModel(
                    readSelectedDocument = { PhotoKnowledgeBundleParseResult.Success(document) },
                    applySelectedBundle = fixture.repository::applyKnowledgeBundle,
                ) as T
            }).get("p23r-ui", PhotoKnowledgeBundleImportViewModel::class.java)
            leaveCallback = { if (model.tryLeave()) left.incrementAndGet() }
            composeRule.activity.setContent {
                val state by model.state.collectAsState()
                val records by fixture.repository.activeRecordsForProject(fixture.project.id).collectAsState(initial = fixture.records)
                val density = LocalDensity.current
                PhotoDirectorTheme {
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        BackHandler { leaveCallback() } // same synchronous gate as the app parent
                        PhotoKnowledgeBundleImportScreen(
                            fixture.project, records, state,
                            onDocumentSelected = model::readDocument,
                            onBind = model::bind,
                            onApply = { model.apply(fixture.project.id) },
                            onReset = model::reset,
                            onBack = { leaveCallback() },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return model
    }

    private fun dispose() {
        composeRule.runOnUiThread { composeRule.activity.setContent {} }
        composeRule.waitForIdle()
    }
}
