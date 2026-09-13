package com.jovi.photoai.ui.project

import androidx.activity.compose.BackHandler
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
    val eligibleIds = eligibleRecords.map { it.photo.id }.toSet()
    val mappedTargetsAvailable = state.bindings.values.all { it in eligibleIds }
    // Always delegate to the parent's synchronous tryLeave gate, including the same frame
    // that apply() begins. An observed isApplying value may still be from the previous frame.
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
    ) {
        TextButton(onClick = onBack, enabled = !state.isApplying) { Text("返回项目") }
        Text("导入离线知识包", style = MaterialTheme.typography.displaySmall)
        Text(
            "只读取你通过系统文件选择器明确选择的结构化 JSON。不会读取照片、路径、EXIF，也不会按顺序猜测照片对应关系。",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space16)) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                GlassPill(text = "${project.title} · 用户逐条确认")
                if (state.bundle == null && state.appliedCount == null && !state.applyOutcomeUnknown) {
                    PrimaryActionButton(
                        text = if (state.isReading) "正在验证知识包" else "选择 JSON 知识包",
                        onClick = { documentPicker.launch(arrayOf("application/json", "text/json")) },
                        enabled = !state.isReading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.isReading) {
                    SecondaryActionButton("取消读取", onReset, Modifier.fillMaxWidth())
                }
                if (state.applyOutcomeUnknown) {
                    Text("提交结果暂时无法确认；请返回项目看板检查，不要立即重复导入。", color = AppColors.Warning)
                    PrimaryActionButton("返回项目看板", onBack, Modifier.fillMaxWidth())
                }
                state.parseError?.let {
                    Text(bundleParseErrorText(it), color = AppColors.Warning, style = MaterialTheme.typography.bodyMedium)
                    SecondaryActionButton("重新选择", { documentPicker.launch(arrayOf("application/json", "text/json")) }, Modifier.fillMaxWidth())
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
                "格式与摘要校验通过 · ${bundle.references.size} 条",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("来源：${bundle.source.origin.name} · ${bundle.source.producerId}", style = MaterialTheme.typography.bodySmall)
            Text("版本：${bundle.contractVersion} · ${bundle.source.releaseId}", style = MaterialTheme.typography.bodySmall)
            Text("知识包：${bundle.bundleId}", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256：${bundle.payloadSha256}", style = MaterialTheme.typography.bodySmall)
            Text(
                "摘要一致不代表发布者身份认证或内容质量审核。请只导入你确认来源的知识包。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Warning,
            )
            Text("已绑定 ${state.bindings.size} / ${bundle.references.size} 条", style = MaterialTheme.typography.titleMedium)
            Text(
                "每条知识须绑定一张尚未 READY、也不在分析中的照片。再次点击已选照片可解除绑定。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            bundle.references.forEachIndexed { index, item ->
                GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space12)) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                        Text("知识条目 ${index + 1} · ${item.photography.scene}", style = MaterialTheme.typography.titleMedium)
                        Text(item.photography.lighting, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text(item.photography.directorPrompt, style = MaterialTheme.typography.bodyMedium)
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
                                            contentDescription = if (selected) {
                                                "解除知识条目 ${index + 1} 与项目照片第 ${record.ordinal + 1} 张的绑定"
                                            } else {
                                                "将知识条目 ${index + 1} 绑定到项目照片第 ${record.ordinal + 1} 张"
                                            }
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
            if (!mappedTargetsAvailable) {
                Text("已选照片已删除或状态已变化，请重新绑定。", color = AppColors.Warning)
            }
            PrimaryActionButton(
                text = if (state.isApplying) "正在原子写入" else "确认全部绑定并导入",
                onClick = onApply,
                enabled = state.isComplete && mappedTargetsAvailable && !state.isApplying && !state.isReading,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isApplying) {
                Text("写入期间暂不退出，完成后将显示结果。", style = MaterialTheme.typography.bodyMedium)
            } else {
                SecondaryActionButton("放弃本次知识包", onReset, Modifier.fillMaxWidth())
            }
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
    PhotoKnowledgeBundleErrorCode.DOCUMENT_UNAVAILABLE -> "无法读取该文件，请重新选择可访问的 JSON；项目没有变化。"
    PhotoKnowledgeBundleErrorCode.DOCUMENT_EMPTY -> "所选文件为空；请选择完整的知识包。"
    PhotoKnowledgeBundleErrorCode.MALFORMED_JSON -> "JSON 语法、字符或嵌套无效；请重新导出标准知识包。"
    PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID -> "知识包字段不符合合同，或包含重复字段。"
    PhotoKnowledgeBundleErrorCode.FIELD_INVALID -> "知识包含不允许的字段值，已拒绝导入。"
    PhotoKnowledgeBundleErrorCode.DUPLICATE_REFERENCE_ID -> "知识条目标识重复，已拒绝导入。"
}

private fun bundleApplyErrorText(code: KnowledgeBundleApplyErrorCode): String = when (code) {
    KnowledgeBundleApplyErrorCode.BUNDLE_INTEGRITY_INVALID -> "知识包完整性已失效，项目没有变化。"
    KnowledgeBundleApplyErrorCode.PROJECT_NOT_FOUND -> "当前项目已不可用。"
    KnowledgeBundleApplyErrorCode.BINDING_INCOMPLETE -> "请为每条知识明确选择一张照片。"
    KnowledgeBundleApplyErrorCode.BINDING_DUPLICATE -> "同一张照片不能绑定多条知识。"
    KnowledgeBundleApplyErrorCode.REFERENCE_NOT_FOUND -> "项目照片已发生变化，请重新选择。"
    KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE -> "目标照片已 READY 或正在分析，未覆盖任何结果。"
    KnowledgeBundleApplyErrorCode.DATABASE_COMMIT_FAILED -> "提交结果无法确认；请返回项目核查，不要立即重复导入。"
}
