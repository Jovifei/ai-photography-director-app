package com.jovi.photoai.data.demo

import com.jovi.photoai.domain.model.GuidePanel
import com.jovi.photoai.domain.model.GuidanceItem
import com.jovi.photoai.domain.model.GuidancePriority
import com.jovi.photoai.domain.model.OverlayMode
import com.jovi.photoai.domain.model.PhotoAnalysis
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.domain.model.ShootingPlan

/**
 * Deterministic, private-media-free content for previews, demos and offline UI development.
 */
object DemoContentRepository {
    val referencePhotos: List<ReferencePhoto> = listOf(
        ReferencePhoto(
            id = "window-portrait",
            title = "窗边柔光人像",
            description = "利用侧窗光和留白，突出安静自然的站姿。",
            sourceLabel = "内置示例",
            imageAssetKey = "demo/window-portrait",
            aspectRatio = 4f / 5f,
        ),
        ReferencePhoto(
            id = "city-leading-lines",
            title = "城市引导线",
            description = "低机位利用建筑线条，将视线引向主体。",
            sourceLabel = "内置示例",
            imageAssetKey = "demo/city-leading-lines",
            aspectRatio = 3f / 4f,
        ),
        ReferencePhoto(
            id = "golden-hour-profile",
            title = "金色时刻侧影",
            description = "逆光轮廓搭配暖色环境，保留面部层次。",
            sourceLabel = "内置示例",
            imageAssetKey = "demo/golden-hour-profile",
            aspectRatio = 4f / 5f,
        ),
    )

    val analyses: List<PhotoAnalysis> = listOf(
        PhotoAnalysis(
            referencePhotoId = "window-portrait",
            summary = "主体位于右侧三分线，左侧窗光形成柔和明暗过渡。",
            environment = "室内窗边，背景简洁、安静、有生活感。",
            composition = "竖幅半身构图，视线前方保留空间，机位略低于眼睛。",
            lighting = "左前方大面积柔光，右侧用环境反射保留暗部细节。",
            pose = "肩线微转，重心落在后脚，下巴轻收并看向窗外。",
            story = "等待、思念、发呆与独处。",
            colorGrading = "低饱和、轻微冷调、自然肤色。",
            onsitePlan = "靠近窗边但不贴墙，平视略低，人物偏右并在左侧留白。",
            confidence = 0.94f,
        ),
        PhotoAnalysis(
            referencePhotoId = "city-leading-lines",
            summary = "道路和立面线条在人物位置汇聚，形成明确视觉方向。",
            environment = "城市街道与建筑立面，线条关系清楚。",
            composition = "低机位广角竖幅，主体居中偏下，建筑顶部保留呼吸空间。",
            lighting = "利用阴天漫射光控制反差，避免天空和人物亮度断层。",
            pose = "双脚前后错开，身体正对镜头，手臂保持自然间隙。",
            story = "行走、抵达与城市探索。",
            colorGrading = "中性低饱和，保留建筑材质。",
            onsitePlan = "先确认引导线汇聚点，再让人物站到交点附近。",
            confidence = 0.91f,
        ),
        PhotoAnalysis(
            referencePhotoId = "golden-hour-profile",
            summary = "暖色逆光勾勒轮廓，人物侧脸与远景亮部错开。",
            environment = "日落前后的开阔户外，背景明暗层次柔和。",
            composition = "侧脸落在左三分线，视线方向保留大面积天空。",
            lighting = "太阳置于肩后，用较暗曝光保护高光并保留轮廓。",
            pose = "身体与镜头约四十五度，颈部伸展，肩膀自然下沉。",
            story = "告别、期待与短暂停留。",
            colorGrading = "暖色高光配合自然肤色，暗部保持中性。",
            onsitePlan = "把太阳放在肩后，先保护高光，再调整侧脸方向。",
            confidence = 0.89f,
        ),
    )

    val defaultShootingPlan: ShootingPlan = ShootingPlan(
        id = "window-portrait-plan",
        title = "复刻窗边柔光",
        objective = "先稳定构图和姿态，再微调人物朝向与光比。",
        referencePhotoId = "window-portrait",
        analysis = analyses.first { it.referencePhotoId == "window-portrait" },
        guidance = listOf(
            GuidanceItem(
                id = "check-footing",
                panel = GuidePanel.ENVIRONMENT,
                priority = GuidancePriority.SAFETY,
                title = "确认人物脚下安全",
                instruction = "移动前检查地面与身后空间，确保没有台阶或障碍物。",
            ),
            GuidanceItem(
                id = "move-right",
                panel = GuidePanel.ENVIRONMENT,
                priority = GuidancePriority.FRAMING,
                title = "向右移动半步",
                instruction = "让右眼落在右侧三分线，并保留左侧窗户空间。",
            ),
            GuidanceItem(
                id = "lower-shoulder",
                panel = GuidePanel.SUBJECT,
                priority = GuidancePriority.BODY,
                title = "放松近镜头侧肩膀",
                instruction = "肩膀下沉并微微后转，让颈部线条更清晰。",
            ),
            GuidanceItem(
                id = "turn-to-window",
                panel = GuidePanel.SUBJECT,
                priority = GuidancePriority.HEAD,
                title = "脸再朝窗户一点",
                instruction = "转动约十度，让远侧眼睛仍保留眼神光。",
            ),
            GuidanceItem(
                id = "soft-expression",
                panel = GuidePanel.SUBJECT,
                priority = GuidancePriority.EMOTION,
                title = "放松眼神",
                instruction = "保持呼吸，视线越过窗框，让表情自然停留。",
            ),
        ),
        defaultOverlayMode = OverlayMode.REFERENCE,
    )

    val featuredReferencePhoto: ReferencePhoto
        get() = referencePhotos.first { it.id == defaultShootingPlan.referencePhotoId }

    fun referencePhoto(id: String): ReferencePhoto? = referencePhotos.firstOrNull { it.id == id }

    fun analysisFor(referencePhotoId: String): PhotoAnalysis? =
        analyses.firstOrNull { it.referencePhotoId == referencePhotoId }
}
