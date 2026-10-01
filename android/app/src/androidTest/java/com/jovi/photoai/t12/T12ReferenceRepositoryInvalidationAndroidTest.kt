package com.jovi.photoai.t12

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Existing collectors must observe writes from another default repository in this process. */
@RunWith(AndroidJUnit4::class)
class T12ReferenceRepositoryInvalidationAndroidTest {
    @Test fun existingProjectsCollectorObservesOtherRepositoryWrite() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val writer = ReferenceRepository.create(app)
        val reader = ReferenceRepository.create(app)
        assertNotSame(writer, reader)
        val emissions = Channel<List<com.jovi.photoai.data.reference.PhotographyProject>>(Channel.UNLIMITED)
        var projectId: String? = null
        val collector = launch(Dispatchers.Default) { reader.projects.collect { emissions.send(it) } }
        try {
            withTimeout(10_000) { emissions.receive() } // Collector is active before the write.
            val project = writer.createProject("T12 invalidation ${UUID.randomUUID()}")
            projectId = project.id
            val observed = withTimeout(10_000) {
                var snapshot = emissions.receive()
                while (snapshot.none { it.id == project.id }) snapshot = emissions.receive()
                snapshot
            }
            assertTrue(observed.any { it.id == project.id && it.title == project.title })
        } finally {
            collector.cancelAndJoin()
            emissions.close()
            projectId?.let { assertTrue(writer.deleteProject(it)) }
        }
    }

    @Test fun existingReferenceCollectorObservesOtherRepositoryImport() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val writer = ReferenceRepository.create(app)
        val reader = ReferenceRepository.create(app)
        assertNotSame(writer, reader)
        val project = writer.createProject("T12 reference invalidation ${UUID.randomUUID()}")
        val emissions = Channel<List<com.jovi.photoai.data.reference.ReferenceRecord>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) {
            reader.activeRecordsForProject(project.id).collect { emissions.send(it) }
        }
        try {
            assertTrue(withTimeout(10_000) { emissions.receive() }.isEmpty())
            SyntheticPickerMediaFactory(app).use { media ->
                val result = writer.importIntoProject(media.jpeg().uri, project.id)
                assertTrue(result is ReferenceImportResult.Success)
                val imported = (result as ReferenceImportResult.Success).record
                val observed = withTimeout(10_000) {
                    var snapshot = emissions.receive()
                    while (snapshot.none { it.photo.id == imported.photo.id }) snapshot = emissions.receive()
                    snapshot
                }
                assertTrue(observed.any { it.photo.id == imported.photo.id && it.projectId == project.id })
            }
        } finally {
            collector.cancelAndJoin()
            emissions.close()
            assertTrue(writer.deleteProject(project.id))
        }
    }
}
