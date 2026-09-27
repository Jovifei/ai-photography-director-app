package com.jovi.photoai.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jovi.photoai.data.demo.DemoContentRepository
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.data.reference.ReferenceImportViewModel
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ProviderAnalysisProvenance
import com.jovi.photoai.data.reference.KnowledgeBundleProvenance
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleImportViewModel
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.ReferenceRecoverySummary
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.data.reference.PhotographyProject
import com.jovi.photoai.data.reference.LEGACY_PROJECT_ID
import com.jovi.photoai.data.reference.LocalLanAnalysisConnection
import com.jovi.photoai.data.reference.LocalLanPairingClient
import com.jovi.photoai.data.reference.LocalLanReferenceAnalysisProvider
import com.jovi.photoai.data.reference.LocalLanProjectSummaryProvider
import com.jovi.photoai.data.reference.PhotoAnalysisCoordinator
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.reference.toCameraDirectorGuidance
import com.jovi.photoai.reference.toDirectorCard
import com.jovi.photoai.reference.toGuidanceItems
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.capture.CaptureEntryScreen
import com.jovi.photoai.ui.capture.CaptureEntryFrame
import com.jovi.photoai.ui.capture.CaptureLibraryHost
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import com.jovi.photoai.ui.home.HomeReferenceItem
import com.jovi.photoai.ui.home.HomeScreen
import com.jovi.photoai.ui.home.ProjectReferenceFilters
import com.jovi.photoai.ui.home.filterProjectReferences
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen
import com.jovi.photoai.ui.project.BatchProjectImportScreen
import com.jovi.photoai.ui.project.ProjectBoardScreen
import com.jovi.photoai.ui.project.ProjectHomeItem
import com.jovi.photoai.ui.project.ProjectSummaryScreen
import com.jovi.photoai.ui.project.ProjectsHomeScreen
import com.jovi.photoai.ui.project.LocalAnalysisConnectionDialog
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import com.jovi.photoai.ui.reference.DirectorCardScreen
import com.jovi.photoai.ui.reference.ReferenceLibraryEntry
import com.jovi.photoai.ui.reference.ReferenceLibraryScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

private data class AppReference(
    val photo: ReferencePhoto,
    val bundle: ReferenceBundle,
    val imageFileName: String?,
    val analysisStatus: PhotoAnalysisStatus = PhotoAnalysisStatus.EXAMPLE_GUIDANCE,
    val analysisProvenance: ProviderAnalysisProvenance? = null,
    val knowledgeBundleProvenance: KnowledgeBundleProvenance? = null,
    val ordinal: Int? = null,
)

internal enum class ReferenceStartupState { RECONCILING, READY }
private enum class ReferenceLibraryMode { BROWSE_ALL_PROJECTS, PICK_FOR_CAPTURE }

internal data class StartupRestoreDecision(
    val destination: AppDestination,
    val shouldClearPersistedActiveReference: Boolean,
)

/**
 * UI1 keeps source-media identity out of composition and durable state. The import ViewModel
 * consumes the picker grant immediately and returns only a private reference record.
 */
@Composable
fun PhotographyDirectorApp() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    val repository = remember(application) { ReferenceRepository.create(application) }
    val libraryPreferences = remember(application) { ReferenceLibraryPreferences(application) }
    PhotographyDirectorAppContent(application, repository, libraryPreferences)
}

/** The same root flow can be qualified against an isolated synthetic repository and preferences. */
@Composable
internal fun PhotographyDirectorAppContent(
    application: Application,
    repository: ReferenceRepository,
    libraryPreferences: ReferenceLibraryPreferences,
) {
    val records by repository.activeRecords.collectAsState(initial = emptyList())
    val projects by repository.projects.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val importViewModel: ReferenceImportViewModel = viewModel(
        factory = remember(application) { ReferenceImportViewModelFactory(application) },
    )
    val knowledgeBundleViewModel: PhotoKnowledgeBundleImportViewModel = viewModel(
        factory = remember(application, repository) { PhotoKnowledgeBundleImportViewModelFactory(application, repository) },
    )
    val captureLibrary: CaptureLibraryViewModel = viewModel(
        factory = remember(application) { CaptureLibraryViewModelFactory(application) },
    )
    val knowledgeBundleState by knowledgeBundleViewModel.state.collectAsState()
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var analysisReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.IMPORT_REFERENCE.name) }
    var importReturnDestinationName by rememberSaveable { mutableStateOf(AppDestination.HOME.name) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var captureProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var captureNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingProject by remember { mutableStateOf<PhotographyProject?>(null) }
    var activeReference by remember { mutableStateOf<AppReference?>(null) }
    var observedActiveReferenceId by remember { mutableStateOf<String?>(null) }
    var startupState by remember { mutableStateOf(ReferenceStartupState.RECONCILING) }
    var recoverySummaryToShow by remember { mutableStateOf<ReferenceRecoverySummary?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedScene by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStatusName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLibraryProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryModeName by rememberSaveable { mutableStateOf(ReferenceLibraryMode.BROWSE_ALL_PROJECTS.name) }
    var localConnection by remember { mutableStateOf<LocalLanAnalysisConnection?>(null) }
    var pairingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var pairingError by rememberSaveable { mutableStateOf<String?>(null) }
    var analysisProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var projectDeletionFailed by rememberSaveable { mutableStateOf(false) }
    val destination = AppDestination.valueOf(destinationName)
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
        ?: pendingProject?.takeIf { it.id == selectedProjectId }
    val selectedProjectRecords by repository.activeRecordsForProject(selectedProjectId.orEmpty())
        .collectAsState(initial = emptyList())
    val selectedProjectSummary by repository.projectSummaryForProject(selectedProjectId.orEmpty())
        .collectAsState(initial = null)

    fun toAppReference(record: ReferenceRecord): AppReference = AppReference(
        photo = record.photo,
        bundle = record.bundle,
        imageFileName = record.imageFileName,
        analysisStatus = record.analysisStatus,
        analysisProvenance = record.analysisProvenance,
        knowledgeBundleProvenance = record.knowledgeBundleProvenance,
        ordinal = record.ordinal,
    )

    LaunchedEffect(repository) {
        val recoverySummary = repository.reconcile()
        val hadPersistedActiveReference = libraryPreferences.hasStoredLastActiveReferenceId()
        val persistedActiveReferenceId = libraryPreferences.lastActiveReferenceId()
        val restoredRecord = if (persistedActiveReferenceId == null) {
            null
        } else {
            repository.activeRecord(persistedActiveReferenceId)
        }
        val restoreDecision = resolveStartupRestore(
            current = AppDestination.valueOf(destinationName),
            hasPersistedActiveReference = hadPersistedActiveReference,
            restoredActiveReference = restoredRecord != null,
        )
        if (restoreDecision.shouldClearPersistedActiveReference) {
            libraryPreferences.clearLastActiveReferenceId(persistedActiveReferenceId)
        }
        if (restoredRecord != null) {
            activeReference = toAppReference(restoredRecord)
            val restoredReturnDestination = restoredReferenceReturnDestination(restoredRecord.projectId)
            if (restoredReturnDestination == AppDestination.PROJECT_BOARD) {
                selectedProjectId = restoredRecord.projectId
            }
            analysisReturnDestinationName = restoredReturnDestination.name
        }
        destinationName = restoreDecision.destination.name
        if (recoverySummary.hasRecoveryNotice) recoverySummaryToShow = recoverySummary
        startupState = ReferenceStartupState.READY
    }
    LaunchedEffect(records, startupState, activeReference?.photo?.id) {
        val current = activeReference ?: return@LaunchedEffect
        if (startupState != ReferenceStartupState.READY || current.imageFileName == null) return@LaunchedEffect
        val latest = records.firstOrNull { it.photo.id == current.photo.id }
        // A just-imported row can precede the Flow emission. Clear only a row we have seen.
        if (latest != null) {
            observedActiveReferenceId = current.photo.id
            val refreshed = toAppReference(latest)
            if (refreshed != current) activeReference = refreshed
        } else if (observedActiveReferenceId == current.photo.id) {
            activeReference = null
            observedActiveReferenceId = null
        }
    }
    val presentationDestination = when {
        activeReference == null && destination in setOf(
            AppDestination.ANALYSIS_DETAIL,
            AppDestination.DIRECTOR_CARD,
            AppDestination.CAMERA_DIRECTOR,
        ) -> AppDestination.HOME
        else -> guardedGuidanceDestination(
            destination,
            activeReference?.analysisStatus,
            activeReference?.analysisProvenance != null,
            activeReference?.knowledgeBundleProvenance != null,
        )
    }

    LaunchedEffect(
        destination,
        activeReference?.photo?.id,
        activeReference?.analysisStatus,
        activeReference?.analysisProvenance,
        activeReference?.knowledgeBundleProvenance,
    ) {
        if (
            activeReference == null && destination in setOf(
                AppDestination.ANALYSIS_DETAIL,
                AppDestination.DIRECTOR_CARD,
                AppDestination.CAMERA_DIRECTOR,
            )
        ) {
            destinationName = AppDestination.HOME.name
        } else if (presentationDestination != destination) {
            captureProjectId = selectedProjectId
            captureNotice = offlineCaptureNotice(
                activeReference?.analysisStatus,
                activeReference?.analysisProvenance != null,
                activeReference?.knowledgeBundleProvenance != null,
            )
            destinationName = presentationDestination.name
        }
    }

    fun navigateTo(next: AppDestination) {
        destinationName = next.name
    }

    fun createProject(title: String) {
        scope.launch {
            val project = repository.createProject(title)
            pendingProject = project
            selectedProjectId = project.id
            importViewModel.dismissBatchResult()
            navigateTo(AppDestination.PROJECT_IMPORT)
        }
    }

    fun openProject(projectId: String) {
        if (projects.none { it.id == projectId }) {
            selectedProjectId = null
            navigateTo(AppDestination.HOME)
            return
        }
        pendingProject = null
        selectedProjectId = projectId
        importViewModel.dismissBatchResult()
        navigateTo(AppDestination.PROJECT_BOARD)
    }

    fun renameSelectedProject(title: String) {
        val projectId = selectedProjectId ?: return
        if (projectId == LEGACY_PROJECT_ID) return
        scope.launch {
            if (!repository.renameProject(projectId, title)) navigateTo(AppDestination.HOME)
        }
    }

    fun openReferenceLibrary(mode: ReferenceLibraryMode) {
        libraryModeName = mode.name
        searchQuery = ""
        selectedScene = null
        selectedSource = null
        selectedStatusName = null
        selectedLibraryProjectId = null
        navigateTo(AppDestination.REFERENCE_LIBRARY)
    }

    fun selectProjectPrimary(referenceId: String?) {
        val project = selectedProject ?: return
        captureNotice = null
        scope.launch { repository.setPrimaryReference(project.id, referenceId) }
    }

    fun startProjectShooting() {
        val project = selectedProject ?: return
        val primaryRecord = selectedProjectRecords.firstOrNull { it.photo.id == project.primaryReferenceId }
        if (primaryRecord == null) {
            captureProjectId = project.id
            captureNotice = null
            navigateTo(AppDestination.CAPTURE_ENTRY)
        } else if (
            isRealAiGuidanceReady(
                primaryRecord.analysisStatus,
                primaryRecord.analysisProvenance != null,
                primaryRecord.knowledgeBundleProvenance != null,
            )
        ) {
            activeReference = toAppReference(primaryRecord)
            libraryPreferences.saveLastActiveReferenceId(primaryRecord.photo.id)
            analysisReturnDestinationName = AppDestination.PROJECT_BOARD.name
            navigateTo(AppDestination.CAMERA_DIRECTOR)
        } else {
            captureProjectId = project.id
            captureNotice = offlineCaptureNotice(
                primaryRecord.analysisStatus,
                primaryRecord.analysisProvenance != null,
                primaryRecord.knowledgeBundleProvenance != null,
            )
            navigateTo(AppDestination.CAPTURE_ENTRY)
        }
    }

    fun startProjectAnalysis(projectId: String, connectionOverride: LocalLanAnalysisConnection? = null) {
        val connection = connectionOverride ?: localConnection
        if (connection == null) {
            pairingError = null
            pairingProjectId = projectId
            return
        }
        analysisProjectId = projectId
        analysisJob?.cancel()
        analysisJob = scope.launch {
            val ownJob = kotlinx.coroutines.currentCoroutineContext()[Job]
            try {
                PhotoAnalysisCoordinator(
                    repository = repository,
                    provider = LocalLanReferenceAnalysisProvider(repository, connection),
                    summaryProvider = LocalLanProjectSummaryProvider(connection),
                ).analyzeProject(projectId)
            } finally {
                if (analysisJob === ownJob) {
                    analysisProjectId = null
                    analysisJob = null
                }
            }
        }
    }

    fun cancelProjectAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        analysisProjectId = null
    }

    fun openCaptureEntry(projectId: String? = null) {
        captureProjectId = projectId
        captureNotice = null
        navigateTo(AppDestination.CAPTURE_ENTRY)
    }

    fun demoReference(photo: ReferencePhoto): AppReference = AppReference(
        photo = photo,
        bundle = DemoReferenceAnalyzer.analyze(photo.id, "built-in-demo"),
        imageFileName = null,
    )

    fun openAnalysis(reference: AppReference, returnDestination: AppDestination) {
        activeReference = reference
        if (reference.imageFileName == null) {
            libraryPreferences.clearLastActiveReferenceId()
        } else {
            libraryPreferences.saveLastActiveReferenceId(reference.photo.id)
        }
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
            libraryPreferences.clearLastActiveReferenceId(id)
            if (activeReference?.photo?.id == id) {
                activeReference = null
                observedActiveReferenceId = null
            }
        }
    }

    fun clearReferences() {
        scope.launch {
            repository.clearAll()
            libraryPreferences.clearLastActiveReferenceId()
            activeReference = null
            observedActiveReferenceId = null
        }
    }

    fun openKnowledgeBundleImport() {
        if (analysisJob?.isActive == true && analysisProjectId == selectedProjectId) return
        if (!knowledgeBundleViewModel.tryLeave()) return
        navigateTo(AppDestination.PROJECT_KNOWLEDGE_IMPORT)
    }

    fun leaveKnowledgeBundleImport() {
        if (knowledgeBundleViewModel.tryLeave()) navigateTo(AppDestination.PROJECT_BOARD)
    }

    fun deleteSelectedProject() {
        val project = selectedProject ?: return
        projectDeletionFailed = false
        scope.launch {
            if (repository.deleteProject(project.id)) {
                // Separate output ledger: detach without deleting originals. Startup also
                // reconciles missing projects if this cross-store notification is interrupted.
                captureLibrary.projectDeleted(project.id)
                libraryPreferences.clearLastActiveReferenceId()
                activeReference = null
                observedActiveReferenceId = null
                selectedProjectId = null
                pendingProject = null
                navigateTo(AppDestination.HOME)
            } else {
                projectDeletionFailed = true
            }
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
    val projectHomeItems = projects.map { project ->
        ProjectHomeItem(
            project = project,
            photoCount = records.count { it.projectId == project.id },
        )
    }
    val libraryProjectTitles = projects.associate { it.id to it.title }
    val libraryFilters = ProjectReferenceFilters(
        query = searchQuery,
        scene = selectedScene,
        source = selectedSource,
        status = selectedStatusName?.let(PhotoAnalysisStatus::valueOf),
        projectId = selectedLibraryProjectId,
    )
    val libraryEntries = records.map { record ->
        ReferenceLibraryEntry(
            photo = record.photo,
            bundle = record.bundle,
            imageFileName = record.imageFileName,
            projectId = record.projectId,
            projectTitle = libraryProjectTitles[record.projectId],
            analysisStatus = record.analysisStatus,
        )
    }
    val visibleLibraryIds = filterProjectReferences(records, libraryProjectTitles, libraryFilters)
        .mapTo(hashSetOf()) { it.record.photo.id }
    val visibleLibraryEntries = libraryEntries.filter { it.photo.id in visibleLibraryIds }

    fun findReference(id: String): AppReference? = allReferences.firstOrNull { it.photo.id == id }

    BackHandler(enabled = presentationDestination != AppDestination.HOME) {
        when (presentationDestination) {
            AppDestination.HOME -> Unit
            AppDestination.PROJECT_IMPORT -> navigateTo(AppDestination.PROJECT_BOARD)
            AppDestination.PROJECT_KNOWLEDGE_IMPORT -> leaveKnowledgeBundleImport()
            AppDestination.PROJECT_BOARD -> navigateTo(AppDestination.HOME)
            AppDestination.PROJECT_SUMMARY -> navigateTo(AppDestination.PROJECT_BOARD)
            AppDestination.CAPTURE_ENTRY -> navigateTo(
                if (captureProjectId == null) AppDestination.HOME else AppDestination.PROJECT_BOARD,
            )
            AppDestination.REFERENCE_LIBRARY -> navigateTo(
                if (libraryModeName == ReferenceLibraryMode.PICK_FOR_CAPTURE.name) AppDestination.CAPTURE_ENTRY
                else AppDestination.HOME,
            )
            AppDestination.IMPORT_REFERENCE -> leaveImport()
            AppDestination.ANALYSIS_DETAIL -> navigateTo(AppDestination.valueOf(analysisReturnDestinationName))
            AppDestination.DIRECTOR_CARD -> navigateTo(AppDestination.ANALYSIS_DETAIL)
            AppDestination.CAMERA_DIRECTOR -> navigateTo(AppDestination.DIRECTOR_CARD)
            AppDestination.DIRECT_CAPTURE -> navigateTo(AppDestination.CAPTURE_ENTRY)
        }
    }

    if (!referenceContentVisible(startupState)) {
        ReferenceStartupRecoveryScreen()
    } else when (presentationDestination) {
        AppDestination.HOME -> CaptureEntryFrame("查看全部成片", onOpen = { captureLibrary.openGallery() }) {
            ProjectsHomeScreen(
                projects = projectHomeItems,
                onCreateProject = ::createProject,
                onOpenProject = ::openProject,
                onOpenCapture = ::openCaptureEntry,
                onOpenLibrary = { openReferenceLibrary(ReferenceLibraryMode.BROWSE_ALL_PROJECTS) },
            )
        }

        AppDestination.PROJECT_IMPORT -> selectedProject?.let { project ->
            BatchProjectImportScreen(
                project = project,
                records = selectedProjectRecords,
                batchState = importViewModel.batchState,
                onPickerResults = { uris -> importViewModel.importBatch(project.id, uris) },
                onPickerCancelled = importViewModel::dismissBatchResult,
                onCancelImport = importViewModel::cancelBatchImport,
                onBack = { navigateTo(AppDestination.PROJECT_BOARD) },
                onOpenBoard = { navigateTo(AppDestination.PROJECT_BOARD) },
                onOpenPhoto = { id ->
                    selectedProjectRecords.firstOrNull { it.photo.id == id }?.let { record ->
                        openAnalysis(toAppReference(record), AppDestination.PROJECT_IMPORT)
                    }
                },
                onDeletePhoto = ::deleteReference,
            )
        }

        AppDestination.PROJECT_KNOWLEDGE_IMPORT -> selectedProject?.let { project ->
            PhotoKnowledgeBundleImportScreen(
                project = project,
                records = selectedProjectRecords,
                state = knowledgeBundleState,
                onDocumentSelected = knowledgeBundleViewModel::readDocument,
                onBind = knowledgeBundleViewModel::bind,
                onApply = { knowledgeBundleViewModel.apply(project.id) },
                onReset = knowledgeBundleViewModel::reset,
                onBack = ::leaveKnowledgeBundleImport,
            )
        }

        AppDestination.PROJECT_BOARD -> selectedProject?.let { project ->
            CaptureEntryFrame("查看本项目成片", onOpen = { captureLibrary.openGallery(project.id) }) {
                ProjectBoardScreen(
                    project = project,
                    records = selectedProjectRecords,
                    onBack = { navigateTo(AppDestination.HOME) },
                    onAddPhotos = { navigateTo(AppDestination.PROJECT_IMPORT) },
                    onOpenPhoto = { id ->
                        selectedProjectRecords.firstOrNull { it.photo.id == id }?.let { record ->
                            openAnalysis(toAppReference(record), AppDestination.PROJECT_BOARD)
                        }
                    },
                    onSelectPrimary = ::selectProjectPrimary,
                    onDeletePhoto = ::deleteReference,
                    onImportKnowledgeBundle = ::openKnowledgeBundleImport,
                    onDeleteProject = ::deleteSelectedProject,
                    onRenameProject = ::renameSelectedProject,
                    canRenameProject = project.id != LEGACY_PROJECT_ID,
                    projectDeletionFailed = projectDeletionFailed,
                    onOpenSummary = { navigateTo(AppDestination.PROJECT_SUMMARY) },
                    onStartShooting = ::startProjectShooting,
                    onStartAnalysis = { startProjectAnalysis(project.id) },
                    onCancelAnalysis = ::cancelProjectAnalysis,
                    analysisInProgress = analysisProjectId == project.id,
                    analysisServiceConnected = localConnection != null,
                )
            }
        }

        AppDestination.PROJECT_SUMMARY -> selectedProject?.let { project ->
            ProjectSummaryScreen(
                project = project,
                records = selectedProjectRecords,
                persistedSummary = selectedProjectSummary,
                onBack = { navigateTo(AppDestination.PROJECT_BOARD) },
                onOpenPhoto = { id ->
                    selectedProjectRecords.firstOrNull { it.photo.id == id }?.let { record ->
                        openAnalysis(toAppReference(record), AppDestination.PROJECT_SUMMARY)
                    }
                },
                onSelectPrimary = ::selectProjectPrimary,
                onStartShooting = ::startProjectShooting,
                analysisServiceConnected = localConnection != null,
            )
        }

        AppDestination.CAPTURE_ENTRY -> CaptureEntryScreen(
            referenceCount = captureProjectId?.let { projectId ->
                if (projectId == selectedProjectId) selectedProjectRecords.size else records.count { it.projectId == projectId }
            } ?: records.size,
            projectTitle = captureProjectId?.let { projectId -> projects.firstOrNull { it.id == projectId }?.title },
            offlineNotice = captureNotice,
            onOpenInspiration = { navigateTo(AppDestination.HOME) },
            onChooseReference = {
                if (captureProjectId != null) {
                    navigateTo(AppDestination.PROJECT_BOARD)
                } else if (records.isEmpty()) {
                    beginFreshImport(AppDestination.CAPTURE_ENTRY)
                } else {
                    openReferenceLibrary(ReferenceLibraryMode.PICK_FOR_CAPTURE)
                }
            },
            onDirectCapture = { navigateTo(AppDestination.DIRECT_CAPTURE) },
        )

        AppDestination.REFERENCE_LIBRARY -> ReferenceLibraryScreen(
            entries = libraryEntries,
            visibleEntries = visibleLibraryEntries,
            filters = libraryFilters,
            onFiltersChange = { next ->
                searchQuery = next.query
                selectedScene = next.scene
                selectedSource = next.source
                selectedStatusName = next.status?.name
                selectedLibraryProjectId = next.projectId
            },
            onBack = {
                navigateTo(
                    if (libraryModeName == ReferenceLibraryMode.PICK_FOR_CAPTURE.name) AppDestination.CAPTURE_ENTRY
                    else AppDestination.HOME,
                )
            },
            onImportReference = { beginFreshImport(AppDestination.REFERENCE_LIBRARY) },
            onOpenReference = { id ->
                records.firstOrNull { it.photo.id == id }?.let { record ->
                    val browseOwner = libraryModeName == ReferenceLibraryMode.BROWSE_ALL_PROJECTS.name &&
                        projects.any { it.id == record.projectId }
                    if (browseOwner) selectedProjectId = record.projectId
                    openAnalysis(
                        toAppReference(record),
                        if (browseOwner) AppDestination.PROJECT_BOARD else AppDestination.REFERENCE_LIBRARY,
                    )
                }
            },
            onOpenProject = { projectId -> openProject(projectId) },
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
                title = reference.ordinal?.let { "项目照片 · 第 ${it + 1} 张" } ?: "参考图分析",
                analysisStatus = reference.analysisStatus,
                analysisProvenance = reference.analysisProvenance,
                knowledgeBundleProvenance = reference.knowledgeBundleProvenance,
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
            val cameraProjectId = records.firstOrNull { it.photo.id == reference.photo.id }?.projectId
            CameraScreen(
                captureLibrary = captureLibrary,
                projectId = cameraProjectId,
                referenceId = reference.photo.id,
                referenceImageFileName = reference.imageFileName,
                guidanceItems = card.toGuidanceItems(),
                referenceGuidance = reference.bundle.toCameraDirectorGuidance(
                    referenceTitle = reference.photo.title,
                    sourceLabel = reference.photo.sourceLabel,
                ),
                onBack = { navigateTo(AppDestination.DIRECTOR_CARD) },
                onReturnToProject = {
                    if (cameraProjectId != null) selectedProjectId = cameraProjectId
                    navigateTo(if (cameraProjectId != null) AppDestination.PROJECT_BOARD else AppDestination.HOME)
                },
            )
        }

        AppDestination.DIRECT_CAPTURE -> CameraScreen(
            captureLibrary = captureLibrary,
            projectId = captureProjectId,
            guidanceItems = emptyList(),
            directCaptureMode = true,
            onBack = { navigateTo(AppDestination.CAPTURE_ENTRY) },
        )
    }

    CaptureLibraryHost(captureLibrary, projects)

    recoverySummaryToShow?.let { summary ->
        RecoverySummaryDialog(
            summary = summary,
            onDismiss = { recoverySummaryToShow = null },
        )
    }
    pairingProjectId?.let { projectId ->
        LocalAnalysisConnectionDialog(
            photoCount = selectedProjectRecords.size,
            errorMessage = pairingError,
            onDismiss = { pairingProjectId = null },
            onPair = { baseUrl, pairingCode, certificatePin ->
                pairingError = null
                scope.launch {
                    LocalLanPairingClient.pair(baseUrl, pairingCode, certificatePin)
                        .onSuccess { connection ->
                            localConnection = connection
                            pairingProjectId = null
                            startProjectAnalysis(projectId, connection)
                        }
                        .onFailure { pairingError = "配对失败，请检查本机地址、一次性配对码和证书 pin。" }
                }
            },
        )
    }
}

@Composable
private fun ReferenceStartupRecoveryScreen() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(com.jovi.photoai.ui.design.AppDimensions.PagePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("正在恢复本地参考图库")
        Text("恢复完成前不会显示参考图片或记录。")
    }
}

@Composable
private fun RecoverySummaryDialog(
    summary: ReferenceRecoverySummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本地参考图库已恢复") },
        text = {
            Text(recoverySummaryMessage(summary))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
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

private class PhotoKnowledgeBundleImportViewModelFactory(
    private val application: Application,
    private val repository: ReferenceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PhotoKnowledgeBundleImportViewModel(repository, application.contentResolver) as T
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

internal fun resolveStartupRestore(
    current: AppDestination,
    hasPersistedActiveReference: Boolean,
    restoredActiveReference: Boolean,
): StartupRestoreDecision = when {
    hasPersistedActiveReference && restoredActiveReference -> StartupRestoreDecision(
        destination = AppDestination.ANALYSIS_DETAIL,
        shouldClearPersistedActiveReference = false,
    )
    hasPersistedActiveReference -> StartupRestoreDecision(
        destination = AppDestination.REFERENCE_LIBRARY,
        shouldClearPersistedActiveReference = true,
    )
    else -> StartupRestoreDecision(current, shouldClearPersistedActiveReference = false)
}

internal fun referenceContentVisible(state: ReferenceStartupState): Boolean =
    state == ReferenceStartupState.READY

internal fun restoredReferenceReturnDestination(projectId: String): AppDestination =
    if (projectId == LEGACY_PROJECT_ID) AppDestination.REFERENCE_LIBRARY else AppDestination.PROJECT_BOARD

internal fun recoverySummaryMessage(summary: ReferenceRecoverySummary): String = buildString {
    if (summary.recoveredItemCount > 0) {
        append("已安全清理 ${summary.recoveredItemCount} 项无效或未完成的本地数据。")
    }
    if (summary.isolatedInvalidRecordsPendingRetry > 0) {
        if (isNotEmpty()) append('\n')
        append("另有 ${summary.isolatedInvalidRecordsPendingRetry} 项已隔离，稍后会继续清理。")
    }
    if (isNotEmpty()) append('\n')
    append("未显示图片、文件名或路径。")
}
