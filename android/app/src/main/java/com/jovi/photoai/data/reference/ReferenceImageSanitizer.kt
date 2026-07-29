package com.jovi.photoai.data.reference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SanitizedReferenceImage(
    val fileName: String,
    val aspectRatio: Float,
)

/** Decodes only a private source part and emits a metadata-free private JPEG derivative. */
internal class ReferenceImageSanitizer(
    private val finalReferencesDirectory: File,
    private val stagingDirectory: File,
) {
    suspend fun sanitize(sourceFile: File, opaqueId: String): Result<SanitizedReferenceImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!finalReferencesDirectory.exists() && !finalReferencesDirectory.mkdirs()) {
                    throw ReferenceSanitizerException(ReferenceImportErrorCode.PRIVATE_WRITE_FAILED)
                }
                if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
                    throw ReferenceSanitizerException(ReferenceImportErrorCode.PRIVATE_WRITE_FAILED)
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw ReferenceSanitizerException(ReferenceImportErrorCode.IMAGE_DECODE_FAILED)
                }
                if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_INPUT_PIXELS) {
                    throw ReferenceSanitizerException(ReferenceImportErrorCode.IMAGE_TOO_LARGE)
                }

                val bitmap = BitmapFactory.decodeFile(
                    sourceFile.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: throw ReferenceSanitizerException(ReferenceImportErrorCode.IMAGE_DECODE_FAILED)

                val rotated = try {
                    rotate(bitmap, orientationFor(sourceFile))
                } catch (_: Exception) {
                    bitmap.recycle()
                    throw ReferenceSanitizerException(ReferenceImportErrorCode.ORIENTATION_FAILED)
                }
                val scaled = scaleToMaxDimension(rotated)
                try {
                    val fileName = "$opaqueId.jpg"
                    // Cache is unconditionally excluded from Android backup. Both directories live
                    // on app-internal storage; rename is used as a single operation and never falls
                    // back to copying a partially written derivative into the backup allowlist.
                    val temporaryFile = File(stagingDirectory, "$opaqueId.derived.part")
                    val finalFile = File(finalReferencesDirectory, fileName)
                    temporaryFile.delete()
                    finalFile.delete()
                    FileOutputStream(temporaryFile).use { output ->
                        if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                            throw ReferenceSanitizerException(ReferenceImportErrorCode.PRIVATE_WRITE_FAILED)
                        }
                        output.fd.sync()
                    }
                    if (!temporaryFile.renameTo(finalFile)) {
                        throw ReferenceSanitizerException(ReferenceImportErrorCode.PRIVATE_COMMIT_FAILED)
                    }
                    SanitizedReferenceImage(
                        fileName = fileName,
                        aspectRatio = scaled.width.toFloat() / scaled.height.toFloat(),
                    )
                } finally {
                    if (scaled !== rotated) scaled.recycle()
                    if (rotated !== bitmap) rotated.recycle()
                    bitmap.recycle()
                }
            }
        }

    fun deleteDerivative(opaqueId: String) {
        File(stagingDirectory, "$opaqueId.derived.part").delete()
        File(finalReferencesDirectory, "$opaqueId.jpg").delete()
    }

    fun exists(fileName: String): Boolean = File(finalReferencesDirectory, fileName).isFile

    /** Startup recovery accepts only a decodable private JPEG with a safe opaque basename. */
    fun isValidPrivateJpeg(fileName: String): Boolean {
        if (!fileName.matches(Regex("^[a-zA-Z0-9_-]+\\.jpg$"))) return false
        val file = File(finalReferencesDirectory, fileName)
        if (!file.isFile) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    fun delete(fileName: String): Boolean {
        val file = File(finalReferencesDirectory, fileName)
        return !file.exists() || file.delete()
    }

    fun removeOrphans(knownFileNames: Set<String>) {
        stagingDirectory.listFiles()
            ?.filter { it.isFile }
            ?.forEach { it.delete() }
        if (!finalReferencesDirectory.isDirectory) return
        finalReferencesDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jpg") && it.name !in knownFileNames }
            ?.forEach { it.delete() }
    }

    private fun orientationFor(file: File): Int = runCatching {
        ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToMaxDimension(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= MAX_DERIVED_DIMENSION) return bitmap
        val ratio = MAX_DERIVED_DIMENSION.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= MAX_DERIVED_DIMENSION) {
            sample *= 2
            largest /= 2
        }
        return sample
    }

    private companion object {
        const val MAX_DERIVED_DIMENSION = 2048
        const val MAX_INPUT_PIXELS = 40_000_000L
        const val JPEG_QUALITY = 92
    }
}

internal class ReferenceSanitizerException(
    val errorCode: ReferenceImportErrorCode,
) : Exception(errorCode.name)
