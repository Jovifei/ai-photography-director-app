package com.jovi.photoai.t4

import android.content.Context
import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Test-owned JPEGs only. Each created project and MediaStore row is removed in finally. */
@RunWith(AndroidJUnit4::class)
class T4AllProjectFlowAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    companion object {
        private lateinit var repository: ReferenceRepository
        private lateinit var media: SyntheticPickerMediaFactory
        private var firstProjectId: String? = null
        private var secondProjectId: String? = null
        private val suffix = UUID.randomUUID().toString().take(8)
        private val firstName = "T4甲$suffix"
        private val secondName = "T4乙$suffix"

        @BeforeClass @JvmStatic fun seedBeforeActivityLaunch() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            repository = ReferenceRepository.create(context)
            media = SyntheticPickerMediaFactory(context)
            runBlocking {
                val first = repository.createProject(firstName)
                firstProjectId = first.id
                val second = repository.createProject(secondName)
                secondProjectId = second.id
                assertTrue(repository.importIntoProject(media.jpeg().uri, first.id) is ReferenceImportResult.Success)
                assertTrue(repository.importIntoProject(media.jpeg().uri, second.id) is ReferenceImportResult.Success)
            }
        }

        @AfterClass @JvmStatic fun cleanOwnedFixture() {
            if (::repository.isInitialized) runBlocking {
                secondProjectId?.let { repository.deleteProject(it) }
                firstProjectId?.let { repository.deleteProject(it) }
            }
            if (::media.isInitialized) media.close()
        }
    }

    @Test fun librarySearchOpensTheActualOwningProject() {
        rule.onNodeWithText("查看全部参考图").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("导入参考图").fetchSemanticsNodes().size >= 2
        }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            rule.onNodeWithTag("reference-library-search").performScrollTo().assertIsDisplayed()
        } finally {
            device.setOrientationNatural()
        }
        rule.onNodeWithTag("reference-library-search").performTextInput(secondName)
        rule.onNodeWithText("打开所属项目").performScrollTo().performClick()
        rule.onNodeWithText("项目看板").assertIsDisplayed()
        rule.onNodeWithText(secondName).assertIsDisplayed()
        rule.onNodeWithText("选择拍摄方式").performScrollTo().performClick()
        rule.onNodeWithText("无指导直接拍摄").performClick()
        rule.onAllNodesWithText("基础拍摄", substring = true).onFirst().assertIsDisplayed()
        rule.onNodeWithText("查看参考图").assertDoesNotExist()
    }
}
