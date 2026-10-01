package com.jovi.photoai.data.reference

internal fun isKnowledgeBundleReplacementTargetEligible(record: ReferenceRecord): Boolean =
    record.analysisStatus == PhotoAnalysisStatus.READY &&
        record.knowledgeBundleProvenance != null && record.analysisProvenance == null &&
        !record.hasProviderProvenanceMetadata

/** Shared preview/commit policy. Releases are opaque identities, never ordered versions. */
internal fun knowledgeBundleReplacementError(
    record: ReferenceRecord,
    incoming: PhotoKnowledgeBundle,
    producerReferenceId: String,
    expected: KnowledgeBundleProvenance? = null,
): KnowledgeBundleApplyErrorCode? {
    if (expected != null && record.knowledgeBundleProvenance != expected) {
        return KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE
    }
    if (!isKnowledgeBundleReplacementTargetEligible(record)) {
        return KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE
    }
    val old = requireNotNull(record.knowledgeBundleProvenance)
    if (old.bundleId != incoming.bundleId || old.producerId != incoming.source.producerId ||
        old.origin != incoming.source.origin || old.producerReferenceId != producerReferenceId) {
        return KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE
    }
    if (old.releaseId == incoming.source.releaseId) {
        return if (old.payloadSha256.equals(incoming.payloadSha256, ignoreCase = true)) {
            KnowledgeBundleApplyErrorCode.REPLACEMENT_ALREADY_APPLIED
        } else KnowledgeBundleApplyErrorCode.REPLACEMENT_IDENTITY_CONFLICT
    }
    return null
}

/** Reject partial provider metadata as well as a fully decoded provider provenance. */
internal fun ReferenceEntity.hasAnyProviderProvenance(): Boolean = listOf(
    analysisProviderId, analysisProviderType, analysisModelId, analysisModelRevision,
    analysisModelArtifactSha256, analysisRuntimeId, analysisStartedAtEpochMillis,
    analysisCompletedAtEpochMillis, analysisLatencyMillis, analysisDirectObservationFields,
    analysisPhotographicInterpretationFields, analysisCreativeRecommendationFields,
    analysisUncertaintyFlags, analysisWarnings,
).any { it != null }
