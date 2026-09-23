package com.jovi.photoai.ui.capture

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jovi.photoai.data.capture.*
import com.jovi.photoai.data.reference.PhotographyProject
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import kotlinx.coroutines.CancellationException
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Adds an explicit entry without changing project/reference screens or their ownership. */
@Composable
internal fun CaptureEntryFrame(label: String, onOpen: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        Surface(color = AppColors.AppBackground) {
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 48.dp)) {
                Text(label)
            }
        }
    }
}

/** Registered outside the camera/gallery navigation so an external picker can return anywhere. */
@Composable
internal fun CaptureLibraryHost(model: CaptureLibraryViewModel, projects: List<PhotographyProject>) {
    val state by model.state.collectAsState()
    val activity = LocalContext.current.captureActivity()
    val ticket = state.pendingExport
    if (ticket != null && activity != null) {
        DisposableEffect(activity, ticket.token) {
            val immutableToken = ticket.token
            val launcher = activity.activityResultRegistry.register(
                "photoai-capture-export:$immutableToken", ActivityResultContracts.CreateDocument("image/jpeg"),
            ) { uri -> model.acceptExportResult(immutableToken, uri) }
            model.launchExportOnce(immutableToken) {
                launcher.launch("photo-director-${ticket.captureId.take(12)}.jpg")
            }
            onDispose { launcher.unregister() }
        }
    }
    if (state.galleryVisible) {
        Dialog(onDismissRequest = model::closeGallery, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CaptureLibraryContent(
                state = state,
                projectTitles = projects.associate { it.id to it.title },
                loadFile = model::previewFile,
                onClose = model::closeGallery,
                onSelect = model::select,
                onUnassigned = model::showUnassigned,
                onExport = model::requestExport,
                onDelete = model::delete,
                onAbandon = model::abandonExport,
                onRetry = model::initialize,
            )
        }
    }
}

private tailrec fun Context.captureActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.captureActivity()
    else -> null
}

@Composable
internal fun CaptureLibraryContent(
    state: CaptureLibraryUiState,
    projectTitles: Map<String, String>,
    loadFile: suspend (CaptureRecord) -> File,
    onClose: () -> Unit,
    onSelect: (String?) -> Unit,
    onUnassigned: (Boolean) -> Unit,
    onExport: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAbandon: (String) -> Unit,
    onRetry: () -> Unit,
) {
    fun effectiveProject(row: CaptureRecord): String? = row.projectId?.takeIf(projectTitles::containsKey)
    val filtered = state.records.filter { row ->
        when {
            state.projectFilter != null && projectTitles.containsKey(state.projectFilter) -> effectiveProject(row) == state.projectFilter
            state.onlyUnassigned || state.projectFilter != null -> effectiveProject(row) == null
            else -> true
        }
    }
    val selected = filtered.firstOrNull { it.id == state.selectedId }
    Column(Modifier.fillMaxSize().background(AppColors.AppBackground).safeDrawingPadding()
        .padding(horizontal = AppDimensions.PagePadding).testTag("capture-library")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = if (selected != null) { { onSelect(null) } } else onClose) {
                Text(if (selected != null) "返回成片" else "返回拍摄／项目")
            }
            TextButton(onClick = onClose) { Text("继续操作") }
        }
        Text(if (selected != null) "成片预览" else state.projectFilter?.let { projectTitles[it] } ?: "全部成片",
            style = MaterialTheme.typography.headlineSmall)
        Text("原片保存在本机应用内；卸载、清除数据或换机不会自动保留。重要照片请另存副本。",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        state.message?.let { Text(it, color = AppColors.Warning, style = MaterialTheme.typography.bodyMedium) }
        val pending = state.pendingExport
        if (pending != null) {
            Text(if (pending.phase == ExportPhase.WRITING) "正在保存副本，请勿重复操作。" else
                "有一个未结束的保存请求。中断后的外部结果可能无法确认。", color = AppColors.Warning)
            if (pending.phase != ExportPhase.WRITING) TextButton(onClick = { onAbandon(pending.token) }) {
                Text("结束上次保存请求（保留原片）")
            }
        }
        if (!state.ready) {
            Text("正在恢复成片记录；恢复完成前不进行写入。")
            TextButton(onClick = onRetry) { Text("重试成片恢复") }
        } else if (selected != null) {
            CaptureDetail(selected, projectTitles[effectiveProject(selected)] ?: "未归类", loadFile,
                state.pendingExport, onExport, onDelete, Modifier.weight(1f))
        } else {
            if (state.projectFilter == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.onlyUnassigned, onClick = { onUnassigned(false) }, label = { Text("全部") })
                FilterChip(selected = state.onlyUnassigned, onClick = { onUnassigned(true) }, label = { Text("未归类") })
            }
            if (filtered.isEmpty()) Text("还没有成片。拍照成功后，可在这里再次预览和保存副本。",
                Modifier.padding(vertical = 24.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(filtered, key = { it.id }) { row ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(row.id) }.testTag("capture-${row.id}"),
                        shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CapturePreview(row, loadFile, Modifier.fillMaxWidth().height(180.dp))
                            Text(projectTitles[effectiveProject(row)] ?: "未归类", style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance().format(Date(row.createdAtMillis)), style = MaterialTheme.typography.bodySmall)
                            Text(if (row.fileState == CaptureFileState.AVAILABLE) captureExportMessage(row) else captureFileMessage(row.fileState),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureDetail(
    record: CaptureRecord, projectTitle: String, loadFile: suspend (CaptureRecord) -> File,
    pending: CaptureExportTicket?, onExport: (String) -> Unit, onDelete: (String) -> Unit,
    modifier: Modifier,
) {
    var confirmDelete by rememberSaveable(record.id) { mutableStateOf(false) }
    var confirmExport by rememberSaveable(record.id) { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapturePreview(record, loadFile, Modifier.fillMaxWidth().height(320.dp))
        Text(projectTitle, style = MaterialTheme.typography.titleMedium)
        Text(if (record.fileState == CaptureFileState.AVAILABLE) captureExportMessage(record) else captureFileMessage(record.fileState))
        if (record.lastExportedAtMillis != null && record.exportState != CaptureExportState.SAVED) {
            Text("此前已有一次保存副本成功记录；最新一次操作状态见上方。", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            if (record.exportState in setOf(CaptureExportState.UNKNOWN, CaptureExportState.SAVED)) confirmExport = true
            else onExport(record.id)
        }, enabled = record.fileState == CaptureFileState.AVAILABLE && pending == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("保存副本") }
        OutlinedButton(onClick = { confirmDelete = true },
            enabled = record.fileState != CaptureFileState.CAPTURING && pending?.captureId != record.id,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("删除应用内成片") }
        Spacer(Modifier.height(20.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除这张应用内成片？") }, text = { Text("未保存外部副本时，删除后无法恢复。不会删除参考图或外部副本。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(record.id) }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("保留") } })
    if (confirmExport) AlertDialog(onDismissRequest = { confirmExport = false },
        title = { Text("再次保存副本？") }, text = { Text("请先检查已有保存位置。再次保存可能创建重复副本，应用不会自动删除旧文件。") },
        confirmButton = { TextButton(onClick = { confirmExport = false; onExport(record.id) }) { Text("继续另存为") } },
        dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("取消") } })
}

@Composable
private fun CapturePreview(record: CaptureRecord, loadFile: suspend (CaptureRecord) -> File, modifier: Modifier) {
    var bitmap by remember(record.id, record.fileSha256) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(record.id, record.fileSha256) { mutableStateOf(false) }
    LaunchedEffect(record.id, record.fileSha256, record.fileState) {
        if (record.fileState == CaptureFileState.AVAILABLE) {
            try {
                bitmap = decodeCaptureImage(loadFile(record))
                failed = bitmap == null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    val image = bitmap
    if (image != null && record.fileState == CaptureFileState.AVAILABLE) {
        Image(image.asImageBitmap(), contentDescription = "本机拍摄成片预览", modifier = modifier, contentScale = ContentScale.Fit)
    } else Box(modifier.background(AppColors.AccentBlueSoft), contentAlignment = Alignment.Center) {
        Text(if (record.fileState != CaptureFileState.AVAILABLE) captureFileMessage(record.fileState)
            else if (failed) "原片不可用或已变化" else "加载成片…", Modifier.padding(16.dp))
    }
}

private fun captureFileMessage(state: CaptureFileState): String = when (state) {
    CaptureFileState.CAPTURING -> "正在拍摄／归档"
    CaptureFileState.AVAILABLE -> "原片已持久保存"
    CaptureFileState.INTERRUPTED -> "拍摄中断，没有已确认的完整成片；未完成文件未自动删除"
    CaptureFileState.UNAVAILABLE -> "原片缺失或不完整"
    CaptureFileState.DELETING -> "删除未完成，可重试或在下次启动继续清理"
}
