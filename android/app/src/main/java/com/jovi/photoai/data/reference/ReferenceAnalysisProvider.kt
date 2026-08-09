package com.jovi.photoai.data.reference

import com.jovi.photoai.reference.ReferenceBundle

/**
 * The future analysis seam deliberately receives only an opaque reference ID. A separately
 * approved adapter must own any private-image access; providers never receive a Picker Uri.
 */
internal data class ReferenceAnalysisRequest(val referenceId: String) {
    init {
        require(referenceId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
    }
}

internal enum class ProviderType { PIPELINE, LOCAL_SERVICE, CLOUD, ON_DEVICE }

internal enum class SafeProviderErrorCode {
    REFERENCE_URI_UNAVAILABLE,
    REFERENCE_PERMISSION_EXPIRED,
    PROVIDER_NOT_CONFIGURED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_TIMEOUT,
    PROVIDER_OUTPUT_EMPTY,
    PROVIDER_OUTPUT_MALFORMED,
    PROVIDER_OUTPUT_SCHEMA_INVALID,
    PROVIDER_SAFETY_REJECTED,
    PROVIDER_LICENSE_BLOCKED,
}

internal enum class UncertaintyLevel { LOW, MEDIUM, HIGH, NOT_ASSESSED }
internal enum class UncertaintyBasis { DIRECT_OBSERVATION, PHOTOGRAPHIC_INTERPRETATION, CREATIVE_RECOMMENDATION, INSUFFICIENT_EVIDENCE }

internal data class AnalysisUncertainty(
    val level: UncertaintyLevel,
    val basis: UncertaintyBasis,
)

/** Field-level evidence basis; this intentionally avoids a fabricated overall percentage. */
internal data class AnalysisConfidenceSummary(
    val directObservationFields: List<String> = emptyList(),
    val photographicInterpretationFields: List<String> = emptyList(),
    val creativeRecommendationFields: List<String> = emptyList(),
)

/** Metadata for a validated real result; it carries neither image bytes nor media location. */
internal data class ProviderAnalysisProvenance(
    val providerId: String,
    val providerType: ProviderType,
    val modelId: String,
    val modelRevision: String,
    val modelArtifactSha256: String,
    val runtimeId: String,
    val completedAtEpochMillis: Long,
    val startedAtEpochMillis: Long = completedAtEpochMillis,
    val latencyMillis: Long = (completedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0),
    val confidenceSummary: AnalysisConfidenceSummary = AnalysisConfidenceSummary(),
    val uncertaintyFlags: Map<String, AnalysisUncertainty> = emptyMap(),
    val warnings: List<String> = emptyList(),
) {
    init {
        require(listOf(providerId, modelId, modelRevision, runtimeId).all { it.isNotBlank() && it.length <= 256 })
        require(modelArtifactSha256.matches(Regex("^[A-Fa-f0-9]{64}$")))
        require(startedAtEpochMillis >= 0)
        require(completedAtEpochMillis >= startedAtEpochMillis)
        require(latencyMillis >= 0)
        require(warnings.size <= 10 && warnings.all { it.isNotBlank() && it.length <= 240 })
    }
}

internal sealed interface ProviderAnalysisResult {
    data class Ready(
        val bundle: ReferenceBundle,
        val provenance: ProviderAnalysisProvenance,
    ) : ProviderAnalysisResult

    data class Failed(val errorCode: SafeProviderErrorCode, val retryable: Boolean) : ProviderAnalysisResult
    data class Unavailable(val errorCode: SafeProviderErrorCode) : ProviderAnalysisResult
}

internal interface ReferenceAnalysisProvider {
    suspend fun analyze(request: ReferenceAnalysisRequest): ProviderAnalysisResult
}

/** Default is fail-closed. Fixed example guidance is intentionally outside this provider seam. */
internal object UnconfiguredReferenceAnalysisProvider : ReferenceAnalysisProvider {
    override suspend fun analyze(request: ReferenceAnalysisRequest): ProviderAnalysisResult =
        ProviderAnalysisResult.Unavailable(SafeProviderErrorCode.PROVIDER_NOT_CONFIGURED)
}

/** A real result is renderable only when its opaque identity is bound to the requested photo. */
internal fun ProviderAnalysisResult.validatedFor(request: ReferenceAnalysisRequest): ProviderAnalysisResult = when (this) {
    is ProviderAnalysisResult.Ready -> if (bundle.referenceId == request.referenceId) this else {
        ProviderAnalysisResult.Failed(SafeProviderErrorCode.PROVIDER_OUTPUT_SCHEMA_INVALID, retryable = false)
    }
    is ProviderAnalysisResult.Failed,
    is ProviderAnalysisResult.Unavailable -> this
}

internal fun ProviderAnalysisResult.toPhotoAnalysisStatus(): PhotoAnalysisStatus = when (this) {
    is ProviderAnalysisResult.Ready -> PhotoAnalysisStatus.READY
    is ProviderAnalysisResult.Failed -> PhotoAnalysisStatus.FAILED
    is ProviderAnalysisResult.Unavailable -> PhotoAnalysisStatus.UNAVAILABLE
}
