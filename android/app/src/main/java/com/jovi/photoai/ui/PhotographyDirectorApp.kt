package com.jovi.photoai.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen

@Composable
fun PhotographyDirectorApp() {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var selectedReferenceUri by remember { mutableStateOf<Uri?>(null) }
    val destination = AppDestination.valueOf(destinationName)

    fun navigateTo(next: AppDestination) {
        destinationName = next.name
    }

    BackHandler(enabled = destination != AppDestination.HOME) {
        navigateTo(
            when (destination) {
                AppDestination.ANALYSIS_DETAIL -> AppDestination.IMPORT_REFERENCE
                AppDestination.CAMERA_DIRECTOR,
                AppDestination.IMPORT_REFERENCE -> AppDestination.HOME
                AppDestination.HOME -> AppDestination.HOME
            }
        )
    }

    when (destination) {
        AppDestination.HOME -> HomeScreen(
            onImportReference = { navigateTo(AppDestination.IMPORT_REFERENCE) },
            onOpenDemoAnalysis = { navigateTo(AppDestination.ANALYSIS_DETAIL) },
            onStartCamera = { navigateTo(AppDestination.CAMERA_DIRECTOR) },
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
            onStartCamera = { navigateTo(AppDestination.CAMERA_DIRECTOR) },
        )

        AppDestination.CAMERA_DIRECTOR -> CameraScreen(
            onBack = { navigateTo(AppDestination.HOME) },
        )
    }
}
