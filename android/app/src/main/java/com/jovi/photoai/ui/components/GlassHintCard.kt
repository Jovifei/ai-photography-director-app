package com.jovi.photoai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

@Composable
fun GlassHintCard(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "当前建议",
    demoLabel: String? = "示意",
    tone: GlassTone = GlassTone.CAMERA_DARK
) {
    GlassSurface(modifier = modifier, tone = tone) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = if (tone == GlassTone.CAMERA_DARK) {
                        AppColors.CameraTextSecondary
                    } else {
                        AppColors.TextSecondary
                    },
                    style = MaterialTheme.typography.labelSmall
                )
                if (demoLabel != null) {
                    GlassPill(text = demoLabel, tone = tone)
                }
            }
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
