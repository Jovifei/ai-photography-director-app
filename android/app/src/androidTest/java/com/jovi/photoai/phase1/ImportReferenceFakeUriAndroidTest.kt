package com.jovi.photoai.phase1

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.ReferenceImportErrorCode
import com.jovi.photoai.data.reference.ReferenceImportUiState
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.importphoto.ImportReferenceScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for the previous unsafe fake-Uri continuation: a failed import must not make
 * the Director continuation available. Real media qualification lives in the UI1 diagnostic test.
 */
@RunWith(AndroidJUnit4::class)
class ImportReferenceFakeUriAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedImport_doesNotExposeDemoContinuation() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PhotoDirectorTheme {
                    ImportReferenceScreen(
                        state = ReferenceImportUiState.Failed(ReferenceImportErrorCode.SOURCE_OPEN_FAILED),
                        onPickerResult = {},
                        onPickerCancelled = {},
                        onBack = {},
                        onContinue = {},
                        onDiscardReady = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(IMPORT_FAILURE).assertIsDisplayed()
        composeRule.onAllNodesWithText(START_DEMO_ANALYSIS).assertCountEquals(0)
    }

    private companion object {
        const val IMPORT_FAILURE = "无法导入这张照片"
        const val START_DEMO_ANALYSIS = "开始示例指导"
    }
}
