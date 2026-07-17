package com.jovi.photoai.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.domain.model.OverlayMode
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

/** Preview-only adapter. Runtime panel state is always [GuidePanel] in [CameraUiState]. */
enum class DirectorGuidePanel { ENVIRONMENT, SUBJECT }

@Composable
fun CameraDirectorChrome(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closePanelOrBack = {
        if (cameraBackClosesPanel(uiState)) {
            onEvent(CameraUiEvent.ClosePanel)
        } else {
            onBack()
        }
    }
    BackHandler(enabled = cameraBackClosesPanel(uiState)) {
        onEvent(CameraUiEvent.ClosePanel)
    }

    Box(modifier = modifier.fillMaxSize()) {
        DemoOverlay(mode = uiState.overlayMode, showGrid = uiState.gridVisible)

        CameraTopBar(
            onBack = closePanelOrBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        CameraHint(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 88.dp),
        )

        EdgeGestureZone(
            panel = GuidePanel.ENVIRONMENT,
            onOpen = { onEvent(CameraUiEvent.GuidePanelSelected(it)) },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        EdgeGestureZone(
            panel = GuidePanel.SUBJECT,
            onOpen = { onEvent(CameraUiEvent.GuidePanelSelected(it)) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        CameraEdgeHandle(
            label = "环境",
            contentDescription = "打开环境指导",
            onClick = { onEvent(CameraUiEvent.GuidePanelSelected(GuidePanel.ENVIRONMENT)) },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        CameraEdgeHandle(
            label = "人物",
            contentDescription = "打开人物指导",
            onClick = { onEvent(CameraUiEvent.GuidePanelSelected(GuidePanel.SUBJECT)) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        CameraBottomControls(
            uiState = uiState,
            onEvent = onEvent,
            onCapture = onCapture,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (uiState.selectedGuidePanel != GuidePanel.NONE) {
            GuidePanelSheet(
                uiState = uiState,
                onEvent = onEvent,
                onClose = { onEvent(CameraUiEvent.ClosePanel) },
                modifier = Modifier.align(
                    if (uiState.selectedGuidePanel == GuidePanel.ENVIRONMENT) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ),
            )
        }
    }
}

@Composable
private fun CameraTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.CameraChromeSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        shadowElevation = AppDimensions.GlassElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.Space12, vertical = AppDimensions.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回" }) {
                Text("返回", color = AppColors.CameraChromeText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "参考图拍摄",
                    color = AppColors.CameraChromeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "当前参考图：窗边等待感 · Demo",
                    color = AppColors.CameraChromeSecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.semantics {
                    contentDescription = "更多，暂未开放"
                    disabled()
                },
            ) {
                Text("更多", color = AppColors.CameraChromeDisabledText)
            }
        }
    }
}

@Composable
private fun CameraHint(uiState: CameraUiState, modifier: Modifier = Modifier) {
    val text = when {
        uiState.message == CameraUiMessage.CAPTURE_FAILED -> "拍摄未完成，请稍后再试 · Demo"
        uiState.currentGuidance != null -> {
            "${uiState.currentGuidance.title} · Demo\n${uiState.currentGuidance.instruction}"
        }
        else -> "暂无 Demo 指导，请先确认现场安全与取景。"
    }
    Surface(
        modifier = modifier.padding(horizontal = 40.dp),
        color = AppColors.CameraChromeSurface,
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        shadowElevation = AppDimensions.GlassElevation,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AppDimensions.Space16, vertical = AppDimensions.Space12),
            color = AppColors.CameraChromeText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EdgeGestureZone(
    panel: GuidePanel,
    onOpen: (GuidePanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var horizontalDrag by remember(panel) { mutableFloatStateOf(0f) }
    var verticalDrag by remember(panel) { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .width(AppDimensions.EdgeGestureWidth)
            .fillMaxHeight()
            .pointerInput(panel, thresholdPx) {
                detectDragGestures(
                    onDragStart = {
                        horizontalDrag = 0f
                        verticalDrag = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        horizontalDrag += amount.x
                        verticalDrag += amount.y
                    },
                    onDragEnd = {
                        if (shouldOpenEdgeGuide(panel, horizontalDrag, verticalDrag, thresholdPx)) {
                            onOpen(panel)
                        }
                    },
                )
            }
            .semantics {
                contentDescription = if (panel == GuidePanel.ENVIRONMENT) {
                    "左侧环境指导手势区"
                } else {
                    "右侧人物指导手势区"
                }
            },
    )
}

@Composable
private fun CameraEdgeHandle(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = AppDimensions.MinTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        color = AppColors.CameraChromeSurface,
        contentColor = AppColors.CameraChromeText,
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = AppDimensions.Space12, vertical = AppDimensions.Space12),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CameraBottomControls(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.CameraChromeSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        shadowElevation = AppDimensions.GlassElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppDimensions.Space16, vertical = AppDimensions.Space12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OverlayModeControls(
                selected = uiState.overlayMode,
                onSelected = { onEvent(CameraUiEvent.OverlayModeSelected(it)) },
            )
            Spacer(Modifier.height(AppDimensions.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(AppDimensions.RadiusSmall),
                    color = AppColors.AccentBlueSoft,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
                ) {
                    Text(
                        "参考图 · Demo",
                        modifier = Modifier.padding(horizontal = AppDimensions.Space12, vertical = AppDimensions.Space12),
                        color = AppColors.CameraChromeText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                CameraShutter(
                    enabled = uiState.canCapture,
                    onClick = onCapture,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { onEvent(CameraUiEvent.GridToggled) }) {
                        Text(
                            if (uiState.gridVisible) "网格开" else "网格关",
                            color = AppColors.CameraChromeText,
                        )
                    }
                    Text(
                        "镜头切换 · 禁用",
                        color = AppColors.CameraChromeDisabledText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = if (uiState.captureInFlight) {
                    "保存中…"
                } else {
                    "已拍 ${uiState.captureCount} 张 · 仅保存至应用缓存"
                },
                color = AppColors.CameraChromeSecondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun OverlayModeControls(
    selected: OverlayMode,
    onSelected: (OverlayMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space4)) {
        OverlayMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Surface(
                onClick = { onSelected(mode) },
                modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget),
                shape = RoundedCornerShape(percent = 50),
                color = if (isSelected) AppColors.AccentBlueSoft else AppColors.SurfacePrimary,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
            ) {
                Text(
                    (when (mode) {
                        OverlayMode.SKELETON -> "骨架 · 示意"
                        OverlayMode.OUTLINE -> "轮廓 · 示意"
                        OverlayMode.REFERENCE -> "参考图 · 示意"
                    }) + if (isSelected) " · 已选" else "",
                    modifier = Modifier.padding(horizontal = AppDimensions.Space8, vertical = AppDimensions.Space12),
                    color = AppColors.CameraChromeText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CameraShutter(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(AppDimensions.ShutterSize)
            .semantics { contentDescription = if (enabled) "拍摄" else "相机准备中" },
        shape = CircleShape,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(3.dp, AppColors.CameraChromeText),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(AppDimensions.ShutterInnerSize),
                shape = CircleShape,
                color = if (enabled) AppColors.CameraChromeText else AppColors.CameraChromeDisabledGraphic,
            ) {}
        }
    }
}

@Composable
private fun GuidePanelSheet(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val environment = uiState.selectedGuidePanel == GuidePanel.ENVIRONMENT
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(AppDimensions.GuidePanelWidthFraction),
        color = AppColors.SurfacePrimary.copy(alpha = 0.96f),
        shadowElevation = AppDimensions.FloatingElevation,
        shape = RoundedCornerShape(
            topStart = if (environment) 0.dp else AppDimensions.RadiusExtraLarge,
            topEnd = if (environment) AppDimensions.RadiusExtraLarge else 0.dp,
            bottomStart = if (environment) 0.dp else AppDimensions.RadiusExtraLarge,
            bottomEnd = if (environment) AppDimensions.RadiusExtraLarge else 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimensions.Space24, vertical = AppDimensions.Space32),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (environment) "环境指导" else "人物指导",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                )
                TextButton(onClick = onClose) { Text("关闭") }
            }
            Text(
                if (environment) "这个背景能讲什么故事" else "怎么站，手放哪里，眼神看哪里",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Demo 指引 · 尚未连接 AI",
                color = AppColors.AccentBlue,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(AppDimensions.Space20))
            if (environment) {
                listOf(
                    "当前场景" to "室内窗边",
                    "背景价值" to "窗边自然光能形成柔和层次，适合安静的人像故事",
                    "可讲的故事" to "像是在等待一个很想见的人",
                    "推荐站位" to "靠近窗边，但不要完全贴墙",
                    "机位建议" to "平视略低",
                    "构图建议" to "人物偏右，左侧留白",
                    "Plan B" to "如果背景太乱，就靠近拍半身",
                ).forEach { (title, detail) ->
                    GuideItem(status = "Demo", title = title, detail = detail)
                    Spacer(Modifier.height(AppDimensions.Space12))
                }
            } else {
                OverlayModeControls(
                    selected = uiState.overlayMode,
                    onSelected = { onEvent(CameraUiEvent.OverlayModeSelected(it)) },
                )
                Spacer(Modifier.height(AppDimensions.Space16))
                listOf(
                    Triple("需要调整", "动作摘要", "身体微侧，重心放在后脚，视线看向窗外"),
                    Triple("接近", "头部", "下巴微收，头部向窗边轻转"),
                    Triple("需要调整", "肩膀", "肩膀放松，近镜头一侧稍向后"),
                    Triple("尚未判断", "手部", "一只手自然垂落，另一只手轻触衣角"),
                    Triple("接近", "腰背", "脊背自然伸展，不要刻意挺胸"),
                    Triple("尚未判断", "腿部", "双脚前后错开，膝盖不要锁死"),
                    Triple("需要调整", "重心", "重心更多落在远离镜头的一侧"),
                    Triple("接近", "情绪提示", "像是在等一个很想见的人，目光轻轻看向远处，带一点期待"),
                    Triple("尚未判断", "Plan B", "如果姿势太别扭，先只保留下巴微收、肩膀放松和看向窗外"),
                ).forEach { (status, title, detail) ->
                    GuideItem(status = status, title = title, detail = detail)
                    Spacer(Modifier.height(AppDimensions.Space12))
                }
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimensions.PrimaryButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentBlue),
            ) {
                Text("返回取景")
            }
        }
    }
}

/** Static panel surface for Compose tooling; it never initializes CameraX. */
@Composable
fun CameraGuidePanelPreviewContent(
    panel: DirectorGuidePanel,
    modifier: Modifier = Modifier,
) {
    val previewPanel = if (panel == DirectorGuidePanel.ENVIRONMENT) {
        GuidePanel.ENVIRONMENT
    } else {
        GuidePanel.SUBJECT
    }
    Box(modifier.fillMaxSize().background(Color(0xFF7A8491))) {
        GuidePanelSheet(
            uiState = CameraUiState(selectedGuidePanel = previewPanel),
            onEvent = {},
            onClose = {},
            modifier = Modifier.align(
                if (previewPanel == GuidePanel.ENVIRONMENT) Alignment.CenterStart else Alignment.CenterEnd,
            ),
        )
    }
}

@Composable
private fun GuideItem(status: String, title: String, detail: String) {
    Surface(
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        color = AppColors.SurfacePrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
    ) {
        Column(modifier = Modifier.padding(AppDimensions.Space16)) {
            Text(status, color = AppColors.AccentBlue, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(AppDimensions.Space4))
            Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(AppDimensions.Space4))
            Text(detail, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DemoOverlay(mode: OverlayMode, showGrid: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (showGrid) GridOverlay()
        when (mode) {
            OverlayMode.REFERENCE -> OverlayLabel("参考轮廓 · 示意")
            OverlayMode.SKELETON -> OverlayLabel("骨架 · 示意")
            OverlayMode.OUTLINE -> OverlayLabel("人物轮廓 · 示意")
        }
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.62f)
        val stroke = 1.dp.toPx()
        drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), stroke)
        drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), stroke)
        drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), stroke)
        drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), stroke)
    }
}

@Composable
private fun OverlayLabel(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.padding(top = 176.dp),
            color = AppColors.CameraChromeSurface,
            shape = RoundedCornerShape(AppDimensions.RadiusSmall),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = AppDimensions.Space12, vertical = AppDimensions.Space8),
                color = AppColors.CameraChromeText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
