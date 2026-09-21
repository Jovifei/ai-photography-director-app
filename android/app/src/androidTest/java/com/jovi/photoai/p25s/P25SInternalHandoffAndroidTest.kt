package com.jovi.photoai.p25s

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.data.reference.KnowledgeBundleApplyErrorCode
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.KnowledgeBundleBinding
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
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
        val bundle = readBundle()
        assertEquals(20, bundle.references.size)
        assertEquals("synthetic_internal_handoff_test", bundle.source.producerId)
        assertEquals("not_for_release", bundle.source.releaseId)
        P23RRoomFixture().use { fixture ->
            fixture.seed(20)
            // Deliberately reverse targets. An implementation that matches array order fails.
            val targets = fixture.records.reversed()
            val bindings = bundle.references.mapIndexed { index, item ->
                KnowledgeBundleBinding(item.referenceId, targets[index].photo.id)
            }
            assertEquals(KnowledgeBundleApplyResult.Success(20), fixture.repository.applyKnowledgeBundle(fixture.project.id, bundle, bindings))
            val persisted = fixture.repository.activeRecordsForProject(fixture.project.id).first()
            assertEquals(20, persisted.size)
            val byLocalId = persisted.associateBy { it.photo.id }
            for ((index, item) in bundle.references.withIndex()) {
                val record = requireNotNull(byLocalId[bindings[index].localReferenceId])
                val expected = item.photography
                assertEquals(PhotoAnalysisStatus.READY, record.analysisStatus)
                assertEquals("离线知识包", record.photo.sourceLabel)
                assertNull(record.analysisProvenance)
                val source = requireNotNull(record.knowledgeBundleProvenance)
                assertEquals(KnowledgeBundleOrigin.PIPELINE, source.origin)
                assertEquals(item.referenceId, source.producerReferenceId)
                assertEquals(bundle.bundleId, source.bundleId)
                assertEquals(bundle.source.producerId, source.producerId)
                assertEquals(bundle.source.releaseId, source.releaseId)
                assertEquals(bundle.payloadSha256, source.payloadSha256)
                assertEquals(expected.scene, record.bundle.scene)
                assertEquals(expected.backgroundStory, record.bundle.backgroundStory)
                assertEquals(expected.lighting, record.bundle.lighting)
                assertEquals(expected.composition, record.bundle.composition)
                assertEquals(expected.subjectIntent, record.bundle.subjectIntent)
                assertEquals(expected.emotion, record.bundle.emotion)
                assertEquals(expected.poseTemplate, record.bundle.poseTemplate)
                assertEquals(expected.cameraPosition, record.bundle.cameraPosition)
                assertEquals(expected.directorPrompt, record.bundle.directorPrompt)
            }
            assertNull(fixture.repository.projectSummary(fixture.project.id))
            assertNull(fixture.repository.projectSummaryForProject(fixture.project.id).first())
        }
    }

    @Test
    fun invalidFinalBinding_causesNoPartialRoomWrites() = runBlocking {
        val bundle = readBundle()
        P23RRoomFixture().use { fixture ->
            fixture.seed(20)
            val bindings = bundle.references.mapIndexed { index, item ->
                KnowledgeBundleBinding(item.referenceId,
                    if (index == 19) "missing-local-reference" else fixture.records[index].photo.id)
            }
            assertEquals(
                KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND),
                fixture.repository.applyKnowledgeBundle(fixture.project.id, bundle, bindings),
            )
            val persisted = fixture.repository.activeRecordsForProject(fixture.project.id).first()
            assertEquals(20, persisted.size)
            assertTrue(persisted.all { it.analysisStatus == PhotoAnalysisStatus.IMPORTED })
            assertTrue(persisted.all { it.knowledgeBundleProvenance == null && it.analysisProvenance == null })
        }
    }

    private fun readBundle(): PhotoKnowledgeBundle {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("p25s/internal-handoff-20.bundle.json").use { it.readBytes() }
        return when (val result = PhotoKnowledgeBundleParser.parse(bytes)) {
            is PhotoKnowledgeBundleParseResult.Success -> result.bundle
            is PhotoKnowledgeBundleParseResult.Failure -> error("P25S_FIXTURE_PARSE_${result.code}")
        }
    }
}
