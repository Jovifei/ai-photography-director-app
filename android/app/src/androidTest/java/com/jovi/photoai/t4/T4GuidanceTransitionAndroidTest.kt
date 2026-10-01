package com.jovi.photoai.t4

import android.Manifest
import android.app.Application
import androidx.room.Room
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.data.reference.AnalysisAttempt
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ProviderAnalysisProvenance
import com.jovi.photoai.data.reference.ProviderAnalysisResult
import com.jovi.photoai.data.reference.ProviderType
import com.jovi.photoai.data.reference.ReferenceAnalysisRequest
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceLibraryDatabase
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.data.reference.SafeProviderErrorCode
import com.jovi.photoai.data.reference.withAnalysisResult
import com.jovi.photoai.ui.PhotographyDirectorAppContent
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Root UI and real Room Flow, isolated from the default app database and preferences. */
@RunWith(AndroidJUnit4::class)
class T4GuidanceTransitionAndroidTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private fun openProjectWhenVisible(title: String) {
        // Room Flow and startup reconciliation can finish after setContent becomes idle.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(title).onFirst().performClick()
    }

    @Test fun mismatchedBundleFailsClosedAndReadyThenUnavailableDropsLiveGuidance() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(application, ReferenceLibraryDatabase::class.java).build()
        val repository = ReferenceRepository.createForTest(application, database)
        val preferencesName = "t4-guidance-${UUID.randomUUID().toString().replace("-", "")}"
        val preferences = ReferenceLibraryPreferences(application, preferencesName)
        var showApp by mutableStateOf(true)
        var contentMounted = false
        SyntheticPickerMediaFactory(application).use { media ->
            var projectId: String? = null
            try {
                val imported = runBlocking {
                    val project = repository.createProject("T4 合成状态项目")
                    projectId = project.id
                    val result = repository.importIntoProject(media.jpeg().uri, project.id)
                    assertTrue(result is ReferenceImportResult.Success)
                    val record = (result as ReferenceImportResult.Success).record
                    assertTrue(repository.setPrimaryReference(project.id, record.photo.id))
                    record
                }
                val referenceId = imported.photo.id
                val request = ReferenceAnalysisRequest(referenceId)
                val provenance = ProviderAnalysisProvenance(
                    providerId = "synthetic-provider", providerType = ProviderType.LOCAL_SERVICE,
                    modelId = "synthetic-model", modelRevision = "synthetic-revision",
                    modelArtifactSha256 = "a".repeat(64), runtimeId = "synthetic-runtime",
                    startedAtEpochMillis = 100L, completedAtEpochMillis = 125L,
                )
                rule.setContent {
                    if (showApp) PhotoDirectorTheme {
                        PhotographyDirectorAppContent(application, repository, preferences)
                    }
                }
                contentMounted = true
                openProjectWhenVisible("T4 合成状态项目")
                rule.onNodeWithText("使用主参考进行无 AI 拍摄").performScrollTo().assertExists()

                val mismatch = AnalysisAttempt(referenceId, "t4-mismatch")
                runBlocking {
                    assertTrue(repository.markAnalysisQueued(mismatch))
                    assertTrue(repository.markAnalysisRunning(mismatch))
                    repository.persistAnalysis(request,
                        ProviderAnalysisResult.Ready(imported.bundle.copy(referenceId = "wrong-id"), provenance), mismatch)
                    assertEquals(PhotoAnalysisStatus.FAILED, repository.activeRecord(referenceId)?.analysisStatus)
                }
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithContentDescription("项目照片第 1 张，分析失败").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("使用主参考进行无 AI 拍摄").performScrollTo().performClick()
                rule.onNodeWithText("无指导直接拍摄").assertExists()
                rule.onNodeWithText("Camera Director").assertDoesNotExist()
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText("项目看板").fetchSemanticsNodes().isNotEmpty()
                }

                val accepted = AnalysisAttempt(referenceId, "t4-ready")
                runBlocking {
                    assertTrue(repository.markAnalysisQueued(accepted))
                    assertTrue(repository.markAnalysisRunning(accepted))
                    repository.persistAnalysis(request,
                        ProviderAnalysisResult.Ready(imported.bundle.copy(scene = "T4 合成场景"), provenance), accepted)
                    assertEquals(PhotoAnalysisStatus.READY, repository.activeRecord(referenceId)?.analysisStatus)
                }
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText("使用主参考进入 AI 拍摄").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("使用主参考进入 AI 拍摄").performScrollTo().performClick()
                rule.onNodeWithText("Camera Director").assertExists()

                runBlocking {
                    val entity = requireNotNull(database.referenceDao().activeById(referenceId))
                    database.referenceDao().update(entity.withAnalysisResult(
                        ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.PROVIDER_NOT_CONFIGURED)))
                }
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText("无指导直接拍摄").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("Camera Director").assertDoesNotExist()
                rule.onNodeWithText("无指导直接拍摄").assertExists()
            } finally {
                try {
                    if (contentMounted) {
                        rule.runOnIdle { showApp = false }
                        rule.waitForIdle()
                    }
                    runBlocking { projectId?.let { assertTrue(repository.deleteProject(it)) } }
                } finally {
                    database.close()
                    application.deleteSharedPreferences(preferencesName)
                }
            }
        }
    }

    @Test fun deletingGuidedPrimaryReturnsSafelyAndNextReferenceHasNoOldGuidance() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(application, ReferenceLibraryDatabase::class.java).build()
        val repository = ReferenceRepository.createForTest(application, database)
        val preferencesName = "t4-switch-${UUID.randomUUID().toString().replace("-", "")}"
        val preferences = ReferenceLibraryPreferences(application, preferencesName)
        var showApp by mutableStateOf(true)
        var contentMounted = false
        SyntheticPickerMediaFactory(application).use { media ->
            var projectId: String? = null
            try {
                val first = runBlocking {
                    val project = repository.createProject("T4 切换项目")
                    projectId = project.id
                    val a = repository.importIntoProject(media.jpeg().uri, project.id) as ReferenceImportResult.Success
                    repository.importIntoProject(media.jpeg().uri, project.id) as ReferenceImportResult.Success
                    assertTrue(repository.setPrimaryReference(project.id, a.record.photo.id))
                    val attempt = AnalysisAttempt(a.record.photo.id, "t4-switch-ready")
                    assertTrue(repository.markAnalysisQueued(attempt))
                    assertTrue(repository.markAnalysisRunning(attempt))
                    repository.persistAnalysis(
                        ReferenceAnalysisRequest(a.record.photo.id),
                        ProviderAnalysisResult.Ready(
                            a.record.bundle.copy(scene = "旧指导场景"),
                            ProviderAnalysisProvenance(
                                providerId = "synthetic-provider", providerType = ProviderType.LOCAL_SERVICE,
                                modelId = "synthetic-model", modelRevision = "synthetic-revision",
                                modelArtifactSha256 = "b".repeat(64), runtimeId = "synthetic-runtime",
                                startedAtEpochMillis = 100L, completedAtEpochMillis = 125L,
                            ),
                        ), attempt,
                    )
                    a.record
                }
                rule.setContent {
                    if (showApp) PhotoDirectorTheme {
                        PhotographyDirectorAppContent(application, repository, preferences)
                    }
                }
                contentMounted = true
                openProjectWhenVisible("T4 切换项目")
                rule.onNodeWithText("使用主参考进入 AI 拍摄").performScrollTo().performClick()
                rule.onNodeWithText("Camera Director").assertExists()

                runBlocking { repository.delete(first.photo.id) }
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithText("拍摄项目").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("Camera Director").assertDoesNotExist()
                openProjectWhenVisible("T4 切换项目")
                rule.onNodeWithContentDescription("设为主参考").performScrollTo().performClick()
                rule.onNodeWithText("使用主参考进行无 AI 拍摄").performScrollTo().performClick()
                rule.onNodeWithText("无指导直接拍摄").assertExists()
                rule.onNodeWithText("Camera Director").assertDoesNotExist()
            } finally {
                try {
                    if (contentMounted) {
                        rule.runOnIdle { showApp = false }
                        rule.waitForIdle()
                    }
                    runBlocking { projectId?.let { assertTrue(repository.deleteProject(it)) } }
                } finally {
                    database.close()
                    application.deleteSharedPreferences(preferencesName)
                }
            }
        }
    }
}
