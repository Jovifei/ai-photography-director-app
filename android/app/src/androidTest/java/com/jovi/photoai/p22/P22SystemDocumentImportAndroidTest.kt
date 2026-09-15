package com.jovi.photoai.p22

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.KnowledgeBundleImportUiState
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.KnowledgeBundlePhotography
import com.jovi.photoai.data.reference.KnowledgeBundleSource
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleDocumentReader
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import com.jovi.photoai.data.reference.canonicalPayloadSha256
import com.jovi.photoai.p23r.P23RRoomFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P22SystemDocumentImportAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun systemOpenDocument_requiresExplicitPhotoMappingBeforeImport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "p22-synthetic-knowledge-${System.currentTimeMillis()}.json"
        val documentUri = publishSyntheticDocument(context, fileName)
        try {
            P23RRoomFixture().use { fixture ->
            runBlocking { fixture.seed(1) }
            val state = mutableStateOf(KnowledgeBundleImportUiState())
            val applyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            composeRule.activity.runOnUiThread {
                composeRule.activity.setContent {
                    PhotoDirectorTheme {
                        PhotoKnowledgeBundleImportScreen(
                            project = fixture.project,
                            records = fixture.records,
                            state = state.value,
                            onDocumentSelected = { uri ->
                                state.value = when (val result = PhotoKnowledgeBundleDocumentReader(context.contentResolver).read(uri)) {
                                    is PhotoKnowledgeBundleParseResult.Success -> KnowledgeBundleImportUiState(bundle = result.bundle)
                                    is PhotoKnowledgeBundleParseResult.Failure -> KnowledgeBundleImportUiState(parseError = result.code)
                                }
                            },
                            onBind = { producerId, localId -> state.value = state.value.copy(bindings = mapOf(producerId to localId)) },
                            onApply = {
                                state.value.bundle?.let { bundle ->
                                    val bindings = state.value.bindings.map { (producer, local) ->
                                        com.jovi.photoai.data.reference.KnowledgeBundleBinding(producer, local)
                                    }
                                    applyScope.launch {
                                        state.value = when (val result = fixture.repository.applyKnowledgeBundle(fixture.project.id, bundle, bindings)) {
                                            is KnowledgeBundleApplyResult.Success -> KnowledgeBundleImportUiState(appliedCount = result.appliedCount)
                                            is KnowledgeBundleApplyResult.Failure -> KnowledgeBundleImportUiState(applyError = result.code)
                                            KnowledgeBundleApplyResult.OutcomeUnknown -> KnowledgeBundleImportUiState(applyOutcomeUnknown = true)
                                        }
                                    }
                                }
                            },
                            onReset = { state.value = KnowledgeBundleImportUiState() },
                            onBack = {},
                        )
                    }
                }
            }

            composeRule.onNodeWithText("选择 JSON 知识包").performClick()
            var fileNode = device.wait(Until.findObject(By.text(fileName)), DOCUMENT_TIMEOUT_MILLIS)
            if (fileNode == null) {
                device.findObject(By.text("下载"))?.click() ?: device.findObject(By.text("Downloads"))?.click()
                fileNode = device.wait(Until.findObject(By.text(fileName)), DOCUMENT_TIMEOUT_MILLIS)
            }
            assertNotNull("Synthetic JSON must be visible in the system document picker", fileNode)
            fileNode!!.click()

            composeRule.waitUntil(DOCUMENT_TIMEOUT_MILLIS) { state.value.bundle != null }
            composeRule.onNodeWithText("格式与摘要校验通过 · 1 条").assertIsDisplayed()
            composeRule.onNodeWithText("第 1 张").performScrollTo().performClick()
            composeRule.onNodeWithText("确认全部绑定并导入").performScrollTo().performClick()
            composeRule.waitUntil(DOCUMENT_TIMEOUT_MILLIS) { state.value.appliedCount == 1 }
            composeRule.onNodeWithText("已将 1 条知识逐张写入项目。").assertIsDisplayed()
            val persisted = runBlocking { fixture.dao.activeById(fixture.records.single().photo.id) }
            assertEquals("READY", persisted?.analysisStatus)
            assertEquals("bundle_document_001", persisted?.knowledgeBundleId)
            applyScope.cancel()
            }
        } finally {
            context.contentResolver.delete(documentUri, null, null)
        }
    }

    private fun publishSyntheticDocument(context: Context, fileName: String): android.net.Uri {
        val bundle = signedBundle()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assertNotNull(uri)
        context.contentResolver.openOutputStream(uri!!)!!.use { stream ->
            stream.write(bundleJson(bundle).toString().toByteArray(Charsets.UTF_8))
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun signedBundle(): PhotoKnowledgeBundle {
        val unsigned = PhotoKnowledgeBundle(
            "1.0",
            "bundle_document_001",
            KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic_pipeline", "release_001"),
            "0".repeat(64),
            listOf(
                PhotoKnowledgeBundleItem(
                    "producer_photo_001",
                    KnowledgeBundlePhotography(
                        "合成场景", "合成背景", "合成光线", "合成构图", "合成主体", "合成情绪", "合成姿态", "合成机位", "合成指导",
                    ),
                ),
            ),
        )
        return unsigned.copy(payloadSha256 = canonicalPayloadSha256(unsigned))
    }

    private fun bundleJson(bundle: PhotoKnowledgeBundle): JSONObject = JSONObject()
        .put("contract_version", bundle.contractVersion)
        .put("bundle_id", bundle.bundleId)
        .put("source", JSONObject().put("origin", bundle.source.origin.name).put("producer_id", bundle.source.producerId).put("release_id", bundle.source.releaseId))
        .put("integrity", JSONObject().put("algorithm", "SHA-256").put("payload_sha256", bundle.payloadSha256))
        .put(
            "references",
            JSONArray(bundle.references.map { item ->
                JSONObject()
                    .put("reference_id", item.referenceId)
                    .put(
                        "photography",
                        JSONObject()
                            .put("scene", item.photography.scene)
                            .put("background_story", item.photography.backgroundStory)
                            .put("lighting", item.photography.lighting)
                            .put("composition", item.photography.composition)
                            .put("subject_intent", item.photography.subjectIntent)
                            .put("emotion", item.photography.emotion)
                            .put("pose_template", item.photography.poseTemplate)
                            .put("camera_position", item.photography.cameraPosition)
                            .put("director_prompt", item.photography.directorPrompt),
                    )
            }),
        )

    private companion object {
        const val DOCUMENT_TIMEOUT_MILLIS = 15_000L
    }
}
