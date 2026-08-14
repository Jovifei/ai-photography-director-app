package com.jovi.photoai.ui1

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.ReferenceRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual system Picker with only a test-owned MediaStore image. */
@RunWith(AndroidJUnit4::class)
class Ui1SystemPickerFlowAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = ReferenceRepository.create(context)
    private val preferences = ReferenceLibraryPreferences(context)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPrivateLibrary() = runBlocking {
        repository.clearAll()
        preferences.clearLastActiveReferenceId()
    }

    @After
    fun cleanupPrivateLibrary() = runBlocking {
        repository.clearAll()
        preferences.clearLastActiveReferenceId()
    }

    @Test
    fun systemPicker_selectsOnlyTestOwnedMedia_andLeavesOnlyPrivateDerivative() = runBlocking {
        SyntheticPickerMediaFactory(context).use { media ->
            val fixture = media.jpeg(displayName = "ui1-picker-${UUID.randomUUID()}.jpg")
            openSystemPicker()
            selectFixture(fixture.displayName)

            val record = awaitSingleActiveRecord()
            assertTrue(record.imageFileName.matches(Regex("^[a-zA-Z0-9_-]+\\.jpg$")))
            assertEquals(1, File(context.filesDir, "references").listFiles()?.count { it.isFile && it.name.endsWith(".jpg") })
            assertTrue(File(context.cacheDir, "reference-import").listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun systemPicker_cancel_returnsToImportWithoutCreatingReference() = runBlocking {
        openSystemPicker()
        device.pressBack()
        waitForAction("选择照片")
        delay(300)
        assertTrue(repository.activeRecords.first().isEmpty())
        assertTrue(File(context.cacheDir, "reference-import").listFiles().isNullOrEmpty())
    }

    private fun openSystemPicker() {
        clickAction("新建拍摄项目")
        clickAction("选择照片")
        assertTrue(device.wait(Until.hasObject(By.pkg(PICKER_PACKAGE)), TIMEOUT_MILLIS))
    }

    private fun clickAction(description: String) {
        waitForAction(description)
        composeRule
            .onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()
    }

    private fun waitForAction(description: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun selectFixture(displayName: String) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var fixture: UiObject2? = null
        while (fixture == null && SystemClock.elapsedRealtime() < deadline) {
            fixture = device.findObject(By.text(displayName))
                ?: device.findObject(By.desc(displayName))
                ?: singlePickerThumbnail()
            if (fixture == null) SystemClock.sleep(100)
        }
        fixture?.click() ?: error("PICKER_FIXTURE_NOT_SELECTABLE")
        device.wait(Until.findObject(By.res(PICKER_PACKAGE, "button_add")), SHORT_TIMEOUT_MILLIS)?.click()
    }

    private fun singlePickerThumbnail(): UiObject2? =
        device.findObjects(By.res(PICKER_PACKAGE, "icon_thumbnail")).singleOrNull()

    private suspend fun awaitSingleActiveRecord(): ReferenceRecord {
        repeat(50) {
            val records = repository.activeRecords.first()
            if (records.size == 1) return records.single()
            delay(200)
        }
        error("PICKER_IMPORT_NOT_COMPLETED")
    }

    private companion object {
        const val PICKER_PACKAGE = "com.google.android.providers.media.module"
        const val TIMEOUT_MILLIS = 10_000L
        const val SHORT_TIMEOUT_MILLIS = 2_000L
    }
}
