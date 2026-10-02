package com.jovi.photoai.data.capture

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.Callable

/** Separate output ledger: the existing reference DB v6 and its READY/migrations stay unchanged. */
@Entity(tableName = "captured_photos")
internal data class CapturedPhotoEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val referenceId: String?,
    val createdAtMillis: Long,
    val fileState: String,
    val byteCount: Long,
    val fileSha256: String?,
    val exportState: String,
    val lastExportedAtMillis: Long?,
) {
    fun record() = CaptureRecord(id, projectId, referenceId, createdAtMillis,
        CaptureFileState.valueOf(fileState), byteCount, fileSha256,
        CaptureExportState.valueOf(exportState), lastExportedAtMillis)
}

internal fun CaptureRecord.entity() = CapturedPhotoEntity(id, projectId, referenceId, createdAtMillis,
    fileState.name, byteCount, fileSha256, exportState.name, lastExportedAtMillis)

@Entity(tableName = "capture_export_slot")
internal data class CaptureExportEntity(
    @PrimaryKey val slot: Int = 1,
    val token: String,
    val captureId: String,
    val processId: String,
    val phase: String,
) {
    fun ticket() = CaptureExportTicket(token, captureId, processId, ExportPhase.valueOf(phase))
}

@Dao
internal interface CaptureDao {
    @Query("SELECT * FROM captured_photos ORDER BY createdAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<CapturedPhotoEntity>>
    @Query("SELECT * FROM capture_export_slot WHERE slot = 1")
    fun observeExport(): Flow<CaptureExportEntity?>
    @Query("SELECT * FROM captured_photos ORDER BY createdAtMillis DESC, id DESC")
    fun all(): List<CapturedPhotoEntity>
    @Query("SELECT * FROM captured_photos WHERE id = :id")
    fun read(id: String): CapturedPhotoEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(row: CapturedPhotoEntity)
    @Update
    fun update(row: CapturedPhotoEntity): Int
    @Query("DELETE FROM captured_photos WHERE id = :id")
    fun remove(id: String)
    @Query("SELECT * FROM capture_export_slot WHERE slot = 1")
    fun pendingExport(): CaptureExportEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setExport(row: CaptureExportEntity)
    @Query("DELETE FROM capture_export_slot WHERE slot = 1")
    fun clearExport()
}

@Database(entities = [CapturedPhotoEntity::class, CaptureExportEntity::class], version = 1, exportSchema = false)
internal abstract class CaptureLibraryDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
}

internal class RoomCaptureLedger(private val db: CaptureLibraryDatabase) : CaptureLedger {
    private val dao = db.captureDao()
    override fun all() = dao.all().map { it.record() }
    override fun read(id: String) = dao.read(id)?.record()
    override fun insert(record: CaptureRecord) = dao.insert(record.entity())
    override fun update(record: CaptureRecord) {
        if (dao.update(record.entity()) != 1) throw CaptureProblem("CAPTURE_ROW_CHANGED")
    }
    override fun remove(id: String) = dao.remove(id)
    override fun pendingExport() = dao.pendingExport()?.ticket()
    override fun setPendingExport(ticket: CaptureExportTicket) = dao.setExport(
        CaptureExportEntity(token = ticket.token, captureId = ticket.captureId,
            processId = ticket.processId, phase = ticket.phase.name))
    override fun clearPendingExport() = dao.clearExport()
    override fun <T> transaction(block: () -> T): T = db.runInTransaction(Callable { block() })
}
