package com.jovi.photoai.data.reference

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

enum class ReferenceImportErrorCode {
    USER_CANCELLED,
    SOURCE_OPEN_FAILED,
    SOURCE_READ_FAILED,
    SOURCE_EMPTY,
    SOURCE_TRUNCATED,
    UNSUPPORTED_IMAGE,
    MIME_CONTENT_MISMATCH,
    IMAGE_TOO_LARGE,
    IMAGE_DECODE_FAILED,
    ORIENTATION_FAILED,
    PRIVATE_WRITE_FAILED,
    PRIVATE_COMMIT_FAILED,
    DATABASE_COMMIT_FAILED,
}

internal enum class PickerUriAuthority {
    SYSTEM_PHOTO_PICKER,
    MEDIASTORE,
    DOCUMENT_PROVIDER,
    OTHER_REDACTED,
}

internal enum class ImageMagic {
    JPEG,
    PNG,
    OTHER,
    EMPTY,
}

/** Ephemeral, non-identifying diagnostics. Never persist or log the source Uri. */
internal data class PickerReadDiagnostics(
    val scheme: String,
    val authority: PickerUriAuthority,
    val mimeType: String?,
    val assetFileDescriptorOpened: Boolean,
    val parcelFileDescriptorOpened: Boolean,
    val statSize: Long?,
    val inputStreamOpened: Boolean,
    val copiedBytes: Long,
    val magic: ImageMagic,
    val exceptionClass: String?,
)

internal sealed interface PickerSourceReadResult {
    data class Success(
        val sourceFile: File,
        val diagnostics: PickerReadDiagnostics,
    ) : PickerSourceReadResult

    data class Failure(
        val code: ReferenceImportErrorCode,
        val diagnostics: PickerReadDiagnostics,
    ) : PickerSourceReadResult
}

/**
 * The sole production reader for a Photo Picker Uri. It copies bytes immediately while the
 * temporary read grant is current, then all later work uses only the private source file.
 */
internal class PickerSourceReader(
    private val contentResolver: ContentResolver,
    private val referencesDirectory: File,
) {
    suspend fun copyImmediately(uri: Uri, opaqueId: String): PickerSourceReadResult =
        withContext(Dispatchers.IO) {
            withTimeout(SOURCE_READ_TIMEOUT_MILLIS) {
                copyBounded(uri, opaqueId)
            }
        }

    fun deleteTemporaryFiles(opaqueId: String) {
        File(referencesDirectory, "$opaqueId.source.part").delete()
        File(referencesDirectory, "$opaqueId.derived.part").delete()
    }

    private suspend fun copyBounded(uri: Uri, opaqueId: String): PickerSourceReadResult {
        if (!referencesDirectory.exists() && !referencesDirectory.mkdirs()) {
            return failure(uri, ReferenceImportErrorCode.PRIVATE_WRITE_FAILED)
        }
        if (!referencesDirectory.isDirectory) {
            return failure(uri, ReferenceImportErrorCode.PRIVATE_WRITE_FAILED)
        }

        val sourceFile = File(referencesDirectory, "$opaqueId.source.part")
        sourceFile.delete()
        val mimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
        var assetOpened = false
        var parcelOpened = false
        var statSize: Long? = null
        var inputOpened = false
        var copiedBytes = 0L
        var magic = ImageMagic.EMPTY
        var exceptionClass: String? = null

        runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                assetOpened = true
                if (descriptor.length >= 0L) statSize = descriptor.length
            }
        }.onFailure { exceptionClass = it.javaClass.simpleName }
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                parcelOpened = true
                val size = descriptor.statSize
                if (size >= 0L) statSize = size
            }
        }.onFailure { if (exceptionClass == null) exceptionClass = it.javaClass.simpleName }

        val input = try {
            contentResolver.openInputStream(uri)?.also { inputOpened = true }
        } catch (error: Exception) {
            exceptionClass = error.javaClass.simpleName
            null
        } ?: return failure(
            uri = uri,
            code = ReferenceImportErrorCode.SOURCE_OPEN_FAILED,
            mimeType = mimeType,
            assetOpened = assetOpened,
            parcelOpened = parcelOpened,
            statSize = statSize,
            inputOpened = inputOpened,
            copiedBytes = copiedBytes,
            magic = magic,
            exceptionClass = exceptionClass,
        )

        try {
            BufferedInputStream(input).use { stream ->
                FileOutputStream(sourceFile).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    val prefix = ByteArray(MAGIC_BYTES)
                    var prefixLength = 0
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        copiedBytes += read
                        if (copiedBytes > MAX_SOURCE_BYTES) {
                            sourceFile.delete()
                            return failure(
                                uri, ReferenceImportErrorCode.SOURCE_TRUNCATED, mimeType, assetOpened,
                                parcelOpened, statSize, inputOpened, copiedBytes, magic, null,
                            )
                        }
                        val copyLength = minOf(MAGIC_BYTES - prefixLength, read)
                        if (copyLength > 0) {
                            buffer.copyInto(prefix, prefixLength, 0, copyLength)
                            prefixLength += copyLength
                        }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                    magic = imageMagicFrom(prefix, prefixLength)
                }
            }
        } catch (cancelled: CancellationException) {
            sourceFile.delete()
            throw cancelled
        } catch (error: Exception) {
            sourceFile.delete()
            return failure(
                uri, ReferenceImportErrorCode.SOURCE_READ_FAILED, mimeType, assetOpened, parcelOpened,
                statSize, inputOpened, copiedBytes, magic, error.javaClass.simpleName,
            )
        }

        val validationError = when {
            copiedBytes == 0L -> ReferenceImportErrorCode.SOURCE_EMPTY
            magic == ImageMagic.OTHER -> ReferenceImportErrorCode.UNSUPPORTED_IMAGE
            mimeType == "image/jpeg" && magic != ImageMagic.JPEG -> ReferenceImportErrorCode.MIME_CONTENT_MISMATCH
            mimeType == "image/png" && magic != ImageMagic.PNG -> ReferenceImportErrorCode.MIME_CONTENT_MISMATCH
            else -> null
        }
        if (validationError != null) {
            sourceFile.delete()
            return failure(
                uri, validationError, mimeType, assetOpened, parcelOpened, statSize, inputOpened,
                copiedBytes, magic, exceptionClass,
            )
        }
        return PickerSourceReadResult.Success(
            sourceFile,
            diagnostics(uri, mimeType, assetOpened, parcelOpened, statSize, inputOpened, copiedBytes, magic, exceptionClass),
        )
    }

    private fun failure(
        uri: Uri,
        code: ReferenceImportErrorCode,
        mimeType: String? = runCatching { contentResolver.getType(uri) }.getOrNull(),
        assetOpened: Boolean = false,
        parcelOpened: Boolean = false,
        statSize: Long? = null,
        inputOpened: Boolean = false,
        copiedBytes: Long = 0L,
        magic: ImageMagic = ImageMagic.EMPTY,
        exceptionClass: String? = null,
    ) = PickerSourceReadResult.Failure(
        code,
        diagnostics(uri, mimeType, assetOpened, parcelOpened, statSize, inputOpened, copiedBytes, magic, exceptionClass),
    )

    private fun diagnostics(
        uri: Uri,
        mimeType: String?,
        assetOpened: Boolean,
        parcelOpened: Boolean,
        statSize: Long?,
        inputOpened: Boolean,
        copiedBytes: Long,
        magic: ImageMagic,
        exceptionClass: String?,
    ) = PickerReadDiagnostics(
        scheme = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> "CONTENT"
            ContentResolver.SCHEME_FILE -> "FILE"
            else -> "OTHER_REDACTED"
        },
        authority = when {
            uri.authority == "media" && uri.pathSegments.firstOrNull() == "picker" -> PickerUriAuthority.SYSTEM_PHOTO_PICKER
            uri.authority == "media" -> PickerUriAuthority.MEDIASTORE
            uri.authority.orEmpty().contains("documents") -> PickerUriAuthority.DOCUMENT_PROVIDER
            else -> PickerUriAuthority.OTHER_REDACTED
        },
        mimeType = mimeType,
        assetFileDescriptorOpened = assetOpened,
        parcelFileDescriptorOpened = parcelOpened,
        statSize = statSize,
        inputStreamOpened = inputOpened,
        copiedBytes = copiedBytes,
        magic = magic,
        exceptionClass = exceptionClass,
    )

    private companion object {
        const val MAGIC_BYTES = 16
        const val BUFFER_BYTES = 32 * 1024
        const val MAX_SOURCE_BYTES = 25L * 1024L * 1024L
        const val SOURCE_READ_TIMEOUT_MILLIS = 10_000L
    }
}

internal fun imageMagicFrom(prefix: ByteArray, size: Int): ImageMagic = when {
    size == 0 -> ImageMagic.EMPTY
    size >= 3 && prefix[0] == 0xFF.toByte() && prefix[1] == 0xD8.toByte() && prefix[2] == 0xFF.toByte() -> ImageMagic.JPEG
    size >= 8 && prefix.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
    ) -> ImageMagic.PNG
    else -> ImageMagic.OTHER
}
