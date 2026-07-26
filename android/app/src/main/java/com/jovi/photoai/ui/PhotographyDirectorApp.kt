package com.jovi.photoai.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.reference.ReferencePhoto
import com.jovi.photoai.reference.toCameraDirectorGuidance
import com.jovi.photoai.reference.toDirectorCard
import com.jovi.photoai.reference.toGuidanceItems
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen
import com.jovi.photoai.ui.reference.DirectorCardScreen
import com.jovi.photoai.ui.reference.ReferenceLibraryEntry
import com.jovi.photoai.ui.reference.ReferenceLibraryScreen

private data class SessionReference(
    val photo: ReferencePhoto,
    val uri: Uri,
    val bundle: ReferenceBundle,
)

/**
 * Phase 1 root navigation. Imported media stays in memory for this app session only; the
 * ReferenceBundle deliberately excludes the Uri and no cloud/network analysis is performed.
 */
@Composable
fun PhotographyDirectorApp() {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var analysisReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.IMPORT_REFERENCE.name) }
    var selectedReferenceUri by remember { mutableStateOf<Uri?>(null) }
    var activeReference by remember { mutableStateOf<SessionReference?>(null) }
    var nextReferenceNumber by remember { mutableIntStateOf(1) }
    val referenceLibrary = remember { mutableStateListOf<SessionReference>() }
    val destination = AppDestination.valueOf(destinationName)

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

    fun openAnalysis(reference: SessionReference, returnDestination: AppDestination) {
        activeReference = reference
        analysisReturnDestinationName = returnDestination.name
        navigateTo(referenceNextDestination(AppDestination.IMPORT_REFERENCE))
    }

    fun beginFreshImport() {
        selectedReferenceUri = null
        navigateTo(AppDestination.IMPORT_REFERENCE)
    }

    fun importSelectedReference() {
        val uri = selectedReferenceUri ?: return
        val id = "session-reference-$nextReferenceNumber"
        val photo = ReferencePhoto(
            id = id,
            title = "导入参考图 $nextReferenceNumber",
            description = "仅本次会话可见；由系统 Photo Picker 选择。",
            sourceLabel = "本次会话",
            imageAssetKey = "session/$id",
            aspectRatio = 4f / 5f,
        )
        val bundle = DemoReferenceAnalyzer.analyze(photo.id, uri.toString())
        val reference = SessionReference(photo = photo, uri = uri, bundle = bundle)
        referenceLibrary.removeAll { it.photo.id == photo.id }
        referenceLibrary.add(0, reference)
        nextReferenceNumber += 1
        selectedReferenceUri = null
        openAnalysis(reference, AppDestination.IMPORT_REFERENCE)
    }

    fun openBuiltInDemo() {
        val photo = DemoContentRepository.featuredReferencePhoto
        openAnalysis(
            reference = SessionReference(
                photo = photo,
                uri = Uri.parse("demo://reference/${photo.id}"),
                bundle = DemoReferenceAnalyzer.analyze(photo.id, "demo://reference/${photo.id}"),
            ),
            returnDestination = AppDestination.HOME,
        )
    }

    fun returnFromCamera() {
        navigateTo(cameraReturnDestination(AppDestination.CAMERA_DIRECTOR))
    }

    BackHandler(enabled = destination != AppDestination.HOME) {
        navigateTo(
            when (destination) {
                AppDestination.REFERENCE_LIBRARY -> AppDestination.HOME
                AppDestination.IMPORT_REFERENCE -> AppDestination.HOME
                AppDestination.ANALYSIS_DETAIL -> AppDestination.valueOf(analysisReturnDestinationName)
                AppDestination.DIRECTOR_CARD -> AppDestination.ANALYSIS_DETAIL
                AppDestination.CAMERA_DIRECTOR -> AppDestination.DIRECTOR_CARD
                AppDestination.HOME -> AppDestination.HOME
            },
        )
    }

    when (destination) {
        AppDestination.HOME -> HomeScreen(
            referenceCount = referenceLibrary.size,
            onImportReference = ::beginFreshImport,
            onOpenReferenceLibrary = { navigateTo(AppDestination.REFERENCE_LIBRARY) },
            onOpenDemoAnalysis = ::openBuiltInDemo,
            onStartCamera = ::beginFreshImport,
        )

        AppDestination.REFERENCE_LIBRARY -> ReferenceLibraryScreen(
            entries = referenceLibrary.map { ReferenceLibraryEntry(photo = it.photo, uri = it.uri) },
            onBack = { navigateTo(AppDestination.HOME) },
            onImportReference = ::beginFreshImport,
            onOpenReference = { referenceId ->
                referenceLibrary.firstOrNull { it.photo.id == referenceId }?.let {
                    openAnalysis(it, AppDestination.REFERENCE_LIBRARY)
                }
            },
        )

        AppDestination.IMPORT_REFERENCE -> ImportReferenceScreen(
            selectedUri = selectedReferenceUri,
            onSelected = { selectedReferenceUri = it },
            onBack = { navigateTo(AppDestination.HOME) },
            onContinue = ::importSelectedReference,
        )

        AppDestination.ANALYSIS_DETAIL -> activeReference?.let { reference ->
            AnalysisDetailScreen(
                selectedUri = reference.uri.takeUnless { it.scheme == "demo" },
                bundle = reference.bundle,
                onBack = { navigateTo(AppDestination.valueOf(analysisReturnDestinationName)) },
                onOpenDirectorCard = { navigateTo(referenceNextDestination(AppDestination.ANALYSIS_DETAIL)) },
            )
        }

        AppDestination.DIRECTOR_CARD -> activeReference?.let { reference ->
            DirectorCardScreen(
                card = reference.bundle.toDirectorCard(),
                sourceLabel = DemoReferenceAnalyzer.SOURCE_LABEL,
                onBack = { navigateTo(AppDestination.ANALYSIS_DETAIL) },
                onEnterCameraDirector = { navigateTo(referenceNextDestination(AppDestination.DIRECTOR_CARD)) },
            )
        }

        AppDestination.CAMERA_DIRECTOR -> activeReference?.let { reference ->
            val card: DirectorCard = reference.bundle.toDirectorCard()
            CameraScreen(
                guidanceItems = card.toGuidanceItems(),
                referenceGuidance = reference.bundle.toCameraDirectorGuidance(reference.photo.title),
                onBack = ::returnFromCamera,
            )
        }
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
