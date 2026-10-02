package com.jovi.photoai.data.capture

import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * One engine per application process. All methods are blocking IO-thread operations.
 * Short state/file transitions are serialized. External document IO never holds this lock.
 * Room is the ledger; the final JPEG rename is a recoverable commit marker.
 */
internal class CaptureEngine(
    private val ledger: CaptureLedger,
    val files: CaptureFiles,
    val processId: String = UUID.randomUUID().toString(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var recovered = false

    @Synchronized
    fun recover() {
        if (recovered) return
        for (record in ledger.all()) {
            when (record.fileState) {
                CaptureFileState.DELETING -> {
                    files.remove(record.id)
                    ledger.transaction { ledger.remove(record.id) }
                }
                CaptureFileState.CAPTURING -> {
                    // A partial alone has no successful CameraX completion marker; retain it
                    // but never expose it as a completed image or silently discard it.
                    val digest = try { files.inspectFinal(record.id) } catch (_: Exception) { null }
                    ledger.transaction {
                        ledger.update(if (digest == null) record.copy(fileState = CaptureFileState.INTERRUPTED)
                        else record.copy(fileState = CaptureFileState.AVAILABLE, byteCount = digest.bytes, fileSha256 = digest.sha256))
                    }
                }
                CaptureFileState.AVAILABLE -> if (!files.readable(record)) {
                    ledger.transaction { ledger.update(record.copy(fileState = CaptureFileState.UNAVAILABLE)) }
                }
                else -> Unit
            }
        }
        val pending = ledger.pendingExport()
        if (pending != null && pending.processId != processId) ledger.transaction {
            ledger.read(pending.captureId)?.let { ledger.update(it.copy(exportState = CaptureExportState.UNKNOWN)) }
            // Keep the old unique registry key until result or explicit user acknowledgement.
            ledger.setPendingExport(pending.copy(phase = ExportPhase.INTERRUPTED))
        }
        recovered = true
    }

    @Synchronized
    fun begin(projectId: String?, referenceId: String?, id: String = UUID.randomUUID().toString().replace("-", "")): CaptureRecord {
        recover()
        if (!id.matches(Regex("^[a-f0-9]{32}$"))) throw CaptureProblem("CAPTURE_ID_INVALID")
        val record = CaptureRecord(id, projectId, referenceId, now())
        ledger.transaction { ledger.insert(record) }
        try { files.reserve(record.id) } catch (error: Exception) {
            ledger.transaction { ledger.update(record.copy(fileState = CaptureFileState.INTERRUPTED)) }
            throw error
        }
        return record
    }

    @Synchronized
    fun complete(id: String): CaptureRecord {
        recover()
        val record = ledger.read(id) ?: throw CaptureProblem("CAPTURE_NOT_FOUND")
        if (record.fileState == CaptureFileState.AVAILABLE) return record // duplicate success callback
        if (record.fileState != CaptureFileState.CAPTURING) throw CaptureProblem("CAPTURE_STALE_COMPLETION")
        val digest = files.finalize(id)
        val completed = record.copy(fileState = CaptureFileState.AVAILABLE, byteCount = digest.bytes, fileSha256 = digest.sha256)
        ledger.transaction { ledger.update(completed) }
        return completed
    }

    @Synchronized
    fun failed(id: String) {
        recover()
        val row = ledger.read(id) ?: return
        if (row.fileState == CaptureFileState.CAPTURING) ledger.transaction {
            // Do not undo an already-renamed final image when only the DB acknowledgement failed.
            val digest = try { files.inspectFinal(id) } catch (_: Exception) { null }
            ledger.update(if (digest == null) row.copy(fileState = CaptureFileState.INTERRUPTED)
            else row.copy(fileState = CaptureFileState.AVAILABLE, byteCount = digest.bytes, fileSha256 = digest.sha256))
        }
    }

    @Synchronized
    fun detachProject(projectId: String) {
        recover()
        ledger.transaction {
            ledger.all().filter { it.projectId == projectId }.forEach {
                ledger.update(it.copy(projectId = null))
            }
        }
    }

    @Synchronized
    fun detachMissingProjects(existing: Set<String>) {
        recover()
        ledger.transaction {
            ledger.all().filter { it.projectId != null && it.projectId !in existing }.forEach {
                ledger.update(it.copy(projectId = null))
            }
        }
    }

    @Synchronized
    fun delete(id: String) {
        recover()
        val row = ledger.read(id) ?: return
        if (row.fileState == CaptureFileState.CAPTURING || ledger.pendingExport()?.captureId == id) {
            throw CaptureProblem("CAPTURE_BUSY")
        }
        ledger.transaction { ledger.update(row.copy(fileState = CaptureFileState.DELETING)) }
        files.remove(id) // a failed unlink leaves a durable tombstone for startup retry
        ledger.transaction { ledger.remove(id) }
    }

    @Synchronized
    fun reserveExport(id: String): CaptureExportTicket {
        recover()
        if (ledger.pendingExport() != null) throw CaptureProblem("EXPORT_ALREADY_PENDING")
        val record = ledger.read(id) ?: throw CaptureProblem("CAPTURE_NOT_FOUND")
        if (record.fileState != CaptureFileState.AVAILABLE) throw CaptureProblem("CAPTURE_UNAVAILABLE")
        files.verify(record) // check the original before creating any external document
        val ticket = CaptureExportTicket(UUID.randomUUID().toString(), id, processId)
        ledger.transaction {
            ledger.setPendingExport(ticket)
            ledger.update(record.copy(exportState = CaptureExportState.SELECTING))
        }
        return ticket
    }

    /** Must be committed before launcher.launch(); an uncertain launch is never automatically repeated. */
    @Synchronized
    fun armExport(token: String): Boolean {
        recover()
        val ticket = ledger.pendingExport() ?: return false
        if (ticket.token != token || ticket.processId != processId || ticket.phase != ExportPhase.RESERVED) return false
        ledger.transaction { ledger.setPendingExport(ticket.copy(phase = ExportPhase.SELECTING)) }
        return true
    }

    @Synchronized
    fun cancelSelection(token: String) {
        recover()
        val ticket = ledger.pendingExport() ?: return
        if (ticket.token != token || ticket.phase == ExportPhase.WRITING) return
        finishExport(token, if (ticket.processId == processId && ticket.phase == ExportPhase.SELECTING)
            CaptureExportState.CANCELLED else CaptureExportState.UNKNOWN)
    }

    /** An explicit user action, not proof that any external document was removed. */
    @Synchronized
    fun abandonExport(token: String) {
        recover()
        val ticket = ledger.pendingExport() ?: return
        if (ticket.token != token || ticket.phase == ExportPhase.WRITING) throw CaptureProblem("EXPORT_BUSY")
        finishExport(token, CaptureExportState.UNKNOWN)
    }

    @Synchronized
    private fun beginExportWrite(token: String): CaptureRecord? {
        recover()
        val ticket = ledger.pendingExport() ?: return null
        if (ticket.token != token) return null
        if (ticket.processId != processId || ticket.phase == ExportPhase.INTERRUPTED) {
            finishExport(token, CaptureExportState.UNKNOWN)
            return null // never replay an old process's document write
        }
        if (ticket.phase != ExportPhase.SELECTING) return null
        val row = ledger.read(ticket.captureId) ?: return null
        if (row.fileState != CaptureFileState.AVAILABLE) {
            finishExport(token, CaptureExportState.FAILED)
            return null
        }
        files.verify(row)
        ledger.transaction {
            ledger.setPendingExport(ticket.copy(phase = ExportPhase.WRITING))
            ledger.update(row.copy(exportState = CaptureExportState.WRITING))
        }
        return row
    }

    /**
     * Input and output are always closed, including open/write/flush/close exceptions.
     * The supplied opener is never called for stale tickets or a different process.
     * External URI writes cannot be rolled back by the app. Exceptions mean UNKNOWN.
     */
    fun export(token: String, openOutput: () -> OutputStream, checkCancelled: () -> Unit = {}) {
        try {
            val record = beginExportWrite(token) ?: return
            val inputFile: File = files.final(record.id)
            inputFile.inputStream().use { input ->
                openOutput().use { output ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        checkCancelled()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        if (count > record.byteCount) throw CaptureProblem("CAPTURE_FILE_CHANGED")
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                    if (count != record.byteCount || digest.digest().joinToString("") { "%02x".format(it) } != record.fileSha256) {
                        throw CaptureProblem("CAPTURE_FILE_CHANGED")
                    }
                    output.flush()
                }
            }
            finishExport(token, CaptureExportState.SAVED)
        } catch (cancelled: CancellationException) {
            markUnknownWithoutMaskingFailure(token)
            throw cancelled
        } catch (_: Exception) {
            markUnknownWithoutMaskingFailure(token)
        }
    }

    @Synchronized
    private fun finishExport(token: String, state: CaptureExportState) {
        val pending = ledger.pendingExport() ?: return
        if (pending.token != token) return
        ledger.transaction {
            ledger.read(pending.captureId)?.let { row ->
                ledger.update(row.copy(exportState = state,
                    lastExportedAtMillis = if (state == CaptureExportState.SAVED) now() else row.lastExportedAtMillis))
            }
            ledger.clearPendingExport()
        }
    }

    private fun markUnknownWithoutMaskingFailure(token: String) {
        // If the ledger itself is unavailable, retain WRITING and ticket. On the next process
        // start recover() maps it to UNKNOWN. Never claim a successful rollback.
        try { finishExport(token, CaptureExportState.UNKNOWN) } catch (_: Exception) { }
    }
}
