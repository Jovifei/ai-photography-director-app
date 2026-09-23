package com.jovi.photoai.data.capture

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Owns only opaque-id files in an app-private directory. Java File APIs retain API 24 support.
 * No cache scan, source-library scan, URI persistence, or arbitrary path import is provided.
 * An existing final file is never overwritten. Directory-wide orphan deletion is forbidden.
 */
internal class CaptureFiles(root: File, private val decodable: (File) -> Boolean) {
    private val root = root.canonicalFile

    init {
        if ((!this.root.isDirectory && !this.root.mkdirs()) || !this.root.isDirectory) {
            throw CaptureProblem("CAPTURE_DIRECTORY_UNAVAILABLE")
        }
    }

    fun partial(id: String): File = owned(id, ".partial.jpg")
    fun final(id: String): File = owned(id, ".jpg")

    fun reserve(id: String) {
        if (final(id).exists() || !partial(id).createNewFile()) throw CaptureProblem("CAPTURE_FILE_CONFLICT")
    }

    fun finalize(id: String): CaptureFileDigest {
        val target = final(id)
        if (target.exists()) return inspect(target)
        val source = partial(id)
        val digest = inspect(source)
        // Force image data before the same-directory rename. This is a process-recovery
        // protocol, NOT a promise of atomic file+Room commit or arbitrary power-loss safety.
        FileOutputStream(source, true).use { it.fd.sync() }
        if (!source.renameTo(target)) throw CaptureProblem("CAPTURE_RENAME_FAILED")
        return digest
    }

    fun inspectFinal(id: String): CaptureFileDigest = inspect(final(id))

    fun verify(record: CaptureRecord): File {
        val file = final(record.id)
        val actual = inspect(file)
        if (record.byteCount != actual.bytes || record.fileSha256 != actual.sha256) {
            throw CaptureProblem("CAPTURE_FILE_CHANGED")
        }
        return file
    }

    fun readable(record: CaptureRecord): Boolean = try {
        val file = final(record.id)
        file.isFile && file.length() == record.byteCount && record.byteCount > 0
    } catch (_: Exception) { false }

    fun remove(id: String) {
        for (file in listOf(partial(id), final(id))) {
            if (file.exists() && !file.delete()) throw CaptureProblem("CAPTURE_DELETE_FAILED")
        }
    }

    private fun owned(id: String, suffix: String): File {
        if (!id.matches(Regex("^[a-f0-9]{32}$"))) throw CaptureProblem("CAPTURE_ID_INVALID")
        val file = File(root, id + suffix)
        // Reject an existing symlink, including links within this directory.
        if (file.canonicalFile != file.absoluteFile || file.canonicalFile.parentFile != root) {
            throw CaptureProblem("CAPTURE_PATH_INVALID")
        }
        return file
    }

    private fun inspect(file: File): CaptureFileDigest {
        val length = file.length()
        if (!file.isFile || length !in 4..MAX_CAPTURE_BYTES) throw CaptureProblem("CAPTURE_FILE_INVALID")
        RandomAccessFile(file, "r").use { input ->
            if (input.readUnsignedShort() != 0xffd8) throw CaptureProblem("CAPTURE_NOT_JPEG")
            input.seek(length - 2)
            if (input.readUnsignedShort() != 0xffd9) throw CaptureProblem("CAPTURE_INCOMPLETE_JPEG")
        }
        if (!decodable(file)) throw CaptureProblem("CAPTURE_DECODE_FAILED")
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                if (count > MAX_CAPTURE_BYTES) throw CaptureProblem("CAPTURE_FILE_TOO_LARGE")
                digest.update(buffer, 0, n)
            }
        }
        if (count != length || file.length() != length) throw CaptureProblem("CAPTURE_FILE_CHANGED")
        return CaptureFileDigest(count, digest.digest().joinToString("") { "%02x".format(it) })
    }

    companion object { const val MAX_CAPTURE_BYTES = 80L * 1024 * 1024 }
}
