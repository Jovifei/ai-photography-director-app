package com.jovi.photoai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.ReferencePhotoCard
import com.jovi.photoai.ui.components.SceneCategoryChip
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

private val sceneCategories = listOf("窗边", "咖啡馆", "楼梯", "街道", "草地", "夜景", "走廊", "海边")

/** UI0 inspiration library. Every built-in item is visibly marked as Demo. */
@Composable
fun HomeScreen(
    onImportReference: () -> Unit,
    onOpenDemoAnalysis: () -> Unit,
    onStartCamera: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.PagePadding, vertical = AppDimensions.Space12),
            shape = RoundedCornerShape(AppDimensions.RadiusLarge),
            contentPadding = PaddingValues(AppDimensions.Space16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
                    Text("灵感库", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary)
                    Text("你的现场拍摄起点", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                }
                GlassPill(text = "UI0 · Demo")
            }
        }

        Column(modifier = Modifier.padding(horizontal = AppDimensions.PagePadding)) {
            Spacer(Modifier.height(AppDimensions.Space12))
            Text(
                "把收藏的好照片，\n变成现场可执行的拍摄方案",
                style = MaterialTheme.typography.displaySmall,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.height(AppDimensions.Space16))
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = AppDimensions.Space16, vertical = AppDimensions.Space12),
            ) {
                Text("搜索场景、光线或拍摄故事", color = AppColors.TextTertiary, style = MaterialTheme.typography.bodyMedium)
            }

            SectionHeading("场景分类", "选择一个现场方向")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
            ) {
                sceneCategories.forEachIndexed { index, category ->
                    SceneCategoryChip(text = category, selected = index == 0, onClick = {})
                }
            }

            SectionHeading("最近使用", "本地固定示例")
            DemoContentRepository.referencePhotos.take(2).forEachIndexed { index, photo ->
                ReferencePhotoCard(
                    title = photo.title,
                    subtitle = photo.description,
                    badge = "Demo",
                    image = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        if (index == 0) {
                                            listOf(AppColors.AccentBlueSoft, AppColors.CameraGlassDark)
                                        } else {
                                            listOf(AppColors.Warning.copy(alpha = 0.24f), AppColors.TextSecondary)
                                        },
                                    ),
                                ),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(
                                "DEMO  /  LIGHT  /  FRAME",
                                modifier = Modifier.padding(AppDimensions.Space16),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.CameraText,
                            )
                        }
                    },
                    onClick = onOpenDemoAnalysis,
                )
                Spacer(Modifier.height(AppDimensions.Space12))
            }

            SectionHeading("我的参考图", "只显示本次会话主动选择的图片")
            EmptyState(
                title = "还没有参考图",
                message = "从系统照片选择器导入一张图片。App 不会浏览或上传其他照片。",
                actionLabel = "导入参考图",
                onAction = onImportReference,
            )

            Spacer(Modifier.height(AppDimensions.Space20))
            PrimaryActionButton(
                text = "导入参考图",
                onClick = onImportReference,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
            SecondaryActionButton(
                text = "开始拍摄",
                onClick = onStartCamera,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space32))
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Spacer(Modifier.height(AppDimensions.Space24))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AppColors.TextTertiary)
    }
    Spacer(Modifier.height(AppDimensions.Space12))
}
