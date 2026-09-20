package com.jovi.photoai.p25s

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParser
import com.jovi.photoai.p23r.P23RRoomFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic internal-handoff fixture only; this is not Pipeline release evidence. */
@RunWith(AndroidJUnit4::class)
class P25SInternalHandoffAndroidTest {
    @Test
    fun currentTwentyEntryFixture_parserExplicitBindingsRoomAndBundleSummaryPolicy() = runBlocking {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("p25s/internal-handoff-20.bundle.json").use { it.readBytes() }
        val bundle = when (val result = PhotoKnowledgeBundleParser.parse(bytes)) {
            is PhotoKnowledgeBundleParseResult.Success -> result.bundle
            is PhotoKnowledgeBundleParseResult.Failure -> error("P25S_FIXTURE_PARSE_${result.code}")
        }
        assertEquals(20, bundle.references.size)
        assertEquals("synthetic_internal_handoff_test", bundle.source.producerId)
        assertEquals("not_for_release", bundle.source.releaseId)

        P23RRoomFixture().use { fixture ->
            fixture.seed(20)
            val bindings = bundle.references.mapIndexed { index, item ->
                com.jovi.photoai.data.reference.KnowledgeBundleBinding(item.referenceId, fixture.records[index].photo.id)
            }
            assertEquals(KnowledgeBundleApplyResult.Success(20), fixture.repository.applyKnowledgeBundle(fixture.project.id, bundle, bindings))
            val persisted = fixture.repository.activeRecordsForProject(fixture.project.id).first()
            assertEquals(20, persisted.size)
            assertTrue(persisted.all { it.analysisStatus == PhotoAnalysisStatus.READY })
            assertTrue(persisted.all { it.photo.sourceLabel == "离线知识包" })
            assertTrue(persisted.all { it.knowledgeBundleProvenance?.origin == KnowledgeBundleOrigin.PIPELINE })
            assertTrue(persisted.all { it.knowledgeBundleProvenance?.producerId == "synthetic_internal_handoff_test" })
            assertNull(fixture.repository.projectSummary(fixture.project.id))
        }
    }

    @Test
    fun invalidFinalBinding_causesNoPartialRoomWrites() = runBlocking {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("p25s/internal-handoff-20.bundle.json").use { it.readBytes() }
        val bundle = (PhotoKnowledgeBundleParser.parse(bytes) as PhotoKnowledgeBundleParseResult.Success).bundle
        P23RRoomFixture().use { fixture ->
            fixture.seed(20)
            val bindings = bundle.references.mapIndexed { index, item ->
                com.jovi.photoai.data.reference.KnowledgeBundleBinding(
                    item.referenceId,
                    if (index == 19) "missing-local-reference" else fixture.records[index].photo.id,
                )
            }
            assertTrue(fixture.repository.applyKnowledgeBundle(fixture.project.id, bundle, bindings) is KnowledgeBundleApplyResult.Failure)
            val persisted = fixture.repository.activeRecordsForProject(fixture.project.id).first()
            assertTrue(persisted.all { it.analysisStatus == PhotoAnalysisStatus.IMPORTED })
            assertTrue(persisted.all { it.knowledgeBundleProvenance == null })
        }
    }
}
