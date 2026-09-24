package com.jovi.photoai.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** CameraX driver. Caller owns output reservation, finalization and persistence. */
class CameraXManager(private val context: Context) {
    companion object { private const val TAG = "CameraXManager" }
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var closed = false
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    private val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

    fun initialize(onReady: (ProcessCameraProvider) -> Unit, onError: (Throwable) -> Unit = {}) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!closed) {
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    onReady(provider)
                } catch (error: Exception) {
                    Log.e(TAG, "Camera initialization failed")
                    onError(error)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Return actual binding outcome. A failed bind must never become CameraReady. */
    fun bindToLifecycle(lifecycleOwner: LifecycleOwner, previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK): Boolean {
        if (closed) return false
        val provider = cameraProvider ?: return false
        val nextPreview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        return try {
            unbind()
            preview = nextPreview
            provider.bindToLifecycle(lifecycleOwner, selector, nextPreview, imageCapture, imageAnalysis)
            true
        } catch (_: Exception) {
            Log.e(TAG, "Camera binding failed")
            false
        }
    }

    fun setAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        if (!closed) imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
    }
    fun takePicture(outputFile: File, onSaved: (File) -> Unit, onError: (Throwable) -> Unit) {
        if (closed) { onError(IllegalStateException("CAMERA_CLOSED")); return }
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { onSaved(outputFile) }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed")
                    onError(exc)
                }
            })
    }
    fun unbind() {
        val provider = cameraProvider ?: return
        val ownPreview = preview
        if (ownPreview == null) provider.unbind(imageCapture, imageAnalysis)
        else provider.unbind(ownPreview, imageCapture, imageAnalysis)
        preview = null
    }
    fun shutdown() {
        if (closed) return
        closed = true
        imageAnalysis.clearAnalyzer()
        unbind()
        analysisExecutor.shutdown()
    }
}
