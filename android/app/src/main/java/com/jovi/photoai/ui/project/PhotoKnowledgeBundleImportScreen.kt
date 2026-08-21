package com.jovi.photoai.ui.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jovi.photoai.data.reference.KnowledgeBundleApplyErrorCode
import com.jovi.photoai.data.reference.KnowledgeBundleImportUiState
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleErrorCode
import com.jovi.photoai.data.reference.PhotographyProject
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.isKnowledgeBundleTargetEligible
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoKnowledgeBundleImportScreen(
    project: PhotographyProject,
    records: List<ReferenceRecord>,
    state: KnowledgeBundleImportUiState,
    onDocumentSelected: (android.net.Uri?) -> Unit,
    onBind: (producerReferenceId: String, localReferenceId: String) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val documentPicker = rememberLauncherForActivityResult(OpenDocument(), onDocumentSelected)
    val eligibleRecords = records.filter { isKnowledgeBundleTargetEligible(it.analysisStatus) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
    ) {
        TextButton(onClick = onBack) { Text("返回项目") }
        Text("导入离线知识包", style = MaterialTheme.typography.displaySmall)
        Text(
            "只读取你通过系统文件选择器明确选择的结构化 JSON。不会读取照片、路径、EXIF，也不会按顺序猜测照片对应关系。",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space16)) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                GlassPill(text = "${project.title} · 用户逐条确认")
                if (state.bundle == null && state.appliedCount == null) {
                    PrimaryActionButton(
                        text = if (state.isReading) "正在验证知识包" else "选择 JSON 知识包",
                        onClick = { documentPicker.launch(arrayOf("application/json", "text/json")) },
                        enabled = !state.isReading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.parseError?.let {
                    Text(bundleParseErrorText(it), color = AppColors.Warning, style = MaterialTheme.typography.bodyMedium)
                    SecondaryActionButton("重新选择", onReset, Modifier.fillMaxWidth())
                }
                state.applyError?.let {
                    Text(bundleApplyErrorText(it), color = AppColors.Warning, style = MaterialTheme.typography.bodyMedium)
                }
                state.appliedCount?.let { count ->
                    Text("已将 $count 条知识逐张写入项目。", style = MaterialTheme.typography.titleMedium)
                    Text("项目级语义汇总和模型推荐主参考未由此知识包提供；请手动选择主参考。", style = MaterialTheme.typography.bodyMedium)
                    PrimaryActionButton("返回项目看板", onBack, Modifier.fillMaxWidth())
                }
            }
        }

        state.bundle?.let { bundle ->
            Text(
                "已验证 ${bundle.references.size} 条 · ${bundle.source.origin.name} · ${bundle.source.releaseId}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "以下每一条都必须绑定到一张尚未 READY、且未在本次使用的项目照片。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            bundle.references.forEachIndexed { index, item ->
                GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space12)) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                        Text("知识条目 ${index + 1} · ${item.photography.scene}", style = MaterialTheme.typography.titleMedium)
                        Text(item.photography.lighting, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
                            verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
                        ) {
                            eligibleRecords.forEach { record ->
                                val selected = state.bindings[item.referenceId] == record.photo.id
                                val usedByOther = state.bindings.any { (producerId, localId) ->
                                    producerId != item.referenceId && localId == record.photo.id
                                }
                                FilterChip(
                                    selected = selected,
                                    enabled = !usedByOther && !state.isApplying,
                                    onClick = { onBind(item.referenceId, record.photo.id) },
                                    label = { Text("第 ${record.ordinal + 1} 张") },
                                    modifier = Modifier
                                        .heightIn(min = AppDimensions.MinTouchTarget)
                                        .semantics {
                                            contentDescription = "将知识条目 ${index + 1} 绑定到项目照片第 ${record.ordinal + 1} 张"
                                        },
                                )
                            }
                        }
                    }
                }
            }
            if (eligibleRecords.size < bundle.references.size) {
                Text("可绑定照片不足；READY 或分析中的照片不会被覆盖。", color = AppColors.Warning)
            }
            PrimaryActionButton(
                text = if (state.isApplying) "正在原子写入" else "确认全部绑定并导入",
                onClick = onApply,
                enabled = state.isComplete && !state.isApplying,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryActionButton("放弃本次知识包", onReset, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(AppDimensions.Space24))
    }
}

private fun bundleParseErrorText(code: PhotoKnowledgeBundleErrorCode): String = when (code) {
    PhotoKnowledgeBundleErrorCode.DOCUMENT_NOT_SELECTED -> "未选择知识包；项目没有变化。"
    PhotoKnowledgeBundleErrorCode.DOCUMENT_TOO_LARGE -> "知识包超过 512 KiB，已拒绝导入。"
    PhotoKnowledgeBundleErrorCode.INVALID_UTF8 -> "知识包不是受支持的无 BOM UTF-8 JSON。"
    PhotoKnowledgeBundleErrorCode.VERSION_UNSUPPORTED -> "知识包版本不受支持。"
    PhotoKnowledgeBundleErrorCode.DIGEST_MISMATCH -> "知识包完整性摘要不匹配，已拒绝导入。"
    else -> "知识包格式或字段无效，项目没有变化。"
}

private fun bundleApplyErrorText(code: KnowledgeBundleApplyErrorCode): String = when (code) {
    KnowledgeBundleApplyErrorCode.BUNDLE_INTEGRITY_INVALID -> "知识包完整性已失效，项目没有变化。"
    KnowledgeBundleApplyErrorCode.PROJECT_NOT_FOUND -> "当前项目已不可用。"
    KnowledgeBundleApplyErrorCode.BINDING_INCOMPLETE -> "请为每条知识明确选择一张照片。"
    KnowledgeBundleApplyErrorCode.BINDING_DUPLICATE -> "同一张照片不能绑定多条知识。"
    KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND -> "项目照片已发生变化，请重新选择。"
    KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE -> "目标照片已 READY 或正在分析，未覆盖任何结果。"
    KnowledgeBundleApplyErrorCode.DATABASE_COMMIT_FAILED -> "知识包未能完整写入，项目保持原状。"
}
