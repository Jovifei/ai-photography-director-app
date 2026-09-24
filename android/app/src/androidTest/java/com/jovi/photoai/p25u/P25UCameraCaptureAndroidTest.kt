package com.jovi.photoai.p25u

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.CaptureFileState
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.capture.CaptureLibraryHost
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual CameraX -> production ViewModel/Room/files -> production preview. Dedicated AVD only. */
@RunWith(AndroidJUnit4::class)
class P25UCameraCaptureAndroidTest {
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cameraOutputOpensDurablePreviewAndSurvivesActivityRecreation() {
        assumeTrue("Explicit dedicated-emulator confirmation is required",
            InstrumentationRegistry.getArguments().getString("p25uDedicatedEmulator") == "true")
        lateinit var model: CaptureLibraryViewModel
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity,
                CaptureLibraryViewModelFactory(compose.activity.application))[CaptureLibraryViewModel::class.java]
            model.closeGallery()
            compose.activity.setContent {
                PhotoDirectorTheme {
                    CameraScreen(emptyList(), directCaptureMode = true, captureLibrary = model)
                    CaptureLibraryHost(model, emptyList())
                }
            }
        }
        compose.waitUntil(15_000) { model.state.value.ready }
        val before = model.state.value.records.map { it.id }.toSet()
        var ownedId: String? = null
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("拍摄").performClick()
            compose.waitUntil(20_000) { model.state.value.records.any { it.id !in before && it.fileState == CaptureFileState.AVAILABLE } }
            ownedId = model.state.value.records.single { it.id !in before }.id
            assertTrue(model.state.value.galleryVisible)
            compose.onNodeWithText("成片预览").assertIsDisplayed()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("本机拍摄成片预览").fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("成片预览").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("本机拍摄成片预览").assertIsDisplayed()
        } finally {
            ownedId?.let { id ->
                compose.runOnUiThread { model.delete(id) }
                compose.waitUntil(10_000) { model.state.value.records.none { it.id == id } }
            }
            compose.runOnUiThread { model.closeGallery() }
        }
    }
}
