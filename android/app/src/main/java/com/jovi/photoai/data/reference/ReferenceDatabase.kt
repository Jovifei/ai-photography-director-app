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
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import kotlinx.coroutines.flow.Flow

internal enum class ReferenceStorageState { ACTIVE, DELETE_PENDING }

@Entity(
    tableName = "reference_records",
    indices = [Index("storageState"), Index("createdAtEpochMillis")],
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
)

@Dao
internal interface ReferenceDao {
    @Query("SELECT * FROM reference_records WHERE storageState = 'ACTIVE' ORDER BY createdAtEpochMillis DESC")
    fun observeActive(): Flow<List<ReferenceEntity>>

    @Query("SELECT * FROM reference_records WHERE storageState = 'ACTIVE' ORDER BY createdAtEpochMillis DESC")
    suspend fun activeOnce(): List<ReferenceEntity>

    @Query("SELECT * FROM reference_records WHERE storageState = 'DELETE_PENDING'")
    suspend fun pendingDeletion(): List<ReferenceEntity>

    @Query("SELECT * FROM reference_records")
    suspend fun allOnce(): List<ReferenceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReferenceEntity)

    @Query("UPDATE reference_records SET storageState = 'DELETE_PENDING' WHERE id IN (:ids) AND storageState = 'ACTIVE'")
    suspend fun markDeletePending(ids: List<String>)

    @Query("DELETE FROM reference_records WHERE id IN (:ids) AND storageState = 'DELETE_PENDING'")
    suspend fun deletePending(ids: List<String>)
}

@Database(entities = [ReferenceEntity::class], version = 1, exportSchema = false)
internal abstract class ReferenceLibraryDatabase : RoomDatabase() {
    abstract fun referenceDao(): ReferenceDao

    companion object {
        fun create(context: Context): ReferenceLibraryDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReferenceLibraryDatabase::class.java,
            "reference-library.db",
        ).setJournalMode(JournalMode.TRUNCATE).build()
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
    )
}
