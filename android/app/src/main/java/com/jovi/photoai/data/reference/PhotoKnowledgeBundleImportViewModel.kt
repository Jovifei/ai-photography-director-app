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
    val replacementMode: Boolean = false,
    val replacementPreview: KnowledgeBundleReplacementPreview? = null,
) {
    val isComplete: Boolean
        get() = bundle?.let {
            isCompleteKnowledgeBundleMapping(it.references.map(PhotoKnowledgeBundleItem::referenceId), bindings)
        } ?: false
}

internal data class KnowledgeBundleReplacementPreview(
    val projectId: String,
    val bundle: PhotoKnowledgeBundle,
    val bindings: List<KnowledgeBundleBinding>,
    val expectedProvenance: Map<String, KnowledgeBundleProvenance>,
)

/** All commands run on the main thread. Suspend functions are injectable for lifecycle tests. */
internal class PhotoKnowledgeBundleImportViewModel(
    private val readSelectedDocument: suspend (Uri) -> PhotoKnowledgeBundleParseResult,
    private val applySelectedBundle: suspend (String, PhotoKnowledgeBundle, List<KnowledgeBundleBinding>) -> KnowledgeBundleApplyResult,
    private val replaceSelectedBundle: suspend (String, PhotoKnowledgeBundle, List<KnowledgeBundleBinding>, Map<String, KnowledgeBundleProvenance>) -> KnowledgeBundleApplyResult = { _, _, _, _ ->
        KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE)
    },
) : ViewModel() {
    constructor(repository: ReferenceRepository, contentResolver: ContentResolver) : this(
        readSelectedDocument = { uri ->
            withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                PhotoKnowledgeBundleDocumentReader(contentResolver).read(uri) { context.ensureActive() }
            }
        },
        applySelectedBundle = repository::applyKnowledgeBundle,
        replaceSelectedBundle = repository::replaceKnowledgeBundle,
    )

    private val mutableState = MutableStateFlow(KnowledgeBundleImportUiState())
    val state: StateFlow<KnowledgeBundleImportUiState> = mutableState.asStateFlow()
    private val session = KnowledgeBundleImportSession()
    private var readJob: Job? = null

    fun readDocument(uri: Uri?) {
        // Cancelling the system picker is not a failed import and must not discard a preview.
        if (uri == null) return
        val ticket = session.beginRead() ?: return
        val replacementMode = mutableState.value.replacementMode
        readJob?.cancel()
        mutableState.value = KnowledgeBundleImportUiState(isReading = true, replacementMode = replacementMode)
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
                is PhotoKnowledgeBundleParseResult.Success -> KnowledgeBundleImportUiState(bundle = result.bundle, replacementMode = replacementMode)
                is PhotoKnowledgeBundleParseResult.Failure -> KnowledgeBundleImportUiState(parseError = result.code, replacementMode = replacementMode)
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
        mutableState.value = current.copy(bindings = bindings, applyError = null, replacementPreview = null)
    }

    fun setReplacementMode(enabled: Boolean) {
        val current = mutableState.value
        if (current.isReading || current.isApplying || current.applyOutcomeUnknown) return
        mutableState.value = current.copy(replacementMode = enabled, bindings = emptyMap(), replacementPreview = null, applyError = null)
    }

    fun cancelReplacementPreview() {
        val current = mutableState.value
        if (!current.isApplying) mutableState.value = current.copy(replacementPreview = null)
    }

    fun previewReplacement(projectId: String, records: List<ReferenceRecord>) {
        val current = mutableState.value
        val bundle = current.bundle ?: return
        if (!current.replacementMode || current.isReading || current.isApplying || current.applyOutcomeUnknown) return
        fun reject(code: KnowledgeBundleApplyErrorCode) {
            mutableState.value = current.copy(replacementPreview = null, applyError = code)
        }
        if (projectId.isBlank()) return reject(KnowledgeBundleApplyErrorCode.PROJECT_NOT_FOUND)
        if (!current.isComplete) return reject(KnowledgeBundleApplyErrorCode.BINDING_INCOMPLETE)
        val bindings = current.bindings.map { KnowledgeBundleBinding(it.key, it.value) }
        val expected = mutableMapOf<String, KnowledgeBundleProvenance>()
        for (binding in bindings) {
            val matches = records.filter { it.photo.id == binding.localReferenceId }
            val record = matches.singleOrNull() ?: return reject(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND)
            if (record.projectId != projectId) return reject(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND)
            knowledgeBundleReplacementError(record, bundle, binding.producerReferenceId)?.let { return reject(it) }
            expected[binding.localReferenceId] = record.knowledgeBundleProvenance ?: return reject(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE)
        }
        val frozenBundle = bundle.copy(references = bundle.references.toList())
        mutableState.value = current.copy(applyError = null, replacementPreview = KnowledgeBundleReplacementPreview(projectId, frozenBundle, bindings.toList(), expected.toMap()))
    }

    fun confirmReplacement(projectId: String) {
        val current = mutableState.value
        val preview = current.replacementPreview ?: return
        if (current.isReading || current.isApplying || current.applyOutcomeUnknown) return
        if (!current.replacementMode || preview.projectId != projectId || preview.bundle != current.bundle ||
            preview.bindings.associate { it.producerReferenceId to it.localReferenceId } != current.bindings) {
            mutableState.value = current.copy(replacementPreview = null, applyError = KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE)
            return
        }
        executeApply(current) { replaceSelectedBundle(preview.projectId, preview.bundle, preview.bindings, preview.expectedProvenance) }
    }

    fun apply(projectId: String) {
        val current = mutableState.value
        val bundle = current.bundle ?: return
        if (current.replacementMode || !current.isComplete || current.isReading || current.isApplying || projectId.isBlank()) return
        val bindings = current.bindings.map { KnowledgeBundleBinding(it.key, it.value) }
        executeApply(current) { applySelectedBundle(projectId, bundle, bindings) }
    }

    private fun executeApply(current: KnowledgeBundleImportUiState, operation: suspend () -> KnowledgeBundleApplyResult) {
        val ticket = session.beginApply() ?: return
        mutableState.value = current.copy(isApplying = true, applyError = null)
        viewModelScope.launch {
            val result = try {
                operation().also { currentCoroutineContext().ensureActive() }
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
            if (!session.finishApply(ticket)) return@launch
            mutableState.value = when (result) {
                is KnowledgeBundleApplyResult.Success -> KnowledgeBundleImportUiState(appliedCount = result.appliedCount)
                KnowledgeBundleApplyResult.OutcomeUnknown -> KnowledgeBundleImportUiState(applyOutcomeUnknown = true)
                is KnowledgeBundleApplyResult.Failure -> if (result.code == KnowledgeBundleApplyErrorCode.DATABASE_COMMIT_FAILED) {
                    KnowledgeBundleImportUiState(applyOutcomeUnknown = true)
                } else {
                    current.copy(isApplying = false, applyError = result.code, replacementPreview = null)
                }
            }
        }
    }

    /** Synchronous gate: callers must not navigate if the transaction is already applying. */
    fun tryLeave(): Boolean {
        if (!session.reset()) return false
        readJob?.cancel()
        readJob = null
        mutableState.value = KnowledgeBundleImportUiState()
        return true
    }

    fun reset() {
        tryLeave()
    }
}
