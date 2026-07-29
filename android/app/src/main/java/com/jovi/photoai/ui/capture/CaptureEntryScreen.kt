package com.jovi.photoai.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.navigation.RootNavigation
import com.jovi.photoai.ui.navigation.RootSection

@Composable
fun CaptureEntryScreen(
    referenceCount: Int,
    onOpenInspiration: () -> Unit,
    onChooseReference: () -> Unit,
    onDirectCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .padding(horizontal = AppDimensions.PagePadding),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space16),
    ) {
        Spacer(Modifier.height(AppDimensions.Space8))
        RootNavigation(
            selected = RootSection.CAPTURE,
            onSelect = { section -> if (section == RootSection.INSPIRATION) onOpenInspiration() },
        )
        Spacer(Modifier.height(AppDimensions.Space8))
        Text("开始拍摄", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
        Text(
            "先选择参考图获得环境、人物和机位建议；也可以进入不带示例或参考指导的基础拍摄。",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
        )
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimensions.CardPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                Text(
                    if (referenceCount > 0) "已有 $referenceCount 张本地参考图" else "尚未选择活动参考图",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                )
                Text(
                    if (referenceCount > 0) {
                        "选择一张参考图后，可继续 Reference → Director 流程。"
                    } else {
                        "选择参考图会打开系统 Photo Picker；不会申请相册读取权限或上传图片。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
        }
        PrimaryActionButton(
            text = if (referenceCount > 0) "选择参考图并拍摄" else "选择参考图并拍摄",
            onClick = onChooseReference,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryActionButton(
            text = "无指导直接拍摄",
            onClick = onDirectCapture,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "基础拍摄 · 无参考指导：不显示 Demo 分析、参考图覆盖或人物指导。",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextTertiary,
        )
    }
}
