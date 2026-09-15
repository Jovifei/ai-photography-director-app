package com.jovi.photoai.data.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoKnowledgeBundleTest {
    @Test
    fun canonicalDigest_isStableAndCoversEveryPhotographyField() {
        val bundle = fixtureBundle()
        val first = canonicalPayloadSha256(bundle)
        val second = canonicalPayloadSha256(bundle)
        val changed = bundle.copy(
            references = listOf(bundle.references.single().copy(
                photography = bundle.references.single().photography.copy(lighting = "柔和侧光"),
            )),
        )

        assertEquals(first, second)
        assertTrue(first.matches(Regex("^[a-f0-9]{64}$")))
        assertNotEquals(first, canonicalPayloadSha256(changed))
    }

    @Test
    fun bundleTargetEligibility_neverOverwritesReadyOrRunningWork() {
        listOf(
            PhotoAnalysisStatus.IMPORTED,
            PhotoAnalysisStatus.FAILED,
            PhotoAnalysisStatus.CANCELLED,
            PhotoAnalysisStatus.UNAVAILABLE,
            PhotoAnalysisStatus.EXAMPLE_GUIDANCE,
        ).forEach { assertTrue(isKnowledgeBundleTargetEligible(it)) }
        listOf(PhotoAnalysisStatus.READY, PhotoAnalysisStatus.QUEUED, PhotoAnalysisStatus.RUNNING)
            .forEach { assertFalse(isKnowledgeBundleTargetEligible(it)) }
    }

    @Test
    fun provenance_rejectsPathLikeOrInvalidIdentifiers() {
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeBundleProvenance(
                bundleId = "../bundle",
                producerReferenceId = "photo_001",
                producerId = "pipeline",
                origin = KnowledgeBundleOrigin.PIPELINE,
                releaseId = "release_001",
                payloadSha256 = "a".repeat(64),
                importedAtEpochMillis = 1L,
            )
        }
    }

    @Test
    fun bundleResult_replacesProviderProvenanceWithoutFabricatingModelMetadata() {
        val bundle = fixtureBundle().let { it.copy(payloadSha256 = canonicalPayloadSha256(it)) }
        val record = ReferenceRecord(
            photo = com.jovi.photoai.domain.model.ReferencePhoto(
                "local_001", "照片", "测试", "本机 VLM", "private/local_001.jpg", 1f,
            ),
            bundle = com.jovi.photoai.reference.ReferenceBundle(
                "local_001", "旧场景", "旧背景", "旧光线", "旧构图", "旧主体", "旧情绪", "旧姿态", "旧机位", "旧提示", "1.0",
            ),
            imageFileName = "local_001.jpg",
            createdAtEpochMillis = 1L,
            projectId = "project_001",
            analysisStatus = PhotoAnalysisStatus.READY,
            analysisProvenance = ProviderAnalysisProvenance(
                "provider", ProviderType.LOCAL_SERVICE, "model", "revision", "a".repeat(64), "runtime", 2L,
            ),
        )

        val entity = record.toEntity().withKnowledgeBundleResult(bundle.references.single(), bundle, 3L)
        val restored = entity.toRecord()

        assertEquals(PhotoAnalysisStatus.READY.name, entity.analysisStatus)
        assertNull(entity.analysisModelId)
        assertNull(restored.analysisProvenance)
        assertEquals(bundle.bundleId, restored.knowledgeBundleProvenance?.bundleId)
        assertEquals("photo_001", restored.knowledgeBundleProvenance?.producerReferenceId)
        assertEquals("local_001", restored.bundle.referenceId)
    }

    private fun fixtureBundle(): PhotoKnowledgeBundle = PhotoKnowledgeBundle(
        contractVersion = PHOTO_KNOWLEDGE_BUNDLE_VERSION,
        bundleId = "bundle_demo_001",
        source = KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "nightly_pipeline", "release_001"),
        payloadSha256 = "0".repeat(64),
        references = listOf(
            PhotoKnowledgeBundleItem(
                referenceId = "photo_001",
                photography = KnowledgeBundlePhotography(
                    scene = "室内人像",
                    backgroundStory = "安静的居家时刻",
                    lighting = "窗边自然光",
                    composition = "三分构图",
                    subjectIntent = "放松站立",
                    emotion = "平静",
                    poseTemplate = "肩部放松",
                    cameraPosition = "眼平机位",
                    directorPrompt = "靠近窗边，保持自然呼吸。",
                ),
            ),
        ),
    )
}
