package com.jovi.photoai.camera

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.OutputStream

/** Copies a captured JPEG to the user-selected system document without persisting its Uri. */
internal object CaptureExporter {
    fun defaultFileName(captureNumber: Int): String =
        "photo-director-${captureNumber.coerceAtLeast(1)}.jpg"

    fun copy(source: File, destination: OutputStream): Boolean = runCatching {
        if (!source.isFile) return false
        source.inputStream().use { input -> destination.use { output -> input.copyTo(output) } }
        true
    }.getOrDefault(false)

    fun copyTo(contentResolver: ContentResolver, source: File, destination: Uri): Boolean =
        runCatching {
            val output = contentResolver.openOutputStream(destination) ?: return false
            copy(source, output)
        }.getOrDefault(false)
}
