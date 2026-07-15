package com.jovi.photoai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppDimensions

/** Static abstract background for Compose previews. Never substitutes the runtime PreviewView. */
@Composable
fun CameraPreviewPlaceholder(
    modifier: Modifier = Modifier,
    label: String = "设计预览",
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFBFC8D6),
                        Color(0xFF758395),
                        Color(0xFF3F4A59)
                    )
                )
            )
            .semantics { contentDescription = "相机界面设计预览，不是真实相机画面" }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val lineColor = Color.White.copy(alpha = 0.22f)
            val stroke = 1.dp.toPx()
            drawLine(
                color = lineColor,
                start = Offset(size.width / 3f, 0f),
                end = Offset(size.width / 3f, size.height),
                strokeWidth = stroke
            )
            drawLine(
                color = lineColor,
                start = Offset(size.width * 2f / 3f, 0f),
                end = Offset(size.width * 2f / 3f, size.height),
                strokeWidth = stroke
            )
            drawLine(
                color = lineColor,
                start = Offset(0f, size.height / 3f),
                end = Offset(size.width, size.height / 3f),
                strokeWidth = stroke
            )
            drawLine(
                color = lineColor,
                start = Offset(0f, size.height * 2f / 3f),
                end = Offset(size.width, size.height * 2f / 3f),
                strokeWidth = stroke
            )
        }
        GlassPill(
            text = label,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = AppDimensions.Space24),
            tone = GlassTone.CAMERA_DARK
        )
        content()
    }
}
