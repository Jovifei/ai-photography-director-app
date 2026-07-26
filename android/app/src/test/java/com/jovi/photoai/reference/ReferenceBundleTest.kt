package com.jovi.photoai.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceBundleTest {
    private val bundle = ReferenceBundle(
        referenceId = "reference-1",
        scene = "城市夜景玻璃幕墙",
        backgroundStory = "利用反射形成未来感和孤独氛围",
        lighting = "侧后方冷色光",
        composition = "三分法，人物位于右侧",
        subjectIntent = "身体微侧，肩膀放松",
        emotion = "安静等待",
        poseTemplate = "文字参考姿态意图",
        cameraPosition = "略低机位，45 度侧向取景",
        directorPrompt = "保留左侧留白。",
        version = ReferenceBundle.CURRENT_VERSION,
    )

    @Test
    fun schemaFields_matchThePhase1ReferenceBundleContract() {
        assertEquals(
            setOf(
                "reference_id",
                "scene",
                "background_story",
                "lighting",
                "composition",
                "subject_intent",
                "emotion",
                "pose_template",
                "camera_position",
                "director_prompt",
                "version",
            ),
            ReferenceBundle.requiredSchemaFields,
        )
    }

    @Test
    fun bundleAndAnalysis_haveAllRequiredNonBlankFields() {
        val analysis = bundle.toReferenceAnalysis()

        assertTrue(
            listOf(
                analysis.scene,
                analysis.backgroundValue,
                analysis.lighting,
                analysis.composition,
                analysis.subjectIntent,
                analysis.emotion,
                analysis.cameraSuggestion,
            ).all(String::isNotBlank),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankRequiredField_isRejected() {
        bundle.copy(directorPrompt = "")
    }
}
