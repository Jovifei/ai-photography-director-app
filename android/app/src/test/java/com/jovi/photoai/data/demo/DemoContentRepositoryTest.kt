package com.jovi.photoai.data.demo

import com.jovi.photoai.domain.model.selectHighestPriorityGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoContentRepositoryTest {
    @Test
    fun referencePhotos_haveUniqueIdsAndAssetKeys() {
        val photos = DemoContentRepository.referencePhotos

        assertTrue(photos.isNotEmpty())
        assertEquals(photos.size, photos.map { it.id }.distinct().size)
        assertEquals(photos.size, photos.map { it.imageAssetKey }.distinct().size)
    }

    @Test
    fun everyAnalysisPointsToAnExistingReferencePhoto() {
        val referenceIds = DemoContentRepository.referencePhotos.map { it.id }.toSet()

        assertTrue(
            DemoContentRepository.analyses.all { it.referencePhotoId in referenceIds }
        )
    }

    @Test
    fun defaultPlanIsInternallyConsistentAndActionable() {
        val plan = DemoContentRepository.defaultShootingPlan

        assertNotNull(DemoContentRepository.referencePhoto(plan.referencePhotoId))
        assertEquals(plan.referencePhotoId, plan.analysis.referencePhotoId)
        assertNotNull(selectHighestPriorityGuidance(plan.guidance))
    }

    @Test
    fun featuredReferenceMatchesDefaultPlan() {
        assertEquals(
            DemoContentRepository.defaultShootingPlan.referencePhotoId,
            DemoContentRepository.featuredReferencePhoto.id,
        )
    }

    @Test
    fun analysisContainsEveryRequiredProductModule() {
        DemoContentRepository.analyses.forEach { analysis ->
            val requiredModules = mapOf(
                "environment" to analysis.environment,
                "composition" to analysis.composition,
                "lighting" to analysis.lighting,
                "pose" to analysis.pose,
                "story" to analysis.story,
                "colorGrading" to analysis.colorGrading,
                "onsitePlan" to analysis.onsitePlan,
            )

            requiredModules.forEach { (module, content) ->
                assertTrue(
                    "${analysis.referencePhotoId} is missing $module",
                    content.isNotBlank(),
                )
            }
        }
    }
}
