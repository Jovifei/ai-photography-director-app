package com.jovi.photoai.t4

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.LEGACY_PROJECT_ID
import com.jovi.photoai.data.reference.PhotographyProjectEntity
import com.jovi.photoai.data.reference.ReferenceLibraryDatabase
import com.jovi.photoai.data.reference.ReferenceRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class T4ProjectRenameAndroidTest {
    @Test
    fun renamePersistsAcrossDatabaseReopenAndRejectsInvalidTargets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "t4-project-rename-${UUID.randomUUID()}.db"
        fun openDatabase() = Room.databaseBuilder(
            context,
            ReferenceLibraryDatabase::class.java,
            databaseName,
        ).build()

        var database = openDatabase()
        try {
            val repository = ReferenceRepository.createForTest(context, database)
            val project = repository.createProject("旧名称")
            database.referenceDao().insertProject(
                PhotographyProjectEntity(LEGACY_PROJECT_ID, "历史参考图库", null, 0, 1L, 1L),
            )

            assertFalse(repository.renameProject(project.id, "   "))
            assertFalse(repository.renameProject(" ", "新名称"))
            assertFalse(repository.renameProject("missing-project", "新名称"))
            assertFalse(repository.renameProject(LEGACY_PROJECT_ID, "不能修改"))
            assertEquals("旧名称", repository.project(project.id)?.title)
            assertEquals("历史参考图库", repository.project(LEGACY_PROJECT_ID)?.title)

            assertTrue(repository.renameProject(project.id, "  ${"新".repeat(70)}  "))
            assertEquals("新".repeat(60), repository.projects.first().first { it.id == project.id }.title)

            database.close()
            database = openDatabase()
            val reopened = ReferenceRepository.createForTest(context, database)
            assertEquals("新".repeat(60), reopened.project(project.id)?.title)
            assertEquals("历史参考图库", reopened.project(LEGACY_PROJECT_ID)?.title)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
