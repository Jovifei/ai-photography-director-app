package com.jovi.photoai.data.reference

import android.content.Context
import android.net.Uri
import java.io.File

internal sealed interface PrivateReferenceImportResult {
    data class Success(
        val image: SanitizedReferenceImage,
        val diagnostics: PickerReadDiagnostics,
    ) : PrivateReferenceImportResult

    data class Failure(
        val code: ReferenceImportErrorCode,
        val diagnostics: PickerReadDiagnostics?,
    ) : PrivateReferenceImportResult
}

/** Coordinates the short-lived source grant with the private source/derived file transaction. */
internal class PrivateReferenceImporter(context: Context) {
    private val finalRoot = File(context.applicationContext.filesDir, "references")
    private val stagingRoot = File(context.applicationContext.cacheDir, "reference-import")
    private val reader = PickerSourceReader(context.applicationContext.contentResolver, stagingRoot)
    private val sanitizer = ReferenceImageSanitizer(finalRoot, stagingRoot)

    suspend fun importImmediately(uri: Uri, opaqueId: String): PrivateReferenceImportResult {
        return when (val source = reader.copyImmediately(uri, opaqueId)) {
            is PickerSourceReadResult.Failure -> PrivateReferenceImportResult.Failure(source.code, source.diagnostics)
            is PickerSourceReadResult.Success -> {
                val sanitized = sanitizer.sanitize(source.sourceFile, opaqueId)
                sanitized.fold(
                    onSuccess = { image -> PrivateReferenceImportResult.Success(image, source.diagnostics) },
                    onFailure = { error ->
                        sanitizer.deleteDerivative(opaqueId)
                        PrivateReferenceImportResult.Failure(
                            (error as? ReferenceSanitizerException)?.errorCode
                                ?: ReferenceImportErrorCode.PRIVATE_WRITE_FAILED,
                            source.diagnostics,
                        )
                    },
                )
            }
        }
    }

    fun finish(opaqueId: String) {
        reader.deleteTemporaryFiles(opaqueId)
    }

    fun rollback(opaqueId: String) {
        reader.deleteTemporaryFiles(opaqueId)
        sanitizer.deleteDerivative(opaqueId)
    }

    fun exists(fileName: String): Boolean = sanitizer.exists(fileName)

    fun isValidPrivateJpeg(fileName: String): Boolean = sanitizer.isValidPrivateJpeg(fileName)

    fun delete(fileName: String): Boolean = sanitizer.delete(fileName)

    fun removeOrphans(knownFileNames: Set<String>) = sanitizer.removeOrphans(knownFileNames)
}
