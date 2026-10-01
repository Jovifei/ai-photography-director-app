package com.jovi.photoai.t15

import android.Manifest
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import com.jovi.photoai.reference.CameraDirectorGuidance
import com.jovi.photoai.reference.ReferenceGuidanceItem
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual CameraScreen and camera capabilities, synthetic guidance, no image/capture writes. */
@RunWith(AndroidJUnit4::class)
class T15CameraLayoutAndroidTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun portraitAndLandscapeKeepActualReferenceHintAndCaptureControlsDisjoint() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertEquals("true", InstrumentationRegistry.getArguments().getString("t15DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim())
        try {
            device.setOrientationNatural()
            rule.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
                CameraScreen(emptyList(), referenceGuidance = CameraDirectorGuidance(
                    "合成参考", "本机参考", "观察现场光线和构图",
                    listOf(ReferenceGuidanceItem("光线", "观察现场")),
                    listOf(ReferenceGuidanceItem("人物", "观察现场"))), referenceId = "t15-layout-reference")
            } } }
            waitReady()
            assertBounds()
            device.setOrientationLeft()
            rule.waitUntil(10_000) { rule.activity.resources.configuration.screenWidthDp > rule.activity.resources.configuration.screenHeightDp }
            waitReady()
            assertBounds()
        } finally {
            rule.runOnUiThread { rule.activity.setContent {} }
            rule.waitForIdle()
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun waitReady() = rule.waitUntil(25_000) {
        rule.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().any { isEnabled().matches(it) }
    }
    private fun assertBounds() {
        val card = rule.onNodeWithTag("camera-reference-card").fetchSemanticsNode().boundsInRoot
        val hint = rule.onNodeWithTag("camera-guidance-hint").fetchSemanticsNode().boundsInRoot
        val controls = rule.onNodeWithTag("camera-controls").fetchSemanticsNode().boundsInRoot
        assertFalse("Reference/hint overlap: $card $hint", card.overlaps(hint))
        assertFalse("Reference/controls overlap: $card $controls", card.overlaps(controls))
        assertFalse("Hint/controls overlap: $hint $controls", hint.overlaps(controls))
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val shutter = rule.onNodeWithContentDescription("拍摄").fetchSemanticsNode().boundsInRoot
        assertTrue(shutter.left >= root.left && shutter.right <= root.right && shutter.top >= root.top && shutter.bottom <= root.bottom)
        listOf("zoom-open", "camera-lens-switch").forEach { tag ->
            if (rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }
}
