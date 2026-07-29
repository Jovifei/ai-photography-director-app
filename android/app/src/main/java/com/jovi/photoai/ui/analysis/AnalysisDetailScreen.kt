package com.jovi.photoai.ui.analysis

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.reference.toReferenceAnalysis
import com.jovi.photoai.ui.components.AnalysisSection
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.reference.PrivateReferenceImage

@Composable
fun AnalysisDetailScreen(
    imageFileName: String?,
    bundle: ReferenceBundle,
    sourceLabel: String,
    onBack: () -> Unit,
    onOpenDirectorCard: () -> Unit,
) {
    val analysis = bundle.toReferenceAnalysis()
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
            GlassPill(text = sourceLabel)
        }

        Spacer(Modifier.height(AppDimensions.Space16))
        Text("参考图分析", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "示例指导：不连接 AI、不上传图片、不生成实时 Pose，也不声称分析了这张照片。",
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
            AnalysisReferenceHero(imageFileName = imageFileName)
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        listOf(
            Triple("背景", analysis.scene, analysis.backgroundValue),
            Triple("光线", sourceLabel, analysis.lighting),
            Triple("构图", sourceLabel, analysis.composition),
            Triple("人物", "参考姿态意图", analysis.subjectIntent),
            Triple("情绪", sourceLabel, analysis.emotion),
            Triple("拍摄建议", "建议机位", analysis.cameraSuggestion),
        ).forEach { (title, label, body) ->
            AnalysisSection(title = title, body = body, label = label)
            Spacer(Modifier.height(AppDimensions.Space12))
        }

        Spacer(Modifier.height(AppDimensions.Space12))
        PrimaryActionButton(
            text = "查看摄影导演卡",
            onClick = onOpenDirectorCard,
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
private fun AnalysisReferenceHero(imageFileName: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(AppColors.AccentBlueSoft, AppColors.CameraGlassDark)),
                RoundedCornerShape(AppDimensions.RadiusMedium),
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (imageFileName != null) {
            PrivateReferenceImage(
                imageFileName = imageFileName,
                contentDescription = "本地私有参考图",
                modifier = Modifier.fillMaxSize(),
            )
        }
        GlassPill(
            text = if (imageFileName == null) "内置示例参考图 · 示例指导" else "本地私有参考图 · 示例指导",
            modifier = Modifier.padding(AppDimensions.Space16),
        )
    }
}
