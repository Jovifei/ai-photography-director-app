package com.jovi.photoai.ui.reference

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.ReferencePhotoCard
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

data class ReferenceLibraryEntry(
    val photo: ReferencePhoto,
    val bundle: ReferenceBundle,
    val imageFileName: String,
)

/** Durable app-private references. The original Photo Picker Uri never reaches this UI. */
@Composable
fun ReferenceLibraryScreen(
    entries: List<ReferenceLibraryEntry>,
    onBack: () -> Unit,
    onImportReference: () -> Unit,
    onOpenReference: (String) -> Unit,
    onDeleteReference: (String) -> Unit,
    onClearAll: () -> Unit,
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
            TextButton(onClick = onClearAll, modifier = Modifier.align(Alignment.End)) {
                Text("清空全部")
            }
            entries.forEach { entry ->
                ReferencePhotoCard(
                    title = entry.photo.title,
                    subtitle = "${entry.bundle.scene} · ${entry.bundle.lighting}\n${entry.bundle.composition}",
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
                TextButton(onClick = { onDeleteReference(entry.photo.id) }, modifier = Modifier.align(Alignment.End)) {
                    Text("删除此参考图")
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
}
