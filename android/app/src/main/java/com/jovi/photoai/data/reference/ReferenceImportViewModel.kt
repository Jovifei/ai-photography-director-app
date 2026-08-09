package com.jovi.photoai.data.reference

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed interface ReferenceImportUiState {
    data object Idle : ReferenceImportUiState
    data object Importing : ReferenceImportUiState
    data class Ready(val record: ReferenceRecord) : ReferenceImportUiState
    data class Failed(val code: ReferenceImportErrorCode) : ReferenceImportUiState
}

/** Safe progress only: Picker Uris remain inside the import coroutine and never enter UI state. */
internal data class BatchImportUiState(
    val projectId: String? = null,
    val requestedCount: Int = 0,
    val completedCount: Int = 0,
    val importedCount: Int = 0,
    val failedCount: Int = 0,
    val lastFailure: ReferenceImportErrorCode? = null,
    val isImporting: Boolean = false,
    val wasCancelled: Boolean = false,
) {
    val hasBatchResult: Boolean
        get() = requestedCount > 0 || wasCancelled
}

internal fun acceptedBatchCount(selectedCount: Int, remainingCapacity: Int): Int =
    selectedCount.coerceAtLeast(0).coerceAtMost(remainingCapacity.coerceAtLeast(0))

/** Keeps only safe private-reference state across configuration changes; source Uris never enter state. */
internal class ReferenceImportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReferenceRepository.create(application)
    private var importJob: Job? = null

    var state by mutableStateOf<ReferenceImportUiState>(ReferenceImportUiState.Idle)
        private set

    var batchState by mutableStateOf(BatchImportUiState())
        private set

    fun importImmediately(uri: Uri) {
        if (state is ReferenceImportUiState.Importing) return
        discardReadyRecord()
        state = ReferenceImportUiState.Importing
        importJob = viewModelScope.launch {
            state = when (val result = repository.importFromPicker(uri)) {
                is ReferenceImportResult.Success -> ReferenceImportUiState.Ready(result.record)
                is ReferenceImportResult.Failure -> ReferenceImportUiState.Failed(result.code)
            }
        }
    }

    fun retry() {
        if (state !is ReferenceImportUiState.Importing) state = ReferenceImportUiState.Idle
    }

    fun importBatch(projectId: String, selectedUris: List<Uri>) {
        if (importJob?.isActive == true || selectedUris.isEmpty()) return
        state = ReferenceImportUiState.Idle
        importJob = viewModelScope.launch {
            var importedCount = 0
            var failedCount = 0
            var lastFailure: ReferenceImportErrorCode? = null
            batchState = BatchImportUiState(
                projectId = projectId,
                requestedCount = selectedUris.size,
                isImporting = true,
            )
            try {
                selectedUris.forEachIndexed { index, uri ->
                    when (val result = repository.importIntoProject(uri, projectId)) {
                        is ReferenceImportResult.Success -> importedCount += 1
                        is ReferenceImportResult.Failure -> {
                            failedCount += 1
                            lastFailure = result.code
                            if (result.code !in NON_COUNTED_BATCH_FAILURES) {
                                repository.recordFailedImport(projectId)
                            }
                            if (result.code == ReferenceImportErrorCode.PROJECT_LIMIT_REACHED) {
                                batchState = batchState.copy(
                                    completedCount = index + 1,
                                    importedCount = importedCount,
                                    failedCount = failedCount,
                                    lastFailure = lastFailure,
                                    isImporting = false,
                                )
                                return@launch
                            }
                        }
                    }
                    batchState = batchState.copy(
                        completedCount = index + 1,
                        importedCount = importedCount,
                        failedCount = failedCount,
                        lastFailure = lastFailure,
                    )
                }
                batchState = batchState.copy(isImporting = false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                batchState = batchState.copy(isImporting = false, wasCancelled = true)
            }
        }
    }

    fun cancelBatchImport() {
        if (importJob?.isActive == true) {
            importJob?.cancel()
            importJob = null
        }
    }

    fun dismissBatchResult() {
        if (!batchState.isImporting) batchState = BatchImportUiState()
    }

    fun discardForBackOrReplacement() {
        importJob?.cancel()
        importJob = null
        discardReadyRecord()
        state = ReferenceImportUiState.Idle
        batchState = BatchImportUiState()
    }

    fun consumeReady(): ReferenceRecord? {
        val record = (state as? ReferenceImportUiState.Ready)?.record
        if (record != null) state = ReferenceImportUiState.Idle
        return record
    }

    private fun discardReadyRecord() {
        val record = (state as? ReferenceImportUiState.Ready)?.record ?: return
        viewModelScope.launch { repository.delete(record.photo.id) }
    }

    private companion object {
        val NON_COUNTED_BATCH_FAILURES = setOf(
            ReferenceImportErrorCode.PROJECT_LIMIT_REACHED,
            ReferenceImportErrorCode.PROJECT_NOT_FOUND,
            ReferenceImportErrorCode.USER_CANCELLED,
        )
    }
}
