package com.jovi.photoai.t13

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic injected operations only; no Activity, media, Room, or provider. */
@RunWith(AndroidJUnit4::class)
class T13BundleReplacementViewModelAndroidTest {
    @Test fun incompleteAndIneligiblePreviewsNeverReachWriter() = runBlocking {
        var writes = 0
        val model = model { _, _, _, _ -> writes++; KnowledgeBundleApplyResult.Success(1) }
        use(model) {
            prepare(model)
            main {
                model.bind("reference", "local")
                model.previewReplacement("project", listOf(record()))
                assertEquals(KnowledgeBundleApplyErrorCode.BINDING_INCOMPLETE, model.state.value.applyError)
                model.bind("reference", "local")
                model.previewReplacement("project", listOf(record().copy(knowledgeBundleProvenance = null)))
                assertEquals(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE, model.state.value.applyError)
                assertNull(model.state.value.replacementPreview)
                model.confirmReplacement("project")
                assertEquals(0, writes)
            }
        }
    }

    @Test fun replacementExceptionEntersInspectProjectState() = runBlocking {
        var writes = 0
        val model = model { _, _, _, _ -> writes++; error("Synthetic uncertain commit") }
        use(model) {
            prepare(model)
            main { model.previewReplacement("project", listOf(record())); model.confirmReplacement("project") }
            withTimeout(3000) { model.state.first { it.applyOutcomeUnknown } }
            main { model.confirmReplacement("project"); model.apply("project") }
            assertEquals(1, writes)
            assertNull(model.state.value.appliedCount)
            assertNull(model.state.value.replacementPreview)
        }
    }

    @Test fun previewCancelAndDirectApplyNeverWrite_andConfirmUsesFrozenTuple() = runBlocking {
        var writes = 0
        val model = model { project, incoming, bindings, expected ->
            writes++
            assertEquals("project", project)
            assertEquals(bundle(), incoming)
            assertEquals(listOf(KnowledgeBundleBinding("reference", "local")), bindings)
            assertEquals(mapOf("local" to provenance()), expected)
            KnowledgeBundleApplyResult.Success(1)
        }
        use(model) {
            prepare(model)
            main {
                model.previewReplacement("project", listOf(record()))
                assertNotNull(model.state.value.replacementPreview)
                model.apply("project")
                model.cancelReplacementPreview()
                model.confirmReplacement("project")
                assertEquals(0, writes)
                model.previewReplacement("project", listOf(record()))
                model.confirmReplacement("project")
            }
            withTimeout(3000) { model.state.first { it.appliedCount == 1 } }
            assertEquals(1, writes)
        }
    }

    @Test fun mappingModeAndDocumentChangesInvalidatePreview_andProjectSwitchRejects() = runBlocking {
        var writes = 0
        val model = model { _, _, _, _ -> writes++; KnowledgeBundleApplyResult.Success(1) }
        use(model) {
            prepare(model)
            main {
                model.previewReplacement("project", listOf(record()))
                model.confirmReplacement("other")
                assertEquals(KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE, model.state.value.applyError)
                assertNull(model.state.value.replacementPreview)
                model.previewReplacement("project", listOf(record().copy(projectId = "other")))
                assertEquals(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND, model.state.value.applyError)
                model.previewReplacement("project", listOf(record()))
                model.bind("reference", "local")
                assertNull(model.state.value.replacementPreview)
                model.bind("reference", "local")
                model.previewReplacement("project", listOf(record()))
                model.setReplacementMode(false)
                assertNull(model.state.value.replacementPreview)
                assertTrue(model.state.value.bindings.isEmpty())
            }
            prepare(model)
            main { model.previewReplacement("project", listOf(record())); model.readDocument(Uri.parse("content://synthetic/new")) }
            withTimeout(3000) { model.state.first { !it.isReading } }
            assertNull(model.state.value.replacementPreview)
            assertEquals(0, writes)
        }
    }

    @Test fun inFlightReplacementIsFenced_andUnknownCommitCannotRetry() = runBlocking {
        val result = CompletableDeferred<KnowledgeBundleApplyResult>()
        var writes = 0
        val model = model { _, _, _, _ -> writes++; result.await() }
        use(model) {
            prepare(model)
            main {
                model.previewReplacement("project", listOf(record()))
                val preview = model.state.value.replacementPreview
                model.confirmReplacement("project")
                model.setReplacementMode(false)
                model.cancelReplacementPreview()
                model.bind("reference", "other")
                model.confirmReplacement("project")
                assertFalse(model.tryLeave())
                assertEquals(preview, model.state.value.replacementPreview)
                assertTrue(model.state.value.replacementMode)
                assertEquals(1, writes)
            }
            result.complete(KnowledgeBundleApplyResult.OutcomeUnknown)
            withTimeout(3000) { model.state.first { it.applyOutcomeUnknown } }
            main { model.confirmReplacement("project"); model.apply("project"); model.setReplacementMode(true) }
            assertEquals(1, writes)
            assertNull(model.state.value.bundle)
        }
    }

    private fun model(replace: suspend (String, PhotoKnowledgeBundle, List<KnowledgeBundleBinding>, Map<String, KnowledgeBundleProvenance>) -> KnowledgeBundleApplyResult) =
        PhotoKnowledgeBundleImportViewModel({ PhotoKnowledgeBundleParseResult.Success(bundle()) }, { _, _, _ -> error("Initial apply bypass") }, replace)

    private suspend fun prepare(model: PhotoKnowledgeBundleImportViewModel) {
        main { model.readDocument(Uri.parse("content://synthetic/bundle")) }
        withTimeout(3000) { model.state.first { !it.isReading && it.bundle != null } }
        main { model.setReplacementMode(true); model.bind("reference", "local") }
    }
    private suspend fun main(action: () -> Unit) = withContext(Dispatchers.Main) { action() }
    private suspend fun use(model: PhotoKnowledgeBundleImportViewModel, action: suspend () -> Unit) {
        val store = ViewModelStore()
        main { store.put("test", model) }
        try { action() } finally { main { store.clear() } }
    }
    private fun provenance() = KnowledgeBundleProvenance("bundle", "reference", "producer", KnowledgeBundleOrigin.PIPELINE, "old", "a".repeat(64), 7)
    private fun record() = ReferenceRecord(
        ReferencePhoto("local", "Synthetic", "Test", "Test", "local.jpg", 1f),
        ReferenceBundle("local", "scene", "background", "light", "composition", "subject", "emotion", "pose", "camera", "prompt", "1"),
        "local.jpg", 1, projectId = "project", analysisStatus = PhotoAnalysisStatus.READY, knowledgeBundleProvenance = provenance(),
    )
    private fun bundle(): PhotoKnowledgeBundle {
        val incoming = PhotoKnowledgeBundle("1.0", "bundle", KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "producer", "new"), "0".repeat(64),
            listOf(PhotoKnowledgeBundleItem("reference", KnowledgeBundlePhotography("scene", "background", "light", "composition", "subject", "emotion", "pose", "camera", "prompt"))))
        return incoming.copy(payloadSha256 = canonicalPayloadSha256(incoming))
    }
}
