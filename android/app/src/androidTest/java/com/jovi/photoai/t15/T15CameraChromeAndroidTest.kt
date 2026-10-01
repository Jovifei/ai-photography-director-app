package com.jovi.photoai.t15

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import android.os.Build
import com.jovi.photoai.MainActivity
import com.jovi.photoai.camera.CameraLens
import com.jovi.photoai.camera.ZoomCapability
import com.jovi.photoai.reference.CameraDirectorGuidance
import com.jovi.photoai.reference.ReferenceGuidanceItem
import com.jovi.photoai.ui.camera.*
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic capability inputs exercise production chrome without initializing CameraX. */
@RunWith(AndroidJUnit4::class)
class T15CameraChromeAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ready = CameraUiState(permission = CameraPermission.GRANTED, cameraRuntime = CameraRuntime.READY)

    @Test fun unsupportedControlsAreHiddenAndShutterHasIndependentGate() {
        rule.activity.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
            CameraDirectorChrome(ready, {}, {}, {}, directCaptureMode = true,
                controlsEnabled = true, captureEnabled = false,
                availableLenses = setOf(CameraLens.BACK))
        } } }
        rule.onNodeWithTag("zoom-open").assertDoesNotExist()
        rule.onNodeWithTag("camera-lens-switch").assertDoesNotExist()
        rule.onNodeWithContentDescription("拍摄").assertIsNotEnabled()
        dispose()
    }

    @Test fun actualZoomRangeSeparatesConfirmedSelectionAndPendingAndClosesOnLensChange() {
        val lens = mutableStateOf(CameraLens.BACK)
        val busy = mutableStateOf(false)
        val pending = mutableStateOf<Float?>(null)
        var requested: Float? = null
        rule.activity.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
            CameraDirectorChrome(ready, {}, {}, {}, directCaptureMode = true,
                zoomCapability = ZoomCapability(0.75f, 3f, 1.25f), confirmedZoomRatio = 1.25f,
                pendingZoomRatio = pending.value, zoomStatus = "等待硬件确认", controlsEnabled = !busy.value,
                availableLenses = setOf(CameraLens.BACK, CameraLens.FRONT),
                currentLens = lens.value, onZoomSelected = { requested = it })
        } } }
        rule.onNodeWithTag("zoom-open").performClick()
        rule.onNodeWithText("已确认：1.25x").assertExists()
        rule.onNodeWithText("拖动选择：1.25x").assertExists()
        rule.onNodeWithText("正在确认：2.50x").assertDoesNotExist()
        val range = rule.onNodeWithTag("zoom-slider").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].range
        assertEquals(0.75f, range.start)
        assertEquals(3f, range.endInclusive)
        rule.onNodeWithTag("zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
        rule.runOnIdle { assertEquals(2.5f, requested!!, 0.001f); pending.value = 2.5f; busy.value = true }
        rule.onNodeWithText("已确认：1.25x").assertExists()
        rule.onNodeWithText("拖动选择：2.50x").assertExists()
        rule.onNodeWithText("正在确认：2.50x").assertExists()
        rule.onNodeWithTag("zoom-slider").assertIsNotEnabled()
        // Pending state alone is sufficient even if a host forgets to lower controlsEnabled.
        rule.runOnIdle { busy.value = false }
        rule.onNodeWithTag("zoom-slider").assertIsNotEnabled()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithTag("camera-lens-switch").assertIsNotEnabled()
        rule.onNodeWithContentDescription("拍摄").assertIsNotEnabled()
        rule.runOnIdle { pending.value = null }
        rule.onNodeWithTag("zoom-open").performClick()
        rule.runOnIdle { lens.value = CameraLens.FRONT }
        rule.onNodeWithTag("zoom-slider").assertDoesNotExist()
        dispose()
    }

    @Test fun bothLensesSwitchTruthfullyAndBusyDisablesOnlyHardwareControls() {
        val busy = mutableStateOf(false)
        var selected: CameraLens? = null
        var captures = 0
        rule.activity.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
            CameraDirectorChrome(ready, {}, {}, { captures++ }, directCaptureMode = true,
                zoomCapability = ZoomCapability(1f, 4f, 1f),
                availableLenses = setOf(CameraLens.BACK, CameraLens.FRONT),
                currentLens = CameraLens.FRONT, controlsEnabled = !busy.value,
                captureEnabled = true, lensStatus = "前置相机已就绪", onLensSelected = { selected = it })
        } } }
        rule.onNodeWithText("前置 → 后置").assertExists()
        rule.onNodeWithTag("camera-lens-switch").performClick()
        rule.runOnIdle { assertEquals(CameraLens.BACK, selected); busy.value = true }
        rule.onNodeWithTag("camera-lens-switch").assertIsNotEnabled()
        rule.onNodeWithTag("zoom-open").assertIsNotEnabled()
        rule.onNodeWithText("前置相机已就绪").assertExists()
        rule.onNodeWithContentDescription("拍摄").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, captures) }
        dispose()
    }

    @Test fun compactLandscapeKeepsGuidanceAndControlsApartAndActionsInsideViewport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("t15DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim())
        try {
            device.setOrientationLeft()
            rule.waitUntil(10_000) { rule.activity.resources.configuration.screenWidthDp >
                rule.activity.resources.configuration.screenHeightDp }
            rule.activity.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
                CameraDirectorChrome(ready, {}, {}, {}, referenceCardVisible = true,
                    referenceGuidance = CameraDirectorGuidance("合成参考", "本机参考", "观察现场光线和构图",
                        listOf(ReferenceGuidanceItem("光线", "观察现场")),
                        listOf(ReferenceGuidanceItem("人物", "观察现场"))),
                    exposureRange = -2..2, exposureStepEv = 0.5f,
                    exposureStatus = "曝光已确认", zoomCapability = ZoomCapability(0.75f, 4f, 1f),
                    zoomStatus = "变焦已确认", lensStatus = "后置相机已就绪",
                    availableLenses = setOf(CameraLens.BACK, CameraLens.FRONT),
                    controlsEnabled = true, saveEnabled = true, saveStatus = "照片已保存")
            } } }
            val hint = rule.onNodeWithTag("camera-guidance-hint").fetchSemanticsNode().boundsInRoot
            val controls = rule.onNodeWithTag("camera-controls").fetchSemanticsNode().boundsInRoot
            val shutter = rule.onNodeWithContentDescription("拍摄").fetchSemanticsNode().boundsInRoot
            assertTrue("Guidance must be above compact controls", hint.bottom <= controls.top)
            assertFalse("Guidance must not overlap shutter", hint.overlaps(shutter))
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val minTouch = 48f * rule.activity.resources.displayMetrics.density
            rule.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().forEach {
                val bounds = it.boundsInRoot
                assertTrue("Action bounds must remain in viewport: $bounds", bounds.left >= root.left &&
                    bounds.top >= root.top && bounds.right <= root.right && bounds.bottom <= root.bottom)
                assertTrue("Action must retain 48dp height: $bounds", bounds.height >= minTouch - 1f)
            }
            listOf("zoom-open", "camera-lens-switch").forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
            rule.onNodeWithText("保存照片").assertIsDisplayed()
            rule.onNodeWithText("曝光").assertIsDisplayed()
        } finally {
            dispose()
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun dispose() {
        rule.activity.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
    }
}
