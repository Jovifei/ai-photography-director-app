package com.jovi.photoai.t3

import android.Manifest
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import com.jovi.photoai.reference.CameraDirectorGuidance
import com.jovi.photoai.reference.ReferenceGuidanceItem
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class T3CameraReferenceAndroidTest {
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun dedicatedEmulatorOnly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(InstrumentationRegistry.getArguments().getString("t3DedicatedEmulator") == "true")
        assertTrue(Build.VERSION.SDK_INT == 35)
        assertTrue(UiDevice.getInstance(instrumentation).executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
    }

    @Test fun directorShowsManualReferenceEntry() {
        showDirector()
        compose.onNodeWithText("查看参考图").assertIsDisplayed()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().isNotEmpty()
        }
        val reference = compose.onNodeWithText("查看参考图").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithText("按真实现场调整", substring = true).fetchSemanticsNode().boundsInRoot
        val shutter = compose.onNodeWithContentDescription("拍摄").fetchSemanticsNode().boundsInRoot
        assertTrue("reference card covers guidance", reference.bottom < hint.top)
        assertTrue("guidance covers shutter", hint.bottom < shutter.top)
    }

    @Test fun landscapeKeepsReferenceAndShutterVisible() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            showDirector()
            device.setOrientationLeft()
            compose.onNodeWithText("查看参考图").assertIsDisplayed()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("拍摄").assertIsDisplayed()
            val reference = compose.onNodeWithText("查看参考图").fetchSemanticsNode().boundsInRoot
            val hint = compose.onNodeWithText("按真实现场调整", substring = true).fetchSemanticsNode().boundsInRoot
            val shutter = compose.onNodeWithContentDescription("拍摄").fetchSemanticsNode().boundsInRoot
            assertTrue("landscape reference card overlaps shutter", !reference.overlaps(shutter))
            assertTrue("landscape guidance overlaps shutter: hint=$hint shutter=$shutter", !hint.overlaps(shutter))
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test fun missingPrivateReferenceOffersRetryAndReturnsToCamera() {
        val name = "t3-missing-${UUID.randomUUID().toString().replace("-", "")}.jpg"
        val file = File(compose.activity.filesDir, "references/$name")
        try {
            showDirector(name)
            compose.onNodeWithText("查看参考图").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("参考图不可用").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("重试参考图").assertIsDisplayed()
            compose.onNodeWithText("返回项目").assertIsDisplayed()
            writeSyntheticJpeg(file)
            compose.onNodeWithText("重试参考图").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("展开的当前参考图").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("关闭参考图").performClick()
            compose.onNodeWithText("查看参考图").assertIsDisplayed()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            file.delete()
        }
    }

    @Test fun expandedReferenceReadsOnlyPrivateSyntheticJpeg() {
        val name = "t3-${UUID.randomUUID().toString().replace("-", "")}.jpg"
        val file = File(compose.activity.filesDir, "references/$name")
        writeSyntheticJpeg(file)
        try {
            showDirector(name)
            compose.onNodeWithText("查看参考图").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("展开的当前参考图").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            file.delete()
        }
    }

    @Test fun directCaptureExposesSupportedExposure() {
        val cameras = compose.activity.getSystemService(CameraManager::class.java)
        val back = cameras.cameraIdList.first { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        val range = cameras.getCameraCharacteristics(back).get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        assertTrue("Dedicated AVD must provide exposure compensation", range != null && range.lower < range.upper)
        compose.activity.setContent {
            PhotoDirectorTheme { CameraScreen(guidanceItems = emptyList(), directCaptureMode = true) }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("曝光").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("曝光").assertIsDisplayed()
        compose.onNodeWithText("曝光").performClick()
        compose.onNodeWithText("曝光补偿").assertIsDisplayed()
        compose.onNodeWithTag("exposure-slider").performTouchInput {
            swipe(start = center, end = Offset(center.x * 1.5f, center.y))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("当前：+", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("重置").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("当前：0 EV").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun directCaptureProvidesFocusSurface() {
        compose.activity.setContent {
            PhotoDirectorTheme { CameraScreen(guidanceItems = emptyList(), directCaptureMode = true) }
        }
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("camera-focus-surface").performTouchInput { click() }
        compose.waitUntil(5_000) {
            listOf("对焦中", "对焦成功", "对焦未确认").any { status ->
                compose.onAllNodesWithContentDescription(status).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun basicCaptureHasNoReferenceComparison() {
        compose.activity.setContent {
            PhotoDirectorTheme { CameraScreen(guidanceItems = emptyList(), directCaptureMode = true) }
        }
        compose.onNodeWithText("查看参考图").assertDoesNotExist()
    }

    private fun showDirector(imageFileName: String? = null) {
        compose.activity.setContent {
            PhotoDirectorTheme {
                CameraScreen(
                    guidanceItems = emptyList(),
                    referenceGuidance = CameraDirectorGuidance(
                        referenceTitle = "样例参考",
                        sourceLabel = "本机参考",
                        centerHint = "按真实现场调整",
                        environment = listOf(ReferenceGuidanceItem("光线", "观察现场")),
                        subject = listOf(ReferenceGuidanceItem("人物", "观察现场")),
                    ),
                    referenceImageFileName = imageFileName,
                    referenceId = "t3-test-reference",
                )
            }
        }
    }

    private fun writeSyntheticJpeg(file: File) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(24, 48, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }
}
