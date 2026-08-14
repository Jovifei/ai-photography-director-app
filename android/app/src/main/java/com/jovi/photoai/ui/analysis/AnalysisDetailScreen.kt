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
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ProviderAnalysisProvenance
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.reference.toReferenceAnalysis
import com.jovi.photoai.ui.components.AnalysisSection
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.isRealAiGuidanceReady
import com.jovi.photoai.ui.reference.PrivateReferenceImage

@Composable
internal fun AnalysisDetailScreen(
    imageFileName: String?,
    bundle: ReferenceBundle,
    sourceLabel: String,
    title: String = "参考图分析",
    analysisStatus: PhotoAnalysisStatus = PhotoAnalysisStatus.EXAMPLE_GUIDANCE,
    analysisProvenance: ProviderAnalysisProvenance? = null,
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
        Text(title, style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            analysisStatusMessage(analysisStatus),
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.AccentBlue,
        )
        if (analysisStatus == PhotoAnalysisStatus.READY && analysisProvenance != null) {
            Spacer(Modifier.height(AppDimensions.Space8))
            AnalysisProvenanceSummary(analysisProvenance)
        }
        Spacer(Modifier.height(AppDimensions.Space20))
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            shape = RoundedCornerShape(AppDimensions.RadiusLarge),
            contentPadding = PaddingValues(AppDimensions.Space8),
        ) {
            AnalysisReferenceHero(imageFileName = imageFileName, sourceLabel = sourceLabel)
        }

        Spacer(Modifier.height(AppDimensions.Space20))
        if (isRealAiGuidanceReady(analysisStatus)) {
            listOf(
                Triple("背景", analysis.scene, analysis.backgroundValue),
                Triple("光线", sourceLabel, analysis.lighting),
                Triple("构图", sourceLabel, analysis.composition),
                Triple("人物", "参考姿态意图", analysis.subjectIntent),
                Triple("情绪", sourceLabel, analysis.emotion),
                Triple("拍摄建议", "建议机位", analysis.cameraSuggestion),
            ).forEach { (sectionTitle, label, body) ->
                AnalysisSection(title = sectionTitle, body = body, label = label)
                Spacer(Modifier.height(AppDimensions.Space12))
            }

            Spacer(Modifier.height(AppDimensions.Space12))
            PrimaryActionButton(
                text = "查看摄影导演卡",
                onClick = onOpenDirectorCard,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AnalysisSection(
                title = "本张照片尚未可用",
                body = "请保留项目中的其他照片，稍后在 Provider 可用后单独重试；当前不会显示固定示例建议，也不能进入 AI Camera Director。",
                label = "安全状态",
                accentColor = AppColors.Warning,
            )
        }
        Spacer(Modifier.height(AppDimensions.Space12))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("更换参考图")
        }
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

private fun analysisStatusMessage(status: PhotoAnalysisStatus): String = when (status) {
    PhotoAnalysisStatus.EXAMPLE_GUIDANCE -> "示例指导：不连接 AI、不上传图片、不生成实时 Pose，也不声称分析了这张照片。"
    PhotoAnalysisStatus.QUEUED -> "这张照片等待真实 Provider 分析；当前不会回退为示例结果。"
    PhotoAnalysisStatus.IMPORTED -> "这张照片已导入，等待真实 Provider 分析。"
    PhotoAnalysisStatus.RUNNING -> "这张照片正在由真实 Provider 分析；完成前不会显示推断结论。"
    PhotoAnalysisStatus.READY -> "此结果来自真实 Provider 的结构化分析；详情和不确定性会随 Provider 记录显示。"
    PhotoAnalysisStatus.FAILED -> "这张照片的真实分析失败；当前不会回退为示例结果。"
    PhotoAnalysisStatus.UNAVAILABLE -> "这张照片的真实分析不可用；当前不会回退为示例结果。"
    PhotoAnalysisStatus.CANCELLED -> "这张照片的真实分析已取消，可单项重试。"
}

@Composable
private fun AnalysisReferenceHero(imageFileName: String?, sourceLabel: String) {
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
            text = "${if (imageFileName == null) "内置示例参考图" else "本地私有参考图"} · $sourceLabel",
            modifier = Modifier.padding(AppDimensions.Space16),
        )
    }
}

@Composable
private fun AnalysisProvenanceSummary(provenance: ProviderAnalysisProvenance) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppDimensions.Space12)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
            Text("来源：本机 VLM", style = MaterialTheme.typography.labelLarge)
            Text("模型版本：${provenance.modelRevision}", style = MaterialTheme.typography.bodySmall)
            Text("逐字段依据已记录，不使用单一置信度百分比。", style = MaterialTheme.typography.bodySmall)
            val uncertain = provenance.uncertaintyFlags.count { it.value.level != com.jovi.photoai.data.reference.UncertaintyLevel.LOW }
            Text("需要谨慎解读的字段：$uncertain 项", style = MaterialTheme.typography.bodySmall)
            if (provenance.warnings.isNotEmpty()) {
                Text("提示：${provenance.warnings.joinToString("；")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
