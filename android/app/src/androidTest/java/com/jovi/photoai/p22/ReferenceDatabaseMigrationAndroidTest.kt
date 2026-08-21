package com.jovi.photoai.p22

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.MIGRATION_4_5
import com.jovi.photoai.data.reference.ReferenceLibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceDatabaseMigrationAndroidTest {
    @Test
    fun migration4To5_preservesExistingReferenceAndAddsNullableBundleProvenance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p22-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(V4_REFERENCE_TABLE)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_records_storageState` ON `reference_records` (`storageState`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_records_createdAtEpochMillis` ON `reference_records` (`createdAtEpochMillis`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_reference_records_projectId` ON `reference_records` (`projectId`)")
            database.execSQL(V4_PROJECT_TABLE)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_photography_projects_updatedAtEpochMillis` ON `photography_projects` (`updatedAtEpochMillis`)")
            database.execSQL(V4_SUMMARY_TABLE)
            database.execSQL(
                "INSERT INTO reference_records (id,title,sourceLabel,imageFileName,aspectRatio,createdAtEpochMillis,storageState," +
                    "scene,backgroundStory,lighting,composition,subjectIntent,emotion,poseTemplate,cameraPosition,directorPrompt," +
                    "bundleVersion,projectId,ordinal,analysisStatus) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    "local_001", "照片", "已导入", "local_001.jpg", 1.0, 1L, "ACTIVE", "场景", "背景", "光线",
                    "构图", "主体", "情绪", "姿态", "机位", "提示", "1.0", "project_001", 0, "IMPORTED",
                ),
            )
            database.version = 4
        }

        val room = Room.databaseBuilder(context, ReferenceLibraryDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val migrated = runBlocking { room.referenceDao().allOnce().single() }

        assertEquals("local_001", migrated.id)
        assertEquals("IMPORTED", migrated.analysisStatus)
        assertNull(migrated.knowledgeBundleId)
        assertNull(migrated.knowledgeBundlePayloadSha256)
        room.close()
        context.deleteDatabase(name)
    }

    private companion object {
        const val V4_REFERENCE_TABLE =
            "CREATE TABLE IF NOT EXISTS `reference_records` (" +
                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `sourceLabel` TEXT NOT NULL, `imageFileName` TEXT NOT NULL, " +
                "`aspectRatio` REAL NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `storageState` TEXT NOT NULL, " +
                "`scene` TEXT NOT NULL, `backgroundStory` TEXT NOT NULL, `lighting` TEXT NOT NULL, `composition` TEXT NOT NULL, " +
                "`subjectIntent` TEXT NOT NULL, `emotion` TEXT NOT NULL, `poseTemplate` TEXT NOT NULL, `cameraPosition` TEXT NOT NULL, " +
                "`directorPrompt` TEXT NOT NULL, `bundleVersion` TEXT NOT NULL, `projectId` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, " +
                "`analysisStatus` TEXT NOT NULL, `safeAnalysisErrorCode` TEXT, `analysisProviderId` TEXT, `analysisProviderType` TEXT, " +
                "`analysisModelId` TEXT, `analysisModelRevision` TEXT, `analysisModelArtifactSha256` TEXT, `analysisRuntimeId` TEXT, " +
                "`analysisStartedAtEpochMillis` INTEGER, `analysisCompletedAtEpochMillis` INTEGER, `analysisLatencyMillis` INTEGER, " +
                "`analysisDirectObservationFields` TEXT, `analysisPhotographicInterpretationFields` TEXT, " +
                "`analysisCreativeRecommendationFields` TEXT, `analysisUncertaintyFlags` TEXT, `analysisWarnings` TEXT, PRIMARY KEY(`id`))"

        const val V4_PROJECT_TABLE =
            "CREATE TABLE IF NOT EXISTS `photography_projects` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `primaryReferenceId` TEXT, " +
                "`failedImportCount` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))"

        const val V4_SUMMARY_TABLE =
            "CREATE TABLE IF NOT EXISTS `project_summaries` (`projectId` TEXT NOT NULL, `status` TEXT NOT NULL, `readyCount` INTEGER NOT NULL, " +
                "`failedCount` INTEGER NOT NULL, `inputDigest` TEXT NOT NULL, `summaryVersion` TEXT, `modelId` TEXT, `modelRevision` TEXT, " +
                "`modelArtifactSha256` TEXT, `runtimeId` TEXT, `commonSceneDirection` TEXT, `commonLightingDirection` TEXT, " +
                "`commonCompositionDirection` TEXT, `commonSubjectDirection` TEXT, `differences` TEXT, `strongestReferences` TEXT, " +
                "`recommendedPrimaryReference` TEXT, `photographerActionSummary` TEXT, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`projectId`))"
    }
}
