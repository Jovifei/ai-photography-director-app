package com.jovi.photoai.ui1

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Creates only test-owned MediaStore rows. It never enumerates or reads pre-existing device media.
 * Test callers must close it; close deletes every row created by this instance.
 */
internal class SyntheticPickerMediaFactory(
    private val context: Context,
) : Closeable {
    private val resolver = context.contentResolver
    private val createdUris = mutableListOf<Uri>()

    fun jpeg(width: Int = 96, height: Int = 64, orientation: Int = ExifInterface.ORIENTATION_NORMAL): Fixture {
        val bytes = jpegBytes(width, height, orientation)
        val expectedAspect = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            -> height.toFloat() / width.toFloat()
            else -> width.toFloat() / height.toFloat()
        }
        return insert(bytes, "image/jpeg", ExpectedFormat.JPEG, expectedAspect)
    }

    fun png(width: Int = 96, height: Int = 64): Fixture =
        insert(pngBytes(width, height), "image/png", ExpectedFormat.PNG, width.toFloat() / height.toFloat())

    fun corrupt(): Fixture =
        insert("not-an-image".encodeToByteArray(), "image/jpeg", ExpectedFormat.INVALID, null)

    fun mimeMismatchPngAsJpeg(): Fixture =
        insert(pngBytes(96, 64), "image/jpeg", ExpectedFormat.PNG, null)

    fun controlledLarge(): Fixture {
        val data = ByteArray(26 * 1024 * 1024)
        data[0] = 0xFF.toByte()
        data[1] = 0xD8.toByte()
        data[2] = 0xFF.toByte()
        return insert(data, "image/jpeg", ExpectedFormat.TOO_LARGE, null)
    }

    fun directDecodes(fixture: Fixture): Boolean =
        resolver.openInputStream(fixture.uri)?.use(BitmapFactory::decodeStream) != null

    override fun close() {
        createdUris.forEach { uri -> resolver.delete(uri, null, null) }
        createdUris.clear()
    }

    private fun insert(
        bytes: ByteArray,
        mimeType: String,
        expectedFormat: ExpectedFormat,
        expectedAspectRatio: Float?,
    ): Fixture {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ui1-fixture-${UUID.randomUUID()}.bin")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UI1PickerFixture")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(resolver.insert(collection, values))
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("TEST_MEDIA_WRITE_UNAVAILABLE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            createdUris += uri
            return Fixture(uri, expectedFormat, sha256(bytes), expectedAspectRatio)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun jpegBytes(width: Int, height: Int, orientation: Int): ByteArray {
        val temporary = File(context.cacheDir, "ui1-fixture-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(0x5B, 0xA4, 0xD9))
            FileOutputStream(temporary).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
                output.fd.sync()
            }
            ExifInterface(temporary).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            return temporary.readBytes()
        } finally {
            bitmap.recycle()
            temporary.delete()
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(0x38, 0x84, 0xA6))
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    internal data class Fixture(
        val uri: Uri,
        val expectedFormat: ExpectedFormat,
        val sourceSha256: String,
        val expectedAspectRatio: Float?,
    )

    internal enum class ExpectedFormat { JPEG, PNG, INVALID, TOO_LARGE }
}
