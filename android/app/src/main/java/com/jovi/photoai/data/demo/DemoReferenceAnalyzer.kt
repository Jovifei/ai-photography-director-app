package com.jovi.photoai.data.demo

import com.jovi.photoai.reference.ReferenceBundle

/**
 * Deliberately fixed local content used to validate the product flow.
 *
 * The image Uri is only validated as a short-lived picker input. It is never decoded here,
 * uploaded, logged, persisted, or used to claim that an AI model analysed the image.
 */
object DemoReferenceAnalyzer {
    const val SOURCE_LABEL = "Demo Analysis"

    fun analyze(referenceId: String, imageUri: String): ReferenceBundle {
        require(referenceId.isNotBlank()) { "Reference id must not be blank" }
        require(imageUri.isNotBlank()) { "Image Uri must not be blank" }

        return ReferenceBundle(
            referenceId = referenceId,
            scene = "城市夜景玻璃幕墙",
            backgroundStory = "利用反射形成未来感和孤独氛围",
            lighting = "侧后方冷色光",
            composition = "三分法，人物位于右侧",
            subjectIntent = "身体微侧，肩膀放松",
            emotion = "安静等待",
            poseTemplate = "文字参考：身体微侧，视线越过镜头，不进行实时骨架检测",
            cameraPosition = "略低机位，45 度侧向取景",
            directorPrompt = "先确认玻璃反射与人物错开，再把人物放在右侧三分线并保留左侧留白。",
            version = ReferenceBundle.CURRENT_VERSION,
        )
    }
}
