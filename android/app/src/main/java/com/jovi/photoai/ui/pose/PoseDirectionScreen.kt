package com.jovi.photoai.ui.pose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jovi.photoai.pose.POSE_DIRECTION_ASSET
import com.jovi.photoai.pose.PoseDirectionDocumentLoader
import com.jovi.photoai.pose.StickFigureGraphic
import com.jovi.photoai.pose.StickShape
import com.jovi.photoai.pose.parseStickFigureSvg
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.components.PrimaryActionButton
import com.jovi.photoai.ui.components.SecondaryActionButton
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.navigation.RootNavigation
import com.jovi.photoai.ui.navigation.RootSection

internal const val POSE_DIRECTION_FAILURE_MESSAGE =
    "这份构图口令无法打开。内容为空、无效，或权威标记不是关闭。"

@Composable
internal fun PoseDirectionScreen(
    onBack: () -> Unit,
    onTakeToShoot: (SelectedPoseDirection) -> Unit = {},
    externalBundlePath: String? = null,
    isRoot: Boolean = false,
    onRootSection: ((RootSection) -> Unit)? = null,
    onImportReferences: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val model = remember(externalBundlePath) {
        val assetText = runCatching {
            context.assets.open(POSE_DIRECTION_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        val document = PoseDirectionDocumentLoader.resolveDocument(assetText, externalBundlePath)
        PoseDirectionViewModel(document)
    }
    var state by remember { mutableStateOf(model.uiState()) }
    PoseDirectionContent(
        state = state,
        onBack = onBack,
        onSelect = { id ->
            model.select(id)
            state = model.uiState()
        },
        onShowList = {
            model.showList()
            state = model.uiState()
        },
        onTakeToShoot = {
            model.selectedPose()?.let(onTakeToShoot)
        },
        isRoot = isRoot,
        onRootSection = onRootSection,
        onImportReferences = onImportReferences,
    )
}

@Composable
internal fun PoseDirectionContent(
    state: PoseDirectionUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onShowList: () -> Unit,
    onTakeToShoot: () -> Unit = {},
    isRoot: Boolean = false,
    onRootSection: ((RootSection) -> Unit)? = null,
    onImportReferences: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.PagePadding)
            .testTag("pose-direction-screen"),
    ) {
        Spacer(Modifier.height(AppDimensions.Space8))
        if (isRoot && onRootSection != null) {
            RootNavigation(
                selected = RootSection.POSE,
                onSelect = onRootSection,
            )
            Spacer(Modifier.height(AppDimensions.Space16))
        } else {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
                Text("返回")
            }
        }
        Text(
            "构图口令",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.TextPrimary,
        )
        Text(
            "火柴人姿态 + 口述提示词 · 示例非权威（authority=false）",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space16))
        when (state) {
            PoseDirectionUiState.Failed -> {
                Text(
                    POSE_DIRECTION_FAILURE_MESSAGE,
                    modifier = Modifier.testTag("pose-direction-failure"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextPrimary,
                )
                Spacer(Modifier.height(AppDimensions.Space16))
                Text(
                    "导入 1 张参考图开始",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                )
                Text(
                    "一张就够。不要求凑满 20 张。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
                if (onImportReferences != null) {
                    Spacer(Modifier.height(AppDimensions.Space12))
                    PrimaryActionButton(
                        text = "导入参考图",
                        onClick = onImportReferences,
                        modifier = Modifier.fillMaxWidth().testTag("pose-direction-import"),
                    )
                }
            }
            is PoseDirectionUiState.ListReady -> {
                if (state.items.isEmpty()) {
                    Text(
                        "导入 1 张参考图开始",
                        modifier = Modifier.testTag("pose-direction-empty"),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        "一张就够。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                    )
                    if (onImportReferences != null) {
                        Spacer(Modifier.height(AppDimensions.Space12))
                        PrimaryActionButton(
                            text = "导入参考图",
                            onClick = onImportReferences,
                            modifier = Modifier.fillMaxWidth().testTag("pose-direction-import"),
                        )
                    }
                } else {
                    PoseDirectionList(state, onSelect)
                    if (onImportReferences != null) {
                        Spacer(Modifier.height(AppDimensions.Space16))
                        SecondaryActionButton(
                            text = "导入参考图",
                            onClick = onImportReferences,
                            modifier = Modifier.fillMaxWidth().testTag("pose-direction-import"),
                        )
                    }
                }
            }
            is PoseDirectionUiState.Detail -> PoseDirectionDetail(state, onShowList, onTakeToShoot)
        }
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun PoseDirectionList(state: PoseDirectionUiState.ListReady, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12)) {
        state.items.forEach { item ->
            val graphic = item.svg?.let(::parseStickFigureSvg)
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimensions.MinTouchTarget)
                    .testTag("pose-item-${item.id}")
                    .clickable(role = Role.Button, onClick = { onSelect(item.id) })
                    .semantics { contentDescription = "打开构图 ${item.id}" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimensions.Space12),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
                ) {
                    if (graphic != null) {
                        StickFigureDiagram(
                            graphic = graphic,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("pose-item-thumb-${item.id}"),
                            diagramHeight = 72.dp,
                        )
                    } else {
                        Spacer(Modifier.size(72.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                            Text(item.id, style = MaterialTheme.typography.labelLarge, color = AppColors.TextTertiary)
                            Text("示例", style = MaterialTheme.typography.labelSmall, color = AppColors.AccentBlue)
                        }
                        Text(
                            spokenPreview(item.spokenDirection),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PoseDirectionDetail(
    state: PoseDirectionUiState.Detail,
    onShowList: () -> Unit,
    onTakeToShoot: () -> Unit,
) {
    TextButton(onClick = onShowList, modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
        Text("全部构图")
    }
    Text(state.item.id, style = MaterialTheme.typography.labelLarge, color = AppColors.TextTertiary)
    Spacer(Modifier.height(AppDimensions.Space12))
    val graphic = state.graphic
    if (graphic != null) {
        StickFigureDiagram(
            graphic = graphic,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pose-direction-figure")
                .semantics { contentDescription = "火柴人姿态示意" },
        )
    }
    state.figureNote?.let { note ->
        Text(note, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
    }
    Spacer(Modifier.height(AppDimensions.Space16))
    Text(
        state.item.spokenDirection,
        modifier = Modifier.testTag("pose-direction-spoken"),
        style = MaterialTheme.typography.titleLarge,
        color = AppColors.TextPrimary,
    )
    if (state.whyLines.isNotEmpty()) {
        Spacer(Modifier.height(AppDimensions.Space12))
        state.whyLines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
    }
    Spacer(Modifier.height(AppDimensions.Space24))
    PrimaryActionButton(
        text = "用此构图拍摄",
        onClick = onTakeToShoot,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pose-direction-take-to-shoot"),
        contentDescription = "用此构图进入拍摄",
    )
    val clipboard = LocalClipboardManager.current
    Spacer(Modifier.height(AppDimensions.Space8))
    SecondaryActionButton(
        text = "复制口述提示",
        onClick = { clipboard.setText(AnnotatedString(state.item.spokenDirection)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pose-direction-copy-spoken"),
    )
}

@Composable
internal fun StickFigureDiagram(
    graphic: StickFigureGraphic,
    modifier: Modifier = Modifier,
    diagramHeight: Dp = 320.dp,
) {
    Canvas(
        modifier
            .background(Color.White, RoundedCornerShape(AppDimensions.RadiusMedium))
            .height(diagramHeight),
    ) {
        val scaleX = size.width / graphic.width
        val scaleY = size.height / graphic.height
        val scale = minOf(scaleX, scaleY)
        fun px(x: Float) = (x - graphic.minX) * scale + (size.width - graphic.width * scale) / 2f
        fun py(y: Float) = (y - graphic.minY) * scale + (size.height - graphic.height * scale) / 2f
        graphic.shapes.forEach { shape ->
            val stroke = Stroke(width = (shape.strokeWidth * scale).coerceAtLeast(1f))
            when (shape) {
                is StickShape.Line -> drawLine(
                    Color.Black,
                    Offset(px(shape.x1), py(shape.y1)),
                    Offset(px(shape.x2), py(shape.y2)),
                    strokeWidth = stroke.width,
                )
                is StickShape.Circle -> drawCircle(
                    Color.Black,
                    radius = shape.r * scale,
                    center = Offset(px(shape.cx), py(shape.cy)),
                    style = stroke,
                )
                is StickShape.Polyline -> {
                    val points = shape.points
                    for (index in 0 until points.lastIndex) {
                        drawLine(
                            Color.Black,
                            Offset(px(points[index].first), py(points[index].second)),
                            Offset(px(points[index + 1].first), py(points[index + 1].second)),
                            strokeWidth = stroke.width,
                        )
                    }
                }
                is StickShape.Rect -> drawRect(
                    Color.Black,
                    topLeft = Offset(px(shape.x), py(shape.y)),
                    size = androidx.compose.ui.geometry.Size(shape.width * scale, shape.height * scale),
                    style = stroke,
                )
            }
        }
    }
}
