package com.jovi.photoai.phase1

import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fake Uri continuation check; inspection mode avoids every media decode and gallery read. */
@RunWith(AndroidJUnit4::class)
class ImportReferenceFakeUriAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fakeUri_enablesDemoAnalysisContinuation_withoutMediaRead() {
        val fakeUri = Uri.parse("content://android.test.fake/reference")
        var continuedWith: Uri? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PhotoDirectorTheme {
                    ImportReferenceScreen(
                        selectedUri = fakeUri,
                        onSelected = {},
                        onBack = {},
                        onContinue = { continuedWith = fakeUri },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(START_DEMO_ANALYSIS)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(fakeUri, continuedWith)
        }
    }

    private companion object {
        const val START_DEMO_ANALYSIS = "\u5f00\u59cb\u5206\u6790\uff08\u793a\u4f8b\uff09"
    }
}
