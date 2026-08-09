package com.jovi.photoai.ui1

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies Compose action semantics and reachability at host-controlled font scales. */
@RunWith(AndroidJUnit4::class)
class Ui1ResponsiveAccessibilityAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun projectEntry_isLabeledClickable_andReachableInLandscape() {
        val expectedFontScale = InstrumentationRegistry.getArguments().getString("fontScale")?.toFloatOrNull()
        if (expectedFontScale != null) {
            assertEquals(
                expectedFontScale,
                InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale,
                0.05f,
            )
        }

        composeRule.waitUntil(STARTUP_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription("新建拍摄项目", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("新建拍摄项目", useUnmergedTree = true)
            .assertHasClickAction()

        device.setOrientationLeft()
        try {
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("新建拍摄项目", useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
        }
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLIS = 10_000L
    }
}
