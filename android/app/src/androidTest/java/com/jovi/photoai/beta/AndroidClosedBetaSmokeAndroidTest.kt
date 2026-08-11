package com.jovi.photoai.beta

import android.Manifest
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.LocalAnalysisConnectionDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** API 35 smoke coverage for the current Closed Beta product shell. */
@RunWith(AndroidJUnit4::class)
class AndroidClosedBetaSmokeAndroidTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun pairingDialog_exposesPrivateLanFields_andErrorSurface() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                PhotoDirectorTheme {
                    LocalAnalysisConnectionDialog(
                        photoCount = 1,
                        errorMessage = "配对失败，请检查本机地址。",
                        onDismiss = {},
                        onPair = { _, _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("连接本机分析电脑").assertIsDisplayed()
        composeRule.onNodeWithText("HTTPS 地址").assertIsDisplayed()
        composeRule.onNodeWithText("一次性配对码").assertIsDisplayed()
        composeRule.onNodeWithText("证书 pin（sha256/…）").assertIsDisplayed()
        composeRule.onNodeWithText("配对失败，请检查本机地址。", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("配对并继续").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
    }

    @Test
    fun directCapture_saveAsSystemPicker_reportsSuccess() {
        openDirectCaptureAndCapture()
        composeRule.onNodeWithText("保存照片").performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT_MILLIS))
        chooseSystemDocumentDestination()
        composeRule.onNodeWithText("照片已保存到你选择的位置").assertIsDisplayed()
    }

    @Test
    fun directCapture_saveAsCancelKeepsCaptureSurface() {
        openDirectCaptureAndCapture()
        composeRule.onNodeWithText("保存照片").performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT_MILLIS))
        device.pressBack()
        composeRule.onNodeWithText("已取消保存，照片仍保留在应用缓存").assertIsDisplayed()
        composeRule.onNodeWithText("保存照片").assertIsEnabled()
    }

    private fun openDirectCaptureAndCapture() {
        bringMainActivityToForeground()
        clickText("拍摄")
        composeRule.onNodeWithText("开始拍摄").assertIsDisplayed()
        composeRule.onNodeWithText("无指导直接拍摄").performClick()
        composeRule.onNodeWithText("保存照片").assertIsDisplayed()

        // A real capture is required before CreateDocument is enabled. This fails closed when
        // the dedicated emulator camera is unavailable instead of calling disabled Save a pass.
        val cameraReady = runCatching {
            composeRule.waitUntil(CAMERA_READY_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithContentDescription("拍摄", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            true
        }.getOrDefault(false)
        assertTrue(
            "P20_BETA_RUNTIME_GATE_BLOCKED_CAMERA_NOT_READY",
            cameraReady,
        )
        composeRule.onAllNodesWithContentDescription("拍摄", useUnmergedTree = true)
            .onFirst()
            .performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithText("保存照片").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    private fun bringMainActivityToForeground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        assertTrue(
            "P20_BETA_RUNTIME_GATE_BLOCKED_MAIN_ACTIVITY_NOT_READY",
            device.wait(Until.hasObject(By.pkg("com.jovi.photoai")), TIMEOUT_MILLIS),
        )
        composeRule.waitForIdle()
    }

    private fun chooseSystemDocumentDestination() {
        val filename = device.findObject(By.res("android:id/title"))
        filename?.setText("beta-capture.jpg")
        val saveButton = device.findObject(By.res("android:id/button1"))
            ?: device.findObject(By.res("com.google.android.documentsui:id/save"))
            ?: device.findObject(By.text("保存"))
            ?: device.findObject(By.text("SAVE"))
            ?: device.findObject(By.text("Save"))
        assertTrue("DocumentsUI save action unavailable", saveButton != null)
        saveButton!!.click()
        assertTrue(device.wait(Until.gone(By.pkg("com.google.android.documentsui")), TIMEOUT_MILLIS))
    }

    private fun clickText(text: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).performClick()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val CAMERA_READY_TIMEOUT_MILLIS = 30_000L
    }
}
