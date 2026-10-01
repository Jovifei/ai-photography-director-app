package com.jovi.photoai.t15

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.camera.*
import com.jovi.photoai.data.capture.*
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.p23r.P23RRoomFixture
import com.jovi.photoai.reference.toCameraDirectorGuidance
import com.jovi.photoai.ui.CameraScreen
import com.jovi.photoai.ui.capture.CaptureLibraryViewModel
import com.jovi.photoai.ui.capture.CaptureLibraryViewModelFactory
import com.jovi.photoai.ui.design.PhotoDirectorTheme
import com.jovi.photoai.ui1.SyntheticPickerMediaFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Dedicated emulator CameraX; unique synthetic references and exact-owned production capture rows. */
@RunWith(AndroidJUnit4::class)
class T15CameraCapabilityAndroidTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun dedicatedEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("t15DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals("1", UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("getprop ro.kernel.qemu").trim())
    }

    @Test fun actualCapabilitiesAndSavedStatePreserveCaptureAssociationAndOriginal() {
        val measured = probeCapabilities()
        assertTrue(measured.isNotEmpty())
        val app = rule.activity.application
        // CaptureRepository validates associations through the production reference factory.
        val refs = ReferenceRepository.create(app)
        val captures = runBlocking { CaptureRepository.get(app) }
        var projectId: String? = null
        var library: CaptureLibraryViewModel? = null
        var captureBaseline: Set<String>? = null
        var captureAccepted = false
        try {
            val reference = runBlocking {
                val project = refs.createProject("T15 synthetic ${UUID.randomUUID()}")
                projectId = project.id
                publish("T15_CREATED_PROJECT_ID", project.id)
                val imported = SyntheticPickerMediaFactory(app).use { media ->
                    refs.importIntoProject(media.jpeg().uri, project.id)
                } as ReferenceImportResult.Success
                publish("T15_CREATED_REFERENCE_ID", imported.record.photo.id)
                P23RRoomFixture().use { fixture ->
                    assertEquals(KnowledgeBundleApplyResult.Success(1), refs.applyKnowledgeBundle(
                        project.id, fixture.bundle(1), listOf(KnowledgeBundleBinding("producer_0", imported.record.photo.id))))
                }
                requireNotNull(refs.activeRecord(imported.record.photo.id))
            }
            val original = File(app.filesDir, "references/${reference.imageFileName}")
            val originalBytes = original.readBytes()
            rule.runOnUiThread {
                library = ViewModelProvider(rule.activity, CaptureLibraryViewModelFactory(app))
                    .get("t15-capture", CaptureLibraryViewModel::class.java).also { it.closeGallery() }
            }
            val activeLibrary = requireNotNull(library)
            val restorer = StateRestorationTester(rule)
            restorer.setContent {
                PhotoDirectorTheme { CameraScreen(emptyList(),
                    referenceGuidance = reference.bundle.toCameraDirectorGuidance(reference.photo.title, reference.photo.sourceLabel),
                    referenceImageFileName = reference.imageFileName,
                    projectId = reference.projectId, referenceId = reference.photo.id, captureLibrary = activeLibrary) }
            }
            waitReady()
            var selected = resolveCameraLens(CameraLens.BACK, measured.keys)!!
            if (measured.keys.containsAll(setOf(CameraLens.BACK, CameraLens.FRONT))) {
                val oldZoom = measured[selected]
                if (oldZoom != null && 2f in oldZoom.minRatio..oldZoom.maxRatio) {
                    applyUiZoom(2f)
                    publish("T15_BACK_PRE_SWITCH_ZOOM", "2.00x_CONFIRMED")
                }
                rule.onNodeWithTag("camera-lens-switch").performClick()
                selected = if (selected == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
                waitReady()
                rule.onNodeWithTag("camera-lens-switch").assertTextContains(lensSwitchLabel(selected))
                measured[selected]?.let { default ->
                    rule.onNodeWithTag("zoom-open").assertTextContains(String.format(Locale.ROOT, "%.2fx", default.currentRatio), substring = true)
                    publish("T15_SWITCH_NEW_LENS_DEFAULT_ZOOM", default.currentRatio.toString())
                }
            } else rule.onNodeWithTag("camera-lens-switch").assertDoesNotExist()
            rule.onNodeWithText("查看参考图").assertExists()
            var expectedZoomLabel: String? = null
            val zoom = measured[selected]
            if (zoom != null) {
                val target = zoom.minRatio + (zoom.maxRatio - zoom.minRatio) * 0.35f
                expectedZoomLabel = applyUiZoom(target)
            } else rule.onNodeWithTag("zoom-open").assertDoesNotExist()

            restorer.emulateSavedInstanceStateRestore()
            waitReady()
            if (measured.size == 2) rule.onNodeWithTag("camera-lens-switch").assertTextContains(lensSwitchLabel(selected))
            expectedZoomLabel?.let { rule.onNodeWithTag("zoom-open").assertTextContains(it, substring = true) }
            rule.onNodeWithText("查看参考图").performClick()
            rule.onNodeWithText("关闭参考图").performClick()
            waitReady()
            if (measured.size == 2) rule.onNodeWithTag("camera-lens-switch").assertTextContains(lensSwitchLabel(selected))
            expectedZoomLabel?.let { rule.onNodeWithTag("zoom-open").assertTextContains(it, substring = true) }

            val baseline = activeLibrary.state.value.records.map { it.id }.toSet()
            captureBaseline = baseline
            publish("T15_CREATED_CAPTURE_IDS", "[]")
            val shutter = rule.onNodeWithContentDescription("拍摄").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val oldSwitch = if (measured.size == 2) rule.onNodeWithTag("camera-lens-switch")
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action else null
            rule.runOnUiThread {
                shutter()
                captureAccepted = activeLibrary.state.value.capturing
                assertTrue(captureAccepted)
                oldSwitch?.invoke() // old enabled callback must recheck the synchronous durable gate.
            }
            try {
                rule.waitUntil(25_000) { !activeLibrary.state.value.capturing && activeLibrary.state.value.galleryVisible && activeLibrary.state.value.records.any {
                    it.id !in baseline && it.projectId == reference.projectId && it.fileState == CaptureFileState.AVAILABLE
                } }
            } catch (failure: Throwable) {
                val state = activeLibrary.state.value
                publish("T15_CAPTURE_DIAGNOSTIC", "ready=${state.ready};capturing=${state.capturing};gallery=${state.galleryVisible};" +
                    "newRows=${state.records.filter { it.id !in baseline }.map { "${it.id}:${it.fileState}:projectMatch=${it.projectId == reference.projectId}" }}")
                throw failure
            }
            val owned = activeLibrary.state.value.records.single { it.id !in baseline && it.projectId == reference.projectId }
            assertEquals(reference.photo.id, owned.referenceId)
            publish("T15_OWNED_CAPTURE_ID", owned.id)
            runBlocking { assertTrue(captures.previewFile(owned).isFile) }
            assertArrayEquals(originalBytes, original.readBytes())
            rule.runOnUiThread { activeLibrary.closeGallery() }
            waitReady()
            if (measured.size == 2) rule.onNodeWithTag("camera-lens-switch").assertTextContains(lensSwitchLabel(selected))
            expectedZoomLabel?.let { rule.onNodeWithTag("zoom-open").assertTextContains(it, substring = true) }
            publish("T15_RUNTIME_CAPTURE_ASSOCIATION", "PASS")
            publish("T15_SAVEABLE_REBIND", "PASS_COMPOSE_STATE_RESTORATION_AND_REFERENCE_GALLERY_RETURN")
        } finally {
            rule.runOnUiThread { rule.activity.setContent {} }
            rule.waitForIdle()
            library?.let { model -> rule.waitUntil(25_000) { !model.state.value.capturing } }
            runBlocking {
                projectId?.let { id ->
                    if (captureAccepted) captureBaseline?.let { before ->
                        val created = captures.records.first().filter { it.id !in before }
                        require(created.size <= 1) { "T15_CAPTURE_CLEANUP_IDENTITY_AMBIGUOUS" }
                        require(created.all { it.projectId == id }) { "T15_CAPTURE_CLEANUP_PROJECT_MISMATCH" }
                        publish("T15_CREATED_CAPTURE_IDS", created.map { it.id }.toString())
                        created.forEach { captures.delete(it.id) }
                    }
                    assertTrue(refs.deleteProject(id))
                }
            }
        }
    }

    @Test fun actualCaptureRejectsSwitchAndStillSettlesAfterManagerShutdown() {
        lateinit var view: PreviewView
        lateinit var manager: CameraXManager
        val initialized = AtomicReference<Boolean?>(null)
        rule.runOnUiThread { view = PreviewView(rule.activity); manager = CameraXManager(rule.activity.applicationContext) }
        rule.setContent { AndroidView(factory = { view }) }
        rule.runOnUiThread { manager.initialize(onReady = {
            val lens = resolveCameraLens(CameraLens.BACK, manager.availableLenses())
            initialized.set(lens != null && manager.bindLens(rule.activity, view, lens))
            manager.setAnalyzer { it.close() }
        }, onError = { initialized.set(false) }) }
        rule.waitUntil(20_000) { initialized.get() != null }
        assertEquals(true, initialized.get())
        rule.waitUntil(25_000) { view.previewStreamState.value == PreviewView.StreamState.STREAMING }
        val output = File(rule.activity.cacheDir, "t15-owned-${UUID.randomUUID()}.jpg")
        val callbacks = AtomicInteger()
        val saved = AtomicReference<Boolean?>(null)
        try {
            rule.runOnUiThread {
                val current = manager.currentLens()!!
                manager.takePicture(output, { callbacks.incrementAndGet(); saved.set(true) }, { callbacks.incrementAndGet(); saved.set(false) })
                assertEquals(LensSwitchResult.Busy, manager.switchLens(rule.activity, view, current))
                manager.shutdown()
            }
            rule.waitUntil(25_000) { callbacks.get() > 0 }
            assertEquals(1, callbacks.get())
            if (saved.get() == true) assertTrue(output.isFile && output.length() > 0)
            publish("T15_CAPTURE_AFTER_SHUTDOWN_SETTLEMENT", if (saved.get() == true) "SAVED_CALLBACK" else "ERROR_CALLBACK")
        } finally {
            rule.runOnUiThread { manager.shutdown(); rule.activity.setContent {} }
            if (callbacks.get() == 1 && output.exists()) assertTrue(output.delete())
        }
    }

    private fun probeCapabilities(): Map<CameraLens, ZoomCapability?> {
        val result = AtomicReference<Map<CameraLens, ZoomCapability?>?>(null)
        val error = AtomicReference<Throwable?>(null)
        rule.runOnUiThread {
            val manager = CameraXManager(rule.activity.applicationContext)
            val view = PreviewView(rule.activity)
            manager.initialize(onReady = {
                try {
                    val map = manager.availableLenses().associateWith { lens ->
                        assertTrue(manager.bindLens(rule.activity, view, lens))
                        manager.zoomCapability()
                    }
                    map.forEach { (lens, zoom) -> publish("T15_MEASURED_$lens", zoom?.toString() ?: "FIXED_OR_UNAVAILABLE_ZOOM") }
                    result.set(map)
                } catch (failure: Throwable) { error.set(failure) } finally { manager.shutdown() }
            }, onError = { error.set(it); manager.shutdown() })
        }
        rule.waitUntil(20_000) { result.get() != null || error.get() != null }
        error.get()?.let { throw AssertionError("Camera capability probe failed", it) }
        return requireNotNull(result.get())
    }

    private fun waitReady() = rule.waitUntil(25_000) {
        rule.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().any { isEnabled().matches(it) }
    }
    private fun applyUiZoom(target: Float): String {
        rule.onNodeWithTag("zoom-open").performClick()
        rule.onNodeWithTag("zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
        val label = String.format(Locale.ROOT, "%.2fx", target)
        rule.waitUntil(20_000) { rule.onAllNodesWithText("已确认：$label").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("完成").performClick()
        waitReady()
        return label
    }
    private fun lensSwitchLabel(lens: CameraLens) = if (lens == CameraLens.BACK) "后置 → 前置" else "前置 → 后置"
    private fun publish(key: String, value: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString(key, value) })
    }
}
