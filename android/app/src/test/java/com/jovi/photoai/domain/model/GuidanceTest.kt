package com.jovi.photoai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class GuidanceTest {
    private val emotionEnvironment = guidance(
        id = "emotion-environment",
        panel = GuidePanel.ENVIRONMENT,
        priority = GuidancePriority.EMOTION,
    )
    private val bodySubject = guidance(
        id = "body-subject",
        panel = GuidePanel.SUBJECT,
        priority = GuidancePriority.BODY,
    )
    private val safetyEnvironment = guidance(
        id = "safety-environment",
        panel = GuidePanel.ENVIRONMENT,
        priority = GuidancePriority.SAFETY,
    )

    @Test
    fun highestPriorityGuidance_selectsMostUrgentItem() {
        val selected = selectHighestPriorityGuidance(
            listOf(emotionEnvironment, bodySubject, safetyEnvironment)
        )

        assertSame(safetyEnvironment, selected)
    }

    @Test
    fun highestPriorityGuidance_filtersByPanelBeforeRanking() {
        val selected = selectHighestPriorityGuidance(
            items = listOf(emotionEnvironment, safetyEnvironment, bodySubject),
            panel = GuidePanel.SUBJECT,
        )

        assertSame(bodySubject, selected)
    }

    @Test
    fun highestPriorityGuidance_preservesInputOrderForTies() {
        val first = guidance("first", GuidePanel.SUBJECT, GuidancePriority.HAND)
        val second = guidance("second", GuidePanel.SUBJECT, GuidancePriority.HAND)

        assertSame(first, selectHighestPriorityGuidance(listOf(first, second)))
    }

    @Test
    fun highestPriorityGuidance_returnsNullForEmptyOrUnmatchedItems() {
        assertNull(selectHighestPriorityGuidance(emptyList()))
        assertNull(selectHighestPriorityGuidance(listOf(bodySubject), GuidePanel.NONE))
    }

    @Test
    fun guidancePriority_declaresExactHighToLowOrder() {
        assertEquals(
            listOf(
                GuidancePriority.SAFETY,
                GuidancePriority.FRAMING,
                GuidancePriority.POSITION,
                GuidancePriority.BODY,
                GuidancePriority.HAND,
                GuidancePriority.HEAD,
                GuidancePriority.EMOTION,
            ),
            GuidancePriority.entries,
        )
    }

    @Test
    fun highestPriorityGuidance_honorsEveryAdjacentPriorityPair() {
        GuidancePriority.entries.zipWithNext().forEach { (higher, lower) ->
            val lowerItem = guidance("lower-$lower", GuidePanel.SUBJECT, lower)
            val higherItem = guidance("higher-$higher", GuidePanel.SUBJECT, higher)

            assertSame(
                "Expected $higher to outrank $lower",
                higherItem,
                selectHighestPriorityGuidance(listOf(lowerItem, higherItem)),
            )
        }
    }

    @Test
    fun panelAndOverlayEnums_declareExactProductValues() {
        assertEquals(
            listOf(GuidePanel.NONE, GuidePanel.ENVIRONMENT, GuidePanel.SUBJECT),
            GuidePanel.entries,
        )
        assertEquals(
            listOf(OverlayMode.SKELETON, OverlayMode.OUTLINE, OverlayMode.REFERENCE),
            OverlayMode.entries,
        )
    }

    @Test
    fun shootingPlan_rejectsAnalysisForAnotherReferencePhoto() {
        val analysis = PhotoAnalysis(
            referencePhotoId = "another-photo",
            summary = "summary",
            environment = "environment",
            composition = "composition",
            lighting = "lighting",
            pose = "pose",
            story = "story",
            colorGrading = "color grading",
            onsitePlan = "onsite plan",
            confidence = 0.8f,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ShootingPlan(
                id = "plan",
                title = "Plan",
                objective = "Objective",
                referencePhotoId = "selected-photo",
                analysis = analysis,
                guidance = emptyList(),
                defaultOverlayMode = OverlayMode.SKELETON,
            )
        }

        assertEquals(
            "Shooting plan and analysis must refer to the same photo",
            error.message,
        )
    }

    private fun guidance(
        id: String,
        panel: GuidePanel,
        priority: GuidancePriority,
    ) = GuidanceItem(
        id = id,
        panel = panel,
        priority = priority,
        title = id,
        instruction = "Do $id",
    )
}
