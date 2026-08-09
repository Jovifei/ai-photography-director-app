package com.jovi.photoai.ui1

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.reference.ReferenceLibraryEntry
import com.jovi.photoai.ui.reference.ReferenceLibraryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceLibraryConfirmationAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deleteAndClear_requireExplicitConfirmation() {
        var deletedReferenceId: String? = null
        var clearCount = 0
        composeRule.setContent {
            PhotoDirectorTheme {
                ReferenceLibraryScreen(
                    entries = listOf(entry()),
                    onBack = {},
                    onImportReference = {},
                    onOpenReference = {},
                    onDeleteReference = { deletedReferenceId = it },
                    onClearAll = { clearCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("删除此参考图").performClick()
        composeRule.onNodeWithText("删除此参考图？").assertIsDisplayed()
        assertEquals(null, deletedReferenceId)
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(null, deletedReferenceId)
        composeRule.onNodeWithText("删除此参考图").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        assertEquals("opaque-reference-id", deletedReferenceId)

        composeRule.onNodeWithText("清空全部").performClick()
        composeRule.onNodeWithText("清空全部参考图？").assertIsDisplayed()
        assertEquals(0, clearCount)
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(0, clearCount)
        composeRule.onNodeWithText("清空全部").performClick()
        composeRule.onNodeWithText("确认清空").performClick()
        assertEquals(1, clearCount)
    }

    private fun entry() = ReferenceLibraryEntry(
        photo = ReferencePhoto(
            id = "opaque-reference-id",
            title = "测试参考图",
            description = "test",
            sourceLabel = "示例指导 · 非图片分析",
            imageAssetKey = "private/missing.jpg",
            aspectRatio = 1f,
        ),
        bundle = ReferenceBundle(
            referenceId = "opaque-reference-id",
            scene = "scene",
            backgroundStory = "story",
            lighting = "lighting",
            composition = "composition",
            subjectIntent = "subject",
            emotion = "emotion",
            poseTemplate = "pose",
            cameraPosition = "camera",
            directorPrompt = "prompt",
            version = "1.0",
        ),
        imageFileName = "missing.jpg",
    )
}
