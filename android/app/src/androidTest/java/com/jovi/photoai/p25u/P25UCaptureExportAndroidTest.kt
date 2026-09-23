package com.jovi.photoai.p25u

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.CaptureFileState
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.capture.CaptureLibraryHost
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P25UCaptureExportAndroidTest {
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var activeLibrary: CaptureLibraryViewModel

    @Test
    fun realCapture_saveCopyThroughDocumentsUi_reportsSaved() {
        val id = openCapturedGallery()
        try {
            compose.onNodeWithText("保存副本").performClick()
            assertTrue(device().wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT))
            chooseDestination()
            compose.waitUntil(TIMEOUT) {
                runCatching {
                    compose.onNodeWithText("已保存副本；应用不跟踪外部文件后续变化").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        } finally {
            delete(id)
        }
    }

    @Test
    fun realCapture_cancelSaveCopyKeepsOriginalAndRetryEnabled() {
        val id = openCapturedGallery()
        try {
            compose.onNodeWithText("保存副本").performClick()
            assertTrue(device().wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT))
            device().pressBack()
            compose.waitUntil(TIMEOUT) {
                runCatching {
                    compose.onNodeWithText("已取消另存为，应用内原片保留").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            compose.onNodeWithText("保存副本").assertIsEnabled()
        } finally {
            delete(id)
        }
    }

    private fun openCapturedGallery(): String {
        lateinit var library: CaptureLibraryViewModel
        compose.runOnUiThread {
            library = ViewModelProvider(
                compose.activity,
                CaptureLibraryViewModelFactory(compose.activity.application),
            )[CaptureLibraryViewModel::class.java]
            activeLibrary = library
            library.closeGallery()
            compose.activity.setContent {
                PhotoDirectorTheme {
                    CameraScreen(
                        guidanceItems = emptyList(),
                        directCaptureMode = true,
                        captureLibrary = library,
                    )
                    CaptureLibraryHost(library, emptyList())
                }
            }
        }
        compose.waitUntil(TIMEOUT) { library.state.value.ready }
        compose.waitUntil(TIMEOUT) { library.state.value.records.isNotEmpty() }
        library.state.value.pendingExport?.let { pending ->
            library.abandonExport(pending.token)
        }
        compose.waitUntil(TIMEOUT) { library.state.value.pendingExport == null }
        val before = library.state.value.records.map { it.id }.toSet()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithContentDescription("拍摄", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("拍摄", useUnmergedTree = true).performClick()
        compose.waitUntil(TIMEOUT) {
            library.state.value.records.any {
                it.id !in before && it.fileState == CaptureFileState.AVAILABLE
            }
        }
        val id = library.state.value.records.first { it.id !in before }.id
        compose.waitUntil(TIMEOUT) { library.state.value.galleryVisible }
        compose.onNodeWithText("成片预览").assertIsDisplayed()
        compose.onNodeWithText("保存副本").assertIsDisplayed()
        return id
    }

    private fun delete(id: String) {
        activeLibrary.delete(id)
    }

    private fun chooseDestination() {
        val device = device()
        device.findObject(By.res("android:id/title"))?.setText("p25u-capture.jpg")
        val save = device.findObject(By.res("android:id/button1"))
            ?: device.findObject(By.res("com.google.android.documentsui:id/save"))
            ?: device.findObject(By.text("保存"))
            ?: device.findObject(By.text("SAVE"))
            ?: device.findObject(By.text("Save"))
        assertNotNull("DocumentsUI save action unavailable", save)
        save!!.click()
        assertTrue(device.wait(Until.gone(By.pkg("com.google.android.documentsui")), TIMEOUT))
    }

    private fun device(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private companion object {
        const val TIMEOUT = 30_000L
    }
}
