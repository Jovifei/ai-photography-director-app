package com.jovi.photoai.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

/** The intentional hand-off between reference interpretation and Camera Director. */
@Composable
fun DirectorCardScreen(
    card: DirectorCard,
    sourceLabel: String,
    onBack: () -> Unit,
    onEnterCameraDirector: () -> Unit,
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
            TextButton(onClick = onBack) { Text("返回分析") }
            GlassPill(text = sourceLabel)
        }
        Spacer(Modifier.height(AppDimensions.Space16))
        Text("摄影导演卡", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(AppDimensions.Space8))
        Text(
            "以下内容是固定 Demo Analysis，用于验证产品流程；不是实时 Pose，也不是对当前现场的 AI 判断。",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space20))

        DirectorCardSection("环境", "你应该站在哪里", card.environment)
        Spacer(Modifier.height(AppDimensions.Space12))
        DirectorCardSection("人物", "你应该怎么站", card.subject)
        Spacer(Modifier.height(AppDimensions.Space12))
        DirectorCardSection("情绪", "你应该表达什么", card.emotion)
        Spacer(Modifier.height(AppDimensions.Space12))
        DirectorCardSection("相机", "建议机位", card.camera)

        Spacer(Modifier.height(AppDimensions.Space24))
        PrimaryActionButton(
            text = "进入 Camera Director",
            onClick = onEnterCameraDirector,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun DirectorCardSection(title: String, subtitle: String, detail: String) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        contentPadding = PaddingValues(AppDimensions.CardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = AppColors.AccentBlue)
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary)
        }
    }
}
