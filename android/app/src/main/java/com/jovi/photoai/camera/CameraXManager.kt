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

/**
 * AH0 baseline CameraX wrapper: Preview + ImageCapture + ImageAnalysis
 * (STRATEGY_KEEP_ONLY_LATEST). No write/move/delete on source photos; only
 * ImageCapture output to app cache. Analyzer must close() each ImageProxy.
 */
class CameraXManager(private val context: Context) {

    companion object { private const val TAG = "CameraXManager" }

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    fun initialize(onReady: (ProcessCameraProvider) -> Unit, onError: (Throwable) -> Unit = {}) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                onReady(provider)
            } catch (t: Throwable) {
                Log.e(TAG, "ProcessCameraProvider init failed", t)
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK
    ) {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also { p ->
            p.setSurfaceProvider(previewView.surfaceProvider)
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, imageAnalysis)
        } catch (t: Throwable) {
            Log.e(TAG, "bindToLifecycle failed", t)
        }
    }

    fun setAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
    }

    fun takePicture(outputFile: File, onSaved: (File) -> Unit, onError: (Throwable) -> Unit) {
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSaved(outputFile)
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "takePicture failed", exc)
                    onError(exc)
                }
            }
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
        unbind()
        analysisExecutor.shutdown()
    }
}
