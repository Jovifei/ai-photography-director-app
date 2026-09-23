package com.jovi.photoai.p25u

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P25UDefaultAppCaptureVerifyAndroidTest {
    @Test
    fun verifyAfterExternalForceStop() {
        val arguments = InstrumentationRegistry.getArguments()
        assertTrue(arguments.getString("p25uDedicatedEmulator") == "true")
        assertTrue(arguments.getString("p25uPhase") == "verify")
        val run = requireNotNull(arguments.getString("p25uRun"))
        require(run.matches(Regex("^[a-f0-9]{32}$")))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val entry = device.wait(Until.findObject(By.text("查看全部成片")), 15_000)
            assertNotNull("default App did not reopen its root entry", entry)
            entry.click()
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
