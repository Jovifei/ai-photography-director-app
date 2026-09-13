package com.jovi.photoai.data.reference

import org.junit.Test

class KnowledgeBundleCoreRegressionTest {
    @Test fun strictJson_rejectsAmbiguousOrResourceUnsafeDocuments() {
        KnowledgeBundleCoreRegressionCases.syntax()
    }

    @Test fun importSession_ignoresStaleCompletionAndProtectsCommit() {
        KnowledgeBundleCoreRegressionCases.session()
    }

    @Test fun mapping_requiresExactOneToOneAndAllowsExplicitUnbind() {
        KnowledgeBundleCoreRegressionCases.mapping()
    }
    @Test fun boundedRead_limitsBytesAndHonoursCancellation() {
        KnowledgeBundleCoreRegressionCases.boundedRead()
    }
}
