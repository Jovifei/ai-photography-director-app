package com.jovi.photoai.data.reference

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException

internal class PhotoKnowledgeBundleDocumentReader(
    private val contentResolver: ContentResolver,
) {
    fun read(uri: Uri?, checkCancelled: () -> Unit = {}): PhotoKnowledgeBundleParseResult {
        if (uri == null) return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_NOT_SELECTED)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        }
        val bytes = try {
            checkCancelled()
            contentResolver.openInputStream(uri)?.use { input ->
                readBoundedKnowledgeBundleBytes(input, MAX_KNOWLEDGE_BUNDLE_BYTES, checkCancelled)
            } ?: return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: KnowledgeBundleDocumentTooLargeException) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_TOO_LARGE)
        } catch (_: SecurityException) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        } catch (_: Exception) {
            return PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
        }
        checkCancelled()
        return PhotoKnowledgeBundleParser.parse(bytes)
    }
}
