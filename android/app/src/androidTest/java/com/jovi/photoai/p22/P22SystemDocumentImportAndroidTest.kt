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
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.data.reference.KnowledgeBundleImportUiState
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.KnowledgeBundlePhotography
import com.jovi.photoai.data.reference.KnowledgeBundleSource
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleDocumentReader
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import com.jovi.photoai.data.reference.PhotographyProject
import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.canonicalPayloadSha256
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import org.json.JSONArray
import org.json.JSONObject
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
        val state = mutableStateOf(KnowledgeBundleImportUiState())
        val project = PhotographyProject("project_001", "合成项目", null, 0, 1L, 1L)
        val record = ReferenceRecord(
            ReferencePhoto("local_001", "合成照片", "测试", "已导入", "private/local_001.jpg", 1f),
            DemoReferenceAnalyzer.analyze("local_001", "p22-system-document"),
            "local_001.jpg",
            1L,
            project.id,
            0,
            PhotoAnalysisStatus.IMPORTED,
        )
        try {
            composeRule.activity.runOnUiThread {
                composeRule.activity.setContent {
                    PhotoDirectorTheme {
                        PhotoKnowledgeBundleImportScreen(
                            project = project,
                            records = listOf(record),
                            state = state.value,
                            onDocumentSelected = { uri ->
                                state.value = when (val result = PhotoKnowledgeBundleDocumentReader(context.contentResolver).read(uri)) {
                                    is PhotoKnowledgeBundleParseResult.Success -> KnowledgeBundleImportUiState(bundle = result.bundle)
                                    is PhotoKnowledgeBundleParseResult.Failure -> KnowledgeBundleImportUiState(parseError = result.code)
                                }
                            },
                            onBind = { producerId, localId -> state.value = state.value.copy(bindings = mapOf(producerId to localId)) },
                            onApply = { state.value = KnowledgeBundleImportUiState(appliedCount = 1) },
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
            composeRule.onNodeWithText("已验证 1 条", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("第 1 张").performScrollTo().performClick()
            composeRule.onNodeWithText("确认全部绑定并导入").performScrollTo().performClick()
            composeRule.onNodeWithText("已将 1 条知识逐张写入项目。").assertIsDisplayed()
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
