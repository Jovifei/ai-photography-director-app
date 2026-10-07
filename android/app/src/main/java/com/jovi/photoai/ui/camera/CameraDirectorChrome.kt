package com.jovi.photoai.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.camera.CameraLens
import com.jovi.photoai.camera.ZoomCapability
import com.jovi.photoai.domain.model.OverlayMode
import com.jovi.photoai.reference.CameraDirectorGuidance
import com.jovi.photoai.ui.pose.PoseDirectionShootPanel
import com.jovi.photoai.ui.pose.SelectedPoseDirection
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import java.util.Locale
import kotlin.math.roundToInt

/** Preview-only adapter. Runtime panel state is always [GuidePanel] in [CameraUiState]. */
enum class DirectorGuidePanel { ENVIRONMENT, SUBJECT }

@Composable
internal fun CameraDirectorChrome(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onSave: () -> Unit = {},
    saveEnabled: Boolean = false,
    saveStatus: String? = null,
    referenceGuidance: CameraDirectorGuidance? = null,
    directCaptureMode: Boolean = false,
    referenceCardVisible: Boolean = false,
    exposureRange: IntRange? = null,
    exposureStepEv: Float = 0f,
    exposureIndex: Int = 0,
    pendingExposureIndex: Int? = null,
    controlsEnabled: Boolean = false,
    exposureStatus: String? = null,
    onExposureSelected: (Int) -> Unit = {},
    zoomCapability: ZoomCapability? = null,
    confirmedZoomRatio: Float = 1f,
    pendingZoomRatio: Float? = null,
    zoomStatus: String? = null,
    onZoomSelected: (Float) -> Unit = {},
    availableLenses: Set<CameraLens> = emptySet(),
    currentLens: CameraLens = CameraLens.BACK,
    lensStatus: String? = null,
    onLensSelected: (CameraLens) -> Unit = {},
    captureEnabled: Boolean = uiState.canCapture,
    poseDirection: SelectedPoseDirection? = null,
    modifier: Modifier = Modifier,
    referenceCard: (@Composable () -> Unit)? = null,
) {
    val hardwareEnabled = controlsEnabled && pendingZoomRatio == null
    var showZoom by remember(zoomCapability, currentLens) { mutableStateOf(false) }
    var sliderZoom by remember(zoomCapability, currentLens, confirmedZoomRatio) {
        mutableFloatStateOf(zoomCapability?.let {
            (if (confirmedZoomRatio.isFinite()) confirmedZoomRatio else it.currentRatio)
                .coerceIn(it.minRatio, it.maxRatio)
        } ?: 1f)
    }
    var showExposure by remember { mutableStateOf(false) }
    var sliderIndex by remember(exposureRange, exposureIndex, pendingExposureIndex) {
        mutableFloatStateOf((pendingExposureIndex ?: exposureIndex).toFloat())
    }
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLandscape = maxWidth > maxHeight
        if (!directCaptureMode && referenceGuidance == null) {
            DemoOverlay(mode = uiState.overlayMode, showGrid = uiState.gridVisible)
        }

        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Only visible guidance receives scroll gestures; blank preview retains tap focus.
                Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("camera-upper-content")
                    .verticalScroll(rememberScrollState())) {
                    CameraTopBar(
                        referenceGuidance = referenceGuidance,
                        directCaptureMode = directCaptureMode,
                        onBack = closePanelOrBack,
                    )
                    referenceCard?.invoke()
                    CameraHint(
                        uiState = uiState,
                        referenceGuidance = referenceGuidance,
                        directCaptureMode = directCaptureMode,
                        compactLandscape = compactLandscape,
                        poseDirection = poseDirection,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                    )
                    if (poseDirection != null) {
                        PoseDirectionShootPanel(
                            selection = poseDirection,
                            compact = compactLandscape,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            }
            CameraBottomControls(
                uiState = uiState, onEvent = onEvent, onCapture = onCapture, onSave = onSave,
                saveEnabled = saveEnabled, saveStatus = saveStatus, referenceGuidance = referenceGuidance,
                directCaptureMode = directCaptureMode, exposureAvailable = exposureRange != null,
                exposureIndex = exposureIndex, exposureStepEv = exposureStepEv, controlsEnabled = hardwareEnabled,
                exposureStatus = exposureStatus, onShowExposure = { showExposure = true },
                zoomCapability = zoomCapability, confirmedZoomRatio = confirmedZoomRatio,
                onShowZoom = { if (hardwareEnabled) showZoom = true }, zoomStatus = zoomStatus,
                availableLenses = availableLenses, currentLens = currentLens, lensStatus = lensStatus,
                onLensSelected = onLensSelected, captureEnabled = captureEnabled && pendingZoomRatio == null,
                captureState = when {
                    uiState.captureInFlight -> "保存中"
                    pendingZoomRatio != null -> "正在确认变焦"
                    captureEnabled -> "可以拍摄"
                    else -> "相机准备中"
                },
                compactLandscape = compactLandscape,
            )
        }

        if (!directCaptureMode) {
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
        }


        if (!directCaptureMode && uiState.selectedGuidePanel != GuidePanel.NONE) {
            GuidePanelSheet(
                uiState = uiState,
                onEvent = onEvent,
                onClose = { onEvent(CameraUiEvent.ClosePanel) },
                referenceGuidance = referenceGuidance,
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
    if (showZoom && zoomCapability != null) AlertDialog(
        onDismissRequest = { showZoom = false },
        title = { Text("手动变焦") },
        text = {
            Column {
                Text("已确认：${zoomLabel(confirmedZoomRatio)}")
                Slider(value = sliderZoom,
                    modifier = Modifier.testTag("zoom-slider").semantics { contentDescription = "变焦，倍率" },
                    onValueChange = { if (hardwareEnabled && it.isFinite()) {
                        sliderZoom = it.coerceIn(zoomCapability.minRatio, zoomCapability.maxRatio)
                    } },
                    valueRange = zoomCapability.minRatio..zoomCapability.maxRatio,
                    enabled = hardwareEnabled,
                    onValueChangeFinished = {
                        if (hardwareEnabled && sliderZoom.isFinite()) onZoomSelected(
                            sliderZoom.coerceIn(zoomCapability.minRatio, zoomCapability.maxRatio))
                    })
                Text("拖动选择：${zoomLabel(sliderZoom)}")
                pendingZoomRatio?.let { Text("正在确认：${zoomLabel(it)}") }
                zoomStatus?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = { showZoom = false }) { Text("完成") } },
    )
    if (showExposure && exposureRange != null) AlertDialog(
        onDismissRequest = { showExposure = false },
        title = { Text("曝光补偿") },
        text = {
            Column {
                Text("当前：${exposureLabel(exposureIndex, exposureStepEv)}")
                Slider(value = sliderIndex,
                    modifier = Modifier.testTag("exposure-slider").semantics { contentDescription = "曝光补偿，EV" },
                    onValueChange = { sliderIndex = it.roundToInt().coerceIn(exposureRange).toFloat() },
                    valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                    steps = (exposureRange.last - exposureRange.first - 1).coerceAtLeast(0),
                    enabled = hardwareEnabled,
                    onValueChangeFinished = { if (hardwareEnabled) onExposureSelected(sliderIndex.roundToInt()) })
                Text("待设置：${exposureLabel(sliderIndex.roundToInt(), exposureStepEv)}")
                if (pendingExposureIndex != null) Text("正在确认曝光设置…")
                exposureStatus?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = { showExposure = false }) { Text("完成") } },
        dismissButton = { TextButton(onClick = {
            sliderIndex = 0f
            if (hardwareEnabled) onExposureSelected(0)
        }, enabled = hardwareEnabled) { Text("重置") } },
    )
}

private fun zoomLabel(ratio: Float): String = String.format(Locale.ROOT, "%.2fx", ratio)

private fun lensLabel(lens: CameraLens): String = if (lens == CameraLens.BACK) "后置" else "前置"

private fun exposureLabel(index: Int, stepEv: Float): String =
    if (index == 0) "0 EV" else String.format(Locale.ROOT, "%+.1f EV", index * stepEv)

@Composable
private fun CameraTopBar(
    referenceGuidance: CameraDirectorGuidance?,
    directCaptureMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
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
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)
                .semantics { contentDescription = "返回" }) {
                Text("返回", color = AppColors.CameraChromeText)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        directCaptureMode -> "基础拍摄"
                        referenceGuidance == null -> "参考图拍摄"
                        else -> "Camera Director"
                    },
                    color = AppColors.CameraChromeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        directCaptureMode -> "无参考指导"
                        referenceGuidance != null -> "当前参考图：${referenceGuidance.referenceTitle} · ${referenceGuidance.sourceLabel}"
                        else -> "当前参考图：窗边等待感 · Demo"
                    },
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
private fun CameraHint(
    uiState: CameraUiState,
    referenceGuidance: CameraDirectorGuidance?,
    directCaptureMode: Boolean,
    compactLandscape: Boolean,
    poseDirection: SelectedPoseDirection? = null,
    modifier: Modifier = Modifier,
) {
    val text = when {
        uiState.message == CameraUiMessage.CAPTURE_FAILED -> "拍摄未完成，请稍后再试"
        poseDirection != null && directCaptureMode -> "构图口令拍摄\n下方火柴人与口令可对照现场"
        poseDirection != null && referenceGuidance != null -> "${referenceGuidance.sourceLabel}\n${referenceGuidance.centerHint}"
        poseDirection != null -> "构图口令拍摄\n下方火柴人与口令可对照现场"
        directCaptureMode -> "基础拍摄 · 无参考指导\n请先确认现场安全与取景。"
        referenceGuidance != null -> "${referenceGuidance.sourceLabel}\n${referenceGuidance.centerHint}"
        uiState.currentGuidance != null -> {
            "${uiState.currentGuidance.title} · Demo\n${uiState.currentGuidance.instruction}"
        }
        else -> "暂无 Demo 指导，请先确认现场安全与取景。"
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = if (compactLandscape) 64.dp else 40.dp)
            .testTag("camera-guidance-hint"),
        color = AppColors.CameraChromeSurface,
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        shadowElevation = AppDimensions.GlassElevation,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimensions.Space16, vertical = AppDimensions.Space12),
            color = AppColors.CameraChromeText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = Int.MAX_VALUE,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraBottomControls(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    onCapture: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saveStatus: String?,
    referenceGuidance: CameraDirectorGuidance?,
    directCaptureMode: Boolean,
    exposureAvailable: Boolean,
    exposureIndex: Int,
    exposureStepEv: Float,
    controlsEnabled: Boolean,
    exposureStatus: String?,
    onShowExposure: () -> Unit,
    zoomCapability: ZoomCapability?,
    confirmedZoomRatio: Float,
    onShowZoom: () -> Unit,
    zoomStatus: String?,
    availableLenses: Set<CameraLens>,
    currentLens: CameraLens,
    lensStatus: String?,
    onLensSelected: (CameraLens) -> Unit,
    captureEnabled: Boolean,
    captureState: String,
    compactLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compactLandscape) {
        Surface(modifier = modifier.fillMaxWidth().testTag("camera-controls"),
            color = AppColors.CameraChromeSurface) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (exposureAvailable) TextButton(onClick = onShowExposure, enabled = controlsEnabled,
                            modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) { Text("曝光") }
                        if (zoomCapability != null) TextButton(onClick = onShowZoom, enabled = controlsEnabled,
                            modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget).testTag("zoom-open")) {
                            Text("变焦 ${zoomLabel(confirmedZoomRatio)}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (CameraLens.BACK in availableLenses && CameraLens.FRONT in availableLenses) {
                            val next = if (currentLens == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
                            TextButton(onClick = { if (controlsEnabled) onLensSelected(next) }, enabled = controlsEnabled,
                                modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget).testTag("camera-lens-switch")) {
                                Text("${lensLabel(currentLens)} → ${lensLabel(next)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = { onEvent(CameraUiEvent.GridToggled) },
                            modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
                            Text(if (uiState.gridVisible) "网格开" else "网格关", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    listOfNotNull(exposureStatus, zoomStatus, lensStatus).forEach {
                        Text(it, color = AppColors.CameraChromeText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    if (!directCaptureMode && referenceGuidance == null) OverlayModeControls(
                        selected = uiState.overlayMode, onSelected = { onEvent(CameraUiEvent.OverlayModeSelected(it)) })
                }
                CameraShutter(enabled = captureEnabled, captureState = captureState, onClick = onCapture)
                Column(Modifier.width(180.dp)) {
                    Button(onClick = onSave, enabled = saveEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.MinTouchTarget)) { Text("保存照片") }
                    Text(saveStatus ?: "原片请查看成片库", color = AppColors.CameraChromeText,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(if (uiState.captureInFlight) "保存中…" else "本次取景已拍 ${uiState.captureCount} 张",
                        color = AppColors.CameraChromeSecondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("camera-controls"),
        color = AppColors.CameraChromeSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        shadowElevation = AppDimensions.GlassElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppDimensions.Space16, vertical = AppDimensions.Space12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
                if (exposureAvailable) TextButton(onClick = onShowExposure, enabled = controlsEnabled,
                    modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)) {
                    Text("曝光")
                    Text(" · ${exposureLabel(exposureIndex, exposureStepEv)}")
                }
                if (zoomCapability != null) TextButton(onClick = onShowZoom, enabled = controlsEnabled,
                    modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget).testTag("zoom-open")) {
                    Text("变焦 · ${zoomLabel(confirmedZoomRatio)}")
                }
                if (CameraLens.BACK in availableLenses && CameraLens.FRONT in availableLenses) {
                    val nextLens = if (currentLens == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
                    TextButton(onClick = { if (controlsEnabled) onLensSelected(nextLens) },
                        enabled = controlsEnabled,
                        modifier = Modifier.heightIn(min = AppDimensions.MinTouchTarget)
                            .testTag("camera-lens-switch")) {
                        Text("${lensLabel(currentLens)} → ${lensLabel(nextLens)}",
                            color = AppColors.CameraChromeText,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            zoomStatus?.let { Text(it, color = AppColors.CameraChromeText,
                style = MaterialTheme.typography.labelSmall) }
            lensStatus?.let { Text(it, color = AppColors.CameraChromeText,
                style = MaterialTheme.typography.labelSmall) }
            exposureStatus?.let { Text(it, color = AppColors.CameraChromeText,
                style = MaterialTheme.typography.labelSmall) }
            if (directCaptureMode) {
                Text(
                    "基础拍摄 · 无参考指导",
                    color = AppColors.CameraChromeText,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else if (referenceGuidance == null) {
                OverlayModeControls(
                    selected = uiState.overlayMode,
                    onSelected = { onEvent(CameraUiEvent.OverlayModeSelected(it)) },
                )
            } else {
                Text(
                    "参考内容指导 · 未启用实时 Pose",
                    color = AppColors.CameraChromeText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(AppDimensions.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(AppDimensions.RadiusSmall),
                    color = AppColors.AccentBlueSoft,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
                ) {
                    Text(
                        when {
                            directCaptureMode -> "无参考指导"
                            referenceGuidance != null -> "参考图 · ${referenceGuidance.sourceLabel}"
                            else -> "参考图 · Demo"
                        },
                        modifier = Modifier.padding(horizontal = AppDimensions.Space12, vertical = AppDimensions.Space12),
                        color = AppColors.CameraChromeText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                CameraShutter(
                    enabled = captureEnabled,
                    captureState = captureState,
                    onClick = onCapture,
                )
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { onEvent(CameraUiEvent.GridToggled) }) {
                        Text(
                            if (uiState.gridVisible) "网格开" else "网格关",
                            color = AppColors.CameraChromeText,
                        )
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppDimensions.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentBlue),
            ) {
                Text("保存照片")
            }
            Text(
                text = saveStatus ?: "原片先存入本机成片库；保存副本时由系统选择位置",
                color = AppColors.CameraChromeSecondaryText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = AppDimensions.Space4),
            )
            Text(
                text = if (uiState.captureInFlight) {
                    "保存中…"
                } else {
                    "本次取景已拍 ${uiState.captureCount} 张 · 原片请查看成片库"
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
private fun CameraShutter(enabled: Boolean, captureState: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(AppDimensions.ShutterSize)
            .semantics { contentDescription = "拍摄"; stateDescription = captureState },
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
    referenceGuidance: CameraDirectorGuidance?,
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
                    modifier = Modifier.weight(1f),
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
                referenceGuidance?.sourceLabel ?: "Demo 指引 · 尚未连接 AI",
                color = AppColors.AccentBlue,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(AppDimensions.Space20))
            val referenceItems = referenceGuidance?.let {
                if (environment) it.environment else it.subject
            }
            if (referenceItems != null) {
                referenceItems.forEach { item ->
                    GuideItem(
                        status = referenceGuidance.sourceLabel,
                        title = item.title,
                        detail = item.detail,
                    )
                    Spacer(Modifier.height(AppDimensions.Space12))
                }
            } else if (environment) {
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
                    .heightIn(min = AppDimensions.PrimaryButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentBlue),
            ) {
                Text("返回取景", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
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
            referenceGuidance = null,
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
