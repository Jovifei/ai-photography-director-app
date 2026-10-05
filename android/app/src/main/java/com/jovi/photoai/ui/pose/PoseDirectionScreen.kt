package com.jovi.photoai.ui.pose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jovi.photoai.pose.POSE_DIRECTION_ASSET
import com.jovi.photoai.pose.StickFigureGraphic
import com.jovi.photoai.pose.StickShape
import com.jovi.photoai.ui.components.GlassSurface
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

internal const val POSE_DIRECTION_FAILURE_MESSAGE =
    "\u8fd9\u4efd\u6784\u56fe\u53e3\u4ee4\u65e0\u6cd5\u6253\u5f00\u3002\u5185\u5bb9\u4e3a\u7a7a\u3001\u65e0\u6548\uff0c\u6216\u6743\u5a01\u6807\u8bb0\u4e0d\u662f\u5173\u95ed\u3002"

@Composable
internal fun PoseDirectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model = remember {
        val text = runCatching {
            context.assets.open(POSE_DIRECTION_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        PoseDirectionViewModel(text)
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
    )
}

@Composable
internal fun PoseDirectionContent(
    state: PoseDirectionUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onShowList: () -> Unit,
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
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
            Text("\u8fd4\u56de")
        }
        Text(
            "\u6784\u56fe\u53e3\u4ee4",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColors.TextPrimary,
        )
        Text(
            "\u6743\u5a01\u4fdd\u6301\u5173\u95ed\uff0c\u4e0d\u662f T14 \u653e\u884c\u3002",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppDimensions.Space16))
        when (state) {
            PoseDirectionUiState.Failed -> Text(
                POSE_DIRECTION_FAILURE_MESSAGE,
                modifier = Modifier.testTag("pose-direction-failure"),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextPrimary,
            )
            is PoseDirectionUiState.ListReady -> PoseDirectionList(state, onSelect)
            is PoseDirectionUiState.Detail -> PoseDirectionDetail(state, onShowList)
        }
        Spacer(Modifier.height(AppDimensions.Space32))
    }
}

@Composable
private fun PoseDirectionList(state: PoseDirectionUiState.ListReady, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12)) {
        state.items.forEach { item ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimensions.MinTouchTarget)
                    .testTag("pose-item-${item.id}")
                    .clickable(role = Role.Button, onClick = { onSelect(item.id) })
                    .semantics { contentDescription = "\u6253\u5f00\u6784\u56fe ${item.id}" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimensions.Space16),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
                    Text(item.id, style = MaterialTheme.typography.labelLarge, color = AppColors.TextTertiary)
                    Text(spokenPreview(item.spokenDirection), style = MaterialTheme.typography.bodyLarge, color = AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PoseDirectionDetail(state: PoseDirectionUiState.Detail, onShowList: () -> Unit) {
    TextButton(onClick = onShowList, modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
        Text("\u5168\u90e8\u6784\u56fe")
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
                .semantics { contentDescription = "\u706b\u67f4\u4eba\u59ff\u6001\u793a\u610f" },
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
}

@Composable
internal fun StickFigureDiagram(graphic: StickFigureGraphic, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(Color.White, RoundedCornerShape(AppDimensions.RadiusMedium))
            .height(320.dp),
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