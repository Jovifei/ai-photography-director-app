package com.jovi.photoai.ui.pose

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.PoseTypography

/** Loads a small asset JPEG by relative path; fail-closed to placeholder. */
@Composable
internal fun PoseAssetImage(
    assetPath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: String = "暂无参考图",
    testTag: String? = null,
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        if (assetPath.isNullOrBlank()) {
            null
        } else {
            runCatching {
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = (if (testTag != null) modifier.testTag(testTag) else modifier)
                .background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                placeholder,
                style = PoseTypography.bodySmall,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
