package com.jovi.photoai.data.capture

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import com.jovi.photoai.data.reference.ReferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

internal data class CaptureReservation(val record: CaptureRecord, val output: File)

/**
 * Application singleton. Reads/writes/decodes never run on the UI thread.
 * Outputs are intentionally device-local in noBackupFilesDir (both JPEGs and Room ledger).
 * Existing reference backup/D2D policy is untouched; output transfer needs explicit Save Copy.
 */
internal class CaptureRepository private constructor(
    private val context: Context,
    private val database: CaptureLibraryDatabase,
    val engine: CaptureEngine,
) {
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val references by lazy { ReferenceRepository.create(context) }
    val records: Flow<List<CaptureRecord>> = database.captureDao().observeAll().map { rows -> rows.map { it.record() } }
    val pendingExport: Flow<CaptureExportTicket?> = database.captureDao().observeExport().map { it?.ticket() }

    suspend fun prepare() {
        withContext(Dispatchers.IO) { engine.recover() }
        val projectIds = references.projects.first().map { it.id }.toSet()
        withContext(Dispatchers.IO) { engine.detachMissingProjects(projectIds) }
    }

    suspend fun reserve(id: String, projectId: String?, referenceId: String?): CaptureReservation {
        val validProject = projectId?.takeIf { references.project(it) != null }
        val validReference = referenceId?.takeIf {
            references.activeRecord(it)?.projectId == validProject && validProject != null
        }
        return withContext(Dispatchers.IO) {
            val row = engine.begin(validProject, validReference, id)
            CaptureReservation(row, engine.files.partial(row.id))
        }
    }

    /** CameraX can finish after the screen/activity is gone. Persist by immutable id anyway. */
    fun settleCapture(id: String, saved: Boolean, onSettled: (CaptureRecord?) -> Unit) {
        persistenceScope.launch {
            val row = try {
                if (saved) engine.complete(id) else { engine.failed(id); null }
            } catch (_: Exception) {
                // A rename may have succeeded before the ledger acknowledgement failed.
                // Reconcile only this capture; never reset all in-flight captures here.
                try {
                    engine.failed(id)
                    database.captureDao().read(id)?.record()?.takeIf { it.fileState == CaptureFileState.AVAILABLE }
                } catch (_: Exception) { null }
            }
            withContext(Dispatchers.Main.immediate) { onSettled(row) }
        }
    }

    suspend fun complete(id: String) = withContext(Dispatchers.IO) { engine.complete(id) }
    suspend fun failed(id: String) = withContext(Dispatchers.IO) { engine.failed(id) }
    suspend fun detachProject(id: String) = withContext(Dispatchers.IO) { engine.detachProject(id) }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { engine.delete(id) }
    suspend fun reserveExport(id: String) = withContext(Dispatchers.IO) { engine.reserveExport(id) }
    suspend fun armExport(token: String) = withContext(Dispatchers.IO) { engine.armExport(token) }
    suspend fun cancelSelection(token: String) = withContext(Dispatchers.IO) { engine.cancelSelection(token) }
    suspend fun abandonExport(token: String) = withContext(Dispatchers.IO) { engine.abandonExport(token) }
    suspend fun previewFile(record: CaptureRecord): File = withContext(Dispatchers.IO) { engine.files.verify(record) }

    suspend fun export(token: String, destination: Uri) = withContext(Dispatchers.IO) {
        if (destination.scheme != "content") {
            engine.abandonExport(token)
            return@withContext
        }
        val coroutineContext = currentCoroutineContext()
        engine.export(token, openOutput = {
            context.contentResolver.openOutputStream(destination, "wt")
                ?: throw CaptureProblem("CAPTURE_DESTINATION_UNAVAILABLE")
        }, checkCancelled = { coroutineContext.ensureActive() })
    }

    companion object {
        @Volatile private var instance: CaptureRepository? = null
        fun get(context: Context): CaptureRepository = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
        private fun build(context: Context): CaptureRepository {
            val root = File(context.noBackupFilesDir, "capture-library")
            if (!root.isDirectory && !root.mkdirs()) throw CaptureProblem("CAPTURE_DIRECTORY_UNAVAILABLE")
            val database = Room.databaseBuilder(context, CaptureLibraryDatabase::class.java,
                File(root, "capture-library.db").absolutePath).build()
            val files = CaptureFiles(File(root, "originals"), ::decodableJpeg)
            return CaptureRepository(context, database, CaptureEngine(RoomCaptureLedger(database), files))
        }
        internal fun decodableJpeg(file: File): Boolean {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outMimeType != "image/jpeg" || options.outWidth !in 1..16_384 ||
                options.outHeight !in 1..16_384 || options.outWidth.toLong() * options.outHeight > 100_000_000L) return false
            var sample = 1
            while (options.outWidth / sample > 512 || options.outHeight / sample > 512) sample *= 2
            return try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }) ?: return false
                bitmap.recycle()
                true
            } catch (_: OutOfMemoryError) { false }
        }
    }
}
