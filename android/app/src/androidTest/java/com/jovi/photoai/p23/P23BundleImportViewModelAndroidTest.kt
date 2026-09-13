package com.jovi.photoai.p23

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No real document, provider, Room, network, or photo. Uses Android Main and ViewModel scope. */
@RunWith(AndroidJUnit4::class)
class P23BundleImportViewModelAndroidTest {
    @Test fun lastSelectionWins_evenWhenOldReaderIgnoresCancellation() = runBlocking {
        val oldResult = CompletableDeferred<PhotoKnowledgeBundleParseResult>()
        val oldStarted = CompletableDeferred<Unit>()
        val oldReturned = CompletableDeferred<Unit>()
        val store = ViewModelStore()
        val model = PhotoKnowledgeBundleImportViewModel(
            readSelectedDocument = { uri ->
                if (uri.lastPathSegment == "old") withContext(NonCancellable) {
                    oldStarted.complete(Unit)
                    try { oldResult.await() } finally { oldReturned.complete(Unit) }
                } else success("new")
            },
            applySelectedBundle = { _, _, _ -> error("Unexpected apply") },
        )
        try {
            withContext(Dispatchers.Main) { store.put("test", model); model.readDocument(uri("old")) }
            withTimeout(3000) { oldStarted.await() }
            withContext(Dispatchers.Main) { model.readDocument(uri("new")) }
            withTimeout(3000) { model.state.first { it.bundle?.bundleId == "new" } }
            oldResult.complete(success("old"))
            withTimeout(3000) { oldReturned.await() }
            withContext(Dispatchers.Main) { assertEquals("new", model.state.value.bundle?.bundleId) }
        } finally {
            oldResult.complete(success("old"))
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun resetInvalidatesPendingRead() = runBlocking {
        val result = CompletableDeferred<PhotoKnowledgeBundleParseResult>()
        val started = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        val store = ViewModelStore()
        val model = PhotoKnowledgeBundleImportViewModel(
            readSelectedDocument = { withContext(NonCancellable) {
                started.complete(Unit)
                try { result.await() } finally { returned.complete(Unit) }
            } },
            applySelectedBundle = { _, _, _ -> error("Unexpected apply") },
        )
        try {
            withContext(Dispatchers.Main) { store.put("test", model); model.readDocument(uri("old")) }
            withTimeout(3000) { started.await() }
            withContext(Dispatchers.Main) { model.reset() }
            result.complete(success("old"))
            withTimeout(3000) { returned.await() }
            withContext(Dispatchers.Main) {
                assertNull(model.state.value.bundle)
                assertFalse(model.state.value.isReading)
            }
        } finally {
            result.complete(success("old"))
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun pickerCancellation_preservesPreviewAndMapping() = runBlocking {
        val store = ViewModelStore()
        val model = PhotoKnowledgeBundleImportViewModel(
            readSelectedDocument = { success("preview") },
            applySelectedBundle = { _, _, _ -> error("Unexpected apply") },
        )
        try {
            withContext(Dispatchers.Main) { store.put("test", model); model.readDocument(uri("preview")) }
            withTimeout(3000) { model.state.first { it.bundle != null } }
            withContext(Dispatchers.Main) {
                model.bind("reference", "local")
                val before = model.state.value
                model.readDocument(null)
                assertEquals(before, model.state.value)
                model.bind("reference", "local")
                assertFalse(model.state.value.isComplete)
            }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun applyCannotBeResetReboundOrSubmittedTwice() = runBlocking {
        val result = CompletableDeferred<KnowledgeBundleApplyResult>()
        val store = ViewModelStore()
        var reads = 0
        var writes = 0
        val model = PhotoKnowledgeBundleImportViewModel(
            readSelectedDocument = { reads++; success("preview") },
            applySelectedBundle = { _, _, bindings ->
                writes++
                assertEquals("local", bindings.single().localReferenceId)
                result.await()
            },
        )
        try {
            withContext(Dispatchers.Main) { store.put("test", model); model.readDocument(uri("preview")) }
            withTimeout(3000) { model.state.first { it.bundle != null } }
            withContext(Dispatchers.Main) {
                model.bind("reference", "local")
                model.apply("project")
                model.apply("project")
                model.reset()
                model.readDocument(uri("other"))
                model.bind("reference", "other")
                assertTrue(model.state.value.isApplying)
                assertEquals("local", model.state.value.bindings["reference"])
                assertEquals(1, reads)
                assertEquals(1, writes)
            }
            result.complete(KnowledgeBundleApplyResult.Success(1))
            withTimeout(3000) { model.state.first { it.appliedCount == 1 } }
            assertFalse(model.state.value.isApplying)
        } finally {
            result.complete(KnowledgeBundleApplyResult.Success(1))
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun unexpectedApplyFailure_doesNotClaimRollbackOrOfferBlindRetry() = runBlocking {
        val store = ViewModelStore()
        var writes = 0
        val model = PhotoKnowledgeBundleImportViewModel(
            readSelectedDocument = { success("preview") },
            applySelectedBundle = { _, _, _ -> writes++; error("Synthetic uncertain outcome") },
        )
        try {
            withContext(Dispatchers.Main) { store.put("test", model); model.readDocument(uri("preview")) }
            withTimeout(3000) { model.state.first { it.bundle != null } }
            withContext(Dispatchers.Main) { model.bind("reference", "local"); model.apply("project") }
            withTimeout(3000) { model.state.first { it.applyOutcomeUnknown } }
            withContext(Dispatchers.Main) {
                model.apply("project")
                assertEquals(1, writes)
                assertNull(model.state.value.appliedCount)
                assertNull(model.state.value.applyError)
                assertFalse(model.state.value.isApplying)
            }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    private fun uri(id: String): Uri = Uri.parse("content://synthetic-p23/$id")

    private fun success(id: String): PhotoKnowledgeBundleParseResult.Success {
        val bundle = PhotoKnowledgeBundle(
            "1.0", id, KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic", "test"),
            "0".repeat(64), listOf(PhotoKnowledgeBundleItem("reference", KnowledgeBundlePhotography(
                "合成场景", "合成背景", "侧光", "三分构图", "站立", "平静", "肩部放松", "眼平", "保持自然呼吸。",
            ))),
        )
        return PhotoKnowledgeBundleParseResult.Success(bundle.copy(payloadSha256 = canonicalPayloadSha256(bundle)))
    }
}
