package com.jovi.photoai.p25u

import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.*
import com.jovi.photoai.ui.capture.CaptureLibraryContent
import com.jovi.photoai.ui.capture.CaptureLibraryUiState
import com.jovi.photoai.ui.capture.decodeCaptureImage
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real JPEG decoder and production gallery; UI callbacks are observed here, Room is tested separately. */
@RunWith(AndroidJUnit4::class)
class P25UCaptureUiAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun originalIsVisibleAndPrivateDeleteRequiresExplicitConfirmation() {
        P25UCaptureFixture().use { f ->
            val row = f.ready()
            var deleted: String? = null
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    PhotoDirectorTheme {
                        CaptureLibraryContent(CaptureLibraryUiState(ready = true, records = listOf(row), selectedId = row.id),
                            mapOf("test-project" to "合成项目"), { f.files.verify(it) }, {}, {}, {}, {},
                            { deleted = it }, {}, {})
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("本机拍摄成片预览").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("删除应用内成片").performScrollTo().performClick()
            assertNull(deleted)
            compose.onNodeWithText("确认删除").performClick()
            assertEquals(row.id, deleted)
            dispose()
        }
    }

    @Test fun oldProjectIsUnassignedAndUnknownSaveRequiresAcknowledgement() {
        P25UCaptureFixture().use { f ->
            val row = f.ready().copy(exportState = CaptureExportState.UNKNOWN)
            val state = mutableStateOf(CaptureLibraryUiState(ready = true, records = listOf(row), selectedId = row.id))
            var exported: String? = null
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    PhotoDirectorTheme {
                        CaptureLibraryContent(state.value, emptyMap(), { f.files.verify(it) }, {}, {}, {},
                            { exported = it }, {}, {}, {})
                    }
                }
            }
            compose.onNodeWithText("未归类").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("保存副本").performScrollTo().performClick()
            assertNull(exported)
            compose.onNodeWithText("继续另存为").performClick()
            assertEquals(row.id, exported)
            dispose()
        }
    }

    @Test fun capturePreviewAppliesExifRotationWithoutChangingOriginal() = runBlocking {
        P25UCaptureFixture().use { f ->
            val row = f.engine.begin(null, null)
            val partial = f.files.partial(row.id)
            P25UCaptureFixture.writeJpeg(partial, width = 120, height = 60)
            ExifInterface(partial).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val completed = f.engine.complete(row.id)
            val file = f.files.verify(completed)
            val before = file.readBytes()
            val image = requireNotNull(decodeCaptureImage(file))
            assertEquals(60, image.width)
            assertEquals(120, image.height)
            image.recycle()
            assertArrayEquals(before, file.readBytes())
        }
    }

    private fun dispose() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
    }
}
