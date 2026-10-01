package com.jovi.photoai.t13

import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.p23r.P23RRoomFixture
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class T13BundleReplacementRepositoryAndroidTest {
    private suspend fun prepare(f: P23RRoomFixture, count: Int = 2): Map<String, KnowledgeBundleProvenance> {
        f.seed(count)
        assertEquals(KnowledgeBundleApplyResult.Success(count), f.repository.applyKnowledgeBundle(f.project.id, f.bundle(), f.bindings()))
        return f.dao.activeByProjectOnce(f.project.id).associate { it.id to requireNotNull(it.toRecord().knowledgeBundleProvenance) }
    }
    private fun digest(raw: PhotoKnowledgeBundle) = raw.copy(payloadSha256 = canonicalPayloadSha256(raw))
    private fun next(f: P23RRoomFixture) = digest(f.bundle().copy(source = f.bundle().source.copy(releaseId = "other_release")))
    private fun rejected(code: KnowledgeBundleApplyErrorCode, result: KnowledgeBundleApplyResult) =
        assertEquals(KnowledgeBundleApplyResult.Failure(code), result)

    @Test fun replacePreservesLocalIdentityAndClearsHistoricalAttempt() = runBlocking {
        P23RRoomFixture().use { f ->
            val expected = prepare(f)
            val before = f.dao.activeByProjectOnce(f.project.id)
            f.dao.update(before.first().copy(analysisAttemptId = "historical_attempt"))
            assertEquals(KnowledgeBundleApplyResult.Success(2), f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), expected))
            before.zip(f.dao.activeByProjectOnce(f.project.id)).forEach { (old, current) ->
                assertEquals(old.id, current.id)
                assertEquals(old.imageFileName, current.imageFileName)
                assertEquals(old.ordinal, current.ordinal)
                assertEquals(old.createdAtEpochMillis, current.createdAtEpochMillis)
                assertEquals("other_release", current.knowledgeBundleReleaseId)
                assertEquals(next(f).payloadSha256, current.knowledgeBundlePayloadSha256)
                assertNull(current.analysisAttemptId)
            }
        }
    }
    @Test fun providerAndActiveStateRefusedWithoutWrites() = runBlocking {
        P23RRoomFixture().use { f ->
            val expected = prepare(f)
            val second = f.dao.activeById("synthetic_1")!!
            for (invalid in listOf(second.copy(analysisProviderId = "partial_provider"), second.copy(analysisStatus = "RUNNING"))) {
                f.dao.update(invalid)
                val before = f.dao.activeByProjectOnce(f.project.id)
                rejected(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE, f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), expected))
                assertEquals(before, f.dao.activeByProjectOnce(f.project.id))
            }
        }
    }
    @Test fun incomingAssociationMismatchRefused() = runBlocking {
        P23RRoomFixture().use { f ->
            val expected = prepare(f, 1)
            val base = next(f)
            for (raw in listOf(base.copy(bundleId = "other_bundle"), base.copy(source = base.source.copy(producerId = "other_producer")),
                base.copy(source = base.source.copy(origin = KnowledgeBundleOrigin.LOCAL_SERVICE)),
                base.copy(references = listOf(base.references.single().copy(referenceId = "other_reference"))))) {
                val incoming = digest(raw)
                rejected(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE, f.repository.replaceKnowledgeBundle(f.project.id, incoming,
                    listOf(KnowledgeBundleBinding(incoming.references.single().referenceId, "synthetic_0")), expected))
            }
        }
    }
    @Test fun sameReleaseConflictAndIdenticalPreserveRowsAndTimestamp() = runBlocking {
        P23RRoomFixture().use { f ->
            val expected = prepare(f, 1)
            val before = f.dao.activeByProjectOnce(f.project.id)
            val project = f.dao.projectById(f.project.id)
            rejected(KnowledgeBundleApplyErrorCode.REPLACEMENT_ALREADY_APPLIED, f.repository.replaceKnowledgeBundle(f.project.id, f.bundle(), f.bindings(), expected))
            val item = f.bundle().references.single()
            val changed = digest(f.bundle().copy(references = listOf(item.copy(photography = item.photography.copy(scene = "changed scene")))))
            rejected(KnowledgeBundleApplyErrorCode.REPLACEMENT_IDENTITY_CONFLICT, f.repository.replaceKnowledgeBundle(f.project.id, changed, f.bindings(), expected))
            assertEquals(before, f.dao.activeByProjectOnce(f.project.id))
            assertEquals(project, f.dao.projectById(f.project.id))
        }
    }
    @Test fun staleSecondTupleRejectsWholeBatch() = runBlocking {
        P23RRoomFixture().use { f ->
            val expected = prepare(f)
            val second = f.dao.activeById("synthetic_1")!!
            f.dao.update(second.copy(knowledgeBundleImportedAtEpochMillis = second.knowledgeBundleImportedAtEpochMillis!! + 1))
            val before = f.dao.activeByProjectOnce(f.project.id)
            rejected(KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE, f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), expected))
            assertEquals(before, f.dao.activeByProjectOnce(f.project.id))
            f.dao.markDeletePending(listOf(second.id))
            val afterDelete = f.dao.activeByProjectOnce(f.project.id)
            rejected(KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE, f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), expected))
            assertEquals(afterDelete, f.dao.activeByProjectOnce(f.project.id))
        }
    }
    @Test fun secondUpdateAbortRollsBackWithUnknownOutcome() = runBlocking {
        P23RRoomFixture().use { f ->
            val expected = prepare(f)
            val before = f.dao.activeByProjectOnce(f.project.id)
            withContext(Dispatchers.IO) { f.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER t13_abort BEFORE UPDATE ON reference_records WHEN NEW.id = 'synthetic_1' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END") }
            assertSame(KnowledgeBundleApplyResult.OutcomeUnknown, f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), expected))
            assertEquals(before, f.dao.activeByProjectOnce(f.project.id))
        }
    }
    @Test fun lostAcknowledgementAfterCommitRemainsUnknown() = runBlocking {
        var fail = false
        P23RRoomFixture(afterKnowledgeBundleCommit = { if (fail) throw SQLiteException("synthetic lost acknowledgement") }).use { f ->
            val expected = prepare(f, 1)
            fail = true
            assertSame(KnowledgeBundleApplyResult.OutcomeUnknown, f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), expected))
            assertEquals("other_release", f.dao.activeById("synthetic_0")!!.knowledgeBundleReleaseId)
        }
    }
    @Test fun initialImportStillRefusesReplacementAndMixedBatchFails() = runBlocking {
        P23RRoomFixture().use { f ->
            f.seed(2)
            assertEquals(KnowledgeBundleApplyResult.Success(1), f.repository.applyKnowledgeBundle(f.project.id, f.bundle(1), f.bindings(1)))
            val old = f.dao.activeById("synthetic_0")!!.toRecord().knowledgeBundleProvenance!!
            rejected(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE, f.repository.applyKnowledgeBundle(f.project.id, next(f), f.bindings()))
            rejected(KnowledgeBundleApplyErrorCode.REPLACEMENT_PREVIEW_STALE,
                f.repository.replaceKnowledgeBundle(f.project.id, next(f), f.bindings(), mapOf("synthetic_0" to old)))
            assertEquals("IMPORTED", f.dao.activeById("synthetic_1")!!.analysisStatus)
        }
    }

    @Test fun scopedReplacementLeavesOmittedOldMemberExactlyUnchanged() = runBlocking {
        P23RRoomFixture().use { f ->
            val allExpected = prepare(f, 3)
            val omittedBefore = f.dao.activeById("synthetic_2")!!
            val raw = f.bundle(2).copy(source = f.bundle(2).source.copy(releaseId = "scoped_release"))
            val incoming = digest(raw)
            val bindings = f.bindings(2)
            val expected = allExpected.filterKeys { id -> bindings.any { it.localReferenceId == id } }

            assertEquals(KnowledgeBundleApplyResult.Success(2),
                f.repository.replaceKnowledgeBundle(f.project.id, incoming, bindings, expected))
            for (binding in bindings) {
                val actual = f.dao.activeById(binding.localReferenceId)!!
                assertEquals("scoped_release", actual.knowledgeBundleReleaseId)
                assertEquals(incoming.payloadSha256, actual.knowledgeBundlePayloadSha256)
                assertEquals(binding.producerReferenceId, actual.knowledgeBundleReferenceId)
            }
            assertEquals(omittedBefore, f.dao.activeById("synthetic_2"))
            assertEquals(3, f.dao.activeByProjectOnce(f.project.id).size)
        }
    }
}
