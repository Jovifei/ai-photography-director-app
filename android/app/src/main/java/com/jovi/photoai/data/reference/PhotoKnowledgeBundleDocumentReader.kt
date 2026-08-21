package com.jovi.photoai.data.reference

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream

internal class PhotoKnowledgeBundleDocumentReader(
    private val contentResolver: ContentResolver,
) {
    fun read(uri: Uri?): PhotoKnowledgeBundleParseResult {
        if (uri == null) return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_NOT_SELECTED)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        }
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    if (output.size() > MAX_KNOWLEDGE_BUNDLE_BYTES) {
                        return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_TOO_LARGE)
                    }
                }
                output.toByteArray()
            } ?: return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        } catch (_: SecurityException) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        } catch (_: Exception) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        }
        return PhotoKnowledgeBundleParser.parse(bytes)
    }
}
