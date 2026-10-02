package com.jovi.photoai.data.capture

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual production engine + real temporary files. Ledger is a transactional test double,
 * not Room; the JPEG inspector is synthetic, not Android BitmapFactory. See androidTest. */
internal class MemoryCaptureLedger : CaptureLedger {
    private val rows = linkedMapOf<String, CaptureRecord>()
    private var pending: CaptureExportTicket? = null
    var failUpdateOnce = false
    var failRemoveOnce = false
    override fun all() = rows.values.toList()
    override fun read(id: String) = rows[id]
    override fun insert(record: CaptureRecord) { check(rows.putIfAbsent(record.id, record) == null) }
    override fun update(record: CaptureRecord) {
        if (failUpdateOnce) { failUpdateOnce = false; error("SYNTHETIC_SQL_FAILURE") }
        check(rows.containsKey(record.id)); rows[record.id] = record
    }
    override fun remove(id: String) {
        if (failRemoveOnce) { failRemoveOnce = false; error("SYNTHETIC_DELETE_FAILURE") }
        rows.remove(id)
    }
    override fun pendingExport() = pending
    override fun setPendingExport(ticket: CaptureExportTicket) { pending = ticket }
    override fun clearPendingExport() { pending = null }
    override fun <T> transaction(block: () -> T): T {
        val snapshot = LinkedHashMap(rows)
        val pendingSnapshot = pending
        return try { block() } catch (error: Exception) {
            rows.clear(); rows.putAll(snapshot); pending = pendingSnapshot; throw error
        }
    }
}

internal object CaptureCoreCases {
    private var checks = 0
    private fun expect(value: Boolean) { check(value) { "CAPTURE_ASSERTION_${checks + 1}" }; checks++ }
    private fun fixture(action: (CaptureEngine, MemoryCaptureLedger) -> Unit) {
        val root = kotlin.io.path.createTempDirectory("photoai-capture-test-").toFile()
        try {
            val ledger = MemoryCaptureLedger()
            val files = CaptureFiles(root) { true }
            action(CaptureEngine(ledger, files, "process-a", now = { 42L }), ledger)
        } finally { root.deleteRecursively() } // only the unique test-owned temporary directory
    }
    private fun completed(engine: CaptureEngine, project: String? = "project-a", payload: Int = 7): CaptureRecord {
        val row = engine.begin(project, "reference-a")
        engine.files.partial(row.id).writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), payload.toByte(), 0xff.toByte(), 0xd9.toByte()))
        return engine.complete(row.id)
    }
    private fun failure(code: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        expect(error is CaptureProblem && error.code == code)
    }

    fun captureAndRecovery() {
        fixture { engine, ledger ->
            val row = completed(engine)
            expect(row.fileState == CaptureFileState.AVAILABLE)
            expect(row.projectId == "project-a" && row.referenceId == "reference-a")
            expect(row.byteCount == 5L && row.fileSha256?.length == 64)
            expect(engine.files.final(row.id).isFile && !engine.files.partial(row.id).exists())
            expect(engine.complete(row.id) == row)
            engine.failed(row.id)
            expect(ledger.read(row.id) == row)
            expect(row.exportState == CaptureExportState.NEVER)
            failure("CAPTURE_ID_INVALID") { engine.begin(null, null, "../outside") }
            expect(ledger.all().size == 1)
            val second = completed(engine, "project-b", 8)
            expect(row.id != second.id)
            expect(engine.files.verify(row).readBytes()[2].toInt() == 7)
            expect(engine.files.verify(second).readBytes()[2].toInt() == 8)
            val restart = CaptureEngine(ledger, engine.files, "process-b")
            restart.recover()
            expect(ledger.read(row.id) == row)
        }
        fixture { engine, ledger ->
            val row = engine.begin("project-a", "reference-a")
            engine.files.partial(row.id).writeBytes(byteArrayOf(-1, -40, 7, -1, -39))
            ledger.failUpdateOnce = true
            expect(runCatching { engine.complete(row.id) }.isFailure)
            expect(engine.files.final(row.id).isFile)
            expect(ledger.read(row.id)?.fileState == CaptureFileState.CAPTURING)
            CaptureEngine(ledger, engine.files, "process-b").recover()
            expect(ledger.read(row.id)?.fileState == CaptureFileState.AVAILABLE)
        }
        fixture { engine, ledger ->
            val row = engine.begin(null, null)
            engine.files.partial(row.id).writeText("incomplete")
            CaptureEngine(ledger, engine.files, "process-b").recover()
            expect(ledger.read(row.id)?.fileState == CaptureFileState.INTERRUPTED)
            expect(engine.files.partial(row.id).exists())
            expect(!engine.files.final(row.id).exists())
        }
        fixture { engine, ledger ->
            val row = completed(engine)
            engine.files.final(row.id).delete()
            CaptureEngine(ledger, engine.files, "process-b").recover()
            expect(ledger.read(row.id)?.fileState == CaptureFileState.UNAVAILABLE)
        }
    }

    fun exportIdentityAndSuccess() {
        fixture { engine, ledger ->
            val a = completed(engine, "project-a", 1)
            val b = completed(engine, "project-b", 2)
            val ticket = engine.reserveExport(a.id)
            expect(ticket.captureId == a.id)
            expect(engine.armExport(ticket.token))
            expect(!engine.armExport(ticket.token))
            failure("EXPORT_ALREADY_PENDING") { engine.reserveExport(b.id) }
            var opened = false
            engine.export("wrong-token", { opened = true; ByteArrayOutputStream() })
            expect(!opened)
            val out = object : ByteArrayOutputStream() { var closed = false; override fun close() { closed = true; super.close() } }
            engine.export(ticket.token, { out })
            expect(out.closed)
            expect(out.toByteArray().contentEquals(engine.files.final(a.id).readBytes()))
            expect(ledger.read(a.id)?.exportState == CaptureExportState.SAVED)
            expect(ledger.read(a.id)?.lastExportedAtMillis == 42L)
            expect(ledger.read(b.id)?.exportState == CaptureExportState.NEVER)
            expect(ledger.pendingExport() == null)
            engine.export(ticket.token, { error("STALE_OPENER_CALLED") })
            expect(ledger.read(a.id)?.exportState == CaptureExportState.SAVED)
            val next = engine.reserveExport(b.id)
            expect(next.token != ticket.token)
            engine.cancelSelection(ticket.token)
            expect(ledger.pendingExport()?.token == next.token)
            expect(engine.armExport(next.token))
            engine.cancelSelection(next.token)
            expect(ledger.read(b.id)?.exportState == CaptureExportState.CANCELLED)
            expect(engine.files.final(b.id).isFile)
        }
    }

    fun exportFailuresAndRestart() {
        for (failAt in listOf("open", "write", "flush", "close", "cancel")) fixture { engine, ledger ->
            val row = completed(engine)
            val ticket = engine.reserveExport(row.id)
            engine.armExport(ticket.token)
            var closed = false
            val output = object : OutputStream() {
                override fun write(value: Int) { if (failAt == "write") error("SYNTHETIC_WRITE_FAILURE") }
                override fun flush() { if (failAt == "flush") error("SYNTHETIC_FLUSH_FAILURE") }
                override fun close() { closed = true; if (failAt == "close") error("SYNTHETIC_CLOSE_FAILURE") }
            }
            val result = runCatching {
                engine.export(ticket.token, {
                    if (failAt == "open") error("SYNTHETIC_OPEN_FAILURE")
                    output
                }, { if (failAt == "cancel") throw CancellationException("SYNTHETIC_CANCEL") })
            }
            expect(if (failAt == "cancel") result.exceptionOrNull() is CancellationException else result.isSuccess)
            expect(failAt == "open" || closed)
            expect(ledger.read(row.id)?.exportState == CaptureExportState.UNKNOWN)
            expect(ledger.read(row.id)?.lastExportedAtMillis == null)
            expect(engine.files.final(row.id).isFile)
        }
        fixture { engine, ledger ->
            val row = completed(engine)
            val old = engine.reserveExport(row.id)
            engine.armExport(old.token)
            val restart = CaptureEngine(ledger, engine.files, "process-b")
            restart.recover()
            expect(ledger.read(row.id)?.exportState == CaptureExportState.UNKNOWN)
            expect(ledger.pendingExport()?.phase == ExportPhase.INTERRUPTED)
            expect(!restart.armExport(old.token))
            restart.export(old.token, { error("OLD_PROCESS_URI_REPLAYED") })
            expect(ledger.pendingExport() == null)
            expect(ledger.read(row.id)?.exportState == CaptureExportState.UNKNOWN)
            val next = restart.reserveExport(row.id)
            restart.export(old.token, { error("OLD_RESULT_WRITES_NEW_REQUEST") })
            expect(ledger.pendingExport()?.token == next.token)
            restart.abandonExport(next.token)
            expect(ledger.pendingExport() == null)
        }
        fixture { engine, ledger ->
            val row = completed(engine)
            val ticket = engine.reserveExport(row.id)
            engine.armExport(ticket.token)
            engine.files.final(row.id).writeBytes(byteArrayOf(-1, -40, 9, -1, -39))
            var opened = false
            engine.export(ticket.token, { opened = true; ByteArrayOutputStream() })
            expect(!opened)
            expect(ledger.read(row.id)?.exportState == CaptureExportState.UNKNOWN)
        }
    }

    fun deletionAndProjectOwnership() {
        fixture { engine, ledger ->
            val a = completed(engine)
            val b = completed(engine, "project-b")
            engine.detachProject("project-a")
            expect(ledger.read(a.id)?.projectId == null)
            expect(ledger.read(b.id)?.projectId == "project-b")
            expect(engine.files.final(a.id).exists())
            engine.detachMissingProjects(emptySet())
            expect(ledger.all().all { it.projectId == null })
            val ticket = engine.reserveExport(a.id)
            failure("CAPTURE_BUSY") { engine.delete(a.id) }
            engine.abandonExport(ticket.token)
            engine.delete(a.id)
            expect(ledger.read(a.id) == null && !engine.files.final(a.id).exists())
            expect(engine.files.final(b.id).exists())
            failure("CAPTURE_NOT_FOUND") { engine.complete(a.id) }
            val writing = engine.begin(null, null)
            failure("CAPTURE_BUSY") { engine.delete(writing.id) }
            engine.failed(writing.id)
            engine.delete(writing.id)
            expect(ledger.read(writing.id) == null)
        }
        fixture { engine, ledger ->
            val row = completed(engine)
            ledger.failRemoveOnce = true
            expect(runCatching { engine.delete(row.id) }.isFailure)
            expect(ledger.read(row.id)?.fileState == CaptureFileState.DELETING)
            CaptureEngine(ledger, engine.files, "process-b").recover()
            expect(ledger.read(row.id) == null)
        }
    }

    fun concurrentExportAndCapture() {
        fixture { engine, ledger ->
            val row = completed(engine)
            val ticket = engine.reserveExport(row.id)
            engine.armExport(ticket.token)
            val writing = CountDownLatch(1)
            val release = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val thread = Thread {
                try {
                    engine.export(ticket.token, { object : ByteArrayOutputStream() {
                        override fun write(bytes: ByteArray, off: Int, len: Int) {
                            writing.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            super.write(bytes, off, len)
                        }
                    } })
                } catch (error: Throwable) { failure.set(error) }
            }
            thread.start()
            try {
                expect(writing.await(5, TimeUnit.SECONDS))
                failure("CAPTURE_BUSY") { engine.delete(row.id) }
                failure("EXPORT_BUSY") { engine.abandonExport(ticket.token) }
                val second = completed(engine, "project-b")
                expect(second.fileState == CaptureFileState.AVAILABLE)
                expect(second.projectId == "project-b")
            } finally { release.countDown(); thread.join(6000) }
            expect(!thread.isAlive && failure.get() == null)
            expect(ledger.read(row.id)?.exportState == CaptureExportState.SAVED)
        }
    }

    fun fileSafety() {
        fixture { engine, ledger ->
            failure("CAPTURE_ID_INVALID") { engine.files.partial("../../escape") }
            val row = engine.begin(null, null)
            failure("CAPTURE_FILE_CONFLICT") { engine.files.reserve(row.id) }
            engine.files.partial(row.id).writeText("notjpeg")
            failure("CAPTURE_NOT_JPEG") { engine.complete(row.id) }
            expect(ledger.read(row.id)?.fileState == CaptureFileState.CAPTURING)
            expect(!engine.files.final(row.id).exists())
            engine.files.partial(row.id).writeBytes(byteArrayOf(-1, -40, 1, 2))
            failure("CAPTURE_INCOMPLETE_JPEG") { engine.complete(row.id) }
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        checks = 0
        captureAndRecovery(); exportIdentityAndSuccess(); exportFailuresAndRestart()
        deletionAndProjectOwnership(); concurrentExportAndCapture(); fileSafety()
        println("PASS capture-core assertions=$checks scope=PRODUCTION_ENGINE_REAL_FILES_TEST_LEDGER_NO_ANDROID_CODEC")
    }
}
