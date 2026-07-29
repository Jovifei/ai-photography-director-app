package com.jovi.photoai.data.reference

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.domain.model.ReferencePhoto
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal sealed interface ReferenceImportResult {
    data class Success(val record: ReferenceRecord) : ReferenceImportResult
    data class Failure(val code: ReferenceImportErrorCode) : ReferenceImportResult
}

/**
 * Owns the Room/filesystem transaction. A Picker Uri exists only inside importFromPicker while
 * the source grant is current; Room receives only a private JPEG basename and bundle data.
 */
internal class ReferenceRepository private constructor(
    private val database: ReferenceLibraryDatabase,
    private val importer: PrivateReferenceImporter,
    private val now: () -> Long,
) {
    private val dao = database.referenceDao()

    val activeRecords: Flow<List<ReferenceRecord>> = dao.observeActive().map { entities ->
        entities.map(ReferenceEntity::toRecord)
    }

    suspend fun importFromPicker(uri: Uri): ReferenceImportResult {
        val id = UUID.randomUUID().toString()
        return try {
            when (val imported = importer.importImmediately(uri, id)) {
                is PrivateReferenceImportResult.Failure -> {
                    importer.rollback(id)
                    ReferenceImportResult.Failure(imported.code)
                }

                is PrivateReferenceImportResult.Success -> {
                    val record = ReferenceRecord(
                        photo = ReferencePhoto(
                            id = id,
                            title = "导入参考图",
                            description = "本地私有派生图；示例指导不分析这张照片。",
                            sourceLabel = "示例指导 · 非图片分析",
                            imageAssetKey = "private/${imported.image.fileName}",
                            aspectRatio = imported.image.aspectRatio,
                        ),
                        bundle = DemoReferenceAnalyzer.analyze(id, "picker-selection"),
                        imageFileName = imported.image.fileName,
                        createdAtEpochMillis = now(),
                    )
                    try {
                        database.withTransaction { dao.insert(record.toEntity()) }
                        importer.finish(id)
                        ReferenceImportResult.Success(record)
                    } catch (_: Exception) {
                        importer.rollback(id)
                        ReferenceImportResult.Failure(ReferenceImportErrorCode.DATABASE_COMMIT_FAILED)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            importer.rollback(id)
            throw cancelled
        } catch (_: Exception) {
            importer.rollback(id)
            ReferenceImportResult.Failure(ReferenceImportErrorCode.PRIVATE_WRITE_FAILED)
        }
    }

    suspend fun delete(referenceId: String) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.markDeletePending(listOf(referenceId)) }
        finishPendingDeletes()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val activeIds = dao.activeOnce().map(ReferenceEntity::id)
        if (activeIds.isNotEmpty()) {
            database.withTransaction { dao.markDeletePending(activeIds) }
        }
        finishPendingDeletes()
    }

    suspend fun reconcile(): Int = withContext(Dispatchers.IO) {
        finishPendingDeletes()
        val invalidActiveIds = dao.activeOnce()
            .filterNot { importer.isValidPrivateJpeg(it.imageFileName) }
            .map(ReferenceEntity::id)
        if (invalidActiveIds.isNotEmpty()) {
            database.withTransaction { dao.markDeletePending(invalidActiveIds) }
            finishPendingDeletes()
        }
        importer.removeOrphans(dao.allOnce().map(ReferenceEntity::imageFileName).toSet())
        invalidActiveIds.size
    }

    private suspend fun finishPendingDeletes() {
        dao.pendingDeletion().forEach { entity ->
            if (importer.delete(entity.imageFileName)) {
                database.withTransaction { dao.deletePending(listOf(entity.id)) }
            }
        }
    }

    companion object {
        fun create(context: Context): ReferenceRepository = ReferenceRepository(
            database = ReferenceLibraryDatabase.create(context),
            importer = PrivateReferenceImporter(context),
            now = { System.currentTimeMillis() },
        )
    }
}
