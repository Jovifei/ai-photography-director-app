package com.jovi.photoai.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.data.reference.ReferenceImportErrorCode
import com.jovi.photoai.data.reference.ReferenceImportUiState
import com.jovi.photoai.reference.toDirectorCard
import com.jovi.photoai.ui.CameraPermissionPreviewContent
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.camera.CameraDirectorChrome
import com.jovi.photoai.ui.camera.CameraGuidePanelPreviewContent
import com.jovi.photoai.ui.camera.CameraUiState
import com.jovi.photoai.ui.camera.DirectorGuidePanel
import com.jovi.photoai.ui.components.CameraPreviewPlaceholder
import com.jovi.photoai.ui.design.PhotographyDirectorTheme
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.home.HomeReferenceItem
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen
import com.jovi.photoai.ui.reference.DirectorCardScreen

@Preview(name = "Home", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() = PreviewTheme {
    HomeScreen(
        references = listOf(
            HomeReferenceItem(
                id = "preview",
                title = "窗边柔光人像",
                sourceLabel = "内置示例",
                scene = "窗边",
                lighting = "柔和侧光",
                composition = "右侧三分线留白",
                tags = setOf("人像", "留白"),
            ),
        ),
        query = "",
        selectedScene = null,
        onQueryChange = {},
        onSceneSelected = {},
        onImportReference = {},
        onOpenReferenceLibrary = {},
        onOpenReference = {},
        onOpenCapture = {},
    )
}

@Preview(name = "Import · Empty", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun ImportEmptyPreview() = PreviewTheme {
    ImportReferenceScreen(
        state = ReferenceImportUiState.Idle,
        onPickerResult = {},
        onPickerCancelled = {},
        onBack = {},
        onContinue = {},
        onDiscardReady = {},
        onRetry = {},
    )
}

@Preview(name = "Import · Error", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun ImportErrorPreview() = PreviewTheme {
    ImportReferenceScreen(
        state = ReferenceImportUiState.Failed(ReferenceImportErrorCode.UNSUPPORTED_IMAGE),
        onPickerResult = {},
        onPickerCancelled = {},
        onBack = {},
        onContinue = {},
        onDiscardReady = {},
        onRetry = {},
    )
}

@Preview(name = "Analysis · Demo", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun AnalysisPreview() = PreviewTheme {
    AnalysisDetailScreen(
        imageFileName = null,
        bundle = DemoReferenceAnalyzer.analyze("preview-reference", "demo://preview"),
        sourceLabel = DemoReferenceAnalyzer.SOURCE_LABEL,
        onBack = {},
        onOpenDirectorCard = {},
    )
}

@Preview(name = "Director Card · Demo", showSystemUi = true, widthDp = 390, heightDp = 844)
@Composable
private fun DirectorCardPreview() = PreviewTheme {
    val bundle = DemoReferenceAnalyzer.analyze("preview-reference", "demo://preview")
    DirectorCardScreen(
        card = bundle.toDirectorCard(),
        sourceLabel = DemoReferenceAnalyzer.SOURCE_LABEL,
        onBack = {},
        onEnterCameraDirector = {},
    )
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
