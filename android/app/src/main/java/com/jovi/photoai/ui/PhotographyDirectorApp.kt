package com.jovi.photoai.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.data.reference.ReferenceImportViewModel
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.reference.toCameraDirectorGuidance
import com.jovi.photoai.reference.toDirectorCard
import com.jovi.photoai.reference.toGuidanceItems
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.capture.CaptureEntryScreen
import com.jovi.photoai.ui.home.HomeReferenceItem
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen
import com.jovi.photoai.ui.reference.DirectorCardScreen
import com.jovi.photoai.ui.reference.ReferenceLibraryEntry
import com.jovi.photoai.ui.reference.ReferenceLibraryScreen
import kotlinx.coroutines.launch

private data class AppReference(
    val photo: ReferencePhoto,
    val bundle: ReferenceBundle,
    val imageFileName: String?,
)

/**
 * UI1 keeps source-media identity out of composition and durable state. The import ViewModel
 * consumes the picker grant immediately and returns only a private reference record.
 */
@Composable
fun PhotographyDirectorApp() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val repository = remember { ReferenceRepository.create(application) }
    val records by repository.activeRecords.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val importViewModel: ReferenceImportViewModel = viewModel(
        factory = remember(application) { ReferenceImportViewModelFactory(application) },
    )
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var analysisReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.IMPORT_REFERENCE.name) }
    var importReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var activeReference by remember { mutableStateOf<AppReference?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedScene by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = AppDestination.valueOf(destinationName)

    LaunchedEffect(repository) { repository.reconcile() }
    LaunchedEffect(destination, activeReference) {
        if (
            activeReference == null && destination in setOf(
                AppDestination.ANALYSIS_DETAIL,
                AppDestination.DIRECTOR_CARD,
                AppDestination.CAMERA_DIRECTOR,
            )
        ) {
            destinationName = AppDestination.HOME.name
        }
    }

    fun navigateTo(next: AppDestination) {
        destinationName = next.name
    }

    fun toAppReference(record: ReferenceRecord): AppReference = AppReference(
        photo = record.photo,
        bundle = record.bundle,
        imageFileName = record.imageFileName,
    )

    fun demoReference(photo: ReferencePhoto): AppReference = AppReference(
        photo = photo,
        bundle = DemoReferenceAnalyzer.analyze(photo.id, "built-in-demo"),
        imageFileName = null,
    )

    fun openAnalysis(reference: AppReference, returnDestination: AppDestination) {
        activeReference = reference
        analysisReturnDestinationName = returnDestination.name
        navigateTo(AppDestination.ANALYSIS_DETAIL)
    }

    fun beginFreshImport(returnDestination: AppDestination = AppDestination.HOME) {
        importViewModel.discardForBackOrReplacement()
        importReturnDestinationName = returnDestination.name
        navigateTo(AppDestination.IMPORT_REFERENCE)
    }

    fun leaveImport() {
        importViewModel.discardForBackOrReplacement()
        navigateTo(AppDestination.valueOf(importReturnDestinationName))
    }

    fun deleteReference(id: String) {
        scope.launch {
            repository.delete(id)
            if (activeReference?.photo?.id == id) activeReference = null
        }
    }

    fun clearReferences() {
        scope.launch {
            repository.clearAll()
            activeReference = null
        }
    }

    val allReferences = buildList {
        addAll(records.map(::toAppReference))
        addAll(DemoContentRepository.referencePhotos.map(::demoReference))
    }
    val homeReferences = allReferences.map { reference ->
        HomeReferenceItem(
            id = reference.photo.id,
            title = reference.photo.title,
            sourceLabel = reference.photo.sourceLabel,
            scene = reference.bundle.scene,
            lighting = reference.bundle.lighting,
            composition = reference.bundle.composition,
            tags = setOf(reference.bundle.scene, reference.bundle.lighting, reference.bundle.composition),
            imageFileName = reference.imageFileName,
        )
    }

    fun findReference(id: String): AppReference? = allReferences.firstOrNull { it.photo.id == id }

    BackHandler(enabled = destination != AppDestination.HOME) {
        when (destination) {
            AppDestination.HOME -> Unit
            AppDestination.CAPTURE_ENTRY,
            AppDestination.REFERENCE_LIBRARY -> navigateTo(AppDestination.HOME)
            AppDestination.IMPORT_REFERENCE -> leaveImport()
            AppDestination.ANALYSIS_DETAIL -> navigateTo(AppDestination.valueOf(analysisReturnDestinationName))
            AppDestination.DIRECTOR_CARD -> navigateTo(AppDestination.ANALYSIS_DETAIL)
            AppDestination.CAMERA_DIRECTOR -> navigateTo(AppDestination.DIRECTOR_CARD)
            AppDestination.DIRECT_CAPTURE -> navigateTo(AppDestination.CAPTURE_ENTRY)
        }
    }

    when (destination) {
        AppDestination.HOME -> HomeScreen(
            references = homeReferences,
            query = searchQuery,
            selectedScene = selectedScene,
            onQueryChange = { searchQuery = it },
            onSceneSelected = { selectedScene = it },
            onImportReference = ::beginFreshImport,
            onOpenReferenceLibrary = { navigateTo(AppDestination.REFERENCE_LIBRARY) },
            onOpenReference = { id -> findReference(id)?.let { openAnalysis(it, AppDestination.HOME) } },
            onOpenCapture = { navigateTo(AppDestination.CAPTURE_ENTRY) },
        )

        AppDestination.CAPTURE_ENTRY -> CaptureEntryScreen(
            referenceCount = records.size,
            onOpenInspiration = { navigateTo(AppDestination.HOME) },
            onChooseReference = {
                if (records.isEmpty()) beginFreshImport(AppDestination.CAPTURE_ENTRY) else navigateTo(AppDestination.REFERENCE_LIBRARY)
            },
            onDirectCapture = { navigateTo(AppDestination.DIRECT_CAPTURE) },
        )

        AppDestination.REFERENCE_LIBRARY -> ReferenceLibraryScreen(
            entries = records.map { record -> ReferenceLibraryEntry(record.photo, record.bundle, record.imageFileName) },
            onBack = { navigateTo(AppDestination.HOME) },
            onImportReference = { beginFreshImport(AppDestination.REFERENCE_LIBRARY) },
            onOpenReference = { id -> findReference(id)?.let { openAnalysis(it, AppDestination.REFERENCE_LIBRARY) } },
            onDeleteReference = ::deleteReference,
            onClearAll = ::clearReferences,
        )

        AppDestination.IMPORT_REFERENCE -> ImportReferenceScreen(
            state = importViewModel.state,
            onPickerResult = importViewModel::importImmediately,
            onPickerCancelled = importViewModel::retry,
            onBack = ::leaveImport,
            onContinue = {
                importViewModel.consumeReady()?.let { record ->
                    openAnalysis(toAppReference(record), AppDestination.IMPORT_REFERENCE)
                }
            },
            onDiscardReady = importViewModel::discardForBackOrReplacement,
            onRetry = importViewModel::retry,
        )

        AppDestination.ANALYSIS_DETAIL -> activeReference?.let { reference ->
            AnalysisDetailScreen(
                imageFileName = reference.imageFileName,
                bundle = reference.bundle,
                sourceLabel = reference.photo.sourceLabel,
                onBack = { navigateTo(AppDestination.valueOf(analysisReturnDestinationName)) },
                onOpenDirectorCard = { navigateTo(AppDestination.DIRECTOR_CARD) },
            )
        }

        AppDestination.DIRECTOR_CARD -> activeReference?.let { reference ->
            DirectorCardScreen(
                card = reference.bundle.toDirectorCard(),
                sourceLabel = reference.photo.sourceLabel,
                onBack = { navigateTo(AppDestination.ANALYSIS_DETAIL) },
                onEnterCameraDirector = { navigateTo(AppDestination.CAMERA_DIRECTOR) },
            )
        }

        AppDestination.CAMERA_DIRECTOR -> activeReference?.let { reference ->
            val card: DirectorCard = reference.bundle.toDirectorCard()
            CameraScreen(
                guidanceItems = card.toGuidanceItems(),
                referenceGuidance = reference.bundle.toCameraDirectorGuidance(
                    referenceTitle = reference.photo.title,
                    sourceLabel = reference.photo.sourceLabel,
                ),
                onBack = { navigateTo(AppDestination.DIRECTOR_CARD) },
            )
        }

        AppDestination.DIRECT_CAPTURE -> CameraScreen(
            guidanceItems = emptyList(),
            directCaptureMode = true,
            onBack = { navigateTo(AppDestination.CAPTURE_ENTRY) },
        )
    }
}

private class ReferenceImportViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReferenceImportViewModel::class.java))
        return ReferenceImportViewModel(application) as T
    }
}

internal fun cameraReturnDestination(from: AppDestination): AppDestination =
    if (from == AppDestination.CAMERA_DIRECTOR) AppDestination.DIRECTOR_CARD else AppDestination.HOME

internal fun referenceNextDestination(from: AppDestination): AppDestination = when (from) {
    AppDestination.IMPORT_REFERENCE -> AppDestination.ANALYSIS_DETAIL
    AppDestination.ANALYSIS_DETAIL -> AppDestination.DIRECTOR_CARD
    AppDestination.DIRECTOR_CARD -> AppDestination.CAMERA_DIRECTOR
    else -> from
}
