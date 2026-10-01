package com.jovi.photoai.t16

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fresh process phases; deliberately has no permission-grant rule and never mutates permission. */
@RunWith(AndroidJUnit4::class)
class T16CameraPermissionAndroidTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before fun dedicatedEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("t16DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim())
        assertEquals("T3_API35_20260927", device.executeShellCommand("getprop ro.boot.qemu.avd_name").trim())
        assertTrue(InstrumentationRegistry.getArguments().getString("t16PermissionRun")!!.matches(Regex("[a-f0-9]{32}")))
    }

    @Test fun prepareMeasuredOriginalPermissionWithoutActivityOrMutation() {
        publish("T16_PERMISSION_RUN_ID", InstrumentationRegistry.getArguments().getString("t16PermissionRun")!!)
        publish("T16_PERMISSION_PREPARE_PID", Process.myPid().toString())
        publish("T16_PERMISSION_ORIGINAL", if (granted()) "GRANTED" else "DENIED")
    }

    @Test fun freshDeniedProcessShowsPermissionContentWithoutHardwareControls() {
        assertFalse(granted())
        publish("T16_PERMISSION_DENIED_PID", Process.myPid().toString())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                PhotoDirectorTheme { CameraScreen(emptyList(), directCaptureMode = true) }
            } }
            compose.waitForIdle()
            // Host normally sets user-fixed temporarily. If a dialog exists, only deny it.
            device.waitForIdle()
            device.findObject(By.res("com.android.permissioncontroller", "permission_deny_button"))?.click()
            compose.waitUntil(20_000) { compose.onAllNodesWithText("需要相机权限").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("需要相机权限").assertIsDisplayed()
            compose.onNodeWithContentDescription("拍摄").assertDoesNotExist()
            compose.onNodeWithTag("zoom-open").assertDoesNotExist()
            compose.onNodeWithTag("camera-lens-switch").assertDoesNotExist()
            assertFalse(granted())
            publish("T16_PERMISSION_DENIED", "PASS_FRESH_PROCESS_PERMISSION_CONTENT_NO_HARDWARE_CONTROLS")
        }
    }

    @Test fun freshGrantedProcessRecoversActualCameraPreview() {
        assertTrue(granted())
        publish("T16_PERMISSION_RECOVERY_PID", Process.myPid().toString())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                PhotoDirectorTheme { CameraScreen(emptyList(), directCaptureMode = true) }
            } }
            compose.waitForIdle()
            compose.waitUntil(25_000) {
                compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().any { isEnabled().matches(it) }
            }
            var streaming = false
            compose.waitUntil(25_000) {
                scenario.onActivity { activity -> streaming = previews(activity.window.decorView).any {
                    it.previewStreamState.value == PreviewView.StreamState.STREAMING
                } }
                streaming
            }
            publish("T16_PERMISSION_RECOVERY", "PASS_FRESH_PROCESS_ENABLED_SHUTTER_AND_ACTUAL_STREAMING")
        }
    }

    private fun granted() = ContextCompat.checkSelfPermission(instrumentation.targetContext,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun previews(view: View): List<PreviewView> = when (view) {
        is PreviewView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { previews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun publish(key: String, value: String) {
        instrumentation.sendStatus(2, Bundle().apply { putString(key, value) })
    }
}
