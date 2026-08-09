package com.jovi.photoai.ui1

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.LocalLanAnalysisConnection
import com.jovi.photoai.data.reference.LocalLanProjectSummaryProvider
import com.jovi.photoai.data.reference.LocalLanReferenceAnalysisProvider
import com.jovi.photoai.data.reference.PhotoAnalysisCoordinator
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceLibraryPreferences
import com.jovi.photoai.data.reference.ReferenceRepository
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-batch harness: one anonymous derivative is exposed to the system Picker per iteration. */
@RunWith(AndroidJUnit4::class)
class P20RealPhotoAnalysisAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = ReferenceRepository.create(context)
    private val preferences = ReferenceLibraryPreferences(context)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetLibrary() = runBlocking {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        device.wait(Until.hasObject(By.pkg("com.jovi.photoai")), PICKER_TIMEOUT)
        repository.clearAll()
        preferences.clearLastActiveReferenceId()
    }

    @After
    fun cleanup() = runBlocking {
        repository.clearAll()
        preferences.clearLastActiveReferenceId()
        Unit
    }

    @Test
    fun realTwentyAnonymousPhotos_areImportedAnalyzedAndSummarized() = runBlocking {
        val names = (1..20).map { "photo_%03d.jpg".format(Locale.US, it) }
        val projectId = importTwentyThroughPicker(names)
        val args = InstrumentationRegistry.getArguments()
        val connection = pairConnection(args)
        PhotoAnalysisCoordinator(
            repository = repository,
            provider = LocalLanReferenceAnalysisProvider(repository, connection),
            summaryProvider = LocalLanProjectSummaryProvider(connection),
        ).analyzeProject(projectId)
        val records = repository.activeRecordsForProject(projectId).first()
        val ready = records.count { it.analysisStatus == PhotoAnalysisStatus.READY }
        val failed = records.count { it.analysisStatus == PhotoAnalysisStatus.FAILED || it.analysisStatus == PhotoAnalysisStatus.UNAVAILABLE }
        val cancelled = records.count { it.analysisStatus == PhotoAnalysisStatus.CANCELLED }
        val itemEvidence = JSONArray(records.sortedBy { it.ordinal }.map { record ->
            JSONObject()
                .put("ordinal", record.ordinal + 1)
                .put("status", record.analysisStatus.name)
                .put("error_code", record.safeAnalysisErrorCode ?: JSONObject.NULL)
                .put("latency_ms", record.analysisProvenance?.latencyMillis ?: JSONObject.NULL)
        })
        val output = JSONObject()
            .put("gate", "P20_REAL_20_PHOTO_VLM_ANALYSIS")
            .put("total", records.size)
            .put("ready", ready)
            .put("failed", failed)
            .put("cancelled", cancelled)
            .put("summary_status", repository.projectSummary(projectId)?.status?.name ?: "MISSING")
            .put("items", itemEvidence)
            .put("status", if (records.size == 20 && ready >= 18 && failed <= 2 && cancelled == 0) "PASS" else "FAIL")
        File(context.filesDir, EVIDENCE_FILE).writeText(output.toString(), Charsets.UTF_8)
        assertEquals(20, records.size)
        assertTrue("P20_READY_GATE_FAILED", ready >= 18)
        assertTrue("P20_FAILED_GATE_FAILED", failed <= 2)
        assertEquals(0, cancelled)
    }

    @Test
    fun pairingSmoke_usesPinnedLocalCertificate() {
        pairConnection(InstrumentationRegistry.getArguments())
    }

    @Test
    fun realTwentyAnonymousPhotos_importOnlyReportsProjectBinding() = runBlocking {
        val names = (1..20).map { "photo_%03d.jpg".format(Locale.US, it) }
        val projectId = importTwentyThroughPicker(names)
        val records = repository.activeRecordsForProject(projectId).first()
        val projectCount = repository.projects.first().size
        File(context.filesDir, EVIDENCE_FILE).writeText(
            JSONObject()
                .put("gate", "P20_IMPORT_BINDING_DIAGNOSTIC")
                .put("project_count", projectCount)
                .put("target_total", records.size)
                .put("status", if (records.size == 20) "PASS" else "FAIL")
                .toString(),
            Charsets.UTF_8,
        )
        assertEquals(20, records.size)
    }

    private suspend fun importTwentyThroughPicker(names: List<String>): String {
        clickAction("新建拍摄项目")
        val existingProjectIds = repository.projects.first().map { it.id }.toSet()
        val projectId = awaitProjectId(existingProjectIds)
        names.forEachIndexed { index, name ->
            val row = HostDerivativeMediaRow(context, name)
            row.insert()
            try {
                // API 35 Photo Picker indexes a newly published MediaStore row asynchronously.
                delay(750)
                clickPhotoPickerAction()
                val thumbnail = device.wait(Until.findObject(By.res(PICKER_PACKAGE, "icon_thumbnail")), PICKER_TIMEOUT)
                    ?: error("P20_PICKER_THUMBNAIL_MISSING_${index + 1}")
                thumbnail.click()
                val addButton = device.wait(Until.findObject(By.res(PICKER_PACKAGE, "button_add")), SHORT_PICKER_TIMEOUT)
                if (addButton != null) {
                    addButton.click()
                } else if (!device.wait(Until.hasObject(By.pkg("com.jovi.photoai")), SHORT_PICKER_TIMEOUT)) {
                    error("P20_PICKER_CONFIRM_MISSING_${index + 1}")
                }
                awaitImportedCount(projectId, index + 1)
            } finally {
                row.delete()
            }
        }
        return projectId
    }

    private suspend fun awaitImportedCount(projectId: String, expected: Int) {
        repeat(100) {
            if (repository.activeRecordsForProject(projectId).first().size == expected) return
            delay(200)
        }
        error("P20_IMPORT_NOT_COMPLETED")
    }

    private suspend fun awaitProjectId(existingProjectIds: Set<String>): String {
        repeat(50) {
            repository.projects.first()
                .asSequence()
                .firstOrNull { it.id != "legacy-project-v1" && it.id !in existingProjectIds }
                ?.id
                ?.let { return it }
            delay(200)
        }
        error("P20_PROJECT_NOT_CREATED")
    }

    private fun pairConnection(args: android.os.Bundle): LocalLanAnalysisConnection {
        val baseUrl = requireNotNull(args.getString("p20BaseUrl"))
        val pairingCode = requireNotNull(args.getString("p20PairingCode"))
        val certificatePin = requireNotNull(args.getString("p20CertificatePin"))
        val result = runBlocking {
            com.jovi.photoai.data.reference.LocalLanPairingClient.pair(baseUrl, pairingCode, certificatePin)
        }
        return result.getOrElse { error ->
            val detail = error.message.orEmpty().replace(Regex("[^A-Za-z0-9]+"), "_").take(96)
            throw IllegalStateException("P20_PAIRING_FAILED_${error::class.java.simpleName}_$detail")
        }
    }

    private fun clickAction(description: String) {
        composeRule.waitUntil(PICKER_TIMEOUT) {
            composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .onFirst().performScrollTo().performClick()
    }

    private fun clickPhotoPickerAction() {
        composeRule.waitUntil(PICKER_TIMEOUT) {
            composeRule.onAllNodesWithContentDescription("选择照片", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("继续添加照片", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val choose = composeRule.onAllNodesWithContentDescription("选择照片", useUnmergedTree = true)
        if (choose.fetchSemanticsNodes().isNotEmpty()) {
            choose.onFirst().performScrollTo().performClick()
        } else {
            composeRule.onAllNodesWithText("继续添加照片", substring = true, useUnmergedTree = true)
                .onFirst().performScrollTo().performClick()
        }
    }

    private companion object {
        const val PICKER_PACKAGE = "com.google.android.providers.media.module"
        const val PICKER_TIMEOUT = 10_000L
        const val SHORT_PICKER_TIMEOUT = 2_000L
        const val EVIDENCE_FILE = "p20-sanitized-aggregate.json"
    }
}

private class HostDerivativeMediaRow(private val context: Context, private val displayName: String) {
    private var uri: Uri? = null

    fun insert() {
        val source = File(requireNotNull(context.getExternalFilesDir("P20")), displayName)
        require(source.isFile) { "P20_DERIVATIVE_MISSING" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/P20Fixture")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
        resolver.openOutputStream(requireNotNull(uri), "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
            ?: error("P20_MEDIASTORE_WRITE_FAILED")
        resolver.update(requireNotNull(uri), ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }

    fun delete() {
        uri?.let { context.contentResolver.delete(it, null, null) }
        uri = null
    }
}
