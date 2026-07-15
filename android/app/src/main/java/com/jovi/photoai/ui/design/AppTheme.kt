package com.jovi.photoai.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppLightColorScheme = lightColorScheme(
    primary = AppColors.AccentBlue,
    onPrimary = AppColors.SurfacePrimary,
    primaryContainer = AppColors.AccentBlueSoft,
    onPrimaryContainer = AppColors.TextPrimary,
    background = AppColors.AppBackground,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.SurfacePrimary,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.AccentBlueSoft,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Divider,
    error = AppColors.Error,
    onError = AppColors.SurfacePrimary
)

/** UI0 is intentionally light; [darkTheme] is retained for a stable future API. */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // UI0 has no approved dark palette. Camera chrome supplies its own contrast tokens.
    @Suppress("UNUSED_VARIABLE")
    val ignoredDarkTheme = darkTheme
    MaterialTheme(
        colorScheme = AppLightColorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun PhotographyDirectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = AppTheme(darkTheme = darkTheme, content = content)

@Composable
fun PhotoDirectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = AppTheme(darkTheme = darkTheme, content = content)
