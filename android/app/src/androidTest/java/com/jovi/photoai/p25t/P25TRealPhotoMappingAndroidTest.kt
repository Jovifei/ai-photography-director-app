package com.jovi.photoai.p25t

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.content.ContentValues
import android.provider.MediaStore
import androidx.lifecycle.ViewModelStore
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleImportViewModel
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParser
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.KnowledgeBundleBinding
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.KnowledgeBundlePhotography
import com.jovi.photoai.data.reference.KnowledgeBundleSource
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult.Success
import com.jovi.photoai.data.reference.KnowledgeBundleImportUiState
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.data.reference.canonicalPayloadSha256
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.project.PhotoKnowledgeBundleImportScreen
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real generated JPEGs + app-private derivatives + visual binding; no user media. */
@RunWith(AndroidJUnit4::class)
class P25TRealPhotoMappingAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun twentyGeneratedJpegs_areDecodedPersistedAndReverseBoundInRealRoom() = runBlocking {
        val repository = ReferenceRepository.create(context)
        val project = repository.createProject("P25T 真实缩略图合成项目")
        try {
            val imported = buildList {
                SyntheticPickerMediaFactory(context).use { media ->
                    repeat(20) { index ->
                        val fixture = media.jpeg(
                            width = 64 + index * 5,
                            height = 48 + (index % 5) * 17,
                            color = android.graphics.Color.rgb(32 + index * 7, 64 + index * 5, 128 + index * 3),
                            displayName = "p25t-real-${index.toString().padStart(2, '0')}.jpg",
                        )
                        assertTrue("source JPEG must decode", media.directDecodes(fixture))
                        val result = repository.importIntoProject(fixture.uri, project.id)
                        assertTrue(result is ReferenceImportResult.Success)
                        add((result as ReferenceImportResult.Success).record)
                    }
                }
            }
            assertEquals(20, imported.size)
            val persisted = repository.activeRecordsForProject(project.id).first()
            assertEquals(20, persisted.size)
            persisted.forEach { record ->
                val bytes = repository.privateAnalysisInput(record.photo.id)?.jpegBytes
                assertNotNull(bytes)
                assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size))
                assertEquals(PhotoAnalysisStatus.IMPORTED, record.analysisStatus)
            }

            val bundle = bundle(20)
            val reverseBindings = bundle.references.mapIndexed { index, item ->
                KnowledgeBundleBinding(item.referenceId, persisted[19 - index].photo.id)
            }
            assertEquals(Success(20), repository.applyKnowledgeBundle(project.id, bundle, reverseBindings))
            val ready = repository.activeRecordsForProject(project.id).first()
            assertEquals(20, ready.size)
            bundle.references.forEachIndexed { index, item ->
                val target = ready.single { it.photo.id == persisted[19 - index].photo.id }
                assertEquals(PhotoAnalysisStatus.READY, target.analysisStatus)
                assertEquals(item.photography.scene, target.bundle.scene)
                assertEquals(item.photography.lighting, target.bundle.lighting)
                assertEquals(item.photography.composition, target.bundle.composition)
                assertEquals("synthetic_internal_handoff_test", target.knowledgeBundleProvenance?.producerId)
            }
        } finally {
            repository.deleteProject(project.id)
        }
    }

    @Test
    fun realPrivateThumbnails_areVisibleForExplicitReverseBindingAndUnbinding() = runBlocking {
        val repository = ReferenceRepository.create(context)
        val project = repository.createProject("P25T 看图绑定合成项目")
        val store = ViewModelStore()
        val model = PhotoKnowledgeBundleImportViewModel(repository, context.contentResolver)
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("p25s/internal-handoff-20.bundle.json").use { it.readBytes() }
        val expected = (PhotoKnowledgeBundleParser.parse(bytes) as PhotoKnowledgeBundleParseResult.Success).bundle
        val name = "p25t-${java.util.UUID.randomUUID()}.json"
        val document = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
        context.contentResolver.openOutputStream(document)!!.use { it.write(bytes) }
        context.contentResolver.update(document, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
        rule.runOnUiThread { store.put("real-mapping", model) }
        try {
            val records = buildList {
                SyntheticPickerMediaFactory(context).use { media ->
                    repeat(20) { index ->
                        val result = repository.importIntoProject(
                            media.jpeg(
                                width = 80 + index,
                                height = 60 + index * 2,
                                color = android.graphics.Color.rgb(40 + index * 6, 90 + index * 4, 150 + index * 2),
                                displayName = "p25t-ui-${index.toString().padStart(2, '0')}.jpg",
                            ).uri,
                            project.id,
                        )
                        assertTrue(result is ReferenceImportResult.Success)
                        add((result as ReferenceImportResult.Success).record)
                    }
                }
            }
            rule.setContent {
                PhotoDirectorTheme {
                    val state by model.state.collectAsState()
                    PhotoKnowledgeBundleImportScreen(
                        project = project,
                        records = records,
                        state = state,
                        onDocumentSelected = model::readDocument,
                        onBind = model::bind,
                        onApply = { model.apply(project.id) },
                        onReset = model::reset,
                        onBack = {},
                    )
                }
            }
            val target20 = records[19].photo.id
            val target19 = records[18].photo.id
            rule.onNodeWithText("选择 JSON 知识包").performClick()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            var node = device.wait(Until.findObject(By.text(name)), 15000)
            if (node == null) {
                (device.findObject(By.text("下载")) ?: device.findObject(By.text("Downloads")))?.click()
                node = device.wait(Until.findObject(By.text(name)), 15000)
            }
            requireNotNull(node) { "P25T_SYSTEM_DOCUMENT_NOT_VISIBLE" }.click()
            rule.waitUntil(10000) { model.state.value.bundle != null }
            val firstId = expected.references[0].referenceId
            val secondId = expected.references[1].referenceId
            rule.onNodeWithTag("bundle-choose-$firstId").performScrollTo().performClick()
            rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$target20"))
            rule.waitUntil(5_000) {
                rule.onAllNodesWithContentDescription("候选照片第 20 张").fetchSemanticsNodes().isNotEmpty()
            }
            val scale = android.provider.Settings.System.getFloat(context.contentResolver, "font_scale")
            val image = rule.onNodeWithTag("bundle-target-list").captureToImage().asAndroidBitmap()
            java.io.File(context.getExternalFilesDir(null), "p25t-mapping-$scale.png").outputStream().use {
                assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
            rule.onNodeWithTag("bundle-target-$target20").assertIsDisplayed().performClick()
            rule.onNodeWithTag("bundle-choose-$secondId").performScrollTo().performClick()
            rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$target20"))
            rule.onNodeWithTag("bundle-target-$target20").assertIsNotEnabled()
            rule.onNodeWithTag("bundle-target-$target19").performClick()
            rule.onNodeWithTag("bundle-unbind-$firstId").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(target19, model.state.value.bindings[secondId])
                assertTrue("unbind must remove the first mapping", firstId !in model.state.value.bindings)
            }
            expected.references.forEachIndexed { index, item ->
                if (index != 1) {
                    val id = records[19 - index].photo.id
                    rule.onNodeWithTag("bundle-choose-${item.referenceId}").performScrollTo().performClick()
                    rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$id"))
                    rule.onNodeWithTag("bundle-target-$id").performClick()
                }
            }
            rule.onNodeWithText("确认全部绑定并导入").performScrollTo().performClick()
            rule.waitUntil(10000) { model.state.value.appliedCount == 20 }
            val ready = repository.activeRecordsForProject(project.id).first().associateBy { it.photo.id }
            expected.references.forEachIndexed { index, item ->
                val row = ready.getValue(records[19 - index].photo.id)
                val p = item.photography
                val b = row.bundle
                assertEquals(listOf(p.scene, p.backgroundStory, p.lighting, p.composition, p.subjectIntent,
                    p.emotion, p.poseTemplate, p.cameraPosition, p.directorPrompt),
                    listOf(b.scene, b.backgroundStory, b.lighting, b.composition, b.subjectIntent,
                        b.emotion, b.poseTemplate, b.cameraPosition, b.directorPrompt))
                assertEquals(item.referenceId, row.knowledgeBundleProvenance?.producerReferenceId)
                assertEquals(PhotoAnalysisStatus.READY, row.analysisStatus)
            }
        } finally {
            rule.runOnUiThread { store.clear() }
            context.contentResolver.delete(document, null, null)
            repository.deleteProject(project.id)
        }
    }

    private fun bundle(count: Int): PhotoKnowledgeBundle {
        val unsigned = PhotoKnowledgeBundle(
            contractVersion = "1.0",
            bundleId = "p25t_real_jpeg_bundle",
            source = KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic_internal_handoff_test", "not_for_release"),
            payloadSha256 = "0".repeat(64),
            references = (0 until count).map { index ->
                PhotoKnowledgeBundleItem(
                    referenceId = "producer_$index",
                    photography = KnowledgeBundlePhotography(
                        scene = "scene-$index", backgroundStory = "background-$index", lighting = "lighting-$index",
                        composition = "composition-$index", subjectIntent = "subject-$index", emotion = "emotion-$index",
                        poseTemplate = "pose-$index", cameraPosition = "camera-$index", directorPrompt = "prompt-$index",
                    ),
                )
            },
        )
        return unsigned.copy(payloadSha256 = canonicalPayloadSha256(unsigned))
    }
}
