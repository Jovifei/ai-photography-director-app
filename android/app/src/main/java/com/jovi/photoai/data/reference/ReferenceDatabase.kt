package com.jovi.photoai.data.reference

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import kotlinx.coroutines.flow.Flow

internal enum class ReferenceStorageState { ACTIVE, DELETE_PENDING }

@Entity(
    tableName = "reference_records",
    indices = [Index("storageState"), Index("createdAtEpochMillis"), Index("projectId")],
)
internal data class ReferenceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceLabel: String,
    val imageFileName: String,
    val aspectRatio: Float,
    val createdAtEpochMillis: Long,
    val storageState: String,
    val scene: String,
    val backgroundStory: String,
    val lighting: String,
    val composition: String,
    val subjectIntent: String,
    val emotion: String,
    val poseTemplate: String,
    val cameraPosition: String,
    val directorPrompt: String,
    val bundleVersion: String,
    val projectId: String,
    val ordinal: Int,
    val analysisStatus: String,
    val safeAnalysisErrorCode: String?,
    val analysisProviderId: String?,
    val analysisProviderType: String?,
    val analysisModelId: String?,
    val analysisModelRevision: String?,
    val analysisModelArtifactSha256: String?,
    val analysisRuntimeId: String?,
    val analysisStartedAtEpochMillis: Long?,
    val analysisCompletedAtEpochMillis: Long?,
    val analysisLatencyMillis: Long?,
    val analysisDirectObservationFields: String?,
    val analysisPhotographicInterpretationFields: String?,
    val analysisCreativeRecommendationFields: String?,
    val analysisUncertaintyFlags: String?,
    val analysisWarnings: String?,
)

@Entity(
    tableName = "photography_projects",
    indices = [Index("updatedAtEpochMillis")],
)
internal data class PhotographyProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val primaryReferenceId: String?,
    val failedImportCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "project_summaries")
internal data class ProjectSummaryEntity(
    @PrimaryKey val projectId: String,
    val status: String,
    val readyCount: Int,
    val failedCount: Int,
    val inputDigest: String,
    val summaryVersion: String?,
    val modelId: String?,
    val modelRevision: String?,
    val modelArtifactSha256: String?,
    val runtimeId: String?,
    val commonSceneDirection: String?,
    val commonLightingDirection: String?,
    val commonCompositionDirection: String?,
    val commonSubjectDirection: String?,
    val differences: String?,
    val strongestReferences: String?,
    val recommendedPrimaryReference: String?,
    val photographerActionSummary: String?,
    val updatedAtEpochMillis: Long,
)

@Dao
internal interface ReferenceDao {
    @Query("SELECT * FROM reference_records WHERE storageState = 'ACTIVE' ORDER BY createdAtEpochMillis DESC")
    fun observeActive(): Flow<List<ReferenceEntity>>

    @Query("SELECT * FROM reference_records WHERE storageState = 'ACTIVE' ORDER BY createdAtEpochMillis DESC")
    suspend fun activeOnce(): List<ReferenceEntity>

    @Query("SELECT * FROM reference_records WHERE projectId = :projectId AND storageState = 'ACTIVE' ORDER BY ordinal ASC, createdAtEpochMillis ASC")
    fun observeActiveByProject(projectId: String): Flow<List<ReferenceEntity>>

    @Query("SELECT * FROM reference_records WHERE projectId = :projectId AND storageState = 'ACTIVE' ORDER BY ordinal ASC, createdAtEpochMillis ASC")
    suspend fun activeByProjectOnce(projectId: String): List<ReferenceEntity>

    @Query("SELECT COUNT(*) FROM reference_records WHERE projectId = :projectId AND storageState = 'ACTIVE'")
    suspend fun activeCountByProject(projectId: String): Int

    @Query("SELECT COALESCE(MAX(ordinal), -1) FROM reference_records WHERE projectId = :projectId")
    suspend fun maxOrdinalByProject(projectId: String): Int

    @Query("SELECT * FROM reference_records WHERE id = :id AND storageState = 'ACTIVE' LIMIT 1")
    suspend fun activeById(id: String): ReferenceEntity?

    @Query("SELECT * FROM reference_records WHERE storageState = 'DELETE_PENDING'")
    suspend fun pendingDeletion(): List<ReferenceEntity>

    @Query("SELECT * FROM reference_records")
    suspend fun allOnce(): List<ReferenceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReferenceEntity)

    @androidx.room.Update
    suspend fun update(entity: ReferenceEntity)

    @Query("SELECT * FROM photography_projects ORDER BY updatedAtEpochMillis DESC")
    fun observeProjects(): Flow<List<PhotographyProjectEntity>>

    @Query("SELECT * FROM photography_projects WHERE id = :id LIMIT 1")
    suspend fun projectById(id: String): PhotographyProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(entity: PhotographyProjectEntity)

    @Query("UPDATE photography_projects SET updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :projectId")
    suspend fun touchProject(projectId: String, updatedAtEpochMillis: Long)

    @Query("UPDATE photography_projects SET primaryReferenceId = :referenceId, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :projectId")
    suspend fun setPrimaryReference(projectId: String, referenceId: String?, updatedAtEpochMillis: Long)

    @Query("UPDATE photography_projects SET failedImportCount = failedImportCount + 1, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :projectId")
    suspend fun recordFailedImport(projectId: String, updatedAtEpochMillis: Long)

    @Query("UPDATE photography_projects SET primaryReferenceId = NULL WHERE primaryReferenceId IN (:referenceIds)")
    suspend fun clearPrimaryReferences(referenceIds: List<String>)

    @Query("UPDATE photography_projects SET primaryReferenceId = NULL WHERE primaryReferenceId IS NOT NULL AND primaryReferenceId NOT IN (SELECT id FROM reference_records WHERE storageState = 'ACTIVE')")
    suspend fun clearMissingPrimaryReferences()

    @Query("UPDATE reference_records SET storageState = 'DELETE_PENDING' WHERE id IN (:ids) AND storageState = 'ACTIVE'")
    suspend fun markDeletePending(ids: List<String>)

    @Query("DELETE FROM reference_records WHERE id IN (:ids) AND storageState = 'DELETE_PENDING'")
    suspend fun deletePending(ids: List<String>)

    @Query("SELECT * FROM project_summaries WHERE projectId = :projectId LIMIT 1")
    suspend fun summaryByProject(projectId: String): ProjectSummaryEntity?

    @Query("SELECT * FROM project_summaries WHERE projectId = :projectId LIMIT 1")
    fun observeSummary(projectId: String): Flow<ProjectSummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(summary: ProjectSummaryEntity)

    @Query("DELETE FROM project_summaries WHERE projectId = :projectId")
    suspend fun deleteSummary(projectId: String)

    @Query("UPDATE reference_records SET analysisStatus = 'CANCELLED' WHERE projectId = :projectId AND storageState = 'ACTIVE' AND analysisStatus IN ('QUEUED', 'RUNNING', 'ANALYZING')")
    suspend fun cancelOutstandingAnalysis(projectId: String)

    @Query("UPDATE reference_records SET analysisStatus = 'RUNNING' WHERE analysisStatus = 'ANALYZING'")
    suspend fun migrateAnalyzingToRunning()

    @Query("UPDATE reference_records SET analysisStatus = 'CANCELLED' WHERE storageState = 'ACTIVE' AND analysisStatus IN ('QUEUED', 'RUNNING', 'ANALYZING')")
    suspend fun cancelAllOutstandingAnalysis()
}

@Database(entities = [ReferenceEntity::class, PhotographyProjectEntity::class, ProjectSummaryEntity::class], version = 4, exportSchema = false)
internal abstract class ReferenceLibraryDatabase : RoomDatabase() {
    abstract fun referenceDao(): ReferenceDao

    companion object {
        fun create(context: Context): ReferenceLibraryDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReferenceLibraryDatabase::class.java,
            "reference-library.db",
        ).setJournalMode(JournalMode.TRUNCATE)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `photography_projects` (" +
                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `primaryReferenceId` TEXT, `failedImportCount` INTEGER NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_photography_projects_updatedAtEpochMillis` ON `photography_projects` (`updatedAtEpochMillis`)")
        database.execSQL("ALTER TABLE `reference_records` ADD COLUMN `projectId` TEXT NOT NULL DEFAULT '$LEGACY_PROJECT_ID'")
        database.execSQL("ALTER TABLE `reference_records` ADD COLUMN `ordinal` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `reference_records` ADD COLUMN `analysisStatus` TEXT NOT NULL DEFAULT 'EXAMPLE_GUIDANCE'")
        database.execSQL("ALTER TABLE `reference_records` ADD COLUMN `safeAnalysisErrorCode` TEXT")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_records_projectId` ON `reference_records` (`projectId`)")
        database.execSQL(
            "INSERT OR IGNORE INTO `photography_projects` " +
                "(`id`, `title`, `primaryReferenceId`, `failedImportCount`, `createdAtEpochMillis`, `updatedAtEpochMillis`) " +
                "SELECT '$LEGACY_PROJECT_ID', '已迁移参考图库', NULL, 0, " +
                "COALESCE(MIN(`createdAtEpochMillis`), 0), COALESCE(MAX(`createdAtEpochMillis`), 0) " +
                "FROM `reference_records`",
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val additions = listOf(
            "analysisProviderId TEXT",
            "analysisProviderType TEXT",
            "analysisModelId TEXT",
            "analysisModelRevision TEXT",
            "analysisModelArtifactSha256 TEXT",
            "analysisRuntimeId TEXT",
            "analysisStartedAtEpochMillis INTEGER",
            "analysisCompletedAtEpochMillis INTEGER",
            "analysisLatencyMillis INTEGER",
            "analysisDirectObservationFields TEXT",
            "analysisPhotographicInterpretationFields TEXT",
            "analysisCreativeRecommendationFields TEXT",
            "analysisUncertaintyFlags TEXT",
            "analysisWarnings TEXT",
        )
        additions.forEach { database.execSQL("ALTER TABLE `reference_records` ADD COLUMN $it") }
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_summaries` (" +
                "`projectId` TEXT NOT NULL, `status` TEXT NOT NULL, `readyCount` INTEGER NOT NULL, `failedCount` INTEGER NOT NULL, " +
                "`inputDigest` TEXT NOT NULL, `summaryVersion` TEXT, `modelId` TEXT, `modelRevision` TEXT, " +
                "`modelArtifactSha256` TEXT, `runtimeId` TEXT, `commonSceneDirection` TEXT, `commonLightingDirection` TEXT, " +
                "`commonCompositionDirection` TEXT, `commonSubjectDirection` TEXT, `differences` TEXT, `strongestReferences` TEXT, " +
                "`recommendedPrimaryReference` TEXT, `photographerActionSummary` TEXT, `updatedAtEpochMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`projectId`))",
        )
        database.execSQL("UPDATE `reference_records` SET `analysisStatus` = 'RUNNING' WHERE `analysisStatus` = 'ANALYZING'")
    }
}

internal fun ReferenceEntity.toRecord(): ReferenceRecord = ReferenceRecord(
    photo = ReferencePhoto(
        id = id,
        title = title,
        description = "本地私有派生图；示例指导不分析这张照片。",
        sourceLabel = sourceLabel,
        imageAssetKey = "private/$imageFileName",
        aspectRatio = aspectRatio,
    ),
    bundle = ReferenceBundle(
        referenceId = id,
        scene = scene,
        backgroundStory = backgroundStory,
        lighting = lighting,
        composition = composition,
        subjectIntent = subjectIntent,
        emotion = emotion,
        poseTemplate = poseTemplate,
        cameraPosition = cameraPosition,
        directorPrompt = directorPrompt,
        version = bundleVersion,
    ),
    imageFileName = imageFileName,
    createdAtEpochMillis = createdAtEpochMillis,
    projectId = projectId,
    ordinal = ordinal,
    analysisStatus = runCatching { PhotoAnalysisStatus.valueOf(analysisStatus) }
        .getOrDefault(PhotoAnalysisStatus.UNAVAILABLE),
    safeAnalysisErrorCode = safeAnalysisErrorCode,
    analysisProvenance = toAnalysisProvenance(),
).also { it.requireSafeImageFileName() }

internal fun ReferenceRecord.toEntity(storageState: ReferenceStorageState = ReferenceStorageState.ACTIVE): ReferenceEntity {
    requireSafeImageFileName()
    return ReferenceEntity(
        id = photo.id,
        title = photo.title,
        sourceLabel = photo.sourceLabel,
        imageFileName = imageFileName,
        aspectRatio = photo.aspectRatio,
        createdAtEpochMillis = createdAtEpochMillis,
        storageState = storageState.name,
        scene = bundle.scene,
        backgroundStory = bundle.backgroundStory,
        lighting = bundle.lighting,
        composition = bundle.composition,
        subjectIntent = bundle.subjectIntent,
        emotion = bundle.emotion,
        poseTemplate = bundle.poseTemplate,
        cameraPosition = bundle.cameraPosition,
        directorPrompt = bundle.directorPrompt,
        bundleVersion = bundle.version,
        projectId = projectId,
        ordinal = ordinal,
        analysisStatus = analysisStatus.name,
        safeAnalysisErrorCode = safeAnalysisErrorCode,
        analysisProviderId = analysisProvenance?.providerId,
        analysisProviderType = analysisProvenance?.providerType?.name,
        analysisModelId = analysisProvenance?.modelId,
        analysisModelRevision = analysisProvenance?.modelRevision,
        analysisModelArtifactSha256 = analysisProvenance?.modelArtifactSha256,
        analysisRuntimeId = analysisProvenance?.runtimeId,
        analysisStartedAtEpochMillis = analysisProvenance?.startedAtEpochMillis,
        analysisCompletedAtEpochMillis = analysisProvenance?.completedAtEpochMillis,
        analysisLatencyMillis = analysisProvenance?.latencyMillis,
        analysisDirectObservationFields = analysisProvenance?.confidenceSummary?.directObservationFields?.joinToString(","),
        analysisPhotographicInterpretationFields = analysisProvenance?.confidenceSummary?.photographicInterpretationFields?.joinToString(","),
        analysisCreativeRecommendationFields = analysisProvenance?.confidenceSummary?.creativeRecommendationFields?.joinToString(","),
        analysisUncertaintyFlags = analysisProvenance?.uncertaintyFlags?.entries?.joinToString(";") {
            "${it.key}|${it.value.level.name}|${it.value.basis.name}"
        },
        analysisWarnings = analysisProvenance?.warnings?.joinToString("\n"),
    )
}

private fun ReferenceEntity.toAnalysisProvenance(): ProviderAnalysisProvenance? {
    val providerId = analysisProviderId ?: return null
    val providerType = analysisProviderType ?: return null
    val modelId = analysisModelId ?: return null
    val modelRevision = analysisModelRevision ?: return null
    val artifactSha = analysisModelArtifactSha256 ?: return null
    val runtimeId = analysisRuntimeId ?: return null
    val startedAt = analysisStartedAtEpochMillis ?: return null
    val completedAt = analysisCompletedAtEpochMillis ?: return null
    val uncertainty = analysisUncertaintyFlags.orEmpty().split(';').mapNotNull { value ->
        val parts = value.split('|')
        if (parts.size != 3) return@mapNotNull null
        val level = runCatching { UncertaintyLevel.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
        val basis = runCatching { UncertaintyBasis.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
        parts[0] to AnalysisUncertainty(level, basis)
    }.toMap()
    return runCatching {
        ProviderAnalysisProvenance(
            providerId = providerId,
            providerType = ProviderType.valueOf(providerType),
            modelId = modelId,
            modelRevision = modelRevision,
            modelArtifactSha256 = artifactSha,
            runtimeId = runtimeId,
            completedAtEpochMillis = completedAt,
            startedAtEpochMillis = startedAt,
            latencyMillis = analysisLatencyMillis ?: (completedAt - startedAt).coerceAtLeast(0),
            confidenceSummary = AnalysisConfidenceSummary(
                directObservationFields = analysisDirectObservationFields.csvFields(),
                photographicInterpretationFields = analysisPhotographicInterpretationFields.csvFields(),
                creativeRecommendationFields = analysisCreativeRecommendationFields.csvFields(),
            ),
            uncertaintyFlags = uncertainty,
            warnings = analysisWarnings.orEmpty().split('\n').filter(String::isNotBlank).take(10),
        )
    }.getOrNull()
}

private fun String?.csvFields(): List<String> = this.orEmpty().split(',').filter(String::isNotBlank)

internal fun ReferenceEntity.withAnalysisResult(result: ProviderAnalysisResult): ReferenceEntity = when (result) {
    is ProviderAnalysisResult.Ready -> copy(
        sourceLabel = "本机 VLM",
        scene = result.bundle.scene,
        backgroundStory = result.bundle.backgroundStory,
        lighting = result.bundle.lighting,
        composition = result.bundle.composition,
        subjectIntent = result.bundle.subjectIntent,
        emotion = result.bundle.emotion,
        poseTemplate = result.bundle.poseTemplate,
        cameraPosition = result.bundle.cameraPosition,
        directorPrompt = result.bundle.directorPrompt,
        bundleVersion = result.bundle.version,
        analysisStatus = PhotoAnalysisStatus.READY.name,
        safeAnalysisErrorCode = null,
        analysisProviderId = result.provenance.providerId,
        analysisProviderType = result.provenance.providerType.name,
        analysisModelId = result.provenance.modelId,
        analysisModelRevision = result.provenance.modelRevision,
        analysisModelArtifactSha256 = result.provenance.modelArtifactSha256,
        analysisRuntimeId = result.provenance.runtimeId,
        analysisStartedAtEpochMillis = result.provenance.startedAtEpochMillis,
        analysisCompletedAtEpochMillis = result.provenance.completedAtEpochMillis,
        analysisLatencyMillis = result.provenance.latencyMillis,
        analysisDirectObservationFields = result.provenance.confidenceSummary.directObservationFields.joinToString(","),
        analysisPhotographicInterpretationFields = result.provenance.confidenceSummary.photographicInterpretationFields.joinToString(","),
        analysisCreativeRecommendationFields = result.provenance.confidenceSummary.creativeRecommendationFields.joinToString(","),
        analysisUncertaintyFlags = result.provenance.uncertaintyFlags.entries.joinToString(";") {
            "${it.key}|${it.value.level.name}|${it.value.basis.name}"
        },
        analysisWarnings = result.provenance.warnings.joinToString("\n"),
    )
    is ProviderAnalysisResult.Failed -> copy(
        sourceLabel = "分析不可用",
        analysisStatus = PhotoAnalysisStatus.FAILED.name,
        safeAnalysisErrorCode = result.errorCode.name,
        analysisProviderId = null,
        analysisProviderType = null,
        analysisModelId = null,
        analysisModelRevision = null,
        analysisRuntimeId = null,
        analysisStartedAtEpochMillis = null,
        analysisCompletedAtEpochMillis = null,
        analysisLatencyMillis = null,
        analysisDirectObservationFields = null,
        analysisPhotographicInterpretationFields = null,
        analysisCreativeRecommendationFields = null,
        analysisUncertaintyFlags = null,
        analysisWarnings = null,
    )
    is ProviderAnalysisResult.Unavailable -> copy(
        sourceLabel = "分析不可用",
        analysisStatus = PhotoAnalysisStatus.UNAVAILABLE.name,
        safeAnalysisErrorCode = result.errorCode.name,
        analysisProviderId = null,
        analysisProviderType = null,
        analysisModelId = null,
        analysisModelRevision = null,
        analysisModelArtifactSha256 = null,
        analysisRuntimeId = null,
        analysisStartedAtEpochMillis = null,
        analysisCompletedAtEpochMillis = null,
        analysisLatencyMillis = null,
        analysisDirectObservationFields = null,
        analysisPhotographicInterpretationFields = null,
        analysisCreativeRecommendationFields = null,
        analysisUncertaintyFlags = null,
        analysisWarnings = null,
    )
}

internal fun PhotographyProjectEntity.toProject(): PhotographyProject = PhotographyProject(
    id = id,
    title = title,
    primaryReferenceId = primaryReferenceId,
    failedImportCount = failedImportCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun PhotographyProject.toEntity(): PhotographyProjectEntity = PhotographyProjectEntity(
    id = id,
    title = title,
    primaryReferenceId = primaryReferenceId,
    failedImportCount = failedImportCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
