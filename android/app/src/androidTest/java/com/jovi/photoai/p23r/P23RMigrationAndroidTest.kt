package com.jovi.photoai.p23r

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real Room schema validation plus synthetic v5 data retention; no user database. */
@RunWith(AndroidJUnit4::class)
class P23RMigrationAndroidTest {
    private data class Table(val name: String, val sql: String, val columns: List<String>, val rows: List<Array<Any?>>)

    @Test fun version5To6_preservesBundle_andClearsLegacyWorkOnRecoveryQuery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p23r-migration-${UUID.randomUUID()}.db"
        val tables = P23RRoomFixture().use { source ->
            source.seed(2)
            source.repository.applyKnowledgeBundle(source.project.id, source.bundle(1), source.bindings(1))
            source.repository.markAnalysisQueued(AnalysisAttempt(source.records[1].photo.id, "legacy_work"))
            val db = source.database.openHelper.writableDatabase
            val ddl = db.query("SELECT name, sql FROM sqlite_master WHERE type='table' AND name IN ('reference_records','photography_projects','project_summaries')").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
            }
            ddl.map { (tableName, sql) ->
                db.query("SELECT * FROM `$tableName`").use { cursor ->
                    val columns = cursor.columnNames.filter { it != "analysisAttemptId" }
                    val indexes = columns.map(cursor::getColumnIndexOrThrow)
                    val rows = buildList<Array<Any?>> {
                        while (cursor.moveToNext()) add(indexes.map { index ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> null
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                                else -> error("Unexpected synthetic column type")
                            }
                        }.toTypedArray())
                    }
                    Table(tableName, sql.replace(Regex(",\\s*`analysisAttemptId` TEXT"), ""), columns, rows)
                }
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (table in tables) {
                            db.execSQL(table.sql)
                            val columns = table.columns.joinToString(",") { "`$it`" }
                            val placeholders = table.columns.joinToString(",") { "?" }
                            for (row in table.rows) db.execSQL("INSERT INTO `${table.name}` ($columns) VALUES ($placeholders)", row)
                        }
                        db.execSQL("CREATE INDEX index_reference_records_storageState ON reference_records(storageState)")
                        db.execSQL("CREATE INDEX index_reference_records_createdAtEpochMillis ON reference_records(createdAtEpochMillis)")
                        db.execSQL("CREATE INDEX index_reference_records_projectId ON reference_records(projectId)")
                        db.execSQL("CREATE INDEX index_photography_projects_updatedAtEpochMillis ON photography_projects(updatedAtEpochMillis)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        try {
            helper.writableDatabase.query("PRAGMA table_info(reference_records)").use { cursor ->
                while (cursor.moveToNext()) assertNotEquals("analysisAttemptId", cursor.getString(1))
            }
            helper.close()
            val migrated = Room.databaseBuilder(context, ReferenceLibraryDatabase::class.java, name)
                .addMigrations(MIGRATION_5_6).build()
            try {
                val dao = migrated.referenceDao()
                val before = dao.allOnce().associateBy { it.id }
                assertEquals(2, before.size) // Room validates the complete schema before queries.
                assertEquals("READY", before.getValue("synthetic_0").analysisStatus)
                assertEquals("synthetic_bundle", before.getValue("synthetic_0").knowledgeBundleId)
                assertEquals("QUEUED", before.getValue("synthetic_1").analysisStatus)
                assertTrue(before.values.all { it.analysisAttemptId == null })
                // Only test the production startup cancellation query here: metadata fixtures
                // intentionally contain no JPEG, so full reconcile would remove them.
                dao.cancelAllOutstandingAnalysis()
                assertEquals(before.getValue("synthetic_0"), dao.activeById("synthetic_0"))
                assertEquals("CANCELLED", dao.activeById("synthetic_1")!!.analysisStatus)
            } finally { migrated.close() }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
