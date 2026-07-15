package com.jovi.photoai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

enum class GlassTone {
    LIGHT,
    CAMERA_LIGHT,
    CAMERA_DARK
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.LIGHT,
    shape: Shape = RoundedCornerShape(AppDimensions.RadiusLarge),
    contentPadding: PaddingValues = PaddingValues(AppDimensions.CardPadding),
    content: @Composable BoxScope.() -> Unit
) {
    val containerColor = when (tone) {
        GlassTone.LIGHT -> AppColors.SurfacePrimary.copy(alpha = 0.82f)
        GlassTone.CAMERA_LIGHT -> AppColors.CameraGlassLight
        GlassTone.CAMERA_DARK -> AppColors.CameraGlassDark
    }
    val contentColor = when (tone) {
        GlassTone.LIGHT -> AppColors.TextPrimary
        GlassTone.CAMERA_LIGHT -> AppColors.TextPrimary
        GlassTone.CAMERA_DARK -> AppColors.CameraText
    }
    val borderColor = when (tone) {
        GlassTone.LIGHT -> AppColors.GlassBorder
        GlassTone.CAMERA_LIGHT -> AppColors.GlassHighlight
        GlassTone.CAMERA_DARK -> AppColors.CameraBorder
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(AppDimensions.GlassStroke, borderColor),
        shadowElevation = AppDimensions.GlassElevation
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/** Convenience overload for callers that need uniform padding. */
@Composable
fun GlassSurface(
    contentPadding: Dp,
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.LIGHT,
    shape: Shape = RoundedCornerShape(AppDimensions.RadiusLarge),
    content: @Composable BoxScope.() -> Unit
) = GlassSurface(
    modifier = modifier,
    tone = tone,
    shape = shape,
    contentPadding = PaddingValues(contentPadding),
    content = content
)

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.LIGHT,
    contentColor: Color = when (tone) {
        GlassTone.LIGHT, GlassTone.CAMERA_LIGHT -> AppColors.TextSecondary
        GlassTone.CAMERA_DARK -> AppColors.CameraText
    }
) {
    GlassSurface(
        modifier = modifier,
        tone = tone,
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(
            horizontal = AppDimensions.Space12,
            vertical = AppDimensions.Space8
        )
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = contentColor,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
    }
}
