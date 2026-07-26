package com.jovi.photoai.reference

import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.domain.model.GuidanceItem
import com.jovi.photoai.domain.model.GuidancePriority
import com.jovi.photoai.domain.model.OverlayMode
import com.jovi.photoai.domain.model.PhotoAnalysis

fun ReferenceBundle.toReferenceAnalysis(): ReferenceAnalysis = ReferenceAnalysis(
    scene = scene,
    backgroundValue = backgroundStory,
    lighting = lighting,
    composition = composition,
    subjectIntent = subjectIntent,
    emotion = emotion,
    cameraSuggestion = "$cameraPosition。$directorPrompt",
)

fun ReferenceBundle.toDirectorCard(): DirectorCard = DirectorCard(
    environment = "$scene。$backgroundStory",
    subject = "$subjectIntent。$poseTemplate",
    emotion = emotion,
    camera = "$cameraPosition。$directorPrompt",
)

fun ReferenceBundle.toCameraDirectorGuidance(referenceTitle: String): CameraDirectorGuidance =
    CameraDirectorGuidance(
        referenceTitle = referenceTitle,
        sourceLabel = "Demo Analysis · 非实时 Pose",
        centerHint = directorPrompt,
        environment = listOf(
            ReferenceGuidanceItem("场景", scene),
            ReferenceGuidanceItem("背景价值", backgroundStory),
            ReferenceGuidanceItem("光线", lighting),
            ReferenceGuidanceItem("构图", composition),
            ReferenceGuidanceItem("建议机位", cameraPosition),
        ),
        subject = listOf(
            ReferenceGuidanceItem("人物意图", subjectIntent),
            ReferenceGuidanceItem("参考姿态", poseTemplate),
            ReferenceGuidanceItem("情绪", emotion),
        ),
    )

fun DirectorCard.toGuidanceItems(): List<GuidanceItem> = listOf(
    GuidanceItem(
        id = "reference-environment",
        panel = GuidePanel.ENVIRONMENT,
        priority = GuidancePriority.FRAMING,
        title = "环境建议",
        instruction = environment,
    ),
    GuidanceItem(
        id = "reference-subject",
        panel = GuidePanel.SUBJECT,
        priority = GuidancePriority.BODY,
        title = "人物建议",
        instruction = subject,
    ),
    GuidanceItem(
        id = "reference-emotion",
        panel = GuidePanel.SUBJECT,
        priority = GuidancePriority.EMOTION,
        title = "情绪建议",
        instruction = emotion,
    ),
    GuidanceItem(
        id = "reference-camera",
        panel = GuidePanel.ENVIRONMENT,
        priority = GuidancePriority.POSITION,
        title = "拍摄建议",
        instruction = camera,
    ),
)

fun ReferenceBundle.toShootingPlan(referencePhoto: ReferencePhoto): ShootingPlan {
    val analysis = PhotoAnalysis(
        referencePhotoId = referencePhoto.id,
        summary = backgroundStory,
        environment = scene,
        composition = composition,
        lighting = lighting,
        pose = poseTemplate,
        story = emotion,
        colorGrading = "Demo Analysis：本阶段不生成真实调色结论",
        onsitePlan = directorPrompt,
        confidence = 1f,
    )
    return ShootingPlan(
        id = "reference-director-$referenceId",
        title = "Demo 摄影导演卡",
        objective = directorPrompt,
        referencePhotoId = referencePhoto.id,
        analysis = analysis,
        guidance = toDirectorCard().toGuidanceItems(),
        defaultOverlayMode = OverlayMode.REFERENCE,
    )
}
