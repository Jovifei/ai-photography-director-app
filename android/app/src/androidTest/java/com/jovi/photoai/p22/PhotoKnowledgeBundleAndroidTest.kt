package com.jovi.photoai.p22

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.KnowledgeBundlePhotography
import com.jovi.photoai.data.reference.KnowledgeBundleSource
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleDocumentReader
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleErrorCode
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParseResult
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleParser
import com.jovi.photoai.data.reference.canonicalPayloadSha256
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoKnowledgeBundleAndroidTest {
    @Test
    fun contentDocument_isReadOnceAndIntegrityChecked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bundle = signedFixture()
        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(directory, "synthetic-knowledge.json")
        file.writeText(bundleJson(bundle).toString(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val result = PhotoKnowledgeBundleDocumentReader(context.contentResolver).read(uri)

        assertTrue(result is PhotoKnowledgeBundleParseResult.Success)
        assertEquals(bundle.payloadSha256, (result as PhotoKnowledgeBundleParseResult.Success).bundle.payloadSha256)
        file.delete()
    }

    @Test
    fun tamperedPayload_failsClosedWithoutRepair() {
        val bundle = signedFixture()
        val tampered = bundleJson(bundle)
        tampered.getJSONArray("references").getJSONObject(0).getJSONObject("photography").put("lighting", "已被修改")

        val result = PhotoKnowledgeBundleParser.parse(tampered.toString().toByteArray(Charsets.UTF_8))

        assertEquals(
            PhotoKnowledgeBundleErrorCode.DIGEST_MISMATCH,
            (result as PhotoKnowledgeBundleParseResult.Failure).code,
        )
    }

    @Test
    fun unknownOrTransportFields_areRejected() {
        val bundle = signedFixture()
        val unknown = bundleJson(bundle).put("image_uri", "content://private")
        assertEquals(
            PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID,
            (PhotoKnowledgeBundleParser.parse(unknown.toString().toByteArray()) as PhotoKnowledgeBundleParseResult.Failure).code,
        )

        val unsafe = bundleJson(bundle)
        unsafe.getJSONArray("references").getJSONObject(0).getJSONObject("photography").put("scene", "content://private")
        assertEquals(
            PhotoKnowledgeBundleErrorCode.FIELD_INVALID,
            (PhotoKnowledgeBundleParser.parse(unsafe.toString().toByteArray()) as PhotoKnowledgeBundleParseResult.Failure).code,
        )
    }

    @Test
    fun duplicateJsonObjectKey_isRejectedBeforeJSONObjectCanOverwriteIt() {
        val valid = bundleJson(signedFixture()).toString()
        val duplicate = valid.replaceFirst(
            "\"contract_version\":\"1.0\"",
            "\"contract_version\":\"1.0\",\"contract_version\":\"1.0\"",
        )

        val result = PhotoKnowledgeBundleParser.parse(duplicate.toByteArray(Charsets.UTF_8))

        assertEquals(
            PhotoKnowledgeBundleErrorCode.SCHEMA_INVALID,
            (result as PhotoKnowledgeBundleParseResult.Failure).code,
        )
    }

    private fun signedFixture(): PhotoKnowledgeBundle {
        val unsigned = PhotoKnowledgeBundle(
            contractVersion = "1.0",
            bundleId = "bundle_android_001",
            source = KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic_pipeline", "release_001"),
            payloadSha256 = "0".repeat(64),
            references = listOf(
                PhotoKnowledgeBundleItem(
                    "photo_001",
                    KnowledgeBundlePhotography(
                        "室内人像",
                        "合成背景故事",
                        "窗边自然光",
                        "三分构图",
                        "自然站立",
                        "平静",
                        "肩部放松",
                        "眼平机位",
                        "保持自然呼吸。",
                    ),
                ),
            ),
        )
        return unsigned.copy(payloadSha256 = canonicalPayloadSha256(unsigned))
    }

    private fun bundleJson(bundle: PhotoKnowledgeBundle): JSONObject = JSONObject()
        .put("contract_version", bundle.contractVersion)
        .put("bundle_id", bundle.bundleId)
        .put(
            "source",
            JSONObject()
                .put("origin", bundle.source.origin.name)
                .put("producer_id", bundle.source.producerId)
                .put("release_id", bundle.source.releaseId),
        )
        .put(
            "integrity",
            JSONObject()
                .put("algorithm", "SHA-256")
                .put("payload_sha256", bundle.payloadSha256),
        )
        .put(
            "references",
            JSONArray(bundle.references.map { item ->
                JSONObject()
                    .put("reference_id", item.referenceId)
                    .put(
                        "photography",
                        JSONObject()
                            .put("scene", item.photography.scene)
                            .put("background_story", item.photography.backgroundStory)
                            .put("lighting", item.photography.lighting)
                            .put("composition", item.photography.composition)
                            .put("subject_intent", item.photography.subjectIntent)
                            .put("emotion", item.photography.emotion)
                            .put("pose_template", item.photography.poseTemplate)
                            .put("camera_position", item.photography.cameraPosition)
                            .put("director_prompt", item.photography.directorPrompt),
                    )
            }),
        )
}
