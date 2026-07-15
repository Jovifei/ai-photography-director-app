package com.jovi.photoai.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jovi.photoai.domain.model.OverlayMode
import com.jovi.photoai.ui.components.AppSegmentedControl
import com.jovi.photoai.ui.components.EdgeGuideHandle
import com.jovi.photoai.ui.components.EdgeGuideSide
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

enum class DirectorGuidePanel {
    ENVIRONMENT,
    SUBJECT,
}

@Composable
fun CameraDirectorChrome(
    captureCount: Int,
    captureInFlight: Boolean,
    captureError: Boolean,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayMode by remember { mutableStateOf(OverlayMode.SKELETON) }
    var showGrid by remember { mutableStateOf(true) }
    var openPanel by remember { mutableStateOf<DirectorGuidePanel?>(null) }

    BackHandler(enabled = openPanel != null) { openPanel = null }

    Box(modifier = modifier.fillMaxSize()) {
        DemoOverlay(mode = overlayMode, showGrid = showGrid)

        CameraTopBar(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CameraHint(
            captureCount = captureCount,
            captureError = captureError,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 88.dp),
        )

        EdgeGestureZone(
            panel = DirectorGuidePanel.ENVIRONMENT,
            onOpen = { openPanel = it },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        EdgeGestureZone(
            panel = DirectorGuidePanel.SUBJECT,
            onOpen = { openPanel = it },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        EdgeGuideHandle(
            side = EdgeGuideSide.ENVIRONMENT,
            onClick = { openPanel = DirectorGuidePanel.ENVIRONMENT },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        EdgeGuideHandle(
            side = EdgeGuideSide.SUBJECT,
            onClick = { openPanel = DirectorGuidePanel.SUBJECT },
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        CameraBottomControls(
            overlayMode = overlayMode,
            onOverlaySelected = { overlayMode = it },
            showGrid = showGrid,
            onGridChanged = { showGrid = it },
            captureCount = captureCount,
            captureInFlight = captureInFlight,
            onCapture = onCapture,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        openPanel?.let { panel ->
            GuidePanelSheet(
                panel = panel,
                onClose = { openPanel = null },
                modifier = Modifier.align(
                    if (panel == DirectorGuidePanel.ENVIRONMENT) {
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回" }) {
            Text("返回", color = AppColors.CameraText)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "参考图拍摄",
                color = AppColors.CameraText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "当前参考图：窗边等待感",
                color = AppColors.CameraTextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(
            onClick = {},
            modifier = Modifier
                .semantics {
                    contentDescription = "更多，本阶段为界面占位"
                },
        ) {
            Text("更多", color = AppColors.CameraText)
        }
    }
}

@Composable
private fun CameraHint(captureCount: Int, captureError: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 52.dp),
        color = AppColors.CameraGlassDark,
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraBorder),
    ) {
        Text(
            text = if (captureError) {
                "未能保存照片，请稍后再试"
            } else if (captureCount == 0) {
                "向右移动半步 · Demo 指引"
            } else {
                "已保存 $captureCount 张至应用缓存"
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = AppColors.CameraText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EdgeGestureZone(
    panel: DirectorGuidePanel,
    onOpen: (DirectorGuidePanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val isLeft = panel == DirectorGuidePanel.ENVIRONMENT
    Box(
        modifier = modifier
            .width(AppDimensions.EdgeGestureWidth)
            .fillMaxHeight()
            .pointerInput(panel) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onHorizontalDrag = { _, amount -> accumulatedDrag += amount },
                    onDragEnd = {
                        val opens = if (isLeft) accumulatedDrag > 56f else accumulatedDrag < -56f
                        if (opens) onOpen(panel)
                    },
                )
            }
            .semantics {
                contentDescription = if (isLeft) "左侧环境指导手势区" else "右侧人物指导手势区"
            },
    )
}

@Composable
private fun CameraBottomControls(
    overlayMode: OverlayMode,
    onOverlaySelected: (OverlayMode) -> Unit,
    showGrid: Boolean,
    onGridChanged: (Boolean) -> Unit,
    captureCount: Int,
    captureInFlight: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppSegmentedControl(
            options = OverlayMode.entries,
            selectedOption = overlayMode,
            onOptionSelected = onOverlaySelected,
            label = {
                when (it) {
                    OverlayMode.SKELETON -> "骨架 · 示意"
                    OverlayMode.OUTLINE -> "轮廓 · 示意"
                    OverlayMode.REFERENCE -> "参考图 · 示意"
                }
            },
            cameraStyle = true,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.CameraGlassLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraBorder),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("参考", color = AppColors.TextPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Box(
                modifier = Modifier
                    .size(AppDimensions.ShutterSize)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .background(
                        if (captureInFlight) Color.White.copy(alpha = 0.5f) else Color.White,
                        CircleShape,
                    )
                    .clickable(enabled = !captureInFlight, onClick = onCapture)
                    .semantics { contentDescription = if (captureInFlight) "正在拍摄" else "拍摄" },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { onGridChanged(!showGrid) }) {
                    Text(if (showGrid) "网格开" else "网格关", color = AppColors.CameraText)
                }
                Text("镜头切换 · 禁用", color = AppColors.CameraTextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (captureInFlight) "保存中…" else "已拍 $captureCount 张 · 仅保存至应用缓存",
            color = AppColors.CameraTextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun GuidePanelSheet(
    panel: DirectorGuidePanel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val environment = panel == DirectorGuidePanel.ENVIRONMENT
    var panelOverlayMode by remember(panel) { mutableStateOf(OverlayMode.SKELETON) }
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(AppDimensions.GuidePanelWidthFraction),
        color = Color(0xFFF7F7F9).copy(alpha = 0.96f),
        shadowElevation = 12.dp,
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
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
                if (environment) {
                    "这个背景能讲什么故事"
                } else {
                    "怎么站，手放哪里，眼神看哪里"
                },
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Demo 指引 · 尚未连接 AI",
                color = AppColors.AccentBlue,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(20.dp))
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
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                AppSegmentedControl(
                    options = OverlayMode.entries,
                    selectedOption = panelOverlayMode,
                    onOptionSelected = { panelOverlayMode = it },
                    label = {
                        when (it) {
                            OverlayMode.SKELETON -> "骨架"
                            OverlayMode.OUTLINE -> "轮廓"
                            OverlayMode.REFERENCE -> "参考图"
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
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
                    Spacer(Modifier.height(12.dp))
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

/** Static panel surface for Compose tooling; it never inspects a camera frame. */
@Composable
fun CameraGuidePanelPreviewContent(
    panel: DirectorGuidePanel,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color(0xFF7A8491))) {
        GuidePanelSheet(
            panel = panel,
            onClose = {},
            modifier = Modifier.align(
                if (panel == DirectorGuidePanel.ENVIRONMENT) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ),
        )
    }
}

@Composable
private fun GuideItem(status: String, title: String, detail: String) {
    Surface(
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        color = Color.White.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(status, color = AppColors.AccentBlue, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DemoOverlay(mode: OverlayMode, showGrid: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (showGrid) Canvas(Modifier.fillMaxSize()) {
            val lineColor = Color.White.copy(alpha = 0.62f)
            drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.dp.toPx())
            drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.dp.toPx())
            drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.dp.toPx())
            drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.dp.toPx())
        }
        when (mode) {
        OverlayMode.REFERENCE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(0.58f)
                    .fillMaxHeight(0.58f)
                    .border(2.dp, Color.White.copy(alpha = 0.68f), RoundedCornerShape(24.dp)),
            )
            Text(
                "参考轮廓 · 示意",
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(AppColors.CameraGlassDark, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        OverlayMode.SKELETON -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 180.dp, height = 360.dp)) {
                val c = Color.White.copy(alpha = 0.72f)
                val stroke = 3.dp.toPx()
                drawCircle(c, 24.dp.toPx(), Offset(size.width / 2f, 32.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawLine(c, Offset(size.width / 2f, 56.dp.toPx()), Offset(size.width / 2f, 190.dp.toPx()), stroke)
                drawLine(c, Offset(size.width / 2f, 88.dp.toPx()), Offset(24.dp.toPx(), 164.dp.toPx()), stroke)
                drawLine(c, Offset(size.width / 2f, 88.dp.toPx()), Offset(size.width - 24.dp.toPx(), 142.dp.toPx()), stroke)
                drawLine(c, Offset(size.width / 2f, 190.dp.toPx()), Offset(48.dp.toPx(), size.height - 12.dp.toPx()), stroke)
                drawLine(c, Offset(size.width / 2f, 190.dp.toPx()), Offset(size.width - 34.dp.toPx(), size.height - 12.dp.toPx()), stroke)
            }
            Text(
                "骨架 · 示意",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 164.dp)
                    .background(AppColors.CameraGlassDark, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        OverlayMode.OUTLINE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 176.dp, height = 356.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.68f), RoundedCornerShape(percent = 50)),
            )
            Text(
                "人物轮廓 · 示意",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 164.dp)
                    .background(AppColors.CameraGlassDark, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        }
    }
}
