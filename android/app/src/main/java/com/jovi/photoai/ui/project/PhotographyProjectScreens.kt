package com.jovi.photoai.ui.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jovi.photoai.data.reference.BatchImportUiState
import com.jovi.photoai.data.reference.MAX_PROJECT_PHOTOS
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PersistedProjectSummary
import com.jovi.photoai.data.reference.PhotographyProject
import com.jovi.photoai.data.reference.ReferenceImportErrorCode
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.acceptedBatchCount
import com.jovi.photoai.data.reference.projectSummaryOf
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.reference.PrivateReferenceImage

internal data class ProjectHomeItem(
    val project: PhotographyProject,
    val photoCount: Int,
)

/** The product root is an editorial contact sheet: work first, examples later. */
@Composable
internal fun ProjectsHomeScreen(
    projects: List<ProjectHomeItem>,
    onCreateProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.PagePadding),
    ) {
        Spacer(Modifier.height(AppDimensions.Space12))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("拍摄项目", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary)
                Text("先整理素材，再带着方向开拍", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            TextButton(onClick = onOpenCapture) { Text("拍摄") }
        }
        Spacer(Modifier.height(AppDimensions.Space20))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimensions.RadiusExtraLarge),
            contentPadding = PaddingValues(AppDimensions.CardPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12)) {
                GlassPill(text = "20 张 / 项目 · 逐张处理")
                Text("把一组照片变成一次可执行的拍摄准备", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "每张照片单独私有导入并保留独立状态；项目汇总只会使用未来真实分析完成的结果。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
                PrimaryActionButton(
                    text = "新建拍摄项目",
                    onClick = onCreateProject,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(AppDimensions.Space24))
        Text("继续项目", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(AppDimensions.Space12))
        if (projects.isEmpty()) {
            EmptyState(
                title = "还没有拍摄项目",
                message = "新建项目后，一次可导入并整理最多 20 张照片。",
                actionLabel = "新建拍摄项目",
                onAction = onCreateProject,
            )
        } else {
            projects.forEach { item ->
                ProjectHomeCard(item, onOpen = { onOpenProject(item.project.id) })
                Spacer(Modifier.height(AppDimensions.Space12))
            }
        }
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun ProjectHomeCard(item: ProjectHomeItem, onOpen: () -> Unit) {
    val project = item.project
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "拍摄项目 ${project.title}，${item.photoCount} 张照片" },
        contentPadding = PaddingValues(AppDimensions.Space16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.photoCount}/$MAX_PROJECT_PHOTOS 张已私有导入" +
                        if (project.failedImportCount > 0) " · ${project.failedImportCount} 项未导入" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
            GlassPill(text = if (project.primaryReferenceId == null) "待选主参考" else "主参考已选")
        }
    }
}

@Composable
internal fun BatchProjectImportScreen(
    project: PhotographyProject,
    records: List<ReferenceRecord>,
    batchState: BatchImportUiState,
    onPickerResults: (List<android.net.Uri>) -> Unit,
    onPickerCancelled: () -> Unit,
    onCancelImport: () -> Unit,
    onBack: () -> Unit,
    onOpenBoard: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
) {
    var pendingDeletionId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val remaining = (MAX_PROJECT_PHOTOS - records.size).coerceAtLeast(0)
    val picker = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(remaining.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isEmpty()) onPickerCancelled() else onPickerResults(uris.take(acceptedBatchCount(uris.size, remaining)))
    }
    val singlePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) onPickerCancelled() else onPickerResults(listOf(uri))
    }
    val openPicker = {
        if (remaining > 1 && !batchState.isImporting) {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        } else if (remaining == 1 && !batchState.isImporting) {
            singlePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }

    ProjectScreenScaffold(
        title = "添加项目照片",
        project = project,
        onBack = onBack,
    ) {
        Text(
            "${records.size}/$MAX_PROJECT_PHOTOS 张已私有导入",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "系统选择器只交给 App 你本次选择的照片。每张会立即、依次转为去元数据的私有 JPEG；不会申请相册权限或保留来源 URI。",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space16))
        ImportProgressCard(batchState)
        if (batchState.isImporting) {
            Spacer(Modifier.height(AppDimensions.Space8))
            SecondaryActionButton(
                text = "停止导入",
                onClick = onCancelImport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(AppDimensions.Space16))
        if (records.isEmpty()) {
            EmptyState(
                title = "从一组照片开始",
                message = "可一次选择多张，也可随时继续添加；每张照片互不覆盖。",
            )
        } else {
            ProjectPhotoGrid(
                records = records,
                primaryReferenceId = project.primaryReferenceId,
                onOpenPhoto = onOpenPhoto,
                onDeletePhoto = { pendingDeletionId = it },
            )
        }
        Spacer(Modifier.height(AppDimensions.Space20))
        if (remaining > 0) {
            PrimaryActionButton(
                text = when {
                    records.isEmpty() -> "选择照片"
                    batchState.failedCount > 0 -> "重新选择未导入照片（剩余 $remaining 张）"
                    else -> "继续添加照片（剩余 $remaining 张）"
                },
                onClick = openPicker,
                enabled = !batchState.isImporting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
        }
        SecondaryActionButton(
            text = if (records.isEmpty()) "稍后添加" else "进入项目看板",
            onClick = onOpenBoard,
            modifier = Modifier.fillMaxWidth(),
            enabled = !batchState.isImporting,
        )
    }
    pendingDeletionId?.let { id ->
        ProjectPhotoDeletionDialog(
            onDismiss = { pendingDeletionId = null },
            onConfirm = {
                pendingDeletionId = null
                onDeletePhoto(id)
            },
        )
    }
}

@Composable
internal fun ProjectBoardScreen(
    project: PhotographyProject,
    records: List<ReferenceRecord>,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onSelectPrimary: (String?) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onOpenSummary: () -> Unit,
    onStartShooting: () -> Unit,
    onStartAnalysis: () -> Unit,
    onCancelAnalysis: () -> Unit,
    analysisInProgress: Boolean = false,
    analysisServiceConnected: Boolean = false,
) {
    var pendingDeletionId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var statusFilterName by rememberSaveable(project.id) { mutableStateOf(ProjectStatusFilter.ALL.name) }
    val statusFilter = ProjectStatusFilter.valueOf(statusFilterName)
    val filteredRecords = records.filter(statusFilter::matches)
    val primaryStatus = records.firstOrNull { it.photo.id == project.primaryReferenceId }?.analysisStatus
    ProjectScreenScaffold(title = "项目看板", project = project, onBack = onBack) {
        Text("${records.size}/$MAX_PROJECT_PHOTOS 张照片", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(AppDimensions.Space4))
        Text(
            buildString {
                append("逐张状态独立保存")
                if (project.failedImportCount > 0) append(" · ${project.failedImportCount} 项未导入，可继续添加")
            },
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(AppDimensions.Space16))
        if (!analysisServiceConnected) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space16)) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
                    GlassPill(text = "本机分析服务未连接")
                    Text("可继续整理项目或无 AI 指导拍摄", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "连接服务后才会逐张产生真实 READY 结果、项目汇总和 AI Camera Director 指导。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(AppDimensions.Space16))
        }
        if (records.isEmpty()) {
            EmptyState(
                title = "项目还没有照片",
                message = "先从系统照片选择器添加 1 到 $MAX_PROJECT_PHOTOS 张照片。",
                actionLabel = "添加照片",
                onAction = onAddPhotos,
            )
        } else {
            ProjectStatusFilters(
                selected = statusFilter,
                onSelect = { statusFilterName = it.name },
            )
            Spacer(Modifier.height(AppDimensions.Space12))
            if (filteredRecords.isEmpty()) {
                EmptyState(
                    title = "没有符合筛选的照片",
                    message = "切换为“全部”可查看所有已私有导入的项目照片。",
                )
            } else {
            ProjectPhotoGrid(
                records = filteredRecords,
                primaryReferenceId = project.primaryReferenceId,
                onOpenPhoto = onOpenPhoto,
                onSelectPrimary = onSelectPrimary,
                onDeletePhoto = { pendingDeletionId = it },
            )
            }
        }
        Spacer(Modifier.height(AppDimensions.Space20))
        if (records.isNotEmpty() && records.size < MAX_PROJECT_PHOTOS) {
            SecondaryActionButton("继续添加照片", onAddPhotos, Modifier.fillMaxWidth())
            Spacer(Modifier.height(AppDimensions.Space12))
        }
        if (records.isNotEmpty()) {
            PrimaryActionButton(
                text = if (analysisInProgress) "停止逐张分析" else "连接本机并逐张分析",
                onClick = if (analysisInProgress) onCancelAnalysis else onStartAnalysis,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
            PrimaryActionButton(
                text = "查看项目汇总",
                onClick = onOpenSummary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
        }
        SecondaryActionButton(
            text = projectShootingActionLabel(primaryStatus),
            onClick = onStartShooting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    pendingDeletionId?.let { id ->
        ProjectPhotoDeletionDialog(
            onDismiss = { pendingDeletionId = null },
            onConfirm = {
                pendingDeletionId = null
                onDeletePhoto(id)
            },
        )
    }
}

@Composable
internal fun ProjectSummaryScreen(
    project: PhotographyProject,
    records: List<ReferenceRecord>,
    persistedSummary: PersistedProjectSummary? = null,
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onSelectPrimary: (String?) -> Unit,
    onStartShooting: () -> Unit,
    analysisServiceConnected: Boolean = false,
) {
    val summary = projectSummaryOf(records, project.failedImportCount, project.primaryReferenceId)
    val primaryStatus = records.firstOrNull { it.photo.id == project.primaryReferenceId }?.analysisStatus
    ProjectScreenScaffold(title = "项目汇总", project = project, onBack = onBack) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space16)) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                if (summary.providerReadyCount == 0) {
                    GlassPill(text = "真实分析尚未接入")
                    Text("还不能生成图片内容汇总", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (analysisServiceConnected) {
                            "本项目还没有完成的 READY 结果；当前照片未参与汇总。"
                        } else {
                            "本机分析服务未连接；本项目的 ${summary.importedCount} 张私有照片当前未参与汇总。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                    )
                    Text(
                        "可继续整理项目，或使用主参考进入无 AI 指导拍摄；不会把示例内容混入项目结论。",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.AccentBlue,
                    )
                } else {
                    GlassPill(text = "${summary.providerReadyCount} 张真实分析已汇总")
                    Text("项目拍摄方向", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "只组合 ${summary.providerReadyCount} 张已完成的真实 Provider 结果；" +
                            "${summary.excludedCount} 项未纳入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                    )
                    if (persistedSummary?.status == com.jovi.photoai.data.reference.ProjectSummaryStatus.SUCCESS) {
                        persistedSummary.commonSceneDirection?.let { Text("共同场景：$it", style = MaterialTheme.typography.bodyMedium) }
                        persistedSummary.commonLightingDirection?.let { Text("共同光线：$it", style = MaterialTheme.typography.bodyMedium) }
                        persistedSummary.commonCompositionDirection?.let { Text("共同构图：$it", style = MaterialTheme.typography.bodyMedium) }
                        persistedSummary.commonSubjectDirection?.let { Text("共同主体：$it", style = MaterialTheme.typography.bodyMedium) }
                        if (persistedSummary.differences.isNotEmpty()) {
                            Text("逐张差异", style = MaterialTheme.typography.titleMedium)
                            persistedSummary.differences.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                        persistedSummary.photographerActionSummary?.let {
                            Text("摄影师行动摘要", style = MaterialTheme.typography.titleMedium)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        persistedSummary.recommendedPrimaryReference?.let { recommended ->
                            Text("模型推荐主参考：${recommended}", style = MaterialTheme.typography.bodyMedium)
                            if (project.primaryReferenceId != recommended) {
                                TextButton(onClick = { onSelectPrimary(recommended) }) { Text("采用模型推荐") }
                            }
                        }
                    }
                    if (persistedSummary?.status != com.jovi.photoai.data.reference.ProjectSummaryStatus.SUCCESS) {
                        GlassPill(text = "汇总不可用")
                        Text("已完成的逐张结果仍可查看；项目级汇总未通过 READY-only 校验。", style = MaterialTheme.typography.bodyMedium, color = AppColors.Warning)
                    }
                }
            }
        }
        Spacer(Modifier.height(AppDimensions.Space20))
        Text("选择一张主参考", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text("主参考只决定后续指导拍摄的入口；不会改变其他照片的独立状态。", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Spacer(Modifier.height(AppDimensions.Space12))
        ProjectPhotoGrid(
            records = records,
            primaryReferenceId = project.primaryReferenceId,
            onOpenPhoto = onOpenPhoto,
            onSelectPrimary = onSelectPrimary,
        )
        Spacer(Modifier.height(AppDimensions.Space20))
        PrimaryActionButton(
            text = projectShootingActionLabel(primaryStatus),
            onClick = onStartShooting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun projectShootingActionLabel(primaryStatus: PhotoAnalysisStatus?): String = when (primaryStatus) {
    null -> "选择拍摄方式"
    PhotoAnalysisStatus.READY -> "使用主参考进入 AI 拍摄"
    else -> "使用主参考进行无 AI 拍摄"
}

@Composable
private fun ProjectPhotoDeletionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这张项目照片？") },
        text = { Text("这会删除当前设备上的私有 JPEG 衍生图和项目记录，不影响其他照片。") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认删除") } },
    )
}

private enum class ProjectStatusFilter(val label: String) {
    ALL("全部"),
    EXAMPLE("示例"),
    IN_PROGRESS("处理中"),
    UNAVAILABLE("不可用"),
    READY("已分析");

    fun matches(record: ReferenceRecord): Boolean = when (this) {
        ALL -> true
        EXAMPLE -> record.analysisStatus == PhotoAnalysisStatus.EXAMPLE_GUIDANCE
        IN_PROGRESS -> record.analysisStatus in setOf(PhotoAnalysisStatus.IMPORTED, PhotoAnalysisStatus.QUEUED, PhotoAnalysisStatus.RUNNING)
        UNAVAILABLE -> record.analysisStatus in setOf(PhotoAnalysisStatus.FAILED, PhotoAnalysisStatus.CANCELLED, PhotoAnalysisStatus.UNAVAILABLE)
        READY -> record.analysisStatus == PhotoAnalysisStatus.READY
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectStatusFilters(
    selected: ProjectStatusFilter,
    onSelect: (ProjectStatusFilter) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
    ) {
        ProjectStatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                modifier = Modifier
                    .heightIn(min = AppDimensions.MinTouchTarget)
                    .semantics { contentDescription = "按${filter.label}状态筛选" },
            )
        }
    }
}

@Composable
private fun ProjectScreenScaffold(
    title: String,
    project: PhotographyProject,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
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
            GlassPill(text = project.title)
        }
        Spacer(Modifier.height(AppDimensions.Space16))
        Text(title, style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space20))
        content()
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun ImportProgressCard(state: BatchImportUiState) {
    if (!state.isImporting && !state.hasBatchResult) return
    GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space16)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
            Text(
                if (state.isImporting) "正在逐张私有导入" else if (state.wasCancelled) "本次导入已停止" else "本次导入完成",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "已处理 ${state.completedCount}/${state.requestedCount} · 成功 ${state.importedCount} · 未导入 ${state.failedCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            state.lastFailure?.let { code ->
                Text(importFailureText(code), style = MaterialTheme.typography.labelMedium, color = AppColors.Warning)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectPhotoGrid(
    records: List<ReferenceRecord>,
    primaryReferenceId: String?,
    onOpenPhoto: (String) -> Unit,
    onSelectPrimary: ((String?) -> Unit)? = null,
    onDeletePhoto: ((String) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 4 else 3
        val tileWidth = (maxWidth - AppDimensions.Space8 * (columns - 1)) / columns
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
        ) {
            records.forEach { record ->
                ProjectPhotoTile(
                    record = record,
                    isPrimary = record.photo.id == primaryReferenceId,
                    modifier = Modifier.width(tileWidth),
                    onOpen = { onOpenPhoto(record.photo.id) },
                    onSelectPrimary = onSelectPrimary?.let { callback ->
                        { callback(if (record.photo.id == primaryReferenceId) null else record.photo.id) }
                    },
                    onDelete = onDeletePhoto?.let { callback -> { callback(record.photo.id) } },
                )
            }
        }
    }
}

@Composable
private fun ProjectPhotoTile(
    record: ReferenceRecord,
    isPrimary: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onSelectPrimary: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val status = analysisStatusText(record.analysisStatus)
    GlassSurface(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "项目照片第 ${record.ordinal + 1} 张，$status" },
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        contentPadding = PaddingValues(AppDimensions.Space4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                PrivateReferenceImage(
                    imageFileName = record.imageFileName,
                    contentDescription = "项目私有照片第 ${record.ordinal + 1} 张",
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = if (isPrimary) "主参考" else status,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(AppDimensions.Space4),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.CameraText,
                )
            }
            Text(
                text = "第 ${record.ordinal + 1} 张",
                modifier = Modifier.padding(horizontal = AppDimensions.Space4),
                style = MaterialTheme.typography.labelMedium,
            )
            if (onSelectPrimary != null || onDelete != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (onSelectPrimary != null) {
                        TextButton(
                            onClick = onSelectPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = if (isPrimary) "取消主参考" else "设为主参考" },
                        ) {
                            Text(if (isPrimary) "取消" else "主图")
                        }
                    }
                    if (onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        ) { Text("删除") }
                    }
                }
            }
        }
    }
}

private fun analysisStatusText(status: PhotoAnalysisStatus): String = when (status) {
    PhotoAnalysisStatus.EXAMPLE_GUIDANCE -> "示例指导"
    PhotoAnalysisStatus.IMPORTED -> "已导入"
    PhotoAnalysisStatus.QUEUED -> "等待分析"
    PhotoAnalysisStatus.RUNNING -> "分析中"
    PhotoAnalysisStatus.READY -> "已分析"
    PhotoAnalysisStatus.FAILED -> "分析失败"
    PhotoAnalysisStatus.CANCELLED -> "已取消"
    PhotoAnalysisStatus.UNAVAILABLE -> "分析不可用"
}

private fun importFailureText(code: ReferenceImportErrorCode): String = when (code) {
    ReferenceImportErrorCode.PROJECT_LIMIT_REACHED -> "项目已达到 20 张上限。"
    ReferenceImportErrorCode.PROJECT_NOT_FOUND -> "当前项目不可用，请返回项目列表。"
    ReferenceImportErrorCode.IMAGE_TOO_LARGE -> "有照片尺寸过大，未被导入。"
    ReferenceImportErrorCode.SOURCE_EMPTY,
    ReferenceImportErrorCode.SOURCE_TRUNCATED,
    ReferenceImportErrorCode.UNSUPPORTED_IMAGE,
    ReferenceImportErrorCode.MIME_CONTENT_MISMATCH,
    ReferenceImportErrorCode.IMAGE_DECODE_FAILED -> "有照片无法安全导入，可重新选择。"
    else -> "有照片未能导入，可继续添加其他照片。"
}
