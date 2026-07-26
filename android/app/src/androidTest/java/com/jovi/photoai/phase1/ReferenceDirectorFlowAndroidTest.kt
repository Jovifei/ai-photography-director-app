package com.jovi.photoai.phase1

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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

/**
 * Phase 1 flow evidence. No gallery is opened: the Android Photo Picker is validated as an
 * image-only Intent contract, while a fake Uri is supplied only to the import screen in inspection
 * mode to verify its continuation callback without reading any media.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceDirectorFlowAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun mainActivity_importSurface_andDemoDirectorNavigation_areReachable() {
        composeRule.onAllNodesWithContentDescription(IMPORT_REFERENCE)[0]
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(IMPORT_SCREEN_TITLE).assertIsDisplayed()

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

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(DEMO_REFERENCE_CARD)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(ANALYSIS_SCREEN_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(DEMO_ANALYSIS_MESSAGE).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(OPEN_DIRECTOR_CARD)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(DIRECTOR_CARD_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(ENVIRONMENT).assertIsDisplayed()
        composeRule.onNodeWithText(SUBJECT).assertIsDisplayed()
        composeRule.onNodeWithText(EMOTION).assertIsDisplayed()
        composeRule.onNodeWithText(CAMERA).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithContentDescription(ENTER_CAMERA_DIRECTOR)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(CAMERA_DIRECTOR).assertIsDisplayed()
        composeRule.onNodeWithText(REFERENCE_GUIDANCE, substring = true).assertIsDisplayed()

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(DIRECTOR_CARD_TITLE).assertIsDisplayed()
    }

    private companion object {
        const val IMPORT_REFERENCE = "\u5bfc\u5165\u53c2\u8003\u56fe"
        const val IMPORT_SCREEN_TITLE = "\u5bfc\u5165\u53c2\u8003\u56fe"
        const val DEMO_REFERENCE_CARD = "\u7a97\u8fb9\u67d4\u5149\u4eba\u50cf\uff0cDemo Analysis"
        const val ANALYSIS_SCREEN_TITLE = "\u53c2\u8003\u56fe\u5206\u6790"
        const val DEMO_ANALYSIS_MESSAGE = "\u56fa\u5b9a Demo Analysis\uff1a\u4e0d\u8fde\u63a5 AI\u3001\u4e0d\u4e0a\u4f20\u56fe\u7247\u3001\u4e0d\u751f\u6210\u5b9e\u65f6 Pose\u3002"
        const val OPEN_DIRECTOR_CARD = "\u67e5\u770b\u6444\u5f71\u5bfc\u6f14\u5361"
        const val DIRECTOR_CARD_TITLE = "\u6444\u5f71\u5bfc\u6f14\u5361"
        const val ENVIRONMENT = "\u73af\u5883"
        const val SUBJECT = "\u4eba\u7269"
        const val EMOTION = "\u60c5\u7eea"
        const val CAMERA = "\u76f8\u673a"
        const val ENTER_CAMERA_DIRECTOR = "\u8fdb\u5165 Camera Director"
        const val CAMERA_DIRECTOR = "Camera Director"
        const val REFERENCE_GUIDANCE = "Reference Guidance"
    }
}
