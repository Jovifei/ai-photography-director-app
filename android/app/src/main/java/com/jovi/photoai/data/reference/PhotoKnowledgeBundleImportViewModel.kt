package com.jovi.photoai.data.reference

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) {
    val isComplete: Boolean
        get() = bundle != null && bundle.references.all { bindings[it.referenceId] != null }
}

internal class PhotoKnowledgeBundleImportViewModel(
    private val repository: ReferenceRepository,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    private val mutableState = MutableStateFlow(KnowledgeBundleImportUiState())
    val state: StateFlow<KnowledgeBundleImportUiState> = mutableState.asStateFlow()

    fun readDocument(uri: Uri?) {
        mutableState.value = KnowledgeBundleImportUiState(isReading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { PhotoKnowledgeBundleDocumentReader(contentResolver).read(uri) }
            mutableState.value = when (result) {
                is PhotoKnowledgeBundleParseResult.Success -> KnowledgeBundleImportUiState(bundle = result.bundle)
                is PhotoKnowledgeBundleParseResult.Failure -> KnowledgeBundleImportUiState(parseError = result.code)
            }
        }
    }

    fun bind(producerReferenceId: String, localReferenceId: String) {
        mutableState.update { current ->
            val bundle = current.bundle ?: return@update current
            if (bundle.references.none { it.referenceId == producerReferenceId }) return@update current
            val withoutLocalDuplicate = current.bindings.filterValues { it != localReferenceId }.toMutableMap()
            withoutLocalDuplicate[producerReferenceId] = localReferenceId
            current.copy(bindings = withoutLocalDuplicate, applyError = null)
        }
    }

    fun apply(projectId: String) {
        val current = mutableState.value
        val bundle = current.bundle ?: return
        if (!current.isComplete || current.isApplying) return
        mutableState.value = current.copy(isApplying = true, applyError = null)
        viewModelScope.launch {
            val result = repository.applyKnowledgeBundle(
                projectId = projectId,
                bundle = bundle,
                bindings = current.bindings.map { KnowledgeBundleBinding(it.key, it.value) },
            )
            mutableState.value = when (result) {
                is KnowledgeBundleApplyResult.Success -> KnowledgeBundleImportUiState(appliedCount = result.appliedCount)
                is KnowledgeBundleApplyResult.Failure -> current.copy(isApplying = false, applyError = result.code)
            }
        }
    }

    fun reset() {
        mutableState.value = KnowledgeBundleImportUiState()
    }
}
