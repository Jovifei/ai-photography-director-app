package com.jovi.photoai.p25t

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
            var state by mutableStateOf(KnowledgeBundleImportUiState(bundle = bundle(20)))
            rule.setContent {
                PhotoDirectorTheme {
                    PhotoKnowledgeBundleImportScreen(
                        project = project,
                        records = records,
                        state = state,
                        onDocumentSelected = {},
                        onBind = { producer, local ->
                            state = state.copy(
                                bindings = if (state.bindings[producer] == local) {
                                    state.bindings - producer
                                } else {
                                    state.bindings + (producer to local)
                                },
                            )
                        },
                        onApply = {},
                        onReset = {},
                        onBack = {},
                    )
                }
            }
            val target20 = records[19].photo.id
            val target19 = records[18].photo.id
            rule.onNodeWithTag("bundle-choose-producer_0").performScrollTo().performClick()
            rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$target20"))
            rule.waitUntil(5_000) {
                rule.onAllNodesWithContentDescription("候选照片第 20 张").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("bundle-target-$target20").assertIsDisplayed().performClick()
            rule.onNodeWithTag("bundle-choose-producer_1").performScrollTo().performClick()
            rule.onNodeWithTag("bundle-target-list").performScrollToNode(hasTestTag("bundle-target-$target20"))
            rule.onNodeWithTag("bundle-target-$target20").assertIsNotEnabled()
            rule.onNodeWithTag("bundle-target-$target19").performClick()
            rule.onNodeWithTag("bundle-unbind-producer_0").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(target19, state.bindings["producer_1"])
                assertTrue("unbind must remove the first mapping", "producer_0" !in state.bindings)
            }
        } finally {
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
