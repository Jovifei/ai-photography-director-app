package com.jovi.photoai.ui.analysis

import android.net.Uri
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
import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.ui.components.AnalysisSection
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.importphoto.ReferencePreviewState
import com.jovi.photoai.ui.importphoto.decodeSampledBitmap

@Composable
fun AnalysisDetailScreen(
    selectedUri: Uri?,
    onBack: () -> Unit,
    onStartCamera: () -> Unit,
) {
    val plan = DemoContentRepository.defaultShootingPlan
    val analysis = plan.analysis

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
            GlassPill(text = "示例 · Demo")
        }

        Spacer(Modifier.height(AppDimensions.Space16))
        Text("参考图分析", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "示例分析，尚未连接 AI",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.AccentBlue,
        )
        Spacer(Modifier.height(AppDimensions.Space20))
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            shape = RoundedCornerShape(AppDimensions.RadiusLarge),
            contentPadding = PaddingValues(AppDimensions.Space8),
        ) {
            AnalysisReferenceHero(selectedUri = selectedUri)
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimensions.RadiusLarge),
            contentPadding = PaddingValues(AppDimensions.CardPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                Text("这张照片为什么成立", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
                Text(
                    "简洁的窗边环境、柔和侧光和人物左侧留白，共同形成安静且有故事感的观看节奏。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(AppDimensions.Space16))
        listOf(
            Triple("环境分析", "室内窗边", analysis.environment),
            Triple("构图分析", "人物偏右", "左侧保留呼吸空间，视线有明确去向。"),
            Triple("光线分析", "柔和侧光", analysis.lighting),
            Triple("人物姿势分析", "自然微侧", "身体微侧、肩膀放松、下巴微收、视线看向窗外。"),
            Triple("情绪和故事", "等待与独处", analysis.story),
            Triple("调色方向", "低饱和轻冷调", analysis.colorGrading),
        ).forEach { (title, label, body) ->
            AnalysisSection(title = title, body = body, label = label)
            Spacer(Modifier.height(AppDimensions.Space12))
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimensions.RadiusLarge),
            contentPadding = PaddingValues(AppDimensions.CardPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                Text("现场怎么拍", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
                Text(analysis.onsitePlan, color = AppColors.TextSecondary)
                Text("固定 Demo 拍摄方案，不是对当前图片生成的建议。", style = MaterialTheme.typography.labelSmall, color = AppColors.TextTertiary)
            }
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        PrimaryActionButton(
            text = "开始复刻拍摄",
            onClick = onStartCamera,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppDimensions.Space12))
        SecondaryActionButton(
            text = "收藏这套方案（UI0 占位）",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppDimensions.Space12))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("更换参考图")
        }
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun AnalysisReferenceHero(selectedUri: Uri?) {
    val context = LocalContext.current
    var state by remember(selectedUri) {
        mutableStateOf<ReferencePreviewState>(ReferencePreviewState.Empty)
    }
    LaunchedEffect(selectedUri) {
        state = if (selectedUri == null) {
            ReferencePreviewState.Empty
        } else {
            decodeSampledBitmap(context.contentResolver, selectedUri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(AppColors.AccentBlueSoft, AppColors.CameraGlassDark)),
                RoundedCornerShape(AppDimensions.RadiusMedium),
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        val ready = state as? ReferencePreviewState.Ready
        if (ready != null) {
            Image(
                bitmap = ready.bitmap.asImageBitmap(),
                contentDescription = "本次会话选择的参考照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        GlassPill(
            text = if (ready == null) "内置示例参考图" else "会话参考图 · 分析仍为示例",
            modifier = Modifier.padding(AppDimensions.Space16),
        )
    }
}
