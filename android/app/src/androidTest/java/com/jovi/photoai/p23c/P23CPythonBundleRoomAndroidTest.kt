package com.jovi.photoai.p23c

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.KnowledgeBundleBinding
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParser
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Persistent synthetic-only harness for Python bundle -> Parser -> Room -> restart. */
@RunWith(AndroidJUnit4::class)
class P23CPythonBundleRoomAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun prepareTwentyPythonEntries_areExplicitlyBoundAndPersisted() = runBlocking {
        val repository = ReferenceRepository.create(context)
        repository.clearAll()
        ReferenceLibraryPreferences(context).clearLastActiveReferenceId()
        val records = buildList {
            SyntheticPickerMediaFactory(context).use { media ->
                repeat(20) { index ->
                    val imported = repository.importFromPicker(
                        media.jpeg(displayName = "p23c-room-${index.toString().padStart(2, '0')}.jpg").uri,
                    )
                    assertTrue(imported is ReferenceImportResult.Success)
                    add((imported as ReferenceImportResult.Success).record)
                }
            }
        }
        val bundle = readPythonBundle()
        val projectId = records.first().projectId
        val bindings = bundle.references.mapIndexed { index, item ->
            KnowledgeBundleBinding(item.referenceId, records[index].photo.id)
        }
        assertEquals(KnowledgeBundleApplyResult.Success(20), repository.applyKnowledgeBundle(projectId, bundle, bindings))
        val persisted = repository.activeRecordsForProject(projectId).first()
        assertEquals(20, persisted.size)
        assertTrue(persisted.all { it.analysisStatus == PhotoAnalysisStatus.READY })
        assertTrue(persisted.all { it.knowledgeBundleProvenance?.origin == KnowledgeBundleOrigin.PIPELINE })
        assertTrue(persisted.all { it.analysisProvenance == null })
    }

    @Test
    fun verifyTwentyPythonEntries_afterOsProcessRestart_preservesSourceAndNoSummary() = runBlocking {
        val repository = ReferenceRepository.create(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val persisted = repository.activeRecordsForProject("legacy-project-v1").first()
            assertEquals(20, persisted.size)
            assertTrue(persisted.all { it.analysisStatus == PhotoAnalysisStatus.READY })
            assertTrue(persisted.all { it.knowledgeBundleProvenance?.origin == KnowledgeBundleOrigin.PIPELINE })
            assertTrue(persisted.all { it.analysisProvenance == null })
            assertNull(repository.projectSummary("legacy-project-v1"))
            assertNotNull(persisted.first().knowledgeBundleProvenance)
        } finally {
            scenario.close()
            repository.clearAll()
            ReferenceLibraryPreferences(context).clearLastActiveReferenceId()
        }
    }

    private fun readPythonBundle(): PhotoKnowledgeBundle {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("p23c/roundtrip-20.bundle.json").use { it.readBytes() }
        return when (val result = PhotoKnowledgeBundleParser.parse(bytes)) {
            is PhotoKnowledgeBundleParseResult.Success -> result.bundle
            is PhotoKnowledgeBundleParseResult.Failure -> error("P23C_PYTHON_BUNDLE_PARSE_${result.code}")
        }
    }
}
