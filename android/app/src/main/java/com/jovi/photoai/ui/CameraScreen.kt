package com.jovi.photoai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jovi.photoai.camera.CameraXManager
import com.jovi.photoai.ui.camera.CameraDirectorChrome
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions
import java.io.File

/**
 * UI0 product shell around the frozen AH0 CameraX baseline.
 * Permission state is refreshed on resume; CameraX only exists while permission is granted.
 */
@Composable
fun CameraScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasCameraPermission) {
        CameraContent(onBack = onBack)
    } else {
        PermissionContent(
            onBack = onBack,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
    }
}

@Composable
private fun CameraContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { CameraXManager(context.applicationContext) }
    val previewView = remember { PreviewView(context) }
    var captureCount by remember { mutableIntStateOf(0) }
    var cameraReady by remember { mutableStateOf(false) }
    var captureInFlight by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        manager.initialize(
            onReady = {
                manager.bindToLifecycle(lifecycleOwner, previewView)
                manager.setAnalyzer { imageProxy ->
                    // Preserve AH0 KEEP_ONLY_LATEST behavior: every frame is always closed.
                    imageProxy.close()
                }
                cameraReady = true
            },
            onError = {
                cameraReady = false
                captureError = true
            },
        )
        onDispose { manager.shutdown() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.TextPrimary),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraDirectorChrome(
            captureCount = captureCount,
            captureInFlight = captureInFlight || !cameraReady,
            captureError = captureError,
            onBack = onBack,
            onCapture = {
                if (cameraReady && !captureInFlight) {
                    captureInFlight = true
                    captureError = false
                    val file = File(
                        context.cacheDir,
                        "captures/capture_${System.currentTimeMillis()}.jpg",
                    )
                    file.parentFile?.mkdirs()
                    manager.takePicture(
                        outputFile = file,
                        onSaved = {
                            captureCount++
                            captureInFlight = false
                        },
                        onError = {
                            captureError = true
                            captureInFlight = false
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun PermissionContent(onBack: () -> Unit, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AppBackground)
            .padding(AppDimensions.PagePadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("相机权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(AppDimensions.MinTouchTarget))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                color = AppColors.SurfacePrimary.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("需要相机权限", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "相机仅用于实时预览与拍摄。照片保存在应用缓存中；UI0 不上传、不联网，也不执行 AI 分析。",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppDimensions.PrimaryButtonHeight),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentBlue),
                    ) {
                        Text("授予权限")
                    }
                }
            }
        }
    }
}

/** Static permission state for Compose tooling. */
@Composable
fun CameraPermissionPreviewContent() {
    PermissionContent(onBack = {}, onRequest = {})
}
