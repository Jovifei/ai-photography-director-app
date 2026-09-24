package com.jovi.photoai.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.jovi.photoai.data.capture.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal data class CaptureLibraryUiState(
    val ready: Boolean = false,
    val records: List<CaptureRecord> = emptyList(),
    val pendingExport: CaptureExportTicket? = null,
    val capturing: Boolean = false,
    val message: String? = null,
    val galleryVisible: Boolean = false,
    val projectFilter: String? = null,
    val onlyUnassigned: Boolean = false,
    val selectedId: String? = null,
)

/** Activity-owned state; durable facts come only from the capture repository. */
internal class CaptureLibraryViewModel(private val application: Application, private val savedState: SavedStateHandle) : ViewModel() {
    private suspend fun repository() = withContext(Dispatchers.IO) { CaptureRepository.get(application) }
    private var initializeJob: Job? = null
    private val mutable = MutableStateFlow(CaptureLibraryUiState(
        galleryVisible = savedState["captureGalleryOpen"] ?: false,
        projectFilter = savedState["captureProjectFilter"],
        onlyUnassigned = savedState["captureUnassigned"] ?: false,
        selectedId = savedState["captureSelectedId"],
    ))
    val state = mutable.asStateFlow()

    init { initialize() }

    fun initialize() {
        if (initializeJob?.isActive == true) return
        initializeJob = viewModelScope.launch {
            try {
                val repo = repository()
                repo.prepare()
                mutable.update { it.copy(ready = true, message = null) }
                coroutineScope {
                    launch { repo.records.collect { rows -> mutable.update { it.copy(records = rows) } } }
                    launch { repo.pendingExport.collect { ticket -> mutable.update { it.copy(pendingExport = ticket) } } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(ready = false, message = "成片恢复未完成，请重试；没有清除任何原片。") } }
        }
    }

    fun openGallery(projectId: String? = null, selectedId: String? = null) {
        savedState["captureGalleryOpen"] = true
        savedState["captureProjectFilter"] = projectId
        savedState["captureUnassigned"] = false
        savedState["captureSelectedId"] = selectedId
        mutable.update { it.copy(galleryVisible = true, projectFilter = projectId,
            onlyUnassigned = false, selectedId = selectedId) }
    }
    fun closeGallery() {
        savedState["captureGalleryOpen"] = false
        mutable.update { it.copy(galleryVisible = false) }
    }
    fun select(id: String?) {
        savedState["captureSelectedId"] = id
        mutable.update { it.copy(selectedId = id) }
    }
    fun showUnassigned(only: Boolean) {
        savedState["captureUnassigned"] = only
        savedState["captureSelectedId"] = null
        mutable.update { it.copy(onlyUnassigned = only, selectedId = null) }
    }
    fun clearMessage() { mutable.update { it.copy(message = null) } }

    fun capture(
        projectId: String?, referenceId: String?,
        driver: (File, (File) -> Unit, (Throwable) -> Unit) -> Unit,
        onSettled: (Boolean) -> Unit,
    ): Boolean {
        if (!mutable.value.ready || mutable.value.capturing) return false
        val id = UUID.randomUUID().toString().replace("-", "")
        mutable.update { it.copy(capturing = true, message = null) } // synchronous double-tap fence
        viewModelScope.launch {
            var repo: CaptureRepository? = null
            try {
                val captureRepo = repository()
                repo = captureRepo
                val reservation = captureRepo.reserve(id, projectId, referenceId)
                val callbackAccepted = AtomicBoolean(false)
                fun settle(saved: Boolean) {
                    if (!callbackAccepted.compareAndSet(false, true)) return
                    captureRepo.settleCapture(id, saved) { row ->
                        if (viewModelScope.isActive) {
                            mutable.update { it.copy(capturing = false,
                                message = if (row == null) "拍摄或归档结果待确认，请查看成片；未删除原片。" else null) }
                            onSettled(row != null)
                            if (row != null) openGallery(row.projectId, row.id)
                        }
                    }
                }
                try { driver(reservation.output, { settle(true) }, { settle(false) }) }
                catch (_: Exception) { settle(false) }
            } catch (cancelled: CancellationException) {
                repo?.settleCapture(id, false) { }
                throw cancelled
            } catch (_: Exception) {
                repo?.settleCapture(id, false) { }
                mutable.update { it.copy(capturing = false, message = "未能开始拍摄，请检查空间和成片记录。") }
                onSettled(false)
            }
        }
        return true
    }

    fun delete(id: String) = operation {
        repository().delete(id)
        if (mutable.value.selectedId == id) select(null)
    }
    fun projectDeleted(id: String) = operation { repository().detachProject(id) }
    fun requestExport(
        id: String,
        onReserved: (CaptureExportTicket) -> Unit = {},
    ) {
        viewModelScope.launch {
            var ticket: CaptureExportTicket? = null
            try {
                val repo = repository()
                val reserved = repo.reserveExport(id)
                ticket = reserved
                if (!repo.armExport(reserved.token)) throw CaptureProblem("EXPORT_ARM_FAILED")
                onReserved(reserved.copy(phase = ExportPhase.SELECTING))
            } catch (cancelled: CancellationException) {
                ticket?.let { runCatching { repository().abandonExport(it.token) } }
                throw cancelled
            } catch (_: Exception) {
                ticket?.let { runCatching { repository().abandonExport(it.token) } }
                mutable.update { it.copy(message = "操作未能确认完成，请检查当前状态；没有自动删除原片或重复保存。") }
            }
        }
    }
    fun abandonExport(token: String) = operation { repository().abandonExport(token) }
    fun cancelSelection(token: String) = operation { repository().cancelSelection(token) }
    fun acceptExportResult(token: String, destination: Uri?) = operation {
        if (destination == null) repository().cancelSelection(token)
        else repository().export(token, destination)
    }
    suspend fun previewFile(record: CaptureRecord) = repository().previewFile(record)

    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = "操作未能确认完成，请检查当前状态；没有自动删除原片或重复保存。") } }
        }
    }
}

internal class CaptureLibraryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(CaptureLibraryViewModel::class.java))
        return CaptureLibraryViewModel(application, extras.createSavedStateHandle()) as T
    }
}
