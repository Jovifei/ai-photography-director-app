package com.jovi.photoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

@Composable
fun <T> AppSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    cameraStyle: Boolean = false,
    enabled: Boolean = true
) {
    require(options.isNotEmpty()) { "AppSegmentedControl requires at least one option" }

    val outerColor = if (cameraStyle) AppColors.CameraGlassDark else AppColors.DisabledContainer
    val selectedColor = if (cameraStyle) {
        AppColors.CameraGlassLight
    } else {
        AppColors.SurfacePrimary
    }
    val selectedTextColor = if (cameraStyle) AppColors.TextPrimary else AppColors.AccentBlue
    val unselectedTextColor = if (cameraStyle) {
        AppColors.CameraTextSecondary
    } else {
        AppColors.TextSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(outerColor)
            .padding(AppDimensions.Space4)
            .selectableGroup()
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            val optionLabel = label(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = AppDimensions.MinTouchTarget)
                    .heightIn(min = AppDimensions.MinTouchTarget)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) selectedColor else Color.Transparent)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onOptionSelected(option) }
                    )
                    .semantics {
                        stateDescription = if (selected) "已选择" else "未选择"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = optionLabel,
                    color = if (selected) selectedTextColor else unselectedTextColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
