package com.jovi.photoai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

enum class EdgeGuideSide(val label: String) {
    ENVIRONMENT("环境"),
    SUBJECT("人物")
}

/**
 * Explicit accessible entry for a side guide. The separate edge-drag sensor may remain 32dp,
 * while this tap target is always at least 48dp.
 */
@Composable
fun EdgeGuideHandle(
    side: EdgeGuideSide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "打开${side.label}指导"
) {
    val shape = when (side) {
        EdgeGuideSide.ENVIRONMENT -> RoundedCornerShape(
            topStart = 0.dp,
            bottomStart = 0.dp,
            topEnd = AppDimensions.RadiusMedium,
            bottomEnd = AppDimensions.RadiusMedium
        )
        EdgeGuideSide.SUBJECT -> RoundedCornerShape(
            topStart = AppDimensions.RadiusMedium,
            bottomStart = AppDimensions.RadiusMedium,
            topEnd = 0.dp,
            bottomEnd = 0.dp
        )
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = AppDimensions.EdgeHandleWidth)
            .heightIn(min = AppDimensions.MinTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = shape,
        color = AppColors.CameraGlassDark,
        contentColor = AppColors.CameraText,
        border = BorderStroke(AppDimensions.GlassStroke, AppColors.CameraBorder)
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = AppDimensions.Space12,
                vertical = AppDimensions.Space12
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = side.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
