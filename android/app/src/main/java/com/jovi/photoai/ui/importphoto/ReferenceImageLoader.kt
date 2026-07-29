package com.jovi.photoai.ui.importphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface ReferencePreviewState {
    data object Loading : ReferencePreviewState
    data class Ready(val bitmap: Bitmap) : ReferencePreviewState
    data class Failed(val message: String) : ReferencePreviewState
}

/** Reads only an app-private derivative. Picker Uris are deliberately unsupported here. */
internal suspend fun decodeSampledBitmap(
    file: File,
    maxDimensionPx: Int = 1600,
): ReferencePreviewState = withContext(Dispatchers.IO) {
    runCatching {
        if (!file.isFile) error("本地参考图不可用")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("本地参考图不可解码")
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: error("本地参考图不可解码")
        ReferencePreviewState.Ready(bitmap)
    }.getOrElse { ReferencePreviewState.Failed("本地参考图不可用") }
}

internal fun calculateSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
    if (width <= 0 || height <= 0 || maxDimensionPx <= 0) return 1
    var sampleSize = 1
    while (width / sampleSize > maxDimensionPx || height / sampleSize > maxDimensionPx) sampleSize *= 2
    return sampleSize
}
