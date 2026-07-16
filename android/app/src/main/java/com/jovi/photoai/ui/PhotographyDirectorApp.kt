package com.jovi.photoai.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen

@Composable
fun PhotographyDirectorApp() {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var cameraReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var selectedReferenceUri by remember { mutableStateOf<Uri?>(null) }
    val destination = AppDestination.valueOf(destinationName)

    fun navigateTo(next: AppDestination) {
        destinationName = next.name
    }

    fun openCamera(from: AppDestination) {
        cameraReturnDestinationName = cameraReturnDestination(from).name
        navigateTo(AppDestination.CAMERA_DIRECTOR)
    }

    fun returnFromCamera() {
        navigateTo(AppDestination.valueOf(cameraReturnDestinationName))
    }

    BackHandler(enabled = destination != AppDestination.HOME) {
        navigateTo(
            when (destination) {
                AppDestination.ANALYSIS_DETAIL -> AppDestination.IMPORT_REFERENCE
                AppDestination.CAMERA_DIRECTOR -> AppDestination.valueOf(cameraReturnDestinationName)
                AppDestination.IMPORT_REFERENCE -> AppDestination.HOME
                AppDestination.HOME -> AppDestination.HOME
            }
        )
    }

    when (destination) {
        AppDestination.HOME -> HomeScreen(
            onImportReference = { navigateTo(AppDestination.IMPORT_REFERENCE) },
            onOpenDemoAnalysis = { navigateTo(AppDestination.ANALYSIS_DETAIL) },
            onStartCamera = { openCamera(AppDestination.HOME) },
        )

        AppDestination.IMPORT_REFERENCE -> ImportReferenceScreen(
            selectedUri = selectedReferenceUri,
            onSelected = { selectedReferenceUri = it },
            onBack = { navigateTo(AppDestination.HOME) },
            onContinue = { navigateTo(AppDestination.ANALYSIS_DETAIL) },
        )

        AppDestination.ANALYSIS_DETAIL -> AnalysisDetailScreen(
            selectedUri = selectedReferenceUri,
            onBack = { navigateTo(AppDestination.IMPORT_REFERENCE) },
            onStartCamera = { openCamera(AppDestination.ANALYSIS_DETAIL) },
        )

        AppDestination.CAMERA_DIRECTOR -> CameraScreen(
            guidanceItems = DemoContentRepository.defaultShootingPlan.guidance,
            onBack = ::returnFromCamera,
        )
    }
}

internal fun cameraReturnDestination(from: AppDestination): AppDestination =
    if (from == AppDestination.ANALYSIS_DETAIL) AppDestination.ANALYSIS_DETAIL else AppDestination.HOME
