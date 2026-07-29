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

sealed interface ReferenceImportUiState {
    data object Idle : ReferenceImportUiState
    data object Importing : ReferenceImportUiState
    data class Ready(val record: ReferenceRecord) : ReferenceImportUiState
    data class Failed(val code: ReferenceImportErrorCode) : ReferenceImportUiState
}

/** Keeps only safe private-reference state across configuration changes; source Uris never enter state. */
internal class ReferenceImportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReferenceRepository.create(application)
    private var importJob: Job? = null

    var state by mutableStateOf<ReferenceImportUiState>(ReferenceImportUiState.Idle)
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

    fun discardForBackOrReplacement() {
        importJob?.cancel()
        importJob = null
        discardReadyRecord()
        state = ReferenceImportUiState.Idle
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
}
