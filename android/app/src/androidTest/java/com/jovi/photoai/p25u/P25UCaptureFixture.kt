package com.jovi.photoai.p25u

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jovi.photoai.data.capture.*
import java.io.File
import java.util.UUID

/** Test-owned disk Room and generated JPEGs, never the production capture singleton or library. */
internal class P25UCaptureFixture : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val root = File(context.noBackupFilesDir, "p25u-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
    var database: CaptureLibraryDatabase = openDatabase()
        private set
    val files = CaptureFiles(File(root, "originals"), { CaptureRepository.decodableJpeg(it) })
    var engine = CaptureEngine(RoomCaptureLedger(database), files, processId = "fixture-process-one")
        private set

    private fun openDatabase() = Room.databaseBuilder(context, CaptureLibraryDatabase::class.java,
        File(root, "ledger.db").absolutePath).allowMainThreadQueries().build()

    fun reopen() {
        database.close()
        database = openDatabase()
        engine = CaptureEngine(RoomCaptureLedger(database), files, processId = "fixture-process-two")
        engine.recover()
    }

    fun ready(projectId: String? = "test-project", referenceId: String? = "test-reference"): CaptureRecord {
        val row = engine.begin(projectId, referenceId)
        writeJpeg(files.partial(row.id))
        return engine.complete(row.id)
    }

    override fun close() {
        database.close()
        check(root.deleteRecursively())
    }

    companion object {
        fun writeJpeg(file: File, width: Int = 80, height: Int = 48) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.rgb(43, 107, 159))
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            } finally { bitmap.recycle() }
        }
    }
}
