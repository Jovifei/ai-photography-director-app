package com.jovi.photoai.p25t

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.reference.ReferenceBundle
import com.jovi.photoai.reference.toDirectorCard
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.reference.DIRECTOR_REFERENCE_NOTICE
import com.jovi.photoai.ui.reference.DirectorCardScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose screens; state restoration is not claimed as OS force-stop qualification. */
@RunWith(AndroidJUnit4::class)
class P25TDirectorPreparationAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val card = DirectorCard("合成环境", "合成人物", "合成情绪", "合成机位")

    @Test fun checklistIsOptionalAndReferenceNoticeIsHonest() {
        var entered = 0
        rule.setContent { PhotoDirectorTheme { DirectorCardScreen(card, "本机 VLM", {}, { entered++ }) } }
        rule.onNodeWithText(DIRECTOR_REFERENCE_NOTICE).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("拍摄准备 · 0 / 5").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("进入 Camera Director").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, entered) }
        rule.onNodeWithTag("director-step-LOCATION").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithText("拍摄准备 · 1 / 5").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("重置准备清单").performScrollTo().performClick()
        rule.onNodeWithText("拍摄准备 · 0 / 5").assertIsDisplayed()
    }

    @Test fun savedStateRestoresButChangedGuidanceResetsChecklist() {
        val content = mutableStateOf(card)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { PhotoDirectorTheme { DirectorCardScreen(content.value, "离线知识包", {}, {}) } }
        rule.onNodeWithTag("director-step-ENVIRONMENT").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("director-step-ENVIRONMENT").performScrollTo().assertIsOn()
        rule.runOnIdle { content.value = card.copy(environment = "新指导") }
        rule.onNodeWithText("拍摄准备 · 0 / 5").performScrollTo().assertIsDisplayed()
    }

    @Test fun actualMapperKeepsLightingAndCompositionInDirectorCard() {
        val bundle = ReferenceBundle("synthetic", "场景", "背景", "光线哨兵", "构图哨兵",
            "主体", "情绪", "姿态", "机位", "指令", "1.0")
        val result = bundle.toDirectorCard()
        assertTrue(result.environment.contains("光线哨兵"))
        assertTrue(result.environment.contains("构图哨兵"))
    }
}
