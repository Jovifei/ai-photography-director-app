package com.jovi.photoai.data.reference

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerSourceReaderTest {
    @Test
    fun magicDetection_acceptsOnlyKnownImageHeaders() {
        assertEquals(
            ImageMagic.JPEG,
            imageMagicFrom(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), 3),
        )
        assertEquals(
            ImageMagic.PNG,
            imageMagicFrom(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
                8,
            ),
        )
        assertEquals(ImageMagic.EMPTY, imageMagicFrom(byteArrayOf(), 0))
        assertEquals(ImageMagic.OTHER, imageMagicFrom(byteArrayOf(0x47, 0x49, 0x46), 3))
    }
}
