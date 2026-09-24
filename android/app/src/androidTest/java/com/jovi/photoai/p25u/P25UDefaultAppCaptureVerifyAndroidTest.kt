package com.jovi.photoai.p25u

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.CaptureFileState
import com.jovi.photoai.data.capture.CaptureRepository
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class P25UDefaultAppCaptureVerifyAndroidTest {
    @Test
    fun verifyAfterExternalForceStop() {
        val arguments = InstrumentationRegistry.getArguments()
        assertTrue(arguments.getString("p25uDedicatedEmulator") == "true")
        assertTrue(arguments.getString("p25uPhase") == "verify")
        val run = requireNotNull(arguments.getString("p25uRun"))
        require(run.matches(Regex("^[a-f0-9]{32}$")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val captureId = requireNotNull(
            File(context.noBackupFilesDir, "p25u-default-recovery-$run.txt")
                .takeIf(File::isFile)?.readText()?.trim(),
        ) { "prepare capture marker missing" }
        assertTrue("prepare capture ID malformed", captureId.matches(Regex("^[a-f0-9]{32}$")))
        val repository = CaptureRepository.get(context)
        val record = requireNotNull(runBlocking {
            repository.records.first().singleOrNull { it.id == captureId }
        }) { "prepared capture ID is absent after process restart" }
        assertEquals(CaptureFileState.AVAILABLE, record.fileState)
        assertTrue("prepared capture file failed integrity verification",
            runBlocking { repository.previewFile(record) }.isFile)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val entry = device.wait(Until.findObject(By.text("查看全部成片")), 15_000)
            assertNotNull("default App did not reopen its root entry", entry)
            entry.click()
            scenario.onActivity { activity ->
                ViewModelProvider(
                    activity,
                    CaptureLibraryViewModelFactory(activity.application),
                )[CaptureLibraryViewModel::class.java].select(captureId)
            }
            val preview = device.wait(
                Until.findObject(By.desc("本机拍摄成片预览")),
                15_000,
            )
            assertNotNull("default App did not reopen the captured preview", preview)
        } finally {
            scenario.close()
        }
    }
}
