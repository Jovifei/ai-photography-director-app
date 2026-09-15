package com.jovi.photoai.phase1

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.jovi.photoai.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Current P20 product flow; this deliberately contains no Demo-first assertions. */
@RunWith(AndroidJUnit4::class)
class ReferenceDirectorFlowAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun currentProjectFlow_reachesBoardAndDirectCapture() {
        clickAction("新建拍摄项目")
        composeRule.onNodeWithText("添加项目照片").assertIsDisplayed()
        composeRule.onNodeWithText("选择照片").assertIsDisplayed()
        composeRule.onNodeWithText("稍后添加").performClick()
        composeRule.onNodeWithText("项目看板").assertIsDisplayed()
        composeRule.onNodeWithText("项目还没有照片").assertIsDisplayed()

        composeRule.onNodeWithText("选择拍摄方式").performClick()
        composeRule.onNodeWithText("开始拍摄").assertIsDisplayed()
        composeRule.onNodeWithText("无指导直接拍摄").performClick()
        composeRule.onAllNodesWithText("基础拍摄", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("保存照片").assertIsDisplayed()
    }

    @Test
    fun currentPickerContract_isImageOnlyAndUsesSystemSelection() {
        val pickerIntent = PickVisualMedia().createIntent(
            composeRule.activity,
            PickVisualMediaRequest(PickVisualMedia.ImageOnly),
        )
        assertEquals("image/*", pickerIntent.type)
        assertTrue(
            "Expected the image-only system picker contract or its AndroidX fallback.",
            pickerIntent.action == MediaStore.ACTION_PICK_IMAGES ||
                pickerIntent.action == Intent.ACTION_OPEN_DOCUMENT ||
                pickerIntent.action.orEmpty().contains("PICK_IMAGES"),
        )
    }

    private fun clickAction(description: String) {
        composeRule.waitUntil(STARTUP_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLIS = 10_000L
    }
}
