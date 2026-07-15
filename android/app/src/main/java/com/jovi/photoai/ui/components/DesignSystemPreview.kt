package com.jovi.photoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.design.AppTheme

private enum class PreviewOverlay(val label: String) {
    SKELETON("骨架"),
    OUTLINE("轮廓"),
    REFERENCE("参考图")
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1600)
@Composable
private fun DesignSystemOverviewPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppColors.AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(AppDimensions.Space20),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.Space16)
        ) {
            GlassPill(text = "示例")
            GlassHintCard(text = "人物再向右移动半步", tone = GlassTone.LIGHT)
            AppSegmentedControl(
                options = PreviewOverlay.entries,
                selectedOption = PreviewOverlay.SKELETON,
                onOptionSelected = {},
                label = PreviewOverlay::label
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                SceneCategoryChip(text = "窗边", selected = true, onClick = {})
                SceneCategoryChip(text = "街道", selected = false, onClick = {})
            }
            ReferencePhotoCard(
                title = "窗边等待感",
                subtitle = "柔和侧光 · 人物偏右",
                modifier = Modifier.fillMaxWidth(),
                onClick = {},
                image = {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE7DDD2), Color(0xFF8B9AAB))
                                )
                            )
                    )
                }
            )
            AnalysisSection(
                title = "构图分析",
                body = "人物偏右，左侧保留呼吸空间",
                label = "示例分析"
            )
            PrimaryActionButton(
                text = "开始拍摄",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryActionButton(
                text = "导入参考图",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            EmptyState(
                title = "还没有参考图",
                message = "导入一张喜欢的照片，开始创建拍摄方案。",
                actionLabel = "从相册选择",
                onAction = {}
            )
            CameraPreviewPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                CameraShutterButton(
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AppDimensions.Space24)
                )
                EdgeGuideHandle(
                    side = EdgeGuideSide.ENVIRONMENT,
                    onClick = {},
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                EdgeGuideHandle(
                    side = EdgeGuideSide.SUBJECT,
                    onClick = {},
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            AppIconButton(onClick = {}, contentDescription = "关闭") {
                Text("×")
            }
        }
    }
}
