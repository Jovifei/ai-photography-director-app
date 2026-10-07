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
import androidx.compose.ui.platform.testTag
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

/** Product roots: composition first, inspiration stub, records demoted. */
enum class RootSection { POSE, INSPIRATION, RECORDS }

@Composable
fun RootNavigation(
    selected: RootSection,
    onSelect: (RootSection) -> Unit,
    modifier: Modifier = Modifier,
    inspirationEnabled: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("root-navigation"),
        color = AppColors.SurfacePrimary.copy(alpha = 0.86f),
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        border = androidx.compose.foundation.BorderStroke(AppDimensions.GlassStroke, AppColors.Divider),
    ) {
        Row(
            modifier = Modifier.padding(AppDimensions.Space4),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space4),
        ) {
            RootNavigationItem(
                text = "构图口令",
                selected = selected == RootSection.POSE,
                enabled = true,
                onClick = { onSelect(RootSection.POSE) },
                modifier = Modifier.weight(1f),
                testTag = "root-nav-pose",
            )
            RootNavigationItem(
                text = "灵感",
                selected = selected == RootSection.INSPIRATION,
                enabled = inspirationEnabled,
                onClick = { if (inspirationEnabled) onSelect(RootSection.INSPIRATION) },
                modifier = Modifier.weight(1f),
                testTag = "root-nav-inspiration",
            )
            RootNavigationItem(
                text = "记录",
                selected = selected == RootSection.RECORDS,
                enabled = true,
                onClick = { onSelect(RootSection.RECORDS) },
                modifier = Modifier.weight(1f),
                testTag = "root-nav-records",
            )
        }
    }
}

@Composable
private fun RootNavigationItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    testTag: String,
) {
    val background = when {
        !enabled -> AppColors.SurfacePrimary.copy(alpha = 0.5f)
        selected -> AppColors.AccentBlueSoft
        else -> AppColors.SurfacePrimary
    }
    val foreground = when {
        !enabled -> AppColors.TextTertiary
        selected -> AppColors.AccentBlue
        else -> AppColors.TextSecondary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = AppDimensions.MinTouchTarget)
            .testTag(testTag),
        color = background,
        shape = RoundedCornerShape(AppDimensions.RadiusMedium),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = AppDimensions.Space12),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
    }
}
