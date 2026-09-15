package com.jovi.photoai.camera

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureExporterTest {
    @Test
    fun defaultFileName_isStableAndDoesNotContainPrivatePaths() {
        assertEquals("photo-director-3.jpg", CaptureExporter.defaultFileName(3))
        assertEquals("photo-director-1.jpg", CaptureExporter.defaultFileName(0))
    }

    @Test
    fun copy_writesCapturedBytes() {
        val source = File.createTempFile("capture-export", ".jpg")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            source.writeBytes(bytes)
            val output = ByteArrayOutputStream()
            assertTrue(CaptureExporter.copy(source, output))
            assertArrayEquals(bytes, output.toByteArray())
        } finally {
            source.delete()
        }
    }

    @Test
    fun copy_missingSource_failsClosed() {
        val output = ByteArrayOutputStream()
        assertFalse(CaptureExporter.copy(File("missing-capture.jpg"), output))
    }

    @Test
    fun copy_outputFailure_returnsFalseAndLeavesSource() {
        val source = File.createTempFile("capture-export", ".jpg")
        try {
            source.writeBytes(byteArrayOf(7, 8))
            val failing = object : OutputStream() {
                override fun write(b: Int) = throw IllegalStateException("synthetic failure")
            }
            assertFalse(CaptureExporter.copy(source, failing))
            assertTrue(source.isFile)
        } finally {
            source.delete()
        }
    }
}
