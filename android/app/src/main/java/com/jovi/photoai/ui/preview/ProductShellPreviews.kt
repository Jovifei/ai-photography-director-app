package com.jovi.photoai.ui.preview

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jovi.photoai.ui.CameraPermissionPreviewContent
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.camera.CameraDirectorChrome
import com.jovi.photoai.ui.camera.CameraGuidePanelPreviewContent
import com.jovi.photoai.ui.camera.CameraUiState
import com.jovi.photoai.ui.camera.DirectorGuidePanel
import com.jovi.photoai.ui.components.CameraPreviewPlaceholder
import com.jovi.photoai.ui.design.PhotographyDirectorTheme
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen

@Preview(name = "Home", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() = PreviewTheme {
    HomeScreen(onImportReference = {}, onOpenDemoAnalysis = {}, onStartCamera = {})
}

@Preview(name = "Import · Empty", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun ImportEmptyPreview() = PreviewTheme {
    ImportReferenceScreen(
        selectedUri = null,
        onSelected = {},
        onBack = {},
        onContinue = {},
    )
}

@Preview(name = "Import · Selected", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun ImportSelectedPreview() = PreviewTheme {
    ImportReferenceScreen(
        selectedUri = Uri.parse("content://ui0-preview/reference"),
        onSelected = {},
        onBack = {},
        onContinue = {},
    )
}

@Preview(name = "Analysis · Demo", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun AnalysisPreview() = PreviewTheme {
    AnalysisDetailScreen(selectedUri = null, onBack = {}, onStartCamera = {})
}

@Preview(name = "Camera · Placeholder", widthDp = 390, heightDp = 844)
@Composable
private fun CameraPreview() = PreviewTheme {
    CameraPreviewPlaceholder(modifier = Modifier.fillMaxSize(), label = "设计预览 · 非实时画面") {
        CameraDirectorChrome(
            uiState = CameraUiState(captureCount = 2),
            onEvent = {},
            onBack = {},
            onCapture = {},
        )
    }
}

@Preview(name = "Panel · Environment", widthDp = 390, heightDp = 844)
@Composable
private fun EnvironmentPanelPreview() = PreviewTheme {
    CameraGuidePanelPreviewContent(panel = DirectorGuidePanel.ENVIRONMENT)
}

@Preview(name = "Panel · Subject", widthDp = 390, heightDp = 844)
@Composable
private fun SubjectPanelPreview() = PreviewTheme {
    CameraGuidePanelPreviewContent(panel = DirectorGuidePanel.SUBJECT)
}

@Preview(name = "Permission", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun PermissionPreview() = PreviewTheme {
    CameraPermissionPreviewContent()
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    PhotographyDirectorTheme {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
