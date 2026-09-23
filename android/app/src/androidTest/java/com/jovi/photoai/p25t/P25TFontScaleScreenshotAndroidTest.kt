package com.jovi.photoai.p25t

import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.MainActivity
import com.jovi.photoai.reference.DirectorCard
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui.reference.DirectorCardScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Host pulls these synthetic-only screenshots; system font_scale is set outside this test. */
@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class P25TFontScaleScreenshotAndroidTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun directorPreparation_isReachableAtCurrentSystemFontScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scale = Settings.System.getFloat(
            instrumentation.targetContext.contentResolver,
            Settings.System.FONT_SCALE,
        )
        val expectedScale = requireNotNull(
            InstrumentationRegistry.getArguments().getString("expectedFontScale"),
        ) { "EXPECTED_FONT_SCALE_ARGUMENT_REQUIRED" }.toFloat()
        org.junit.Assert.assertEquals("system font_scale", expectedScale, scale, 0.01f)
        val label = if (expectedScale == 2f) "200" else "100"
        var entered = false
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                PhotoDirectorTheme {
                    DirectorCardScreen(
                        card = DirectorCard(
                            environment = "长环境指导：合成光线与构图文字用于字体可达性检查",
                            subject = "人物主体与姿态指导",
                            emotion = "情绪表达指导",
                            camera = "相机机位与拍摄指令指导",
                        ),
                        sourceLabel = "离线知识包",
                        onBack = {},
                        onEnterCameraDirector = { entered = true },
                    )
                }
            }
        }
        rule.onNodeWithText("摄影导演卡").assertIsDisplayed()
        rule.onNodeWithText("拍摄准备 · 0 / 5").performScrollTo().assertIsDisplayed()
        val output = File(
            requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "p25t-font-$label.png",
        )
        output.delete()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(output).use { stream ->
            assertTrue("screenshot must be encoded for font_scale=$scale", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        assertTrue(output.isFile)
        rule.onNodeWithText("进入 Camera Director").performScrollTo().assertIsDisplayed()
        val bottom = rule.onRoot().captureToImage().asAndroidBitmap()
        File(output.parentFile, "p25t-font-$label-bottom.png").outputStream().use {
            assertTrue(bottom.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bottom.recycle()
        rule.onNodeWithText("进入 Camera Director").performClick()
        rule.runOnIdle { assertTrue(entered) }
    }
}
