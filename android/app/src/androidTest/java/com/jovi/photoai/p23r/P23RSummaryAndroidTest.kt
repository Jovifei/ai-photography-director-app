package com.jovi.photoai.p23r

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.PersistedProjectSummary
import com.jovi.photoai.data.reference.ProjectSummaryStatus
import com.jovi.photoai.data.reference.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P23RSummaryAndroidTest {
    @Test fun bundleOnlyLegacySummary_isHiddenAndDeletedByReadValidation() = runBlocking {
        P23RRoomFixture().use { fixture ->
            fixture.seed(1)
            val applied = fixture.repository.applyKnowledgeBundle(fixture.project.id, fixture.bundle(), fixture.bindings())
            assertTrue(applied is KnowledgeBundleApplyResult.Success)
            fixture.dao.upsertSummary(
                PersistedProjectSummary(
                    projectId = fixture.project.id,
                    status = ProjectSummaryStatus.SUCCESS,
                    readyCount = 1,
                    failedCount = 0,
                    inputDigest = "stale-bundle-only-digest",
                    modelId = "legacy-provider-model",
                ).toEntity(),
            )

            assertNull(fixture.repository.projectSummary(fixture.project.id))
            assertNull(fixture.dao.summaryByProject(fixture.project.id))
        }
    }
}
