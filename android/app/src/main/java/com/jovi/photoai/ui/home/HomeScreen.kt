package com.jovi.photoai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.jovi.photoai.ui.components.EmptyState
import com.jovi.photoai.ui.components.GlassPill
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.ReferencePhotoCard
import com.jovi.photoai.ui.components.SceneCategoryChip
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.navigation.RootNavigation
import com.jovi.photoai.ui.navigation.RootSection
import com.jovi.photoai.ui.reference.PrivateReferenceImage

data class HomeReferenceItem(
    val id: String,
    val title: String,
    val sourceLabel: String,
    val scene: String,
    val lighting: String,
    val composition: String,
    val tags: Set<String>,
    val imageFileName: String? = null,
)

private val sceneCategories = listOf("窗边", "咖啡馆", "楼梯", "街道", "草地", "夜景", "走廊", "海边")

/** Inspiration root: local filtering operates only on already-present demo/private reference metadata. */
@Composable
fun HomeScreen(
    references: List<HomeReferenceItem>,
    query: String,
    selectedScene: String?,
    onQueryChange: (String) -> Unit,
    onSceneSelected: (String?) -> Unit,
    onImportReference: () -> Unit,
    onOpenReferenceLibrary: () -> Unit,
    onOpenReference: (String) -> Unit,
    onOpenCapture: () -> Unit,
) {
    val filtered = references.filter { item ->
        SearchableReference(
            id = item.id,
            title = item.title,
            scene = item.scene,
            lighting = item.lighting,
            composition = item.composition,
            tags = item.tags,
        ).matches(query, selectedScene)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = AppDimensions.PagePadding)) {
            Spacer(Modifier.height(AppDimensions.Space8))
            RootNavigation(
                selected = RootSection.INSPIRATION,
                onSelect = { section -> if (section == RootSection.CAPTURE) onOpenCapture() },
            )
            Spacer(Modifier.height(AppDimensions.Space16))
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                contentPadding = PaddingValues(AppDimensions.Space16),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
                    Text("灵感库", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary)
                    Text("你的现场拍摄起点", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    GlassPill(text = "示例指导 · 非图片分析")
                }
            }
            Spacer(Modifier.height(AppDimensions.Space20))
            Text(
                "把收藏的好照片，\n变成现场可执行的拍摄方案",
                style = MaterialTheme.typography.displaySmall,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.height(AppDimensions.Space16))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索场景、光线、构图或标签") },
                placeholder = { Text("例如：窗边、侧光、留白") },
            )

            SectionHeading("场景分类", if (selectedScene == null) "选择一个现场方向" else "已筛选：$selectedScene")
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
            ) {
                sceneCategories.forEach { category ->
                    SceneCategoryChip(
                        text = category,
                        selected = selectedScene == category,
                        onClick = { onSceneSelected(if (selectedScene == category) null else category) },
                    )
                }
            }
            if (selectedScene != null || query.isNotBlank()) {
                TextButton(onClick = { onQueryChange(""); onSceneSelected(null) }) {
                    Text("清除搜索和筛选")
                }
            }

            SectionHeading("最近使用", "本地与内置示例")
            if (filtered.isEmpty()) {
                EmptyState(
                    title = "没有匹配的参考图",
                    message = "可以清除筛选，或导入一张新的参考图。",
                    actionLabel = "导入参考图",
                    onAction = onImportReference,
                )
            } else {
                filtered.take(6).forEachIndexed { index, reference ->
                    ReferencePhotoCard(
                        title = reference.title,
                        subtitle = "${reference.scene} · ${reference.lighting}\n${reference.composition}",
                        badge = reference.sourceLabel,
                        image = {
                            if (reference.imageFileName != null) {
                                PrivateReferenceImage(
                                    imageFileName = reference.imageFileName,
                                    contentDescription = "本地私有参考图：${reference.title}",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                DemoCardArtwork(index)
                            }
                        },
                        onClick = { onOpenReference(reference.id) },
                    )
                    Spacer(Modifier.height(AppDimensions.Space12))
                }
            }

            Spacer(Modifier.height(AppDimensions.Space12))
            SecondaryActionButton(
                text = "查看全部参考图库",
                onClick = onOpenReferenceLibrary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space12))
            PrimaryActionButton(
                text = "导入参考图",
                onClick = onImportReference,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AppDimensions.Space32))
        }
    }
}

@Composable
private fun DemoCardArtwork(index: Int) {
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
            "DEMO / LOCAL / REFERENCE",
            modifier = Modifier.padding(AppDimensions.Space16),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.CameraText,
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Spacer(Modifier.height(AppDimensions.Space24))
    Text(title, style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = AppColors.TextTertiary)
    Spacer(Modifier.height(AppDimensions.Space12))
}
