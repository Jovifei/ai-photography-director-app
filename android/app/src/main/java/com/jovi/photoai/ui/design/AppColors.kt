package com.jovi.photoai.ui.design

import androidx.compose.ui.graphics.Color

/** Centralized UI0 color tokens. */
object AppColors {
    val AppBackground = Color(0xFFF5F5F7)
    val SurfacePrimary = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFF1D1D1F)
    val TextSecondary = Color(0xFF6E6E73)
    val TextTertiary = Color(0xFF86868B)

    val AccentBlue = Color(0xFF007AFF)
    val AccentBlueSoft = Color(0xFFEAF3FF)
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9F0A)
    val Error = Color(0xFFFF3B30)
    val Divider = Color(0xFFD2D2D7)

    val CameraGlassLight = Color.White.copy(alpha = 0.72f)
    val CameraGlassDark = Color.Black.copy(alpha = 0.22f)
    val CameraText = Color.White
    val CameraTextSecondary = Color.White.copy(alpha = 0.76f)

    // Stable light containers keep camera controls legible over both bright and dark preview frames.
    val CameraChromeSurface = Color(0xEBFFFFFF)
    val CameraChromeText = Color(0xFF1D1D1F)
    val CameraChromeSecondaryText = Color(0xFF515156)
    val CameraChromeBorder = Color(0xFFD2D2D7)
    val CameraChromeDisabled = Color(0xFF6E6E73)

    val GlassBorder = Color.White.copy(alpha = 0.56f)
    val GlassHighlight = Color.White.copy(alpha = 0.72f)
    val CameraBorder = Color.White.copy(alpha = 0.34f)
    val CameraScrim = Color.Black.copy(alpha = 0.32f)
    val DisabledContainer = Color(0xFFE5E5EA)
    val DisabledContent = Color(0xFFAEAEB2)
}
