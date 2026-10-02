package com.jovi.photoai.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.ReferencePhotoCard
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.home.ProjectReferenceFilters

data class ReferenceLibraryEntry(
    val photo: ReferencePhoto,
    val bundle: ReferenceBundle,
    val imageFileName: String,
    val projectId: String? = null,
    val projectTitle: String? = null,
    val analysisStatus: PhotoAnalysisStatus? = null,
)

private sealed interface PendingReferenceDeletion {
    data object ClearAll : PendingReferenceDeletion
    data class Single(val id: String) : PendingReferenceDeletion
}

/** Durable app-private references. The original Photo Picker Uri never reaches this UI. */
@Composable
internal fun ReferenceLibraryScreen(
    entries: List<ReferenceLibraryEntry>,
    onBack: () -> Unit,
    onImportReference: () -> Unit,
    onOpenReference: (String) -> Unit,
    onDeleteReference: (String) -> Unit,
    onClearAll: () -> Unit,
    visibleEntries: List<ReferenceLibraryEntry> = entries,
    filters: ProjectReferenceFilters = ProjectReferenceFilters(),
    onFiltersChange: (ProjectReferenceFilters) -> Unit = {},
    onOpenProject: (String) -> Unit = {},
) {
    var pendingDeletion by remember { mutableStateOf<PendingReferenceDeletion?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.PagePadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AppDimensions.Space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            GlassPill(text = "本地私有 · 可备份")
        }
        Spacer(Modifier.height(AppDimensions.Space16))
        Text("参考图库", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "导入后会保存为去除 EXIF 的应用私有派生图。删除会移除当前设备上的记录和派生图；旧云备份按系统保留策略过期。",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space20))

        if (entries.isEmpty()) {
            EmptyState(
                title = "还没有参考图",
                message = "导入一张喜欢的照片，先体验 Reference → Director 流程。",
                actionLabel = "导入参考图",
                onAction = onImportReference,
            )
        } else {
            OutlinedTextField(
                value = filters.query,
                onValueChange = { onFiltersChange(filters.copy(query = it)) },
                label = { Text("搜索照片、场景、来源或项目") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("reference-library-search"),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
            LibraryFilterRow(
                label = "场景",
                options = entries.map { it.bundle.scene }.distinct().sorted(),
                selected = filters.scene,
                onSelect = { onFiltersChange(filters.copy(scene = it)) },
            )
            LibraryFilterRow(
                label = "来源",
                options = entries.map { it.photo.sourceLabel }.distinct().sorted(),
                selected = filters.source,
                onSelect = { onFiltersChange(filters.copy(source = it)) },
            )
            LibraryFilterRow(
                label = "状态",
                options = entries.mapNotNull { it.analysisStatus }.distinct().map { it.name },
                selected = filters.status?.name,
                optionLabel = { PhotoAnalysisStatus.valueOf(it).libraryLabel() },
                onSelect = { option ->
                    onFiltersChange(filters.copy(status = option?.let { PhotoAnalysisStatus.valueOf(it) }))
                },
            )
            val projectOptions = entries.mapNotNull { entry ->
                entry.projectId?.let { id -> id to (entry.projectTitle ?: "未命名项目") }
            }.distinctBy { it.first }
            if (projectOptions.isNotEmpty()) {
                Text("项目", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                    FilterChip(selected = filters.projectId == null,
                        onClick = { onFiltersChange(filters.copy(projectId = null)) }, label = { Text("全部") })
                    projectOptions.forEach { (id, title) ->
                        FilterChip(selected = filters.projectId == id,
                            onClick = { onFiltersChange(filters.copy(projectId = id)) }, label = { Text(title) })
                    }
                }
            }
            TextButton(onClick = { pendingDeletion = PendingReferenceDeletion.ClearAll }, modifier = Modifier.align(Alignment.End)) {
                Text("清空全部")
            }
            if (visibleEntries.isEmpty()) {
                EmptyState(
                    title = "没有符合条件的参考图",
                    message = "请调整搜索词或筛选条件。",
                    actionLabel = "清除筛选",
                    onAction = { onFiltersChange(ProjectReferenceFilters()) },
                )
            }
            visibleEntries.forEach { entry ->
                ReferencePhotoCard(
                    title = entry.photo.title,
                    subtitle = "${entry.bundle.scene} · ${entry.bundle.lighting}\n${entry.bundle.composition}\n" +
                        "${entry.projectTitle ?: "未归属项目"} · ${entry.analysisStatus?.libraryLabel() ?: "状态待确认"}",
                    badge = entry.photo.sourceLabel,
                    image = {
                        PrivateReferenceImage(
                            imageFileName = entry.imageFileName,
                            contentDescription = "本地私有参考图：${entry.photo.title}",
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    onClick = { onOpenReference(entry.photo.id) },
                )
                Row(modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                    entry.projectId?.takeIf { entry.projectTitle != null }?.let { projectId ->
                        TextButton(onClick = { onOpenProject(projectId) }) { Text("打开所属项目") }
                    }
                    TextButton(onClick = { pendingDeletion = PendingReferenceDeletion.Single(entry.photo.id) }) {
                        Text("删除此参考图")
                    }
                }
                Spacer(Modifier.height(AppDimensions.Space12))
            }
        }
        Spacer(Modifier.height(AppDimensions.Space20))
        PrimaryActionButton(
            text = "导入另一张参考图",
            onClick = onImportReference,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppDimensions.Space32))
    }

    pendingDeletion?.let { deletion ->
        val clearAll = deletion is PendingReferenceDeletion.ClearAll
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(if (clearAll) "清空全部参考图？" else "删除此参考图？") },
            text = {
                Text(
                    if (clearAll) {
                        "这会删除当前设备上的全部参考记录和私有派生图。"
                    } else {
                        "这会删除当前设备上的参考记录和私有派生图。"
                    },
                )
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("取消") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        when (deletion) {
                            PendingReferenceDeletion.ClearAll -> onClearAll()
                            is PendingReferenceDeletion.Single -> onDeleteReference(deletion.id)
                        }
                    },
                ) {
                    Text(if (clearAll) "确认清空" else "确认删除")
                }
            },
        )
    }
}

@Composable
private fun LibraryFilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    optionLabel: (String) -> String = { it },
) {
    if (options.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) },
            modifier = Modifier.testTag("reference-filter-$label-all"), label = { Text("全部") })
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) },
                modifier = Modifier.testTag("reference-filter-$label-$option"),
                label = { Text(optionLabel(option)) })
        }
    }
}

private fun PhotoAnalysisStatus.libraryLabel(): String = when (this) {
    PhotoAnalysisStatus.EXAMPLE_GUIDANCE -> "示例指导"
    PhotoAnalysisStatus.IMPORTED -> "已导入"
    PhotoAnalysisStatus.QUEUED -> "等待分析"
    PhotoAnalysisStatus.RUNNING -> "分析中"
    PhotoAnalysisStatus.READY -> "分析可用"
    PhotoAnalysisStatus.FAILED -> "分析失败"
    PhotoAnalysisStatus.CANCELLED -> "已取消"
    PhotoAnalysisStatus.UNAVAILABLE -> "分析不可用"
}
