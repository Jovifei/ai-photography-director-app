package com.jovi.photoai.ui.pose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jovi.photoai.domain.pose.PoseEstimate

const val PoseDebugOverlayContentDescription = "Pose Debug / 非产品功能"

/** Isolated diagnostic overlay; not wired into CameraScreen and not a product result. */
@Composable
fun PoseDebugOverlay(
    estimate: PoseEstimate?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = PoseDebugOverlayContentDescription },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            estimate?.person?.points?.values?.forEach { point ->
                drawCircle(
                    color = Color.Magenta,
                    radius = 5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(
                        x = (point.xNorm * size.width).toFloat(),
                        y = (point.yNorm * size.height).toFloat(),
                    ),
                )
            }
        }
        Text(
            text = "$PoseDebugOverlayContentDescription · ${estimate?.state ?: "NO_ESTIMATE"}",
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color.White,
        )
    }
}
