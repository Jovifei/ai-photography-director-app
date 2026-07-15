package com.jovi.photoai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = AppDimensions.MinTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.AccentBlue,
            contentColor = Color.White,
            disabledContainerColor = AppColors.DisabledContainer,
            disabledContentColor = AppColors.DisabledContent
        )
    ) {
        Text(text = text)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = AppDimensions.MinTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
        border = BorderStroke(AppDimensions.GlassStroke, AppColors.Divider),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AppColors.TextPrimary,
            disabledContentColor = AppColors.DisabledContent
        )
    ) {
        Text(text = text)
    }
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cameraStyle: Boolean = false,
    icon: @Composable BoxScope.() -> Unit
) {
    val container = if (cameraStyle) AppColors.CameraGlassDark else Color.Transparent
    Surface(
        modifier = modifier
            .size(AppDimensions.MinTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = container,
        contentColor = if (cameraStyle) AppColors.CameraText else AppColors.TextPrimary
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.size(AppDimensions.IconVisualSize),
                contentAlignment = Alignment.Center,
                content = icon
            )
        }
    }
}

@Composable
fun CameraShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "拍照"
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(AppDimensions.ShutterSize)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, Color.White),
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(AppDimensions.ShutterInnerSize),
                shape = CircleShape,
                color = if (enabled) Color.White else AppColors.DisabledContent,
                border = BorderStroke(AppDimensions.GlassStroke, Color.Black.copy(alpha = 0.08f))
            ) {}
        }
    }
}
