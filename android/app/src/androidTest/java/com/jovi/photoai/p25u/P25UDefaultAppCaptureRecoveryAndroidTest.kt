package com.jovi.photoai.p25u

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.CaptureFileState
import com.jovi.photoai.data.capture.CaptureRepository
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class P25UDefaultAppCaptureRecoveryAndroidTest {
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun prepare() {
        requirePhase("prepare")
        val library = library()
        compose.waitUntil(15_000) { library.state.value.ready }
        compose.onNodeWithText("拍摄", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("无指导直接拍摄", useUnmergedTree = true).performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("拍摄", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val before = library.state.value.records.map { it.id }.toSet()
        compose.onNodeWithContentDescription("拍摄", useUnmergedTree = true).performClick()
        compose.waitUntil(25_000) {
            library.state.value.records.any {
                it.id !in before && it.fileState == CaptureFileState.AVAILABLE
            }
        }
        val ownedId = library.state.value.records.first { it.id !in before }.id
        compose.onNodeWithText("成片预览").assertIsDisplayed()
        marker().writeText(ownedId)
    }

    @Test
    fun cleanup() {
        requirePhase("cleanup")
        val id = marker().takeIf(File::isFile)?.readText()?.trim()
        if (!id.isNullOrBlank()) {
            runBlocking {
                CaptureRepository.get(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                ).delete(id)
            }
        }
        marker().delete()
    }

    private fun library(): CaptureLibraryViewModel =
        ViewModelProvider(
            compose.activity,
            CaptureLibraryViewModelFactory(compose.activity.application),
        )[CaptureLibraryViewModel::class.java]

    private fun requirePhase(expected: String) {
        val args = InstrumentationRegistry.getArguments()
        assertTrue(args.getString("p25uDedicatedEmulator") == "true")
        assertTrue(args.getString("p25uPhase") == expected)
        require(args.getString("p25uRun")?.matches(Regex("^[a-f0-9]{32}$")) == true)
    }

    private fun marker(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = requireNotNull(InstrumentationRegistry.getArguments().getString("p25uRun"))
        return File(context.noBackupFilesDir, "p25u-default-recovery-$run.txt")
    }
}
