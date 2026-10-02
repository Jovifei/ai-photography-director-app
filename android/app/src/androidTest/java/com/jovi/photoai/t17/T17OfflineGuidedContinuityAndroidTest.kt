package com.jovi.photoai.t17

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
import com.jovi.photoai.data.capture.*
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Actual production root and CameraX. Host-only receipts connect fresh instrumentation processes. */
@RunWith(AndroidJUnit4::class)
class T17OfflineGuidedContinuityAndroidTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val args get() = InstrumentationRegistry.getArguments()
    private val runId get() = required("t17Run")
    private val mode get() = required("t17Mode")
    private val refs get() = ReferenceRepository.create(app)
    private val captures get() = CaptureRepository.get(app)
    private val preferences get() = app.getSharedPreferences(ReferenceLibraryPreferences.FILE_NAME, 0)

    @Before fun exactDedicatedEmulatorAndMeasuredPermission() {
        assertEquals("true", args.getString("t17DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        val device = UiDevice.getInstance(instrumentation)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim())
        assertEquals("T3_API35_20260927", device.executeShellCommand("getprop ro.boot.qemu.avd_name").trim())
        assertEquals(app.applicationInfo.uid, Process.myUid())
        assertNotEquals(instrumentation.context.applicationInfo.uid, Process.myUid())
        assertTrue(runId.matches(Regex("[a-f0-9]{32}")))
        assertTrue(mode in setOf("success", "deleted"))
    }

    @Test fun prepareRootGuidedCaptureAndCurrentBundle() {
        requireMeasuredCameraPermission()
        receipt("runId", runId); receipt("mode", mode)
        receipt("c1Attempted", "false"); receipt("c2Attempted", "false")
        val prefPresent = preferences.contains(PREFERENCE_KEY)
        val rawOriginalPref = preferences.all[PREFERENCE_KEY]
        require(!prefPresent || rawOriginalPref is String) { "T17_UNSUPPORTED_ORIGINAL_PREFERENCE_TYPE" }
        val originalPref = rawOriginalPref as? String
        receipt("prefPresent", prefPresent.toString())
        receipt("prefBase64", encodePreference(originalPref))
        require(!prefPresent || (originalPref != null && ReferenceLibraryPreferences.isSafeOpaqueReferenceId(originalPref) &&
            runBlocking { refs.activeRecord(originalPref) } != null)) { "T17_UNSUPPORTED_ORIGINAL_PREFERENCE_REFUSE_ROOT_MUTATION" }
        receipt("processEpoch", PROCESS_EPOCH); receipt("processId", Process.myPid().toString())
        receipt("processName", Application.getProcessName()); receipt("processUid", Process.myUid().toString())
        receipt("targetPackage", app.packageName); receipt("targetUid", app.applicationInfo.uid.toString())
        receipt("testPackage", instrumentation.context.packageName)
        receipt("testUid", instrumentation.context.applicationInfo.uid.toString())
        val reference = runBlocking {
            val project = refs.createProject(projectTitle())
            receipt("projectId", project.id)
            val imported = SyntheticPickerMediaFactory(app).use { media ->
                refs.importIntoProject(media.jpeg(displayName = "t17-$runId.jpg").uri, project.id)
            } as ReferenceImportResult.Success
            receipt("referenceId", imported.record.photo.id)
            assertEquals(KnowledgeBundleApplyResult.Success(1), refs.applyKnowledgeBundle(project.id,
                T17KnowledgeBundles.create(runId, "A"), listOf(binding(imported.record.photo.id))))
            requireNotNull(refs.activeRecord(imported.record.photo.id))
        }
        val referenceSha = sha(referenceFile(reference))
        receipt("referenceSha256", referenceSha)
        var phaseFailure: Throwable? = null
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            toHome()
            compose.onNodeWithContentDescription("拍摄项目 ${projectTitle()}，1 张照片").performScrollTo().performClick()
            waitText("项目看板")
            compose.onNodeWithContentDescription("项目照片第 1 张，知识包 READY").performScrollTo().performClick()
            waitText("项目照片 · 第 1 张")
            compose.onNodeWithText("查看摄影导演卡").performScrollTo().performClick()
            waitText("摄影导演卡")
            compose.onNodeWithText("进入 Camera Director").performScrollTo().performClick()
            cameraReady(scenario)
            assertGuidance("A")
            val c1 = actualCapture("c1", reference.projectId, reference.photo.id)
            val tuple = c1.toString()
            receipt("c1TupleBase64", encode(tuple)); receipt("c1Sha256", sha(runBlocking { captures.previewFile(c1) }))
            receipt("c1ByteCount", c1.byteCount.toString())
            if (mode == "success") {
                runBlocking {
                    val frozen = requireNotNull(refs.activeRecord(reference.photo.id)).knowledgeBundleProvenance!!
                    assertEquals(KnowledgeBundleApplyResult.Success(1), refs.replaceKnowledgeBundle(reference.projectId,
                        T17KnowledgeBundles.create(runId, "B"), listOf(binding(reference.photo.id)), mapOf(reference.photo.id to frozen)))
                    val current = requireNotNull(refs.activeRecord(reference.photo.id))
                    assertEquals(T17KnowledgeBundles.create(runId, "B").source.releaseId, current.knowledgeBundleProvenance!!.releaseId)
                    assertEquals(T17KnowledgeBundles.composition("B"), current.bundle.composition)
                    receipt("bundleBPayloadSha256", requireNotNull(current.knowledgeBundleProvenance).payloadSha256)
                }
            } else {
                // Preserve Owner's key before deleting the reference can trigger missing-reference recovery.
                restorePreferenceValue(prefPresent, originalPref, reference.photo.id)
                runBlocking { refs.delete(reference.photo.id); assertNull(refs.activeRecord(reference.photo.id)) }
                receipt("referenceDeleted", "true")
            }
            assertUnchangedCapture(c1.id, tuple, c1.fileSha256!!)
            if (mode == "success") assertEquals(referenceSha, sha(referenceFile(reference)))
            receipt("prepare", "PASS")
        } } catch (failure: Throwable) { phaseFailure = failure; throw failure }
        finally { preservePhaseFailureDuringRestore(phaseFailure) { restorePreferenceValue(prefPresent, originalPref, reference.photo.id) } }
    }

    @Test fun verifyFreshProcessRetakeUsesCurrentGuidance() {
        requireMeasuredCameraPermission()
        assertNotEquals(required("t17ProcessEpoch"), PROCESS_EPOCH)
        assertEquals(required("t17ProcessUid"), Process.myUid().toString())
        assertEquals(required("t17TargetUid"), app.applicationInfo.uid.toString())
        assertEquals(required("t17TestUid"), instrumentation.context.applicationInfo.uid.toString())
        assertEquals(required("t17ProcessName"), Application.getProcessName())
        assertEquals(required("t17TargetPackage"), app.packageName)
        assertEquals(required("t17TestPackage"), instrumentation.context.packageName)
        receipt("verifyProcessEpoch", PROCESS_EPOCH); receipt("verifyProcessId", Process.myPid().toString())
        receipt("verifyProcessUid", Process.myUid().toString())
        val projectId = required("t17ProjectId"); val referenceId = required("t17ReferenceId")
        val c1Id = required("t17C1Id"); val c1Tuple = decode(required("t17C1TupleBase64"))
        val c1Sha = required("t17C1Sha256")
        assertEquals(projectTitle(), runBlocking { refs.project(projectId) }!!.title)
        val c1 = assertUnchangedCapture(c1Id, c1Tuple, c1Sha)
        assertEquals(projectId, c1.projectId); assertEquals(referenceId, c1.referenceId)
        val reference = runBlocking { refs.activeRecord(referenceId) }
        if (mode == "success") {
            requireNotNull(reference)
            val expectedB = T17KnowledgeBundles.create(runId, "B")
            val currentProvenance = requireNotNull(reference.knowledgeBundleProvenance)
            assertEquals(PhotoAnalysisStatus.READY, reference.analysisStatus)
            assertEquals(expectedB.bundleId, currentProvenance.bundleId)
            assertEquals(expectedB.source.releaseId, currentProvenance.releaseId)
            assertEquals(expectedB.source.producerId, currentProvenance.producerId)
            assertEquals(expectedB.source.origin, currentProvenance.origin)
            assertEquals(T17KnowledgeBundles.PRODUCER_REFERENCE_ID, currentProvenance.producerReferenceId)
            assertEquals(canonicalPayloadSha256(expectedB), currentProvenance.payloadSha256)
            assertTrue("T17_BUNDLE_IMPORT_TIMESTAMP_INVALID", currentProvenance.importedAtEpochMillis > 0)
            assertEquals(T17KnowledgeBundles.composition("B"), reference.bundle.composition)
            assertEquals(T17KnowledgeBundles.directorPrompt("B"), reference.bundle.directorPrompt)
            assertEquals(required("t17ReferenceSha256"), sha(referenceFile(reference)))
        } else assertNull(reference)
        var phaseFailure: Throwable? = null
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            toHome()
            compose.onNodeWithText("查看全部成片").performClick()
            compose.waitUntil(TIMEOUT) { compose.onAllNodesWithTag("capture-$c1Id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("capture-$c1Id").performScrollTo().performClick()
            waitText("成片预览")
            if (mode == "deleted") {
                compose.onNodeWithTag("capture-open-reference").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithTag("capture-retake").performScrollTo().assertIsNotEnabled()
                receipt("deletedReferenceActions", "PASS_DISABLED_NO_ALTERNATE_REFERENCE")
            } else {
                val currentSource = "当前来源：${requireNotNull(reference).photo.sourceLabel} · 知识 Bundle · " +
                    "${T17KnowledgeBundles.create(runId, "B").source.producerId} / ${T17KnowledgeBundles.create(runId, "B").source.releaseId}"
                compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(currentSource).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(currentSource).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("这是当前关联参考图的状态，不是拍摄时的指导快照，也不是对成片的分析。")
                    .performScrollTo().assertIsDisplayed()
                compose.waitUntil(TIMEOUT) { compose.onAllNodesWithTag("capture-retake").fetchSemanticsNodes().any { isEnabled().matches(it) } }
                compose.onNodeWithTag("capture-retake").performScrollTo().assertIsEnabled().performClick()
                cameraReady(scenario)
                assertGuidance("B")
                val c2 = actualCapture("c2", projectId, referenceId)
                assertNotEquals(c1Id, c2.id)
                receipt("c2Sha256", sha(runBlocking { captures.previewFile(c2) }))
                assertEquals(required("t17ReferenceSha256"), sha(referenceFile(requireNotNull(reference))))
            }
            assertUnchangedCapture(c1Id, c1Tuple, c1Sha)
            receipt("verify", "PASS")
        } } catch (failure: Throwable) { phaseFailure = failure; throw failure }
        finally { preservePhaseFailureDuringRestore(phaseFailure) { restorePreference() } }
    }

    @Test fun restoreOriginalPreferenceGuardedIndependentOfFixture() = restorePreference()

    @Test fun cleanupExactRecordedFixtureAndRestorePreference() {
        restorePreference()
        val projectId = args.getString("t17ProjectId")
        if (projectId.isNullOrBlank()) { receipt("cleanup", "PASS_NO_RECORDED_PROJECT"); receipt("cleanupFixtureAbsent", "true"); return }
        val referenceId = args.getString("t17ReferenceId")
        val known = listOfNotNull(args.getString("t17C1Id"), args.getString("t17C2Id")).filter { it.isNotBlank() }.toSet()
        for (prefix in listOf("C1", "C2")) {
            val attempted = required("t17${prefix}Attempted").toBooleanStrict()
            require(!attempted || !args.getString("t17${prefix}Id").isNullOrBlank()) { "T17_ATTEMPT_WITHOUT_ID_RETAIN_FIXTURE" }
        }
        runBlocking {
            val project = refs.project(projectId)
            if (project == null) {
                require(captures.records.first().none { it.projectId == projectId || it.id in known })
                receipt("cleanup", "PASS_ALREADY_ABSENT"); receipt("cleanupFixtureAbsent", "true"); return@runBlocking
            }
            assertEquals(projectTitle(), project.title)
            val rows = captures.records.first().filter { it.projectId == projectId }
            T17OwnershipPolicy.requireExactCaptures(rows, known, projectId, referenceId)
            val projectRefs = refs.activeRecordsForProject(projectId).first()
            require(projectRefs.all { it.photo.id == referenceId && it.projectId == projectId }) { "T17_REFERENCE_IDENTITY_MISMATCH" }
            // All validation above precedes the first destructive operation.
            rows.forEach { row ->
                val file = if (row.fileState == CaptureFileState.AVAILABLE) captures.previewFile(row) else null
                captures.delete(row.id)
                assertFalse(captures.records.first().any { it.id == row.id })
                file?.let { assertFalse(it.exists()) }
            }
            referenceId?.let { id ->
                refs.activeRecord(id)?.let { record ->
                    assertEquals(projectId, record.projectId)
                    val file = referenceFile(record)
                    refs.delete(id)
                    assertFalse(file.exists())
                }
                assertNull(refs.activeRecord(id))
            }
            assertTrue(refs.deleteProject(projectId)); assertNull(refs.project(projectId))
        }
        receipt("cleanup", "PASS_EXACT_RECORDED_FIXTURE_DELETED")
        receipt("cleanupFixtureAbsent", "true")
    }

    private fun restorePreference() {
        val originalPresent = required("t17OriginalPrefPresent").toBooleanStrict()
        val original = decodePreference(required("t17OriginalPrefBase64"))
        restorePreferenceValue(originalPresent, original, args.getString("t17ReferenceId"))
    }
    private fun restorePreferenceValue(originalPresent: Boolean, original: String?, owned: String?) {
        val currentPresent = preferences.contains(PREFERENCE_KEY)
        val rawCurrent = preferences.all[PREFERENCE_KEY]
        require(!currentPresent || rawCurrent is String) { "T17_FOREIGN_PREFERENCE_TYPE_REFUSE_RESTORE" }
        val current = rawCurrent as? String
        val decision = T17OwnershipPolicy.preferenceDecision(originalPresent, original, currentPresent, current, owned)
        if (decision == T17PreferenceDecision.RESTORE_ORIGINAL) {
            val editor = preferences.edit()
            if (originalPresent) editor.putString(PREFERENCE_KEY, requireNotNull(original)) else editor.remove(PREFERENCE_KEY)
            assertTrue(editor.commit())
        }
        assertTrue("T17_PREFERENCE_PRESENCE_RESTORE_FAILED", originalPresent == preferences.contains(PREFERENCE_KEY))
        assertTrue("T17_PREFERENCE_VALUE_RESTORE_FAILED", original == preferences.getString(PREFERENCE_KEY, null))
        receipt("preferenceRestoration", "PASS_EXACT_ONE_KEY")
        receipt("prefRestored", "true")
    }
    private fun preservePhaseFailureDuringRestore(phaseFailure: Throwable?, restore: () -> Unit) {
        try { restore() } catch (restoreFailure: Throwable) {
            if (phaseFailure == null) throw restoreFailure
            phaseFailure.addSuppressed(restoreFailure)
        }
    }

    private fun actualCapture(prefix: String, projectId: String, referenceId: String): CaptureRecord {
        val before = runBlocking { captures.records.first().map { it.id }.toSet() }
        receipt("${prefix}Attempted", "true")
        compose.onNodeWithContentDescription("拍摄").assertIsEnabled().performClick()
        var reserved: CaptureRecord? = null
        compose.waitUntil(TIMEOUT) {
            val added = runBlocking { captures.records.first().filter { it.id !in before } }
            require(added.size <= 1) { "T17_AMBIGUOUS_NEW_CAPTURE_ROWS" }
            reserved = added.singleOrNull()
            reserved != null
        }
        val row = requireNotNull(reserved)
        receipt("${prefix}Id", row.id); receipt("${prefix}ProjectId", row.projectId ?: "NONE")
        receipt("${prefix}ReferenceId", row.referenceId ?: "NONE")
        assertEquals(projectId, row.projectId); assertEquals(referenceId, row.referenceId)
        compose.waitUntil(TIMEOUT) {
            runBlocking { captures.records.first().singleOrNull { it.id == row.id }?.fileState == CaptureFileState.AVAILABLE }
        }
        return runBlocking { captures.records.first().single { it.id == row.id } }.also {
            assertTrue(it.byteCount > 0); assertEquals(it.fileSha256, sha(runBlocking { captures.previewFile(it) }))
        }
    }
    private fun assertUnchangedCapture(id: String, tuple: String, digest: String): CaptureRecord = runBlocking {
        captures.records.first().single { it.id == id }.also {
            assertTrue("T17_CAPTURE_TUPLE_CHANGED", tuple == it.toString()); assertEquals(digest, sha(captures.previewFile(it)))
        }
    }
    private fun toHome() {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("拍摄项目").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("项目照片 · 第", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("参考图分析").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("参考图库").fetchSemanticsNodes().isNotEmpty() }
        repeat(4) {
            if (compose.onAllNodesWithText("拍摄项目").fetchSemanticsNodes().isNotEmpty()) return
            UiDevice.getInstance(instrumentation).pressBack(); compose.waitForIdle()
        }
        waitText("拍摄项目")
    }
    private fun assertGuidance(release: String) {
        compose.onNodeWithText(T17KnowledgeBundles.directorPrompt(release), substring = true).assertExists()
        compose.onNodeWithText(T17KnowledgeBundles.directorPrompt(if (release == "A") "B" else "A"), substring = true).assertDoesNotExist()
    }
    private fun cameraReady(scenario: ActivityScenario<MainActivity>) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().any { isEnabled().matches(it) } }
        var streaming = false
        compose.waitUntil(TIMEOUT) {
            scenario.onActivity { streaming = previews(it.window.decorView).any { view -> view.previewStreamState.value == PreviewView.StreamState.STREAMING } }
            streaming
        }
    }
    private fun previews(view: View): List<PreviewView> = when (view) {
        is PreviewView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { previews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun waitText(text: String) = compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun projectTitle() = "T17 continuity $runId"
    private fun requireMeasuredCameraPermission() {
        assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA))
    }
    private fun binding(referenceId: String) = KnowledgeBundleBinding(T17KnowledgeBundles.PRODUCER_REFERENCE_ID, referenceId)
    private fun referenceFile(reference: ReferenceRecord) = File(app.filesDir, "references/${reference.imageFileName}")
    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun required(name: String) = requireNotNull(args.getString(name)) { "T17_REQUIRED_ARGUMENT_$name" }
    private fun receipt(key: String, value: String) = instrumentation.sendStatus(2, Bundle().apply { putString("T17_RECEIPT_$key", value) })
    private fun encode(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP).toString(Charsets.UTF_8)
    private fun encodePreference(value: String?) = when (value) { null -> "NONE"; "" -> "EMPTY"; else -> encode(value) }
    private fun decodePreference(value: String) = when (value) { "NONE" -> null; "EMPTY" -> ""; else -> decode(value) }
    companion object { private const val PREFERENCE_KEY = "last_active_reference_id"; private const val TIMEOUT = 30_000L; private val PROCESS_EPOCH = UUID.randomUUID().toString() }
}

/** Test harness ownership gates, shared by real cleanup/restoration and adversarial no-write tests. */
internal enum class T17PreferenceDecision { NOOP, RESTORE_ORIGINAL }
internal object T17OwnershipPolicy {
    fun requireExactCaptures(rows: List<CaptureRecord>, known: Set<String>, projectId: String, referenceId: String?) {
        require(rows.map { it.id }.toSet() == known && rows.size == known.size) { "T17_UNKNOWN_CAPTURE_ID_RETAIN_FIXTURE" }
        require(rows.all { it.projectId == projectId && it.referenceId == referenceId }) {
            "T17_CAPTURE_ASSOCIATION_MISMATCH_RETAIN_FIXTURE"
        }
    }
    fun preferenceDecision(originalPresent: Boolean, original: String?, currentPresent: Boolean,
        current: String?, owned: String?): T17PreferenceDecision {
        if (currentPresent == originalPresent && current == original) return T17PreferenceDecision.NOOP
        require(currentPresent && owned != null && current == owned) { "T17_FOREIGN_PREFERENCE_CHANGE_REFUSE_RESTORE" }
        return T17PreferenceDecision.RESTORE_ORIGINAL
    }
}
