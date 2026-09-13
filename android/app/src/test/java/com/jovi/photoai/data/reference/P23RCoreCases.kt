package com.jovi.photoai.data.reference

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** Tests the actual production policies/classifier, not a duplicate database implementation. */
internal object P23RCoreCases {
    fun fencing(): Int {
        var checked = 0
        fun expect(value: Boolean) { check(value) { "fencing assertion ${checked + 1}" }; checked++ }
        val statuses = listOf("IMPORTED", "FAILED", "UNAVAILABLE", "CANCELLED", "QUEUED", "RUNNING", "READY", "EXAMPLE_GUIDANCE", "UNKNOWN")
        for (status in statuses) for (hasKnowledge in listOf(false, true)) for (token in listOf(null, "old")) {
            val expected = status in setOf("IMPORTED", "FAILED", "UNAVAILABLE", "CANCELLED") && !hasKnowledge && token == null
            expect(AnalysisAttemptPolicy.canClaim(status, hasKnowledge, token) == expected)
        }
        for (status in statuses) for (required in listOf("QUEUED", "RUNNING")) {
            for (hasKnowledge in listOf(false, true)) for (stored in listOf(null, "old", "new")) {
                val expected = status == required && !hasKnowledge && stored == "new"
                expect(AnalysisAttemptPolicy.owns(status, required, hasKnowledge, stored, "new") == expected)
            }
        }
        expect(!AnalysisAttemptPolicy.owns("RUNNING", "RUNNING", false, "", ""))
        expect(runCatching { AnalysisAttempt("", "token") }.isFailure)
        expect(runCatching { AnalysisAttempt("ref", "") }.isFailure)
        expect(AnalysisAttempt("ref", "old") != AnalysisAttempt("ref", "new"))
        return checked
    }

    suspend fun outcomes(): Int {
        var checked = 0
        fun expect(value: Boolean) { check(value) { "outcome assertion ${checked + 1}" }; checked++ }
        expect(observeCommitOutcome { "ack" } == CommitOutcome.Acknowledged("ack"))
        expect(observeCommitOutcome { "known pre-write rejection" } == CommitOutcome.Acknowledged("known pre-write rejection"))
        expect(observeCommitOutcome<Unit> { throw IllegalStateException("before commit") } === CommitOutcome.Unknown)
        var committed = false
        val result = observeCommitOutcome<Unit> { committed = true; throw IllegalStateException("lost acknowledgement") }
        expect(committed)
        expect(result === CommitOutcome.Unknown)
        val marker = CancellationException("cancel")
        val caught = try { observeCommitOutcome<Unit> { throw marker }; null } catch (e: CancellationException) { e }
        expect(caught === marker)
        var durable = false
        val after = try {
            observeCommitOutcome<Unit> { durable = true; throw marker }
            null
        } catch (e: CancellationException) { e }
        expect(durable)
        expect(after === marker)
        return checked
    }

    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val fencing = fencing()
        val outcomes = outcomes()
        println("PASS fencing=$fencing outcomes=$outcomes total=${fencing + outcomes} scope=HOST_POLICIES_ONLY")
    }
}
