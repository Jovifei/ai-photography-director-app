package com.jovi.photoai.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jovi.photoai.camera.CameraXManager
import com.jovi.photoai.data.capture.CaptureFileState
import com.jovi.photoai.data.capture.captureExportMessage
import com.jovi.photoai.domain.model.GuidanceItem
import com.jovi.photoai.reference.CameraDirectorGuidance
import com.jovi.photoai.ui.camera.*
import com.jovi.photoai.ui.capture.CaptureLibraryHost
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

private val CameraUiStateSaver = mapSaver(
    save = { state: CameraUiState ->
        val snapshot = state.toSnapshot()
        mapOf(
            "version" to snapshot.version,
            "reference" to (snapshot.selectedReferencePhotoId ?: ""),
            "panel" to snapshot.selectedGuidePanel.name,
            "overlay" to snapshot.overlayMode.name,
            "grid" to snapshot.gridVisible,
            "captureCount" to snapshot.captureCount,
        )
    },
    restore = { values ->
        val reference = values["reference"] as? String
        val panel = (values["panel"] as? String)?.let { name ->
            runCatching { com.jovi.photoai.domain.model.GuidePanel.valueOf(name) }.getOrNull()
        } ?: com.jovi.photoai.domain.model.GuidePanel.NONE
        val overlay = (values["overlay"] as? String)?.let { name ->
            runCatching { com.jovi.photoai.domain.model.OverlayMode.valueOf(name) }.getOrNull()
        } ?: com.jovi.photoai.domain.model.OverlayMode.SKELETON
        restoreCameraUiState(
            CameraUiSnapshot(
                version = values["version"] as? Int ?: 0,
                selectedReferencePhotoId = reference?.takeIf(String::isNotBlank),
                selectedGuidePanel = panel,
                overlayMode = overlay,
                gridVisible = values["grid"] as? Boolean ?: true,
                captureCount = values["captureCount"] as? Int ?: 0,
            ),
        )
    },
)

/** CameraX preview + durable output capture. No live image/Pose analysis is added. */
@Composable
internal fun CameraScreen(
    guidanceItems: List<GuidanceItem>,
    referenceGuidance: CameraDirectorGuidance? = null,
    directCaptureMode: Boolean = false,
    onBack: () -> Unit = {},
    projectId: String? = null,
    referenceId: String? = null,
    captureLibrary: CaptureLibraryViewModel? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val library: CaptureLibraryViewModel = captureLibrary ?: viewModel(
        factory = remember(application) { CaptureLibraryViewModelFactory(application) },
    )
    val libraryState by library.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var uiState by rememberSaveable(stateSaver = CameraUiStateSaver) { mutableStateOf(CameraUiState()) }
    val dispatch: (CameraUiEvent) -> Unit = { event -> uiState = reduceCameraUiState(uiState, event) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    LaunchedEffect(Unit) { if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(hasCameraPermission) {
        dispatch(CameraUiEvent.PermissionObserved(if (hasCameraPermission) CameraPermission.GRANTED else CameraPermission.DENIED))
    }
    LaunchedEffect(guidanceItems) { dispatch(CameraUiEvent.GuidanceUpdated(cameraGuidanceFor(guidanceItems))) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Dispose the viewfinder while reviewing; a new CameraXManager is created on return.
    if (libraryState.galleryVisible) {
        Box(Modifier.fillMaxSize().background(AppColors.AppBackground))
    } else if (hasCameraPermission) {
        val latest = libraryState.records.firstOrNull {
            it.projectId == projectId && it.fileState == CaptureFileState.AVAILABLE
        }
        CameraContent(uiState, dispatch, referenceGuidance, directCaptureMode, library,
            projectId, referenceId, saveEnabled = latest != null && libraryState.pendingExport == null,
            saveStatus = libraryState.message ?: when {
                !libraryState.ready -> "正在恢复成片，请稍候"
                libraryState.capturing -> "正在拍摄并持久保存"
                else -> latest?.let(::captureExportMessage)
            }, onSave = { latest?.let { library.openGallery(it.projectId, it.id) } }, onBack = onBack)
    } else PermissionContent(onBack = onBack, onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
    // Allows isolated CameraScreen tests/hosts without duplicating the App-level host.
    if (captureLibrary == null) CaptureLibraryHost(library, emptyList())
}

@Composable
private fun CameraContent(
    uiState: CameraUiState,
    dispatch: (CameraUiEvent) -> Unit,
    referenceGuidance: CameraDirectorGuidance?,
    directCaptureMode: Boolean,
    library: CaptureLibraryViewModel,
    projectId: String?,
    referenceId: String?,
    saveEnabled: Boolean,
    saveStatus: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(lifecycleOwner) { CameraXManager(context.applicationContext) }
    val previewView = remember { PreviewView(context) }
    DisposableEffect(lifecycleOwner, manager) {
        dispatch(CameraUiEvent.PermissionObserved(CameraPermission.GRANTED))
        dispatch(CameraUiEvent.CameraStartRequested)
        manager.initialize(
            onReady = {
                if (manager.bindToLifecycle(lifecycleOwner, previewView)) {
                    manager.setAnalyzer { it.close() }
                    dispatch(CameraUiEvent.CameraReady)
                } else dispatch(CameraUiEvent.CameraFailed)
            },
            onError = { dispatch(CameraUiEvent.CameraFailed) },
        )
        onDispose { manager.shutdown() }
    }
    Box(Modifier.fillMaxSize().background(AppColors.TextPrimary)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraDirectorChrome(
            uiState = uiState,
            onEvent = dispatch,
            onBack = onBack,
            referenceGuidance = referenceGuidance,
            directCaptureMode = directCaptureMode,
            saveEnabled = saveEnabled,
            saveStatus = saveStatus,
            onSave = onSave,
            onCapture = {
                if (uiState.canCapture) {
                    manager.imageCapture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    if (library.capture(projectId, referenceId, driver = manager::takePicture,
                        onSettled = { success -> dispatch(if (success) CameraUiEvent.CaptureSucceeded else CameraUiEvent.CaptureFailed) })) {
                        dispatch(CameraUiEvent.CaptureStarted)
                    }
                }
            },
        )
    }
}

@Composable
private fun PermissionContent(onBack: () -> Unit, onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppColors.AppBackground).padding(AppDimensions.PagePadding)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("相机权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(AppDimensions.MinTouchTarget))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(AppDimensions.RadiusLarge),
                color = AppColors.SurfacePrimary.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("需要相机权限", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("相机用于预览与拍摄。原片保存在本机应用内，拍摄后可预览、归档和另存副本。卸载、清除数据或换机前请保存外部副本。参考图分析仅连接你主动配对的服务。",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(AppDimensions.PrimaryButtonHeight),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.AccentBlue)) { Text("授予权限") }
                }
            }
        }
    }
}

@Composable
fun CameraPermissionPreviewContent() { PermissionContent(onBack = {}, onRequest = {}) }
