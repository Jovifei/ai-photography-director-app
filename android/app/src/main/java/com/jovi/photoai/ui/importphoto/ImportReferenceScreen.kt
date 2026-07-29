package com.jovi.photoai.ui.importphoto

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jovi.photoai.data.reference.ReferenceImportErrorCode
import com.jovi.photoai.data.reference.ReferenceImportUiState
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.reference.PrivateReferenceImage

/** Displays only private derivatives. The Picker Uri is consumed immediately by the callback. */
@Composable
fun ImportReferenceScreen(
    state: ReferenceImportUiState,
    onPickerResult: (Uri) -> Unit,
    onPickerCancelled: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onDiscardReady: () -> Unit,
    onRetry: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) onPickerCancelled() else onPickerResult(uri)
    }
    val openPicker = {
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    val retryPicker = {
        onRetry()
        openPicker()
    }
    val isImporting = state is ReferenceImportUiState.Importing

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
            TextButton(onClick = onBack, enabled = !isImporting) { Text("返回") }
            GlassPill(text = "私有导入")
        }

        Spacer(Modifier.height(AppDimensions.Space16))
        Text("导入参考图", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "系统照片选择器只把你选择的一张图片交给 App。选中后会立即导入到本机，" +
                "仅保留去元数据的私有派生图；不会申请相册读取权限、上传或保存来源 URI。",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space24))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
        ) {
            RecommendationCard("推荐图片类型", "单人、光线清楚、姿态完整、背景关系明确", Modifier.weight(1f))
            RecommendationCard("暂不建议类型", "多人合照、严重模糊、拼图、带大量遮挡的图片", Modifier.weight(1f))
        }
        Spacer(Modifier.height(AppDimensions.Space16))

        when (state) {
            ReferenceImportUiState.Idle -> EmptyState(
                title = "还没有参考图",
                message = "选择一张能代表目标光线、姿态或构图的照片。",
                actionLabel = "打开系统照片选择器",
                onAction = openPicker,
            )

            ReferenceImportUiState.Importing -> GlassSurface(
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                contentPadding = PaddingValues(AppDimensions.CardPadding),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(AppDimensions.Space12))
                        Text("正在导入到本机", color = AppColors.TextPrimary)
                    }
                }
            }

            is ReferenceImportUiState.Ready -> GlassSurface(
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                contentPadding = PaddingValues(AppDimensions.Space8),
            ) {
                PrivateReferenceImage(
                    imageFileName = state.record.imageFileName,
                    contentDescription = "已导入的私有参考照片",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is ReferenceImportUiState.Failed -> EmptyState(
                title = "无法导入这张照片",
                message = importFailureMessage(state.code),
                actionLabel = "重新选择",
                onAction = retryPicker,
            )
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimensions.RadiusMedium),
            contentPadding = PaddingValues(AppDimensions.Space16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                Text("本页不会执行真实 AI 分析", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                Text("继续后显示的是预置示例，用来验证产品流程与视觉层级。", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        when (state) {
            is ReferenceImportUiState.Ready -> {
                SecondaryActionButton("清除已导入图片", onDiscardReady, Modifier.fillMaxWidth())
                Spacer(Modifier.height(AppDimensions.Space12))
                PrimaryActionButton("开始示例指导", onContinue, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(AppDimensions.Space12))
                SecondaryActionButton(
                    "更换照片",
                    onClick = {
                        onDiscardReady()
                        openPicker()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ReferenceImportUiState.Importing -> Text(
                "请保持此页面打开，导入完成后即可继续。",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> SecondaryActionButton("选择照片", openPicker, Modifier.fillMaxWidth())
        }
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

private fun importFailureMessage(code: ReferenceImportErrorCode): String = when (code) {
    ReferenceImportErrorCode.SOURCE_EMPTY,
    ReferenceImportErrorCode.SOURCE_TRUNCATED,
    ReferenceImportErrorCode.UNSUPPORTED_IMAGE,
    ReferenceImportErrorCode.MIME_CONTENT_MISMATCH,
    ReferenceImportErrorCode.IMAGE_DECODE_FAILED -> "该图片无法安全导入，请选择另一张图片。"
    ReferenceImportErrorCode.IMAGE_TOO_LARGE -> "这张图片尺寸过大，请选择较小的图片。"
    ReferenceImportErrorCode.USER_CANCELLED -> "已取消导入。"
    else -> "导入未完成，请重试或选择另一张图片。"
}
