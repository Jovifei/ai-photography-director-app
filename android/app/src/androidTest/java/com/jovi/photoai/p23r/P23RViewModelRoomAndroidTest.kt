package com.jovi.photoai.p23r

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read input is synthetic; every apply below calls the actual Repository + Room. */
@RunWith(AndroidJUnit4::class)
class P23RViewModelRoomAndroidTest {
    @Test fun sameFrameLeaveAndReset_cannotEscapeRealApply() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(1)
            val store = ViewModelStore()
            val model = model(f)
            try {
                load(f, store, model)
                // All commands happen in ONE main-thread callback, before Compose could recompose.
                withContext(Dispatchers.Main) {
                    model.bind("producer_0", f.records.single().photo.id)
                    model.apply(f.project.id)
                    assertFalse(model.tryLeave())
                    model.reset()
                    model.apply(f.project.id)
                    assertTrue(model.state.value.isApplying)
                }
                withTimeout(5000) { model.state.first { it.appliedCount == 1 } }
                assertEquals("READY", f.dao.activeById(f.records.single().photo.id)!!.analysisStatus)
                withContext(Dispatchers.Main) { assertTrue(model.tryLeave()) }
            } finally { withContext(Dispatchers.Main) { store.clear() } }
        }
    }

    @Test fun realCommitAcknowledgementFailure_hasNoRetryablePreviewOrRollbackClaim() = runBlocking {
        P23RRoomFixture(afterKnowledgeBundleCommit = { throw IllegalStateException("synthetic lost acknowledgement") }).use { f ->
            f.seed(1)
            val store = ViewModelStore()
            val model = model(f)
            try {
                load(f, store, model)
                withContext(Dispatchers.Main) {
                    model.bind("producer_0", f.records.single().photo.id)
                    model.apply(f.project.id)
                }
                withTimeout(5000) { model.state.first { it.applyOutcomeUnknown } }
                val stored = f.dao.activeById(f.records.single().photo.id)!!
                assertEquals("READY", stored.analysisStatus)
                withContext(Dispatchers.Main) {
                    assertNull(model.state.value.bundle)
                    assertNull(model.state.value.applyError)
                    assertNull(model.state.value.appliedCount)
                    model.apply(f.project.id) // no selected bundle -> cannot blindly retry
                }
                assertEquals(stored, f.dao.activeById(stored.id))
            } finally { withContext(Dispatchers.Main) { store.clear() } }
        }
    }

    @Test fun realCommitCancellation_exposesUnknownInsteadOfPermanentSpinner() = runBlocking {
        P23RRoomFixture(afterKnowledgeBundleCommit = { throw CancellationException("synthetic cancelled acknowledgement") }).use { f ->
            f.seed(1)
            val store = ViewModelStore()
            val model = model(f)
            try {
                load(f, store, model)
                withContext(Dispatchers.Main) {
                    model.bind("producer_0", f.records.single().photo.id)
                    model.apply(f.project.id)
                }
                withTimeout(5000) { model.state.first { it.applyOutcomeUnknown } }
                assertFalse(model.state.value.isApplying)
                assertEquals("READY", f.dao.activeById(f.records.single().photo.id)!!.analysisStatus)
            } finally { withContext(Dispatchers.Main) { store.clear() } }
        }
    }

    private fun model(f: P23RRoomFixture) = PhotoKnowledgeBundleImportViewModel(
        readSelectedDocument = { PhotoKnowledgeBundleParseResult.Success(f.bundle()) },
        applySelectedBundle = f.repository::applyKnowledgeBundle,
    )

    private suspend fun load(f: P23RRoomFixture, store: ViewModelStore, model: PhotoKnowledgeBundleImportViewModel) {
        withContext(Dispatchers.Main) {
            store.put("test", model)
            model.readDocument(Uri.parse("content://synthetic-p23r/fixture"))
        }
        withTimeout(5000) { model.state.first { it.bundle != null } }
    }
}
