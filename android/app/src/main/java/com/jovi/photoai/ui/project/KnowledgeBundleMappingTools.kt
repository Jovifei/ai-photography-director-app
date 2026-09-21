package com.jovi.photoai.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.isKnowledgeBundleTargetEligible
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.reference.PrivateReferenceImage

/** Preview existing private derivatives only. No original media access or inferred binding. */
@Composable
internal fun KnowledgeBundleMappingTools(
    scopeKey: String,
    item: PhotoKnowledgeBundleItem,
    records: List<ReferenceRecord>,
    bindings: Map<String, String>,
    busy: Boolean,
    onBind: (String, String) -> Unit,
) {
    var choosing by remember(scopeKey, item.referenceId) { mutableStateOf(false) }
    var details by remember(scopeKey, item.referenceId) { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) { choosing = false; details = false } }
    val selectedId = bindings[item.referenceId]
    val selected = records.firstOrNull { it.photo.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { details = true }, enabled = !busy,
            modifier = Modifier.testTag("bundle-details-${item.referenceId}")) {
            Text("查看完整指导（9 项）")
        }
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivateReferenceImage(selected.imageFileName, "当前绑定照片第 ${selected.ordinal + 1} 张",
                    Modifier.width(72.dp).height(96.dp), contentScale = ContentScale.Fit)
                Text("已绑定：第 ${selected.ordinal + 1} 张", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (selectedId != null) {
            Text("原绑定照片已不可用，请解除后重新选择。", color = AppColors.Warning)
        }
        TextButton(onClick = { choosing = true }, enabled = !busy && records.isNotEmpty(),
            modifier = Modifier.testTag("bundle-choose-${item.referenceId}")) {
            Text(if (selectedId == null) "选择照片（看图绑定）" else "更换绑定照片")
        }
        if (selectedId != null) {
            TextButton(onClick = { onBind(item.referenceId, selectedId) }, enabled = !busy,
                modifier = Modifier.testTag("bundle-unbind-${item.referenceId}")) { Text("解除当前绑定") }
        }
    }
    if (details && !busy) {
        val photo = item.photography
        val fields = listOf(
            "场景" to photo.scene, "背景" to photo.backgroundStory, "光线" to photo.lighting,
            "构图" to photo.composition, "主体意图" to photo.subjectIntent, "情绪" to photo.emotion,
            "姿态意图" to photo.poseTemplate, "机位" to photo.cameraPosition, "拍摄指令" to photo.directorPrompt,
        )
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("完整摄影指导") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("内容来自所选知识包；不是当前照片或现场的重新分析。")
                    fields.forEach { (label, value) ->
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = false }) { Text("关闭指导详情") } },
        )
    }
    if (choosing && !busy) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("选择当前项目照片") },
            text = {
                // Decode only visible rows, not 20 x 20 thumbnails in the parent screen.
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("bundle-target-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    this.item { Text("请按内容手动对应；不会按编号或文件顺序自动匹配。") }
                    items(records, key = { it.photo.id }) { record ->
                        val id = record.photo.id
                        val usedElsewhere = bindings.any { (producer, local) -> producer != item.referenceId && local == id }
                        val eligible = isKnowledgeBundleTargetEligible(record.analysisStatus) && record.knowledgeBundleProvenance == null
                        val enabled = eligible && !usedElsewhere
                        Row(
                            Modifier.fillMaxWidth().testTag("bundle-target-$id")
                                .selectable(selected = selectedId == id, enabled = enabled, role = Role.RadioButton,
                                    onClick = { onBind(item.referenceId, id); choosing = false })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PrivateReferenceImage(record.imageFileName, "候选照片第 ${record.ordinal + 1} 张",
                                Modifier.width(64.dp).height(80.dp), contentScale = ContentScale.Fit)
                            Column(Modifier.weight(1f)) {
                                Text("第 ${record.ordinal + 1} 张")
                                if (usedElsewhere) Text("已绑定其他条目", color = AppColors.TextSecondary)
                                else if (!eligible) Text("已有结果或正在分析，不可覆盖", color = AppColors.TextSecondary)
                                else Text("点击选择；已选项再次点击可解除", style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected = selectedId == id, onClick = null, enabled = enabled)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("取消选择") } },
        )
    }
}
