package com.jovi.photoai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import java.io.File

/**
 * AH0 baseline camera screen. B1 remediation:
 * - CameraScreen owns permission state + first request + ON_RESUME re-check.
 * - CameraContent (and thus CameraX init/bind) is composed ONLY when permission is granted.
 * - When permission flips false->true, CameraContent re-enters composition and a NEW
 *   CameraXManager is created. When it flips true->false, onDispose shuts the manager down.
 * - ON_RESUME re-checks permission so returning from system Settings is reflected even if
 *   the launcher callback was never invoked.
 */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // First entry: request once if not granted.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Re-check on ON_RESUME (user may have toggled permission in system Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasCameraPermission) {
        CameraContent()
    } else {
        PermissionContent(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}

@Composable
private fun CameraContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Re-created when CameraContent re-enters composition (permission false->true).
    val manager = remember { CameraXManager(context.applicationContext) }
    val previewView = remember { PreviewView(context) }
    var captureCount by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        manager.initialize(
            onReady = {
                manager.bindToLifecycle(lifecycleOwner, previewView)
                manager.setAnalyzer { imageProxy ->
                    // KEEP_ONLY_LATEST: close immediately so preview/shutter never block.
                    imageProxy.close()
                }
            }
        )
        onDispose { manager.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Button(
            onClick = {
                val file = File(context.cacheDir, "captures/capture_${System.currentTimeMillis()}.jpg")
                file.parentFile?.mkdirs()
                manager.takePicture(
                    outputFile = file,
                    onSaved = { captureCount++ },
                    onError = { }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) { Text("拍摄 ($captureCount)") }
    }
}

@Composable
private fun PermissionContent(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("需要相机权限")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRequest) { Text("授予权限") }
    }
}
