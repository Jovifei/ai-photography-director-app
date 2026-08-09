package com.jovi.photoai.ui1

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.ReferenceRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fixture consumed by the host backup script; it never writes user media or source Uris. */
@RunWith(AndroidJUnit4::class)
class Ui1BackupFixtureAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = ReferenceRepository.create(context)
    private val preferences = ReferenceLibraryPreferences(context)
    private val cacheProbe = File(context.cacheDir, CACHE_PROBE_NAME)

    @Test
    fun prepare() = runBlocking {
        repository.clearAll()
        preferences.clearLastActiveReferenceId()
        cacheProbe.delete()

        SyntheticPickerMediaFactory(context).use { media ->
            val record = (repository.importFromPicker(media.jpeg().uri) as ReferenceImportResult.Success).record
            preferences.saveLastActiveReferenceId(record.photo.id)
            cacheProbe.writeText("synthetic-only")

            assertTrue(File(context.filesDir, "references/${record.imageFileName}").isFile)
            assertEquals(record.photo.id, preferences.lastActiveReferenceId())
            assertTrue(File(context.cacheDir, "reference-import").listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun verify() = runBlocking {
        val records = repository.activeRecords.first()
        assertEquals(1, records.size)
        val restoredId = requireNotNull(preferences.lastActiveReferenceId())
        assertTrue("RESTORED_ACTIVE_ID_MISMATCH", restoredId == records.single().photo.id)
        assertNotNull(repository.activeRecord(restoredId))
        assertTrue(File(context.filesDir, "references/${records.single().imageFileName}").isFile)
        assertFalse(cacheProbe.exists())
        assertTrue(File(context.cacheDir, "reference-import").listFiles().isNullOrEmpty())

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            assertTrue(device.wait(Until.hasObject(By.pkg("com.jovi.photoai")), STARTUP_TIMEOUT_MILLIS))
        } finally {
            scenario.close()
            repository.clearAll()
            preferences.clearLastActiveReferenceId()
            cacheProbe.delete()
        }
    }

    @Test
    fun reconcileRemovesMissingDerivedImageAndClearsPrimaryReference() = runBlocking {
        repository.clearAll()
        preferences.clearLastActiveReferenceId()

        SyntheticPickerMediaFactory(context).use { media ->
            val record = (repository.importFromPicker(media.jpeg().uri) as ReferenceImportResult.Success).record
            preferences.saveLastActiveReferenceId(record.photo.id)
            assertTrue(File(context.filesDir, "references/${record.imageFileName}").delete())

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                assertTrue(device.wait(Until.hasObject(By.pkg("com.jovi.photoai")), STARTUP_TIMEOUT_MILLIS))
                val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MILLIS
                while (preferences.lastActiveReferenceId() != null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100)
                }
                assertEquals(null, preferences.lastActiveReferenceId())
                assertEquals(0, repository.activeRecords.first().size)
            } finally {
                scenario.close()
                repository.clearAll()
                preferences.clearLastActiveReferenceId()
            }
        }
    }

    @Test
    fun reconcileRemovesOrphanDerivedImage() = runBlocking {
        repository.clearAll()
        preferences.clearLastActiveReferenceId()

        SyntheticPickerMediaFactory(context).use { media ->
            val record = (repository.importFromPicker(media.jpeg().uri) as ReferenceImportResult.Success).record
            val knownFile = File(context.filesDir, "references/${record.imageFileName}")
            val orphanFile = File(context.filesDir, "references/ui1-orphan.jpg")
            assertTrue(knownFile.copyTo(orphanFile, overwrite = true).exists())
            repository.clearAll()

            val summary = repository.reconcile()

            assertEquals(1, summary.removedOrphanFiles)
            assertFalse(orphanFile.exists())
        }
    }

    private companion object {
        const val CACHE_PROBE_NAME = "ui1-backup-probe"
        const val STARTUP_TIMEOUT_MILLIS = 10_000L
    }
}
