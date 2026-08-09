package com.jovi.photoai.data.reference

import com.jovi.photoai.data.demo.DemoReferenceAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAnalysisProviderTest {
    @Test
    fun unconfiguredProvider_failsClosedWithoutExampleGuidance() = runBlocking {
        val result = UnconfiguredReferenceAnalysisProvider.analyze(ReferenceAnalysisRequest("photo_1"))

        assertTrue(result is ProviderAnalysisResult.Unavailable)
        assertEquals(SafeProviderErrorCode.PROVIDER_NOT_CONFIGURED, (result as ProviderAnalysisResult.Unavailable).errorCode)
        assertEquals(PhotoAnalysisStatus.UNAVAILABLE, result.toPhotoAnalysisStatus())
    }

    @Test
    fun readyResult_withAnotherPhotoId_isRejectedBeforeRendering() {
        val result = ProviderAnalysisResult.Ready(
            bundle = DemoReferenceAnalyzer.analyze("photo_1", "fixture"),
            provenance = ProviderAnalysisProvenance(
                providerId = "fixture-provider",
                providerType = ProviderType.ON_DEVICE,
                modelId = "fixture-model",
                modelRevision = "fixture-r1",
                modelArtifactSha256 = "a".repeat(64),
                runtimeId = "fixture-runtime",
                completedAtEpochMillis = 0,
            ),
        ).validatedFor(ReferenceAnalysisRequest("photo_2"))

        assertTrue(result is ProviderAnalysisResult.Failed)
        assertEquals(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID, (result as ProviderAnalysisResult.Failed).errorCode)
    }
}
