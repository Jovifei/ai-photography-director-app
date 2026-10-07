package com.jovi.photoai.ui.pose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

/**
 * Compact always-available pose cue on the camera chrome.
 * Drawn as Compose overlay only; does not touch CameraX capture.
 */
@Composable
internal fun PoseDirectionShootPanel(
    selection: SelectedPoseDirection,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 64.dp else 24.dp)
            .testTag("pose-direction-shoot-panel")
            .semantics { contentDescription = "\u6784\u56fe\u53e3\u4ee4 ${selection.id}" },
        color = AppColors.CameraChromeSurface,
        shape = RoundedCornerShape(AppDimensions.RadiusLarge),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CameraChromeBorder),
        shadowElevation = AppDimensions.GlassElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.Space12, vertical = AppDimensions.Space8),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val graphic = selection.graphic
            if (graphic != null) {
                StickFigureDiagram(
                    graphic = graphic,
                    diagramHeight = 112.dp,
                    modifier = Modifier
                        .width(72.dp)
                        .testTag("pose-direction-shoot-figure"),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.Space4),
            ) {
                Text(
                    selection.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.CameraChromeText.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selection.spokenDirection,
                    modifier = Modifier.testTag("pose-direction-shoot-spoken"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.CameraChromeText,
                    maxLines = if (compact) 3 else 5,
                    overflow = TextOverflow.Ellipsis,
                )
                selection.figureNote?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.CameraChromeText.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
