package com.jovi.photoai.t17

import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.KnowledgeBundlePhotography
import com.jovi.photoai.data.reference.KnowledgeBundleSource
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.canonicalPayloadSha256

/** Image-free synthetic A/B guidance. Real repository entry points verify the official PKB1 digest. */
internal object T17KnowledgeBundles {
    const val PRODUCER_REFERENCE_ID = "t17_reference"

    fun create(runId: String, release: String): PhotoKnowledgeBundle {
        require(runId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")))
        require(release == "A" || release == "B")
        val raw = PhotoKnowledgeBundle(
            contractVersion = "1.0",
            bundleId = "t17_bundle_$runId",
            source = KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic_t17",
                if (release == "A") "t17_release_a" else "t17_release_b"),
            payloadSha256 = "0".repeat(64),
            references = listOf(PhotoKnowledgeBundleItem(PRODUCER_REFERENCE_ID,
                KnowledgeBundlePhotography(
                    scene = "合成室内场景",
                    backgroundStory = "合成背景层次",
                    lighting = "观察现场侧光",
                    composition = composition(release),
                    subjectIntent = "合成人物站立意图",
                    emotion = "平静",
                    poseTemplate = "肩部自然放松",
                    cameraPosition = "眼平机位",
                    directorPrompt = directorPrompt(release),
                ))),
        )
        return raw.copy(payloadSha256 = canonicalPayloadSha256(raw))
    }

    fun composition(release: String): String = when (release) {
        "A" -> "T17 A 构图：人物靠左，右侧留白"
        "B" -> "T17 B 构图：人物靠右，左侧留白"
        else -> error("Expected synthetic release A or B")
    }

    fun directorPrompt(release: String): String = when (release) {
        "A" -> "T17 A 指导：观察左侧人物与右侧背景"
        "B" -> "T17 B 指导：观察右侧人物与左侧背景"
        else -> error("Expected synthetic release A or B")
    }
}
