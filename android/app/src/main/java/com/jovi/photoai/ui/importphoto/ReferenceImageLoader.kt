package com.jovi.photoai.ui.importphoto

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface ReferencePreviewState {
    data object Empty : ReferencePreviewState
    data object Loading : ReferencePreviewState
    data object PreviewSelected : ReferencePreviewState
    data class Ready(val bitmap: Bitmap) : ReferencePreviewState
    data class Failed(val message: String) : ReferencePreviewState
}

internal suspend fun decodeSampledBitmap(
    contentResolver: ContentResolver,
    uri: Uri,
    maxDimensionPx: Int = 1600,
): ReferencePreviewState = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: error("无法读取所选照片")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("所选文件不是可解码的图片")
        }

        val sampleSize = calculateSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimensionPx = maxDimensionPx,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("照片解码失败")

        ReferencePreviewState.Ready(bitmap)
    }.getOrElse { error ->
        ReferencePreviewState.Failed(error.message ?: "无法打开所选照片")
    }
}

internal fun calculateSampleSize(
    width: Int,
    height: Int,
    maxDimensionPx: Int,
): Int {
    if (width <= 0 || height <= 0 || maxDimensionPx <= 0) return 1
    var sampleSize = 1
    while (width / sampleSize > maxDimensionPx || height / sampleSize > maxDimensionPx) {
        sampleSize *= 2
    }
    return sampleSize
}
