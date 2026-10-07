package com.jovi.photoai.ui.pose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
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
import com.jovi.photoai.ui.design.PoseTypography
import com.jovi.photoai.ui.navigation.RootNavigation
import com.jovi.photoai.ui.navigation.RootSection

private val StickInk = Color(0xFF2C2C2C)
private val ThumbFallbackBg = Color(0xFFF0F0F0)

internal const val POSE_DIRECTION_FAILURE_MESSAGE =
    "\u8fd9\u4efd\u6784\u56fe\u53e3\u4ee4\u65e0\u6cd5\u6253\u5f00\u3002\u5185\u5bb9\u4e3a\u7a7a\u3001\u65e0\u6548\uff0c\u6216\u6807\u8bb0\u4e0d\u7b26\u5408\u8981\u6c42\u3002"

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
    when (state) {
        is PoseDirectionUiState.Detail -> PoseDirectionDetailScaffold(
            state = state,
            onShowList = onShowList,
            onTakeToShoot = onTakeToShoot,
        )
        else -> {
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
                        Text("\u8fd4\u56de", style = PoseTypography.labelLarge, color = AppColors.TextPrimary)
                    }
                }
                Text(
                    "\u6784\u56fe\u53e3\u4ee4",
                    style = PoseTypography.titleLarge,
                    color = AppColors.TextPrimary,
                )
                Text(
                    "\u9009\u4e00\u4e2a\u59ff\u6001\uff0c\u6309\u53e3\u8ff0\u53e3\u4ee4\u62cd",
                    style = PoseTypography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(AppDimensions.Space16))
                when (state) {
                    PoseDirectionUiState.Failed -> {
                        Text(
                            POSE_DIRECTION_FAILURE_MESSAGE,
                            modifier = Modifier.testTag("pose-direction-failure"),
                            style = PoseTypography.bodyLarge,
                            color = AppColors.TextPrimary,
                        )
                        Spacer(Modifier.height(AppDimensions.Space16))
                        Text(
                            "\u5bfc\u5165 1 \u5f20\u53c2\u8003\u56fe\u5f00\u59cb",
                            style = PoseTypography.titleMedium,
                            color = AppColors.TextPrimary,
                        )
                        Text(
                            "\u4e00\u5f20\u5c31\u591f\u3002\u4e0d\u8981\u6c42\u51d1\u6ee1 20 \u5f20\u3002",
                            style = PoseTypography.bodyMedium,
                            color = AppColors.TextSecondary,
                        )
                        if (onImportReferences != null) {
                            Spacer(Modifier.height(AppDimensions.Space12))
                            PrimaryActionButton(
                                text = "\u5bfc\u5165\u53c2\u8003\u56fe",
                                onClick = onImportReferences,
                                modifier = Modifier.fillMaxWidth().testTag("pose-direction-import"),
                            )
                        }
                    }
                    is PoseDirectionUiState.ListReady -> {
                        if (state.items.isEmpty()) {
                            Text(
                                "\u5bfc\u5165 1 \u5f20\u53c2\u8003\u56fe\u5f00\u59cb",
                                modifier = Modifier.testTag("pose-direction-empty"),
                                style = PoseTypography.titleMedium,
                                color = AppColors.TextPrimary,
                            )
                            Text(
                                "\u4e00\u5f20\u5c31\u591f\u3002",
                                style = PoseTypography.bodyMedium,
                                color = AppColors.TextSecondary,
                            )
                            if (onImportReferences != null) {
                                Spacer(Modifier.height(AppDimensions.Space12))
                                PrimaryActionButton(
                                    text = "\u5bfc\u5165\u53c2\u8003\u56fe",
                                    onClick = onImportReferences,
                                    modifier = Modifier.fillMaxWidth().testTag("pose-direction-import"),
                                )
                            }
                        } else {
                            PoseDirectionList(state, onSelect)
                            if (onImportReferences != null) {
                                Spacer(Modifier.height(AppDimensions.Space16))
                                SecondaryActionButton(
                                    text = "\u5bfc\u5165\u53c2\u8003\u56fe",
                                    onClick = onImportReferences,
                                    modifier = Modifier.fillMaxWidth().testTag("pose-direction-import"),
                                )
                            }
                        }
                    }
                    is PoseDirectionUiState.Detail -> Unit
                }
                Spacer(Modifier.height(AppDimensions.Space32))
            }
        }
    }
}

@Composable
private fun PoseDirectionList(state: PoseDirectionUiState.ListReady, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12)) {
        state.items.forEach { item ->
            val graphic = item.svg?.let(::parseStickFigureSvg)
            val title = displayTitle(item)
            val thumbAsset = referenceThumbAsset(item.referenceImage)
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp, max = 96.dp)
                    .testTag("pose-item-${item.id}")
                    .clickable(role = Role.Button, onClick = { onSelect(item.id) })
                    .semantics { contentDescription = "\u6253\u5f00\u6784\u56fe $title" },
                contentPadding = PaddingValues(AppDimensions.Space12),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ThumbFallbackBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (thumbAsset != null) {
                            PoseAssetImage(
                                assetPath = thumbAsset,
                                contentDescription = "\u53c2\u8003\u56fe",
                                modifier = Modifier.fillMaxSize().testTag("pose-item-photo-${item.id}"),
                                contentScale = ContentScale.Crop,
                            )
                        } else if (graphic != null) {
                            StickFigureDiagram(
                                graphic = graphic,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("pose-item-thumb-${item.id}"),
                                diagramHeight = 72.dp,
                                showBackground = false,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                style = PoseTypography.titleMedium,
                                color = AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                "\u793a\u4f8b",
                                style = PoseTypography.bodySmall,
                                color = AppColors.AccentBlue,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            resolveSpokenSteps(item).take(2).forEach { step ->
                                Text(
                                    step,
                                    style = PoseTypography.bodyMedium,
                                    color = AppColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoseDirectionDetailScaffold(
    state: PoseDirectionUiState.Detail,
    onShowList: () -> Unit,
    onTakeToShoot: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val title = displayTitle(state.item)
    val thumbAsset = referenceThumbAsset(state.item.referenceImage)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .safeDrawingPadding()
            .testTag("pose-direction-screen"),
        containerColor = AppColors.AppBackground,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimensions.PagePadding, vertical = AppDimensions.Space12),
            ) {
                PrimaryActionButton(
                    text = "\u7528\u6b64\u6784\u56fe\u62cd\u6444",
                    onClick = onTakeToShoot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimensions.PrimaryButtonHeight)
                        .testTag("pose-direction-take-to-shoot"),
                    contentDescription = "\u7528\u6b64\u6784\u56fe\u8fdb\u5165\u62cd\u6444",
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimensions.PagePadding),
        ) {
            TextButton(onClick = onShowList, modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
                Text("\u5168\u90e8\u6784\u56fe", style = PoseTypography.labelLarge, color = AppColors.TextPrimary)
            }
            Text(title, style = PoseTypography.titleLarge, color = AppColors.TextPrimary)
            Spacer(Modifier.height(AppDimensions.Space12))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ThumbFallbackBg)
                    .testTag("pose-direction-reference"),
                contentAlignment = Alignment.Center,
            ) {
                PoseAssetImage(
                    assetPath = thumbAsset,
                    contentDescription = "\u539f\u59cb\u53c2\u8003\u56fe",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(AppDimensions.Space16))
            Text(
                "\u59ff\u6001\u793a\u610f",
                style = PoseTypography.titleSmall,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(AppDimensions.Space8))
            val graphic = state.graphic
            if (graphic != null) {
                StickFigureDiagram(
                    graphic = graphic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pose-direction-figure")
                        .semantics { contentDescription = "\u59ff\u6001\u793a\u610f" },
                    diagramHeight = 190.dp,
                )
            } else {
                Text(
                    state.figureNote ?: "\u6682\u65e0\u59ff\u6001\u793a\u610f",
                    style = PoseTypography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(AppDimensions.Space16))
            Text(
                "\u53e3\u8ff0\u53e3\u4ee4",
                style = PoseTypography.titleSmall,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(AppDimensions.Space8))
            Column(
                modifier = Modifier.testTag("pose-direction-spoken"),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8),
            ) {
                resolveSpokenSteps(state.item).forEach { step ->
                    Text(
                        step,
                        style = PoseTypography.bodyLarge,
                        color = AppColors.TextPrimary,
                    )
                }
            }
            if (state.whyLines.isNotEmpty()) {
                Spacer(Modifier.height(AppDimensions.Space12))
                state.whyLines.forEach { line ->
                    Text(line, style = PoseTypography.bodyMedium, color = AppColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(AppDimensions.Space16))
            SecondaryActionButton(
                text = "\u590d\u5236\u53e3\u4ee4",
                onClick = { clipboard.setText(AnnotatedString(state.item.spokenDirection)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pose-direction-copy-spoken"),
            )
            Spacer(Modifier.height(AppDimensions.Space24))
        }
    }
}

@Composable
internal fun StickFigureDiagram(
    graphic: StickFigureGraphic,
    modifier: Modifier = Modifier,
    diagramHeight: Dp = 320.dp,
    showBackground: Boolean = true,
) {
    val canvasMod = if (showBackground) {
        modifier
            .background(Color.White, RoundedCornerShape(AppDimensions.RadiusMedium))
            .height(diagramHeight)
    } else {
        modifier.height(diagramHeight)
    }
    Canvas(canvasMod) {
        val scaleX = size.width / graphic.width
        val scaleY = size.height / graphic.height
        val scale = minOf(scaleX, scaleY)
        fun px(x: Float) = (x - graphic.minX) * scale + (size.width - graphic.width * scale) / 2f
        fun py(y: Float) = (y - graphic.minY) * scale + (size.height - graphic.height * scale) / 2f
        graphic.shapes.forEach { shape ->
            val strokeWidth = (shape.strokeWidth * scale).coerceIn(3f, 4.5f)
            when (shape) {
                is StickShape.Line -> drawLine(
                    StickInk,
                    Offset(px(shape.x1), py(shape.y1)),
                    Offset(px(shape.x2), py(shape.y2)),
                    strokeWidth = strokeWidth,
                )
                is StickShape.Circle -> drawCircle(
                    StickInk,
                    radius = shape.r * scale,
                    center = Offset(px(shape.cx), py(shape.cy)),
                    style = Stroke(width = strokeWidth),
                )
                is StickShape.Polyline -> {
                    val points = shape.points
                    for (index in 0 until points.lastIndex) {
                        drawLine(
                            StickInk,
                            Offset(px(points[index].first), py(points[index].second)),
                            Offset(px(points[index + 1].first), py(points[index + 1].second)),
                            strokeWidth = strokeWidth,
                        )
                    }
                }
                is StickShape.Rect -> drawRect(
                    StickInk,
                    topLeft = Offset(px(shape.x), py(shape.y)),
                    size = androidx.compose.ui.geometry.Size(shape.width * scale, shape.height * scale),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}
