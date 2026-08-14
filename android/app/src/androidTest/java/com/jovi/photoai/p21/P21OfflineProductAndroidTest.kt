package com.jovi.photoai.p21

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.ui.analysis.AnalysisDetailScreen
import com.jovi.photoai.ui.capture.CaptureEntryScreen
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic API 35 coverage for P21's no-provider truthfulness boundary. */
@RunWith(AndroidJUnit4::class)
class P21OfflineProductAndroidTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun unreadyPrimaryCaptureEntry_isExplicitlyManual() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                PhotoDirectorTheme {
                    CaptureEntryScreen(
                        referenceCount = 1,
                        projectTitle = "合成项目",
                        offlineNotice = "该参考尚未完成真实分析，本次拍摄不会使用 AI 指导。",
                        onOpenInspiration = {},
                        onChooseReference = {},
                        onDirectCapture = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("无 AI 指导拍摄").assertIsDisplayed()
        composeRule.onNodeWithText("该参考尚未完成真实分析，本次拍摄不会使用 AI 指导。").assertIsDisplayed()
        composeRule.onNodeWithText("无指导直接拍摄").assertIsDisplayed()
    }

    @Test
    fun exampleGuidance_neverExposesDirectorCardAction() {
        setAnalysisDetail(PhotoAnalysisStatus.EXAMPLE_GUIDANCE)

        composeRule.onAllNodesWithText("查看摄影导演卡").assertCountEquals(0)
        composeRule.onNodeWithText("也不能进入 AI Camera Director", substring = true).assertIsDisplayed()
    }

    @Test
    fun readyFixture_retainsDirectorCardAction() {
        setAnalysisDetail(PhotoAnalysisStatus.READY)

        composeRule.onNodeWithText("查看摄影导演卡").performScrollTo().assertIsDisplayed()
    }

    private fun setAnalysisDetail(status: PhotoAnalysisStatus) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                PhotoDirectorTheme {
                    AnalysisDetailScreen(
                        imageFileName = null,
                        bundle = DemoReferenceAnalyzer.analyze("p21_fixture", "synthetic-fixture"),
                        sourceLabel = "合成夹具",
                        analysisStatus = status,
                        onBack = {},
                        onOpenDirectorCard = {},
                    )
                }
            }
        }
    }
}
