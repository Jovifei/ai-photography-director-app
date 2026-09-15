package com.jovi.photoai.p23c

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleErrorCode
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParser
import com.jovi.photoai.data.reference.canonicalPayloadSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Python-produced synthetic vector only. No Room write, photo, Provider or network. */
@RunWith(AndroidJUnit4::class)
class P23CPythonBundleCompatibilityAndroidTest {
    @Test
    fun pythonVector_acceptedByActualAndroidParser() {
        val result = PhotoKnowledgeBundleParser.parse(vector())
        assertTrue(result is PhotoKnowledgeBundleParseResult.Success)
        val bundle = (result as PhotoKnowledgeBundleParseResult.Success).bundle
        assertEquals(2, bundle.references.size)
        assertEquals(EXPECTED_DIGEST, bundle.payloadSha256)
        assertEquals(EXPECTED_DIGEST, canonicalPayloadSha256(bundle))
    }

    @Test
    fun unicodeBytes_notNormalizedOrCountedAsUtf16Units() {
        val result = PhotoKnowledgeBundleParser.parse(vector()) as PhotoKnowledgeBundleParseResult.Success
        assertEquals("合成测试 scene 0 📷 e\u0301", result.bundle.references.first().photography.scene)
        assertEquals(EXPECTED_DIGEST, canonicalPayloadSha256(result.bundle))
    }

    @Test
    fun jsonLineEndings_doNotChangePayloadDigest() {
        val crlf = vector().toString(Charsets.UTF_8).replace("\n", "\r\n").toByteArray(Charsets.UTF_8)
        val result = PhotoKnowledgeBundleParser.parse(crlf) as PhotoKnowledgeBundleParseResult.Success
        assertEquals(EXPECTED_DIGEST, result.bundle.payloadSha256)
    }

    @Test
    fun changedGuidance_withOriginalPythonDigest_isRejected() {
        val changed = vector().toString(Charsets.UTF_8)
            .replace("合成测试 scene 0", "被修改 scene 0").toByteArray(Charsets.UTF_8)
        val result = PhotoKnowledgeBundleParser.parse(changed) as PhotoKnowledgeBundleParseResult.Failure
        assertEquals(PhotoKnowledgeBundleErrorCode.DIGEST_MISMATCH, result.code)
    }

    private fun vector(): ByteArray = InstrumentationRegistry.getInstrumentation().context.assets
        .open("p23c/roundtrip.bundle.json").use { it.readBytes() }

    private companion object {
        const val EXPECTED_DIGEST = "889ccdde3f9e9065795f8440c97e8c1364c87864c7f8b519e903ae6573213ca1"
    }
}
