package com.jovi.photoai.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import java.util.concurrent.TimeUnit

internal data class ExposureCapability(
    val minIndex: Int,
    val maxIndex: Int,
    val stepEv: Float,
    val currentIndex: Int,
)

/** CameraX driver. Caller owns output reservation, finalization and persistence. */
class CameraXManager(private val context: Context) {
    companion object { private const val TAG = "CameraXManager" }
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var closed = false
    private val controlFence = CameraControlFence()
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
            camera = provider.bindToLifecycle(lifecycleOwner, selector, nextPreview, imageCapture, imageAnalysis)
            true
        } catch (_: Exception) {
            Log.e(TAG, "Camera binding failed")
            false
        }
    }

    fun setAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        if (!closed) imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
    }

    internal fun exposureCapability(): ExposureCapability? {
        val state = camera?.cameraInfo?.exposureState ?: return null
        val range = state.exposureCompensationRange
        val step = state.exposureCompensationStep.toFloat()
        if (!state.isExposureCompensationSupported || range.lower >= range.upper ||
            !step.isFinite() || step <= 0f) return null
        return ExposureCapability(range.lower, range.upper, step, state.exposureCompensationIndex)
    }

    internal fun focusAt(previewView: PreviewView, x: Float, y: Float, onResult: (Boolean) -> Unit) {
        val active = camera ?: return onResult(false)
        val request = controlFence.focusRequest()
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            val future = active.cameraControl.startFocusAndMetering(action)
            future.addListener({
                val success = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
                if (!closed && camera === active && controlFence.currentFocus(request)) onResult(success)
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) {
            if (!closed && camera === active && controlFence.currentFocus(request)) onResult(false)
        }
    }

    internal fun setExposure(index: Int, onResult: (Int?) -> Unit) {
        val active = camera ?: return onResult(null)
        val range = active.cameraInfo.exposureState.exposureCompensationRange
        if (index !in range.lower..range.upper) return onResult(null)
        val request = controlFence.exposureRequest()
        try {
            val future = active.cameraControl.setExposureCompensationIndex(index)
            future.addListener({
                val confirmed = runCatching { future.get() }.getOrNull()
                if (!closed && camera === active && controlFence.currentExposure(request)) onResult(confirmed)
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) {
            if (!closed && camera === active && controlFence.currentExposure(request)) onResult(null)
        }
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
        controlFence.invalidate()
        camera = null
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
