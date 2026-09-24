package com.jovi.photoai.p25u

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.data.capture.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Ordered, external force-stop harness for the production engine and real disk Room.
 * It is not proof of Activity saved-state or the complete system document picker workflow.
 * No method operates on the default database, pre-existing media, or an unowned directory.
 */
@RunWith(AndroidJUnit4::class)
class P25UDiskProcessRecoveryAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun root(phase: String): File {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("This method requires an explicit ordered runner phase", args.getString("p25uPhase") == phase)
        val run = requireNotNull(args.getString("p25uRun"))
        require(run.matches(Regex("^[a-f0-9]{32}$")))
        return File(context.noBackupFilesDir, "p25u-recovery-$run")
    }
    private fun open(root: File) = Room.databaseBuilder(context, CaptureLibraryDatabase::class.java,
        File(root, "ledger.db").absolutePath).allowMainThreadQueries().build()

    @Test fun prepare() {
        val root = root("prepare")
        check(!root.exists() && root.mkdirs())
        check(File(root, "test-owned.marker").createNewFile())
        val db = open(root)
        try {
            val files = CaptureFiles(File(root, "originals"), { CaptureRepository.decodableJpeg(it) })
            val engine = CaptureEngine(RoomCaptureLedger(db), files, "before-os-stop")
            val row = engine.begin("synthetic-project", "synthetic-reference", "1".repeat(32))
            P25UCaptureFixture.writeJpeg(files.partial(row.id))
            engine.complete(row.id)
            val ticket = engine.reserveExport(row.id)
            assertTrue(engine.armExport(ticket.token))
            assertEquals(CaptureFileState.AVAILABLE, db.captureDao().read(row.id)!!.record().fileState)
        } finally { db.close() }
    }

    @Test fun verifyAfterExternalForceStop() {
        val root = root("verify")
        check(File(root, "test-owned.marker").isFile)
        val db = open(root)
        try {
            val files = CaptureFiles(File(root, "originals"), { CaptureRepository.decodableJpeg(it) })
            val engine = CaptureEngine(RoomCaptureLedger(db), files, "after-os-stop")
            engine.recover()
            val row = db.captureDao().read("1".repeat(32))!!.record()
            assertEquals("synthetic-project", row.projectId)
            assertEquals("synthetic-reference", row.referenceId)
            assertEquals(CaptureFileState.AVAILABLE, row.fileState)
            assertEquals(CaptureExportState.UNKNOWN, row.exportState)
            assertTrue(files.verify(row).isFile)
            val old = requireNotNull(db.captureDao().pendingExport()).ticket()
            assertEquals(ExportPhase.INTERRUPTED, old.phase)
            var opened = false
            engine.export(old.token, { opened = true; ByteArrayOutputStream() })
            assertFalse(opened)
            assertNull(db.captureDao().pendingExport())
        } finally { db.close() }
    }

    @Test fun cleanup() {
        val root = root("cleanup")
        if (root.exists()) {
            check(File(root, "test-owned.marker").isFile)
            check(root.deleteRecursively())
        }
    }
}
