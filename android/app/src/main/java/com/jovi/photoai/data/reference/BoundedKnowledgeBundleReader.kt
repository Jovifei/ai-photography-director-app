package com.jovi.photoai.data.reference

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal class KnowledgeBundleDocumentTooLargeException : IOException()

/** Reads at most limit + one probe byte. Caller owns closing the transient document stream. */
internal fun readBoundedKnowledgeBundleBytes(
    input: InputStream,
    limit: Int,
    checkCancelled: () -> Unit = {},
): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(minOf(8 * 1024, limit))
    while (true) {
        checkCancelled()
        val remaining = limit - output.size()
        val wanted = minOf(buffer.size.toLong(), remaining.toLong() + 1).toInt()
        val count = input.read(buffer, 0, wanted)
        if (count < 0) break
        if (count == 0) {
            // Defend against providers returning zero instead of blocking or signalling EOF.
            val single = input.read()
            if (single < 0) break
            if (remaining == 0) throw KnowledgeBundleDocumentTooLargeException()
            output.write(single)
        } else {
            if (count > remaining) throw KnowledgeBundleDocumentTooLargeException()
            output.write(buffer, 0, count)
        }
    }
    checkCancelled()
    return output.toByteArray()
}
