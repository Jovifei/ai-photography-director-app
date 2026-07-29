package com.jovi.photoai.ui1

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.exifinterface.media.ExifInterface
import com.jovi.photoai.data.reference.PrivateReferenceImportResult
import com.jovi.photoai.data.reference.PrivateReferenceImporter
import com.jovi.photoai.data.reference.ReferenceImportErrorCode
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dedicated-device diagnostic. All sources are synthetic MediaStore rows made by this test;
 * no pre-existing gallery media, raw Uri, source filename, or image bytes enter test output.
 */
@RunWith(AndroidJUnit4::class)
class PhotoPickerImportDiagnosticAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun syntheticMedia_matrix_isImmediate_private_and_failClosed() = runBlocking {
        SyntheticPickerMediaFactory(context).use { media ->
            val importer = PrivateReferenceImporter(context)
            listOf(
                media.jpeg(),
                media.png(),
                media.jpeg(orientation = 6),
                media.jpeg(orientation = 3),
                media.jpeg(orientation = 8),
                media.jpeg(width = 3072, height = 2304),
            ).forEach { fixture ->
                assertTrue("Expected a generated valid source to direct-decode before Picker.", media.directDecodes(fixture))
                val id = UUID.randomUUID().toString()
                val result = importer.importImmediately(fixture.uri, id)
                assertTrue(result is PrivateReferenceImportResult.Success)
                result as PrivateReferenceImportResult.Success
                assertTrue(result.image.fileName.matches(Regex("^[a-zA-Z0-9_-]+\\.jpg$")))
                val privateFile = File(context.filesDir, "references/${result.image.fileName}")
                assertTrue(privateFile.isFile)
                assertEquals(fixture.expectedAspectRatio!!, result.image.aspectRatio, 0.02f)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(privateFile.absolutePath, bounds)
                assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 2048)
                assertFalse(
                    ExifInterface(privateFile).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) in setOf(
                        ExifInterface.ORIENTATION_ROTATE_90,
                        ExifInterface.ORIENTATION_ROTATE_180,
                        ExifInterface.ORIENTATION_ROTATE_270,
                    ),
                )
                assertEquals(null, result.diagnostics.exceptionClass)
                importer.rollback(id)
            }

            assertFailure(media.corrupt(), ReferenceImportErrorCode.UNSUPPORTED_IMAGE, importer)
            assertFailure(media.mimeMismatchPngAsJpeg(), ReferenceImportErrorCode.MIME_CONTENT_MISMATCH, importer)
            assertFailure(media.controlledLarge(), ReferenceImportErrorCode.SOURCE_TRUNCATED, importer)
            assertStagingIsEmpty()
        }
    }

    @Test
    fun repository_persists_only_private_basename_and_reconciles_missing_or_corrupt_derivative() = runBlocking {
        SyntheticPickerMediaFactory(context).use { media ->
            val repository = ReferenceRepository.create(context)
            repository.clearAll()
            val imported = repository.importFromPicker(media.jpeg().uri)
            assertTrue(imported is com.jovi.photoai.data.reference.ReferenceImportResult.Success)
            val record = (imported as com.jovi.photoai.data.reference.ReferenceImportResult.Success).record
            assertFalse(record.imageFileName.contains("://"))
            assertFalse(record.photo.imageAssetKey.contains("://"))
            val image = File(context.filesDir, "references/${record.imageFileName}")
            assertTrue(image.delete())
            assertEquals(1, repository.reconcile())
            assertNotNull(record.photo.id)

            val second = repository.importFromPicker(media.jpeg().uri)
            assertTrue(second is com.jovi.photoai.data.reference.ReferenceImportResult.Success)
            val corruptRecord = (second as com.jovi.photoai.data.reference.ReferenceImportResult.Success).record
            File(context.filesDir, "references/${corruptRecord.imageFileName}").writeBytes(byteArrayOf(0x01, 0x02))
            assertEquals(1, repository.reconcile())
            assertStagingIsEmpty()
        }
    }

    @Test
    fun repository_delete_clear_restart_and_orphan_recovery_are_idempotent() = runBlocking {
        SyntheticPickerMediaFactory(context).use { media ->
            val repository = ReferenceRepository.create(context)
            repository.clearAll()
            val first = (repository.importFromPicker(media.jpeg().uri) as ReferenceImportResult.Success).record
            val second = (repository.importFromPicker(media.png().uri) as ReferenceImportResult.Success).record
            assertEquals(setOf(first.photo.id, second.photo.id), repository.activeRecords.first().map { it.photo.id }.toSet())

            ReferenceRepository.create(context).also { restarted ->
                assertTrue(restarted.activeRecords.first().any { it.photo.id == first.photo.id })
                restarted.delete(first.photo.id)
                assertFalse(File(context.filesDir, "references/${first.imageFileName}").exists())
                assertEquals(listOf(second.photo.id), restarted.activeRecords.first().map { it.photo.id })

                val orphan = File(context.filesDir, "references/${UUID.randomUUID()}.jpg")
                orphan.parentFile?.mkdirs()
                orphan.writeBytes(byteArrayOf(0x01))
                restarted.reconcile()
                assertFalse(orphan.exists())
                restarted.clearAll()
                assertTrue(restarted.activeRecords.first().isEmpty())
                assertFalse(File(context.filesDir, "references/${second.imageFileName}").exists())
            }
            assertStagingIsEmpty()
        }
    }

    private suspend fun assertFailure(
        fixture: SyntheticPickerMediaFactory.Fixture,
        expected: ReferenceImportErrorCode,
        importer: PrivateReferenceImporter,
    ) {
        val id = UUID.randomUUID().toString()
        val result = importer.importImmediately(fixture.uri, id)
        assertTrue(result is PrivateReferenceImportResult.Failure)
        assertEquals(expected, (result as PrivateReferenceImportResult.Failure).code)
        importer.rollback(id)
    }

    private fun assertStagingIsEmpty() {
        assertTrue(File(context.cacheDir, "reference-import").listFiles().isNullOrEmpty())
    }
}
