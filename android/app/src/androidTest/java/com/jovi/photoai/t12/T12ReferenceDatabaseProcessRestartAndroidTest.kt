package com.jovi.photoai.t12

import android.app.Application
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic-only check of the production database factory across a fresh instrumentation process. */
@RunWith(AndroidJUnit4::class)
class T12ReferenceDatabaseProcessRestartAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val targetContext get() = instrumentation.targetContext.applicationContext

    @Test fun prepareUniqueProjectAndReferenceForProcessStop() = runBlocking {
        val runId = runId()
        val targetPackage = instrumentation.targetContext.packageName
        val testPackage = instrumentation.context.packageName
        val targetUid = instrumentation.targetContext.applicationInfo.uid
        val testUid = instrumentation.context.applicationInfo.uid
        assertEquals(targetUid, Process.myUid())
        assertNotEquals(testUid, Process.myUid())

        val repository = ReferenceRepository.create(targetContext)
        val project = repository.createProject("T12 DB restart $runId")
        var prepared = false
        try {
            val imported = SyntheticPickerMediaFactory(targetContext).use { media ->
                repository.importIntoProject(media.jpeg(displayName = "t12-restart-$runId.jpg").uri, project.id)
            }
            val reference = imported as? ReferenceImportResult.Success
                ?: error("T12_RESTART_SYNTHETIC_IMPORT_FAILED: $imported")
            assertEquals(project.id, reference.record.projectId)
            assertNotNull(repository.activeRecord(reference.record.photo.id))

            assertEquals(project.id, repository.project(project.id)?.id)
            assertEquals(reference.record.photo.id,
                repository.activeRecordsForProject(project.id).first().single().photo.id)
            publishIdentity("PREPARE", mapOf(
                "runId" to runId,
                "projectId" to project.id,
                "referenceId" to reference.record.photo.id,
                "processEpoch" to PROCESS_EPOCH,
                "processId" to Process.myPid().toString(),
                "processName" to Application.getProcessName(),
                "processUid" to Process.myUid().toString(),
                "targetPackage" to targetPackage,
                "targetUid" to targetUid.toString(),
                "testPackage" to testPackage,
                "testUid" to testUid.toString(),
            ))
            prepared = true
        } finally {
            if (!prepared) repository.deleteProject(project.id)
        }
    }

    @Test fun verifyPersistedProjectAndReferenceInNewProcess() = runBlocking {
        val runId = runId()
        val projectId = requiredArgument("t12RestartProjectId")
        val referenceId = requiredArgument("t12RestartReferenceId")
        assertEquals(instrumentation.targetContext.packageName, requiredArgument("t12RestartTargetPackage"))
        assertEquals(instrumentation.context.packageName, requiredArgument("t12RestartTestPackage"))
        val targetUid = instrumentation.targetContext.applicationInfo.uid
        val testUid = instrumentation.context.applicationInfo.uid
        assertEquals(targetUid.toString(), requiredArgument("t12RestartTargetUid"))
        assertEquals(testUid.toString(), requiredArgument("t12RestartTestUid"))
        assertEquals(requiredArgument("t12RestartProcessName"), Application.getProcessName())
        assertEquals(targetUid.toString(), requiredArgument("t12RestartProcessUid"))
        assertEquals(targetUid, Process.myUid())
        assertNotEquals(testUid, Process.myUid())
        assertNotEquals("T12_RESTART_PROCESS_EPOCH_NOT_CHANGED", requiredArgument("t12RestartProcessEpoch"), PROCESS_EPOCH)
        assertNotEquals("T12_RESTART_PROCESS_ID_NOT_CHANGED", requiredArgument("t12RestartProcessId"), Process.myPid().toString())

        val repository = ReferenceRepository.create(targetContext)
        val project = requireNotNull(repository.project(projectId)) { "T12_RESTART_PROJECT_MISSING" }
        assertEquals("T12 DB restart $runId", project.title)
        val references = repository.activeRecordsForProject(projectId).first()
        assertEquals(1, references.size)
        assertEquals(referenceId, references.single().photo.id)
        assertEquals(projectId, references.single().projectId)
        assertNotNull(repository.activeRecord(referenceId))
        publishIdentity("VERIFY", mapOf(
            "runId" to runId,
            "projectId" to projectId,
            "referenceId" to referenceId,
            "processEpoch" to PROCESS_EPOCH,
            "processId" to Process.myPid().toString(),
            "processName" to Application.getProcessName(),
            "processUid" to Process.myUid().toString(),
        ))
    }

    @Test fun cleanupDeletesOnlyUniqueSyntheticProject() = runBlocking {
        val runId = runId()
        val suppliedProjectId = InstrumentationRegistry.getArguments().getString("t12RestartProjectId")
        suppliedProjectId?.let { require(it.matches(Regex("^[a-f0-9-]{36}$"))) }
        val repository = ReferenceRepository.create(targetContext)
        val expectedTitle = "T12 DB restart $runId"
        val project = if (suppliedProjectId != null) {
            repository.project(suppliedProjectId)
        } else {
            val matches = repository.projects.first().filter { it.title == expectedTitle }
            require(matches.size <= 1) { "T12_RESTART_AMBIGUOUS_UNREPORTED_PROJECT" }
            matches.singleOrNull()
        }
        if (project != null) {
            require(project.title == expectedTitle) { "T12_RESTART_PROJECT_IDENTITY_DRIFT" }
            if (suppliedProjectId != null) require(project.id == suppliedProjectId)
            assertTrue(repository.deleteProject(project.id))
            assertNull(repository.project(project.id))
            assertTrue(repository.activeRecordsForProject(project.id).first().isEmpty())
        } else if (suppliedProjectId != null) {
            assertNull(repository.project(suppliedProjectId))
            assertTrue(repository.activeRecordsForProject(suppliedProjectId).first().isEmpty())
        }
    }

    private fun runId(): String {
        val value = requireNotNull(InstrumentationRegistry.getArguments().getString("t12RestartRun"))
        require(value.matches(Regex("^[a-f0-9]{32}$"))) { "T12_RESTART_RUN_ID_INVALID" }
        return value
    }

    private fun requiredArgument(key: String) = requireNotNull(InstrumentationRegistry.getArguments().getString(key)) {
        "T12_RESTART_ARGUMENT_MISSING_$key"
    }

    private fun publishIdentity(phase: String, values: Map<String, String>) {
        val status = Bundle().apply { values.forEach { (key, value) -> putString("T12_RESTART_${phase}_$key", value) } }
        instrumentation.sendStatus(2, status)
    }

    private companion object {
        val PROCESS_EPOCH: String = UUID.randomUUID().toString()
    }
}
