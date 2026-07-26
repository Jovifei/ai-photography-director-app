package com.jovi.photoai.ui.reference

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.jovi.photoai.reference.ReferencePhoto
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.ReferencePhotoCard
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.importphoto.ReferencePreviewState
import com.jovi.photoai.ui.importphoto.decodeSampledBitmap

/** UI-session only. The Uri is not put in ReferenceBundle or persisted anywhere. */
data class ReferenceLibraryEntry(
    val photo: ReferencePhoto,
    val uri: Uri,
)

/** In-memory Reference Library. It deliberately does not persist Photo Picker Uris across sessions. */
@Composable
fun ReferenceLibraryScreen(
    entries: List<ReferenceLibraryEntry>,
    onBack: () -> Unit,
    onImportReference: () -> Unit,
    onOpenReference: (String) -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimensions.Space12),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            GlassPill(text = "仅本次会话")
        }
        Spacer(Modifier.height(AppDimensions.Space16))
        Text("参考图库", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "这里仅列出本次会话中已完成 Demo Analysis 的参考图。不会保存图片或上传图片。",
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
            entries.forEachIndexed { index, entry ->
                val reference = entry.photo
                ReferencePhotoCard(
                    title = reference.title,
                    subtitle = reference.description,
                    badge = "Demo Analysis",
                    image = { SessionReferenceThumbnail(uri = entry.uri, index = index) },
                    onClick = { onOpenReference(reference.id) },
                )
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

@Composable
private fun SessionReferenceThumbnail(uri: Uri, index: Int) {
    val context = LocalContext.current
    var previewState by remember(uri) { mutableStateOf<ReferencePreviewState>(ReferencePreviewState.Loading) }
    LaunchedEffect(uri) {
        previewState = decodeSampledBitmap(context.contentResolver, uri, maxDimensionPx = 720)
    }
    val ready = previewState as? ReferencePreviewState.Ready
    if (ready != null) {
        Image(
            bitmap = ready.bitmap.asImageBitmap(),
            contentDescription = "本次会话选择的参考照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (index % 2 == 0) {
                            listOf(AppColors.AccentBlueSoft, AppColors.CameraGlassDark)
                        } else {
                            listOf(AppColors.Warning.copy(alpha = 0.24f), AppColors.TextSecondary)
                        },
                    ),
                ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                "SESSION  /  REFERENCE",
                modifier = Modifier.padding(AppDimensions.Space16),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.CameraText,
            )
        }
    }
}
