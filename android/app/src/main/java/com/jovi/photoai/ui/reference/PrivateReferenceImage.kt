package com.jovi.photoai.ui.reference

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import com.jovi.photoai.ui.importphoto.ReferencePreviewState
import com.jovi.photoai.ui.importphoto.decodeSampledBitmap
import java.io.File

/** Shows an app-private derivative by filename only; no original Picker Uri is retained. */
@Composable
internal fun PrivateReferenceImage(
    imageFileName: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var state by remember(imageFileName) { mutableStateOf<ReferencePreviewState>(ReferencePreviewState.Loading) }
    LaunchedEffect(imageFileName) {
        state = decodeSampledBitmap(File(context.filesDir, "references/$imageFileName"), maxDimensionPx = 720)
    }
    val bitmap = (state as? ReferencePreviewState.Ready)?.bitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = modifier.background(AppColors.AccentBlueSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state is ReferencePreviewState.Failed) "图片不可用" else "加载参考图…",
                modifier = Modifier.padding(AppDimensions.Space12),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary,
            )
        }
    }
}
