package com.jovi.photoai.p25u

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.capture.*
import com.jovi.photoai.camera.CaptureExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class P25UCaptureRepositoryAndroidTest {
    @Test fun persistedOriginal_reopensWithOwnerDigestAndFlow() = runBlocking {
        P25UCaptureFixture().use { f ->
            val row = f.ready()
            val bytes = f.files.final(row.id).readBytes()
            assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { it.recycle() })
            f.reopen()
            val restored = f.database.captureDao().read(row.id)!!.record()
            assertEquals(row, restored)
            assertEquals(row, f.database.captureDao().observeAll().first().single().record())
            assertArrayEquals(bytes, f.files.verify(restored).readBytes())
        }
    }

    @Test fun finalRenameBeforeLedgerFailure_recoversWithoutDuplicatingRow() {
        P25UCaptureFixture().use { f ->
            val delegate = RoomCaptureLedger(f.database)
            var failAvailableWrite = true
            val ledger = object : CaptureLedger by delegate {
                override fun update(record: CaptureRecord) {
                    if (record.fileState == CaptureFileState.AVAILABLE && failAvailableWrite) {
                        failAvailableWrite = false
                        throw IOException("synthetic ledger write failure")
                    }
                    delegate.update(record)
                }
            }
            val engine = CaptureEngine(ledger, f.files, "fault-owner")
            val row = engine.begin("snapshot-project", "snapshot-reference")
            P25UCaptureFixture.writeJpeg(f.files.partial(row.id))
            assertTrue(runCatching { engine.complete(row.id) }.isFailure)
            assertTrue(f.files.final(row.id).isFile)
            assertEquals(CaptureFileState.CAPTURING, delegate.read(row.id)!!.fileState)
            f.reopen()
            val restored = f.database.captureDao().read(row.id)!!.record()
            assertEquals(CaptureFileState.AVAILABLE, restored.fileState)
            assertEquals("snapshot-project", restored.projectId)
            assertEquals("snapshot-reference", restored.referenceId)
            assertEquals(1, f.database.captureDao().all().size)
        }
    }

    @Test fun partialWithoutCameraCompletion_isRetainedButNeverDeclaredAvailable() {
        P25UCaptureFixture().use { f ->
            val row = f.engine.begin(null, null)
            P25UCaptureFixture.writeJpeg(f.files.partial(row.id))
            f.reopen()
            assertEquals(CaptureFileState.INTERRUPTED, f.database.captureDao().read(row.id)!!.record().fileState)
            assertTrue(f.files.partial(row.id).isFile)
            assertFalse(f.files.final(row.id).exists())
        }
    }

    @Test fun exportCancelledOldCallbackAndStreamFailure_preserveTheCorrectOriginal() {
        P25UCaptureFixture().use { f ->
            val one = f.ready("project-one")
            val two = f.ready("project-two")
            val old = f.engine.reserveExport(one.id)
            assertTrue(f.engine.armExport(old.token))
            f.engine.cancelSelection(old.token)
            val current = f.engine.reserveExport(two.id)
            assertTrue(f.engine.armExport(current.token))
            var staleOpenerCalled = false
            f.engine.export(old.token, { staleOpenerCalled = true; ByteArrayOutputStream() })
            assertFalse(staleOpenerCalled)
            assertEquals(current.token, f.database.captureDao().pendingExport()!!.token)
            f.engine.export(current.token, { object : ByteArrayOutputStream() {
                override fun close() { throw IOException("synthetic close failure") }
            } })
            assertEquals(CaptureExportState.CANCELLED, f.database.captureDao().read(one.id)!!.record().exportState)
            assertEquals(CaptureExportState.UNKNOWN, f.database.captureDao().read(two.id)!!.record().exportState)
            assertTrue(f.files.final(one.id).isFile)
            assertTrue(f.files.final(two.id).isFile)
            assertNull(f.database.captureDao().pendingExport())
        }
    }

    @Test fun previousProcessExport_isUnknownAndNeverWritesReturnedDestination() {
        P25UCaptureFixture().use { f ->
            val row = f.ready()
            val ticket = f.engine.reserveExport(row.id)
            assertTrue(f.engine.armExport(ticket.token))
            f.reopen()
            assertEquals(CaptureExportState.UNKNOWN, f.database.captureDao().read(row.id)!!.record().exportState)
            assertEquals(ExportPhase.INTERRUPTED, f.database.captureDao().pendingExport()!!.ticket().phase)
            var opened = false
            f.engine.export(ticket.token, { opened = true; ByteArrayOutputStream() })
            assertFalse(opened)
            assertNull(f.database.captureDao().pendingExport())
            assertTrue(f.files.final(row.id).isFile)
        }
    }

    @Test fun deletingProjectDetachesWithoutDeletingOriginal_andNoLateResurrection() {
        P25UCaptureFixture().use { f ->
            val row = f.ready()
            f.engine.detachProject("test-project")
            assertNull(f.database.captureDao().read(row.id)!!.projectId)
            assertTrue(f.files.final(row.id).isFile)
            f.engine.delete(row.id)
            assertNull(f.database.captureDao().read(row.id))
            assertFalse(f.files.final(row.id).exists())
            assertTrue(runCatching { f.engine.complete(row.id) }.isFailure)
        }
    }

    @Test fun legacyExporterClosesDestinationEvenWhenSourceIsMissing() {
        P25UCaptureFixture().use { f ->
            var closed = false
            val out = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
            assertFalse(CaptureExporter.copy(File(f.root, "absent.jpg"), out))
            assertTrue(closed)
        }
    }
}
