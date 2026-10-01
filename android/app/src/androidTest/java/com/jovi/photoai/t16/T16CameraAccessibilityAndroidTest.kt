package com.jovi.photoai.t16

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import com.jovi.photoai.camera.CameraLens
import com.jovi.photoai.camera.ZoomCapability
import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.reference.CameraDirectorGuidance
import com.jovi.photoai.reference.ReferenceGuidanceItem
import com.jovi.photoai.ui.camera.*
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic chrome probes use font2 override; host font/orientation qualification runs separately. */
@RunWith(AndroidJUnit4::class)
class T16CameraAccessibilityAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ready = CameraUiState(permission = CameraPermission.GRANTED, cameraRuntime = CameraRuntime.READY)
    @Before fun dedicatedEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("t16DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals("1", UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("getprop ro.kernel.qemu").trim())
    }
    @After fun cleanupChrome() = dispose()

    @Test fun disabledShutterKeepsActionNameAndExplainsState() {
        show(captureEnabled = false)
        rule.onNodeWithContentDescription("拍摄").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "相机准备中"))
        dispose()
    }

    @Test fun zoomSliderNamesOperationAndUnitAndSetProgressRequestsOnlySelectedValue() {
        var requested: Float? = null
        show(onZoom = { requested = it })
        rule.onNodeWithTag("zoom-open").performClick()
        rule.onNodeWithTag("zoom-slider").assertContentDescriptionEquals("变焦，倍率")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2.5f) }
        rule.runOnIdle { assertEquals(2.5f, requested!!, 0.001f) }
        rule.onNodeWithText("已确认：1.25x").assertExists()
        rule.onNodeWithText("拖动选择：2.50x").assertExists()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithText("曝光", substring = true).performClick()
        rule.onNodeWithTag("exposure-slider").assertContentDescriptionEquals("曝光补偿，EV")
        dispose()
    }

    @Test fun largeFontCapabilitiesStayReachableAndInstructionDoesNotSilentlyClip() {
        show(fontScale = 2f)
        assertCapabilitiesAndText()
        dispose()
    }

    @Test fun hostFontAndOrientationKeepCapabilitiesAndInstructionReadable() {
        val args = InstrumentationRegistry.getArguments()
        val originalOrientation = rule.activity.requestedOrientation
        val orientation = args.getString("t16Orientation")
        val expectedFont = args.getString("t16ExpectedFontScale")?.toFloat()
        try {
            if (orientation != null) {
                require(orientation == "portrait" || orientation == "landscape")
                val requested = if (orientation == "portrait") ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                rule.runOnUiThread { rule.activity.requestedOrientation = requested }
                val expected = if (orientation == "portrait") Configuration.ORIENTATION_PORTRAIT
                    else Configuration.ORIENTATION_LANDSCAPE
                rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == expected }
            }
            expectedFont?.let { expected ->
                rule.waitUntil(15_000) { kotlin.math.abs(rule.activity.resources.configuration.fontScale - expected) < .001f }
            }
            show()
            assertCapabilitiesAndText()
            dispose()
            assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(rule.activity, Manifest.permission.CAMERA))
            rule.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
                CameraScreen(emptyList(), referenceGuidance = CameraDirectorGuidance(
                    "很长的合成参考图名称用于字体放大验证", "本机参考", INSTRUCTION,
                    listOf(ReferenceGuidanceItem("光线", "观察现场")), listOf(ReferenceGuidanceItem("人物", "观察现场"))),
                    referenceId = "t16-font-reference")
            } } }
            rule.waitUntil(25_000) {
                rule.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().any { isEnabled().matches(it) }
            }
            val card = rule.onNodeWithTag("camera-reference-card").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val controls = rule.onNodeWithTag("camera-controls").fetchSemanticsNode().boundsInRoot
            assertFalse("Reference card overlaps controls", card.overlaps(controls))
            val cardLayouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("camera-reference-open-label", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(cardLayouts)) }
            assertTrue(cardLayouts.all { it.layoutInput.text.text == "查看参考图" })
            assertFalse("Reference action text clipped: ${cardLayouts.map { "text=${it.layoutInput.text.text} size=${it.size} width=${it.didOverflowWidth} height=${it.didOverflowHeight} lines=${it.lineCount}" }}", cardLayouts.any { it.hasVisualOverflow })
            dispose()
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = originalOrientation }
        }
    }

    @Test fun largeFontGuidePanelReturnActionCanBeReadAndExecuted() {
        var closeEvents = 0
        show(fontScale = 2f, state = ready.copy(selectedGuidePanel = GuidePanel.SUBJECT),
            onEvent = { if (it == CameraUiEvent.ClosePanel) closeEvents++ })
        rule.onNodeWithText("关闭").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("返回取景").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertFalse("Guide return action clipped: ${layouts.map { "size=${it.size} width=${it.didOverflowWidth} height=${it.didOverflowHeight} lines=${it.lineCount}" }}", layouts.any { it.hasVisualOverflow })
        rule.onNodeWithText("返回取景").performClick()
        rule.runOnIdle { assertEquals(1, closeEvents) }
    }

    private fun assertCapabilitiesAndText() {
        val viewport = rule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("zoom-open", "camera-lens-switch").forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag outside viewport: $bounds", bounds.left >= viewport.left && bounds.right <= viewport.right)
            val density = rule.activity.resources.displayMetrics.density
            assertTrue("$tag touch height", bounds.height >= 48f * density - 1)
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(INSTRUCTION, substring = true).performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(layouts))
        }
        assertTrue(layouts.isNotEmpty())
        assertFalse("Instruction visually clipped: ${layouts.map { "text=${it.layoutInput.text.text} size=${it.size} width=${it.didOverflowWidth} height=${it.didOverflowHeight} lines=${it.lineCount}" }}", layouts.any { it.hasVisualOverflow })
        rule.onNodeWithTag("camera-upper-content").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, layouts.maxOf { layout -> layout.size.height }.toFloat())
        }
        val upper = rule.onNodeWithTag("camera-upper-content").fetchSemanticsNode().boundsInRoot
        val instruction = rule.onNodeWithText(INSTRUCTION, substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Instruction tail unreachable: $instruction $upper", instruction.bottom <= upper.bottom + 1f)
    }

    private fun show(fontScale: Float? = null, captureEnabled: Boolean = true, onZoom: (Float) -> Unit = {},
        state: CameraUiState = ready, onEvent: (CameraUiEvent) -> Unit = {}) {
        rule.runOnUiThread { rule.activity.setContent { PhotoDirectorTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides (fontScale?.let { Density(density.density, it) } ?: density)) {
                CameraDirectorChrome(state, onEvent, {}, {},
                    referenceGuidance = CameraDirectorGuidance("很长的合成参考图名称用于字体放大验证", "本机参考内容来源标签用于验证放大后仍能阅读完整构图指令", INSTRUCTION,
                        listOf(ReferenceGuidanceItem("光线", "观察现场")), listOf(ReferenceGuidanceItem("人物", "观察现场"))),
                    exposureRange = -3..3, exposureStepEv = .5f,
                    zoomCapability = ZoomCapability(.75f, 4f, 1.25f), confirmedZoomRatio = 1.25f,
                    availableLenses = setOf(CameraLens.BACK, CameraLens.FRONT),
                    controlsEnabled = true, captureEnabled = captureEnabled, onZoomSelected = onZoom)
            }
        } } }
        rule.waitForIdle()
    }
    private fun dispose() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }
    companion object { const val INSTRUCTION = "观察现场光线和构图，将人物置于画面右侧并保持左侧完整留白。" }
}
