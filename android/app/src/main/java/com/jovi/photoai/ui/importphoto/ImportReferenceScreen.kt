package com.jovi.photoai.ui.importphoto

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

@Composable
fun ImportReferenceScreen(
    selectedUri: Uri?,
    onSelected: (Uri?) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    var previewState by remember(selectedUri) {
        mutableStateOf<ReferencePreviewState>(
            if (selectedUri == null) ReferencePreviewState.Empty else ReferencePreviewState.Loading,
        )
    }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) onSelected(uri)
    }
    val openPicker = {
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(selectedUri, isInspection) {
        previewState = if (selectedUri == null) {
            ReferencePreviewState.Empty
        } else if (isInspection) {
            ReferencePreviewState.PreviewSelected
        } else {
            ReferencePreviewState.Loading
            decodeSampledBitmap(context.contentResolver, selectedUri)
        }
    }

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
        Text(
            text = "导入参考图",
            style = MaterialTheme.typography.displaySmall,
            color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            text = "系统照片选择器只把你选择的这一张图片交给 App。UI0 不上传、不联网，也不持久保存 URI。",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space24))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
        ) {
            RecommendationCard(
                title = "推荐图片类型",
                detail = "单人、光线清楚、姿态完整、背景关系明确",
                modifier = Modifier.weight(1f),
            )
            RecommendationCard(
                title = "暂不建议类型",
                detail = "多人合照、严重模糊、拼图、带大量遮挡的图片",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(AppDimensions.Space16))

        when (val state = previewState) {
            ReferencePreviewState.Empty -> EmptyState(
                title = "还没有参考图",
                message = "选择一张能代表目标光线、姿态或构图的照片。",
                actionLabel = "打开系统照片选择器",
                onAction = openPicker,
            )

            ReferencePreviewState.Loading -> GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f),
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                contentPadding = PaddingValues(AppDimensions.CardPadding),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ReferencePreviewState.Failed -> EmptyState(
                title = "无法打开这张照片",
                message = state.message,
                actionLabel = "重新选择",
                onAction = openPicker,
            )

            ReferencePreviewState.PreviewSelected -> GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f),
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                contentPadding = PaddingValues(AppDimensions.Space8),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.AccentBlueSoft, AppColors.CameraGlassDark),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassPill(text = "已选择 · 设计预览")
                }
            }

            is ReferencePreviewState.Ready -> GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                contentPadding = PaddingValues(AppDimensions.Space8),
            ) {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "本次会话选择的参考照片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimensions.RadiusMedium),
            contentPadding = PaddingValues(AppDimensions.Space16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                Text(
                    text = "本页不会执行真实 AI 分析",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                )
                Text(
                    text = "继续后显示的是预置示例，用来验证产品流程与视觉层级。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        if (selectedUri != null) {
            SecondaryActionButton(
                text = "清除选择",
                onClick = { onSelected(null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
        }
        PrimaryActionButton(
            text = "开始分析（示例）",
            onClick = onContinue,
            enabled = previewState is ReferencePreviewState.Ready ||
                previewState is ReferencePreviewState.PreviewSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppDimensions.Space12))
        SecondaryActionButton(
            text = if (selectedUri == null) "选择照片" else "更换照片",
            onClick = openPicker,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun RecommendationCard(title: String, detail: String, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        contentPadding = PaddingValues(AppDimensions.Space16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
    }
}
