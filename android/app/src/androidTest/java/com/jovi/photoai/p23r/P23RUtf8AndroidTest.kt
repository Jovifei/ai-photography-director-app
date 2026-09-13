package com.jovi.photoai.p23r

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P23RUtf8AndroidTest {
    @Test fun malformedUtf8_isRejectedBeforeJsonAndDigestChecks() {
        listOf(
            byteArrayOf(0x80.toByte()),
            byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
            byteArrayOf(0xe2.toByte(), 0x82.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()),
        ).forEach { invalid ->
            val document = "{\"scene\":\"".toByteArray() + invalid + "\"}".toByteArray()
            val result = PhotoKnowledgeBundleParser.parse(document) as PhotoKnowledgeBundleParseResult.Failure
            assertEquals(PhotoKnowledgeBundleErrorCode.INVALID_UTF8, result.code)
        }
    }
}
