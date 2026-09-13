package com.jovi.photoai.data.reference

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class KnowledgeBundleImportUiState(
    val bundle: PhotoKnowledgeBundle? = null,
    val bindings: Map<String, String> = emptyMap(),
    val isReading: Boolean = false,
    val isApplying: Boolean = false,
    val appliedCount: Int? = null,
    val parseError: PhotoKnowledgeBundleErrorCode? = null,
    val applyError: KnowledgeBundleApplyErrorCode? = null,
    val applyOutcomeUnknown: Boolean = false,
) {
    val isComplete: Boolean
        get() = bundle?.let {
            isCompleteKnowledgeBundleMapping(it.references.map(PhotoKnowledgeBundleItem::referenceId), bindings)
        } ?: false
}

/** All commands run on the main thread. Suspend functions are injectable for lifecycle tests. */
internal class PhotoKnowledgeBundleImportViewModel(
    private val readSelectedDocument: suspend (Uri) -> PhotoKnowledgeBundleParseResult,
    private val applySelectedBundle: suspend (String, PhotoKnowledgeBundle, List<KnowledgeBundleBinding>) -> KnowledgeBundleApplyResult,
) : ViewModel() {
    constructor(repository: ReferenceRepository, contentResolver: ContentResolver) : this(
        readSelectedDocument = { uri ->
            withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                PhotoKnowledgeBundleDocumentReader(contentResolver).read(uri) { context.ensureActive() }
            }
        },
        applySelectedBundle = repository::applyKnowledgeBundle,
    )

    private val mutableState = MutableStateFlow(KnowledgeBundleImportUiState())
    val state: StateFlow<KnowledgeBundleImportUiState> = mutableState.asStateFlow()
    private val session = KnowledgeBundleImportSession()
    private var readJob: Job? = null

    fun readDocument(uri: Uri?) {
        // Cancelling the system picker is not a failed import and must not discard a preview.
        if (uri == null) return
        val ticket = session.beginRead() ?: return
        readJob?.cancel()
        mutableState.value = KnowledgeBundleImportUiState(isReading = true)
        readJob = viewModelScope.launch {
            val result = try {
                readSelectedDocument(uri)
            } catch (cancelled: CancellationException) {
                if (session.finishRead(ticket)) mutableState.value = KnowledgeBundleImportUiState()
                throw cancelled
            } catch (_: Exception) {
                PhotoKnowledgeBundleParseResult.Failure(PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE)
            }
            currentCoroutineContext().ensureActive()
            if (!session.finishRead(ticket)) return@launch
            mutableState.value = when (result) {
                is PhotoKnowledgeBundleParseResult.Success -> KnowledgeBundleImportUiState(bundle = result.bundle)
                is PhotoKnowledgeBundleParseResult.Failure -> KnowledgeBundleImportUiState(parseError = result.code)
            }
        }
    }

    fun bind(producerReferenceId: String, localReferenceId: String) {
        val current = mutableState.value
        val bundle = current.bundle ?: return
        if (current.isReading || current.isApplying) return
        val bindings = toggleKnowledgeBundleBinding(
            bundle.references.map(PhotoKnowledgeBundleItem::referenceId), current.bindings,
            producerReferenceId, localReferenceId,
        )
        mutableState.value = current.copy(bindings = bindings, applyError = null)
    }

    fun apply(projectId: String) {
        val current = mutableState.value
        val bundle = current.bundle ?: return
        if (!current.isComplete || current.isReading || current.isApplying || projectId.isBlank()) return
        val ticket = session.beginApply() ?: return
        val bindings = current.bindings.map { KnowledgeBundleBinding(it.key, it.value) }
        mutableState.value = current.copy(isApplying = true, applyError = null)
        viewModelScope.launch {
            val result = try {
                applySelectedBundle(projectId, bundle, bindings)
            } catch (cancelled: CancellationException) {
                // No rollback claim, even if a provider cancels without clearing this ViewModel.
                if (session.finishApply(ticket)) {
                    mutableState.value = KnowledgeBundleImportUiState(applyOutcomeUnknown = true)
                }
                throw cancelled
            } catch (_: Exception) {
                if (session.finishApply(ticket)) {
                    mutableState.value = KnowledgeBundleImportUiState(applyOutcomeUnknown = true)
                }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (!session.finishApply(ticket)) return@launch
            mutableState.value = when (result) {
                is KnowledgeBundleApplyResult.Success -> KnowledgeBundleImportUiState(appliedCount = result.appliedCount)
                is KnowledgeBundleApplyResult.Failure -> current.copy(isApplying = false, applyError = result.code)
            }
        }
    }

    fun reset() {
        if (!session.reset()) return
        readJob?.cancel()
        readJob = null
        mutableState.value = KnowledgeBundleImportUiState()
    }
}
