package com.jovi.photoai.camera

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException

/** Legacy stream helper; durable exports now use CaptureEngine's exact-ticket protocol. */
internal object CaptureExporter {
    fun defaultFileName(captureNumber: Int): String = "photo-director-${captureNumber.coerceAtLeast(1)}.jpg"

    fun copy(source: File, destination: OutputStream): Boolean {
        return try {
            destination.use { output ->
                if (!source.isFile) return false
                source.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    fun copyTo(contentResolver: ContentResolver, source: File, destination: Uri): Boolean {
        return try {
            val output = contentResolver.openOutputStream(destination) ?: return false
            copy(source, output)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
