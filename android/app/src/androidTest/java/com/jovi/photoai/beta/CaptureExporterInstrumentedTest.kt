package com.jovi.photoai.beta

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.camera.CaptureExporter
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureExporterInstrumentedTest {
    @Test
    fun failingDocumentProvider_keepsCacheAndReturnsFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File.createTempFile("beta-capture", ".jpg", context.cacheDir)
        try {
            source.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            val destination = Uri.parse("content://com.jovi.photoai.test.failing/document")

            assertFalse(CaptureExporter.copyTo(context.contentResolver, source, destination))
            assertTrue(source.isFile)
        } finally {
            source.delete()
        }
    }
}
