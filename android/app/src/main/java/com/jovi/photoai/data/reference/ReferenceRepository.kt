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

internal data class PrivateAnalysisInput(
    val referenceId: String,
    val jpegBytes: ByteArray,
)

/** Count-only startup repair result. It contains no filename, path, source URI, or image metadata. */
internal data class ReferenceRecoverySummary(
    val removedInvalidRecords: Int = 0,
    val isolatedInvalidRecordsPendingRetry: Int = 0,
    val completedPendingDeletes: Int = 0,
    val removedTemporaryFiles: Int = 0,
    val removedOrphanFiles: Int = 0,
) {
    val recoveredItemCount: Int
        get() = removedInvalidRecords + completedPendingDeletes + removedTemporaryFiles + removedOrphanFiles

    val hasRecoveryNotice: Boolean
        get() = recoveredItemCount > 0 || isolatedInvalidRecordsPendingRetry > 0
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

    val projects: Flow<List<PhotographyProject>> = dao.observeProjects().map { entities ->
        entities.map(PhotographyProjectEntity::toProject)
    }

    fun activeRecordsForProject(projectId: String): Flow<List<ReferenceRecord>> =
        dao.observeActiveByProject(projectId).map { entities -> entities.map(ReferenceEntity::toRecord) }

    fun projectSummaryForProject(projectId: String): Flow<PersistedProjectSummary?> =
        dao.observeSummary(projectId).map { it?.toPersistedSummary() }

    suspend fun createProject(title: String = "新的拍摄项目"): PhotographyProject = withContext(Dispatchers.IO) {
        val timestamp = now()
        PhotographyProject(
            id = UUID.randomUUID().toString(),
            title = title.trim().take(MAX_PROJECT_TITLE_LENGTH).ifBlank { "新的拍摄项目" },
            primaryReferenceId = null,
            failedImportCount = 0,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
        ).also { project -> database.withTransaction { dao.insertProject(project.toEntity()) } }
    }

    suspend fun project(projectId: String): PhotographyProject? = withContext(Dispatchers.IO) {
        dao.projectById(projectId)?.toProject()
    }

    suspend fun importFromPicker(uri: Uri): ReferenceImportResult =
        importIntoProject(uri, ensureLegacyProject().id)

    suspend fun importIntoProject(uri: Uri, projectId: String): ReferenceImportResult = withContext(Dispatchers.IO) {
        if (dao.projectById(projectId) == null) return@withContext ReferenceImportResult.Failure(ReferenceImportErrorCode.PROJECT_NOT_FOUND)
        if (dao.activeCountByProject(projectId) >= MAX_PROJECT_PHOTOS) {
            return@withContext ReferenceImportResult.Failure(ReferenceImportErrorCode.PROJECT_LIMIT_REACHED)
        }
        val id = UUID.randomUUID().toString()
        try {
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
                        projectId = projectId,
                        analysisStatus = PhotoAnalysisStatus.IMPORTED,
                    )
                    try {
                        database.withTransaction {
                            if (dao.activeCountByProject(projectId) >= MAX_PROJECT_PHOTOS) {
                                throw ProjectCapacityReachedException()
                            }
                            val ordinal = dao.maxOrdinalByProject(projectId) + 1
                            dao.insert(record.copy(ordinal = ordinal).toEntity())
                            dao.deleteSummary(projectId)
                            dao.touchProject(projectId, now())
                        }
                        importer.finish(id)
                        val persisted = activeRecord(id) ?: error("PRIVATE_REFERENCE_NOT_VISIBLE")
                        ReferenceImportResult.Success(persisted)
                    } catch (_: ProjectCapacityReachedException) {
                        importer.rollback(id)
                        ReferenceImportResult.Failure(ReferenceImportErrorCode.PROJECT_LIMIT_REACHED)
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

    suspend fun recordFailedImport(projectId: String) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.recordFailedImport(projectId, now()) }
    }

    suspend fun delete(referenceId: String) = withContext(Dispatchers.IO) {
        val projectId = dao.activeById(referenceId)?.projectId
        database.withTransaction {
            dao.markDeletePending(listOf(referenceId))
            dao.clearPrimaryReferences(listOf(referenceId))
            projectId?.let { dao.deleteSummary(it) }
        }
        finishPendingDeletes()
    }

    /** Deletes exactly one project and its private derivatives; any failed file deletion stays retryable. */
    suspend fun deleteProject(projectId: String): Boolean = withContext(Dispatchers.IO) {
        if (dao.projectById(projectId) == null) return@withContext false
        val ids = dao.allByProjectOnce(projectId).map(ReferenceEntity::id)
        database.withTransaction {
            if (ids.isNotEmpty()) {
                dao.markDeletePending(ids)
                dao.clearPrimaryReferences(ids)
            }
            dao.deleteSummary(projectId)
        }
        finishPendingDeletes(ids.toSet())
        if (dao.recordCountByProject(projectId) != 0) return@withContext false
        database.withTransaction {
            dao.deleteSummary(projectId)
            dao.deleteProject(projectId)
        }
        true
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val activeIds = dao.activeOnce().map(ReferenceEntity::id)
        if (activeIds.isNotEmpty()) {
            database.withTransaction {
                dao.markDeletePending(activeIds)
                dao.clearPrimaryReferences(activeIds)
                activeIds.mapNotNull { id -> dao.activeById(id)?.projectId }.distinct().forEach { dao.deleteSummary(it) }
            }
        }
        finishPendingDeletes()
    }

    suspend fun setPrimaryReference(projectId: String, referenceId: String?): Boolean = withContext(Dispatchers.IO) {
        val projectExists = dao.projectById(projectId) != null
        val validReference = referenceId == null || dao.activeById(referenceId)?.projectId == projectId
        if (!projectExists || !validReference) return@withContext false
        database.withTransaction { dao.setPrimaryReference(projectId, referenceId, now()) }
        true
    }

    suspend fun activeRecord(id: String): ReferenceRecord? = withContext(Dispatchers.IO) {
        dao.activeById(id)
            ?.takeIf { importer.isValidPrivateJpeg(it.imageFileName) }
            ?.toRecord()
    }

    suspend fun privateAnalysisInput(referenceId: String): PrivateAnalysisInput? = withContext(Dispatchers.IO) {
        val entity = dao.activeById(referenceId) ?: return@withContext null
        importer.readPrivateJpeg(entity.imageFileName)?.let { bytes ->
            PrivateAnalysisInput(referenceId = entity.id, jpegBytes = bytes)
        }
    }

    suspend fun persistAnalysis(
        request: ReferenceAnalysisRequest,
        result: ProviderAnalysisResult,
    ): ProviderAnalysisResult? = withContext(Dispatchers.IO) {
        val current = dao.activeById(request.referenceId) ?: return@withContext null
        val validated = result.validatedFor(request)
        database.withTransaction { dao.update(current.withAnalysisResult(validated)) }
        validated
    }

    /** Applies an integrity-checked bundle atomically after revalidating every explicit mapping. */
    suspend fun applyKnowledgeBundle(
        projectId: String,
        bundle: PhotoKnowledgeBundle,
        bindings: List<KnowledgeBundleBinding>,
    ): KnowledgeBundleApplyResult = withContext(Dispatchers.IO) {
        if (
            bundle.contractVersion != PHOTO_KNOWLEDGE_BUNDLE_VERSION ||
            bundle.references.size !in 1..MAX_PROJECT_PHOTOS ||
            bundle.references.map(PhotoKnowledgeBundleItem::referenceId).distinct().size != bundle.references.size ||
            !bundle.payloadSha256.equals(canonicalPayloadSha256(bundle), ignoreCase = true)
        ) {
            return@withContext KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.BUNDLE_INTEGRITY_INVALID)
        }
        try {
            database.withTransaction {
                if (dao.projectById(projectId) == null) throw KnowledgeBundleApplyException(KnowledgeBundleApplyErrorCode.PROJECT_NOT_FOUND)
                val producerIds = bundle.references.map(PhotoKnowledgeBundleItem::referenceId)
                if (bindings.size != producerIds.size || bindings.map(KnowledgeBundleBinding::producerReferenceId).toSet() != producerIds.toSet()) {
                    throw KnowledgeBundleApplyException(KnowledgeBundleApplyErrorCode.BINDING_INCOMPLETE)
                }
                if (
                    bindings.map(KnowledgeBundleBinding::producerReferenceId).distinct().size != bindings.size ||
                    bindings.map(KnowledgeBundleBinding::localReferenceId).distinct().size != bindings.size
                ) {
                    throw KnowledgeBundleApplyException(KnowledgeBundleApplyErrorCode.BINDING_DUPLICATE)
                }
                val currentById = dao.activeByProjectOnce(projectId).associateBy(ReferenceEntity::id)
                val bindingByProducer = bindings.associateBy(KnowledgeBundleBinding::producerReferenceId)
                val importedAt = now()
                bundle.references.forEach { item ->
                    val localId = bindingByProducer[item.referenceId]?.localReferenceId
                        ?: throw KnowledgeBundleApplyException(KnowledgeBundleApplyErrorCode.BINDING_INCOMPLETE)
                    val current = currentById[localId]
                        ?: throw KnowledgeBundleApplyException(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND)
                    val status = runCatching { PhotoAnalysisStatus.valueOf(current.analysisStatus) }
                        .getOrDefault(PhotoAnalysisStatus.UNAVAILABLE)
                    if (!isKnowledgeBundleTargetEligible(status)) {
                        throw KnowledgeBundleApplyException(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE)
                    }
                    dao.update(current.withKnowledgeBundleResult(item, bundle, importedAt))
                }
                dao.deleteSummary(projectId)
                dao.touchProject(projectId, importedAt)
            }
            KnowledgeBundleApplyResult.Success(bindings.size)
        } catch (error: KnowledgeBundleApplyException) {
            KnowledgeBundleApplyResult.Failure(error.code)
        } catch (_: Exception) {
            KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.DATABASE_COMMIT_FAILED)
        }
    }

    suspend fun markAnalysisQueued(referenceId: String): Boolean = withContext(Dispatchers.IO) {
        val current = dao.activeById(referenceId) ?: return@withContext false
        database.withTransaction {
            dao.update(
                current.copy(
                    sourceLabel = "本机分析等待",
                    analysisStatus = PhotoAnalysisStatus.QUEUED.name,
                    safeAnalysisErrorCode = null,
                ),
            )
        }
        true
    }

    suspend fun markAnalysisRunning(referenceId: String): Boolean = withContext(Dispatchers.IO) {
        val current = dao.activeById(referenceId) ?: return@withContext false
        database.withTransaction {
            dao.update(current.copy(sourceLabel = "本机分析中", analysisStatus = PhotoAnalysisStatus.RUNNING.name, safeAnalysisErrorCode = null))
        }
        true
    }

    suspend fun cancelOutstandingAnalysis(projectId: String) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.cancelOutstandingAnalysis(projectId) }
    }

    suspend fun invalidateProjectSummary(projectId: String) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.deleteSummary(projectId) }
    }

    suspend fun persistProjectSummary(summary: PersistedProjectSummary) = withContext(Dispatchers.IO) {
        database.withTransaction { dao.upsertSummary(summary.toEntity()) }
    }

    suspend fun projectSummary(projectId: String): PersistedProjectSummary? = withContext(Dispatchers.IO) {
        dao.summaryByProject(projectId)?.toPersistedSummary()
    }

    suspend fun readyRecordsOnce(projectId: String): List<ReferenceRecord> = withContext(Dispatchers.IO) {
        dao.activeByProjectOnce(projectId).map(ReferenceEntity::toRecord)
            .filter {
                it.analysisStatus == PhotoAnalysisStatus.READY &&
                    (it.analysisProvenance != null || it.knowledgeBundleProvenance != null)
            }
    }

    suspend fun reconcile(): ReferenceRecoverySummary = withContext(Dispatchers.IO) {
        database.withTransaction { dao.cancelAllOutstandingAnalysis() }
        val completedPendingDeletes = finishPendingDeletes()
        val invalidActiveIds = dao.activeOnce()
            .filterNot { importer.isValidPrivateJpeg(it.imageFileName) }
            .map(ReferenceEntity::id)
        if (invalidActiveIds.isNotEmpty()) {
            database.withTransaction { dao.markDeletePending(invalidActiveIds) }
        }
        val removedInvalidRecords = finishPendingDeletes(invalidActiveIds.toSet())
        val orphanCleanup = importer.removeOrphans(dao.allOnce().map(ReferenceEntity::imageFileName).toSet())
        database.withTransaction { dao.clearMissingPrimaryReferences() }
        ReferenceRecoverySummary(
            removedInvalidRecords = removedInvalidRecords,
            isolatedInvalidRecordsPendingRetry = invalidActiveIds.size - removedInvalidRecords,
            completedPendingDeletes = completedPendingDeletes,
            removedTemporaryFiles = orphanCleanup.removedTemporaryFiles,
            removedOrphanFiles = orphanCleanup.removedOrphanFiles,
        )
    }

    private suspend fun finishPendingDeletes(onlyIds: Set<String>? = null): Int {
        var completedDeletes = 0
        dao.pendingDeletion()
            .filter { onlyIds == null || it.id in onlyIds }
            .forEach { entity ->
                if (importer.delete(entity.imageFileName)) {
                    database.withTransaction { dao.deletePending(listOf(entity.id)) }
                    completedDeletes += 1
                }
            }
        return completedDeletes
    }

    private suspend fun ensureLegacyProject(): PhotographyProject = withContext(Dispatchers.IO) {
        dao.projectById(LEGACY_PROJECT_ID)?.toProject() ?: PhotographyProject(
            id = LEGACY_PROJECT_ID,
            title = "已导入参考图",
            primaryReferenceId = null,
            failedImportCount = 0,
            createdAtEpochMillis = now(),
            updatedAtEpochMillis = now(),
        ).also { project -> database.withTransaction { dao.insertProject(project.toEntity()) } }
    }

    private class ProjectCapacityReachedException : RuntimeException()
    private class KnowledgeBundleApplyException(val code: KnowledgeBundleApplyErrorCode) : RuntimeException()

    companion object {
        private const val MAX_PROJECT_TITLE_LENGTH = 60

        fun create(context: Context): ReferenceRepository = ReferenceRepository(
            database = ReferenceLibraryDatabase.create(context),
            importer = PrivateReferenceImporter(context),
            now = { System.currentTimeMillis() },
        )
    }
}
