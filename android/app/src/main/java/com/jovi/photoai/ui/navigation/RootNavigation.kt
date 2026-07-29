package com.jovi.photoai.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

enum class RootSection { INSPIRATION, CAPTURE }

/** The two product roots stay separate from the transient Reference → Director flow pages. */
@Composable
fun RootNavigation(
    selected: RootSection,
    onSelect: (RootSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppColors.SurfacePrimary.copy(alpha = 0.86f),
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        border = androidx.compose.foundation.BorderStroke(AppDimensions.GlassStroke, AppColors.Divider),
    ) {
        Row(
            modifier = Modifier.padding(AppDimensions.Space4),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space4),
        ) {
            RootNavigationItem(
                text = "灵感",
                selected = selected == RootSection.INSPIRATION,
                onClick = { onSelect(RootSection.INSPIRATION) },
                modifier = Modifier.weight(1f),
            )
            RootNavigationItem(
                text = "拍摄",
                selected = selected == RootSection.CAPTURE,
                onClick = { onSelect(RootSection.CAPTURE) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RootNavigationItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppDimensions.MinTouchTarget),
        color = if (selected) AppColors.AccentBlueSoft else AppColors.SurfacePrimary,
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = AppDimensions.Space12),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AppColors.AccentBlue else AppColors.TextSecondary,
        )
    }
}
