package com.jovi.photoai.t4

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.ReferenceRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class T4RootNavigationAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val suffix = UUID.randomUUID().toString().take(8)
    private val initialName = "T4 合成项目 $suffix"
    private val renamedName = "T4 已改名项目 $suffix"

    @After fun removeOnlyThisTestProject() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ReferenceRepository.create(context)
        repository.projects.first().filter { it.title == initialName || it.title == renamedName }
            .forEach { repository.deleteProject(it.id) }
        assertTrue(repository.projects.first().none { it.title == initialName || it.title == renamedName })
    }

    @Test fun homeLibraryAndProjectNamingAreReachable() {
        rule.onNodeWithText("查看全部参考图").performClick()
        rule.onNodeWithText("参考图库").assertIsDisplayed()
        rule.onNodeWithText("返回").performClick()

        rule.onAllNodesWithText("新建拍摄项目").onFirst().performClick()
        rule.onNodeWithTag("project-name-input").performTextInput(initialName)
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("稍后添加").performClick()
        rule.onNodeWithText("项目看板").assertIsDisplayed()
        rule.onNodeWithText("修改项目名称").performClick()
        rule.onNodeWithTag("project-name-input").performTextClearance()
        rule.onNodeWithTag("project-name-input").performTextInput(renamedName)
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText(renamedName).assertIsDisplayed()
    }
}
