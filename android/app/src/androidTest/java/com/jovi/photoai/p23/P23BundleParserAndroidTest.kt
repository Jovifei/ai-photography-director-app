package com.jovi.photoai.p23

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic text only. These tests require Android's actual org.json implementation. */
@RunWith(AndroidJUnit4::class)
class P23BundleParserAndroidTest {
    @Test fun validV1_keepsCanonicalDigestAndUnicode() {
        val result = PhotoKnowledgeBundleParser.parse((" \r\n" + document() + "\t").toByteArray())
        assertTrue(result is PhotoKnowledgeBundleParseResult.Success)
        val bundle = (result as PhotoKnowledgeBundleParseResult.Success).bundle
        assertEquals("窗边📷", bundle.references.single().photography.scene)
        assertEquals(canonicalPayloadSha256(bundle), bundle.payloadSha256)
    }

    @Test fun androidLenientSyntax_isRejectedBeforeDecoding() {
        val valid = document()
        listOf(
            valid + " {}", valid + "garbage", valid.replace('"', '\''),
            valid.replaceFirst("{", "{/*comment*/"),
            valid.replaceFirst("\"contract_version\"", "contract_version"),
            valid.replaceFirst(":", "="), valid.replaceFirst(",", ";"),
            valid.dropLast(1) + ",}",
        ).forEach { assertFailure(it, PhotoKnowledgeBundleErrorCode.MALFORMED_JSON) }
    }

    @Test fun escapedDuplicateKey_isRejected() {
        val duplicate = document().replaceFirst(
            "\"contract_version\":\"1.0\"",
            "\"contract_version\":\"1.0\",\"contract_\\u0076ersion\":\"1.0\"",
        )
        assertFailure(duplicate, PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
    }

    @Test fun malformedUnicodeAndDeepNesting_areRejected() {
        assertFailure(document().replace("窗边📷", "\\ud800"), PhotoKnowledgeBundleErrorCode.MALFORMED_JSON)
        assertFailure("{\"a\":" + "[".repeat(10000) + "0" + "]".repeat(10000) + "}", PhotoKnowledgeBundleErrorCode.MALFORMED_JSON)
    }

    @Test fun digestAndUnknownFieldChecks_stillApplyAfterSyntaxGate() {
        assertFailure(document().replace("窗边📷", "修改后的场景"), PhotoKnowledgeBundleErrorCode.DIGEST_MISMATCH)
        assertFailure(document().replaceFirst("{", "{\"unknown\":true,"), PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID)
    }

    @Test fun boundedByteAndUtf8Rules_areUnchanged() {
        val tooLarge = ByteArray(MAX_KNOWLEDGE_BUNDLE_BYTES + 1) { 32 }
        assertEquals(PhotoKnowledgeBundleErrorCode.DOCUMENT_TOO_LARGE, (PhotoKnowledgeBundleParser.parse(tooLarge) as PhotoKnowledgeBundleParseResult.Failure).code)
        val bom = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + document().toByteArray()
        assertEquals(PhotoKnowledgeBundleErrorCode.INVALID_UTF8, (PhotoKnowledgeBundleParser.parse(bom) as PhotoKnowledgeBundleParseResult.Failure).code)
    }

    private fun assertFailure(text: String, code: PhotoKnowledgeBundleErrorCode) {
        assertEquals(code, (PhotoKnowledgeBundleParser.parse(text.toByteArray()) as PhotoKnowledgeBundleParseResult.Failure).code)
    }

    private fun document(): String {
        val fixture = PhotoKnowledgeBundle(
            "1.0", "synthetic_p23", KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "test_producer", "test_release"),
            "0".repeat(64), listOf(PhotoKnowledgeBundleItem("test_reference", KnowledgeBundlePhotography(
                "窗边📷", "合成背景", "侧光", "三分构图", "自然站立", "平静", "肩部放松", "眼平", "保持自然呼吸。",
            ))),
        )
        val digest = canonicalPayloadSha256(fixture)
        return """{"contract_version":"1.0","bundle_id":"synthetic_p23","source":{"origin":"PIPELINE","producer_id":"test_producer","release_id":"test_release"},"integrity":{"algorithm":"SHA-256","payload_sha256":"$digest"},"references":[{"reference_id":"test_reference","photography":{"scene":"窗边📷","background_story":"合成背景","lighting":"侧光","composition":"三分构图","subject_intent":"自然站立","emotion":"平静","pose_template":"肩部放松","camera_position":"眼平","director_prompt":"保持自然呼吸。"}}]}"""
    }
}
