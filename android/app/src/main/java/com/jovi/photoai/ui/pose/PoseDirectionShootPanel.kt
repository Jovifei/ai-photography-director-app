package com.jovi.photoai.ui.pose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.design.PoseTypography

/**
 * Compact always-available pose cue on the camera chrome.
 * Chinese only: 40x40 thumb + one-line name + one-line spoken.
 */
@Composable
internal fun PoseDirectionShootPanel(
    selection: SelectedPoseDirection,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val thumbAsset = referenceThumbAsset(selection.referenceImage)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 64.dp else 24.dp)
            .testTag("pose-direction-shoot-panel")
            .semantics { contentDescription = "\u6784\u56fe\u53e3\u4ee4 ${selection.title}" },
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0F0F0))
                    .testTag("pose-direction-shoot-thumb"),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbAsset != null) {
                    PoseAssetImage(
                        assetPath = thumbAsset,
                        contentDescription = "\u53c2\u8003\u56fe",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    val graphic = selection.graphic
                    if (graphic != null) {
                        StickFigureDiagram(
                            graphic = graphic,
                            diagramHeight = 40.dp,
                            showBackground = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("pose-direction-shoot-figure"),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    selection.title,
                    style = PoseTypography.titleSmall,
                    color = AppColors.CameraChromeText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selection.spokenSteps.firstOrNull() ?: selection.spokenDirection,
                    modifier = Modifier.testTag("pose-direction-shoot-spoken"),
                    style = PoseTypography.bodySmall,
                    color = AppColors.CameraChromeText.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
