package com.jovi.photoai.t16

import android.Manifest
import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.jovi.photoai.MainActivity
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

/** Actual Activity lifecycle and CameraX. Focused screen host, not root navigation qualification. */
@RunWith(AndroidJUnit4::class)
class T16CameraLifecycleAndroidTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun dedicatedEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("t16DedicatedEmulator"))
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals("1", UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("getprop ro.kernel.qemu").trim())
    }

    @Test fun pausedStaleShutterCannotReserveAndTrueRecreationRestoresConfirmedContext() {
        val app = rule.activity.application
        val refs = ReferenceRepository.create(app)
        val captures = runBlocking { CaptureRepository.get(app) }
        val reference = runBlocking {
            val project = refs.createProject("T16 synthetic ${UUID.randomUUID()}")
            publish("T16_CREATED_PROJECT_ID", project.id)
            val imported = SyntheticPickerMediaFactory(app).use { media ->
                refs.importIntoProject(media.jpeg().uri, project.id)
            } as ReferenceImportResult.Success
            publish("T16_CREATED_REFERENCE_ID", imported.record.photo.id)
            P23RRoomFixture().use { fixture ->
                assertEquals(KnowledgeBundleApplyResult.Success(1), refs.applyKnowledgeBundle(project.id,
                    fixture.bundle(1), listOf(KnowledgeBundleBinding("producer_0", imported.record.photo.id))))
            }
            requireNotNull(refs.activeRecord(imported.record.photo.id))
        }
        lateinit var model: CaptureLibraryViewModel
        var modelInstalled = false
        fun install(activity: MainActivity) {
            model = ViewModelProvider(activity, CaptureLibraryViewModelFactory(app))
                .get("t16-capture", CaptureLibraryViewModel::class.java)
            modelInstalled = true
            activity.setContent {
                PhotoDirectorTheme {
                    CameraScreen(emptyList(), referenceGuidance = reference.bundle.toCameraDirectorGuidance(
                        reference.photo.title, reference.photo.sourceLabel), referenceImageFileName = reference.imageFileName,
                        projectId = reference.projectId, referenceId = reference.photo.id, captureLibrary = model)
                }
            }
        }
        // Called after MainActivity.onCreate/setContent returns, before the first resumed composition.
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) install(activity)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        var baseline: Set<String>? = null
        val original = File(app.filesDir, "references/${reference.imageFileName}").readBytes()
        try {
            app.registerActivityLifecycleCallbacks(callback)
            rule.runOnUiThread { install(rule.activity); model.closeGallery() }
            waitReady()
            baseline = runBlocking { captures.records.first().map { it.id }.toSet() }
            val before = requireNotNull(baseline)
            val zoomLabel = if (rule.onAllNodesWithTag("zoom-open").fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag("zoom-open").performClick()
                rule.onNodeWithTag("zoom-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
                val label = String.format(Locale.ROOT, "%.2fx", 2f)
                rule.waitUntil(20_000) { rule.onAllNodesWithText("已确认：$label").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("完成").performClick()
                waitReady()
                label
            } else null
            val stale = rule.onNodeWithContentDescription("拍摄").fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action!!
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertEquals(Lifecycle.State.CREATED, rule.activity.lifecycle.currentState)
                stale()
                assertFalse("Paused stale shutter reserved a capture", model.state.value.capturing)
            }
            assertEquals(before, runBlocking { captures.records.first().map { it.id }.toSet() })
            publish("T16_PAUSED_STALE_SHUTTER", "REJECTED_NO_RESERVATION")
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitReady()
            zoomLabel?.let { rule.onNodeWithTag("zoom-open").assertTextContains(it, substring = true) }
            val previous = rule.activity
            rule.activityRule.scenario.recreate()
            assertNotSame(previous, rule.activity)
            waitReady()
            zoomLabel?.let { rule.onNodeWithTag("zoom-open").assertTextContains(it, substring = true) }
            rule.onNodeWithText("查看参考图").assertExists()
            assertEquals(before, runBlocking { captures.records.first().map { it.id }.toSet() })
            rule.onNodeWithContentDescription("拍摄").performClick()
            rule.waitUntil(25_000) { !model.state.value.capturing && model.state.value.records.any {
                it.id !in before && it.fileState == CaptureFileState.AVAILABLE
            } }
            val owned = model.state.value.records.single { it.id !in before }
            assertEquals(reference.projectId, owned.projectId)
            assertEquals(reference.photo.id, owned.referenceId)
            publish("T16_OWNED_CAPTURE_ID", owned.id)
            assertArrayEquals(original, File(app.filesDir, "references/${reference.imageFileName}").readBytes())
            publish("T16_TRUE_RECREATE", "PASS_FOCUSED_PRODUCTION_SCREEN_ACTIVITY_SAVED_STATE")
        } finally {
            app.unregisterActivityLifecycleCallbacks(callback)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.runOnUiThread { rule.activity.setContent {} }
            if (modelInstalled) rule.waitUntil(25_000) { !model.state.value.capturing }
            runBlocking {
                baseline?.let { before ->
                    val owned = captures.records.first().filter { it.id !in before }
                    require(owned.size <= 1 && owned.all { it.projectId == reference.projectId }) {
                        "T16_CAPTURE_CLEANUP_IDENTITY_AMBIGUOUS"
                    }
                    publish("T16_CREATED_CAPTURE_IDS", owned.map { it.id }.toString())
                    owned.forEach { captures.delete(it.id) }
                }
                assertTrue(refs.deleteProject(reference.projectId))
            }
        }
    }

    @Test fun acceptedDriverCaptureSettlesOnceAcrossActualBackgroundResume() {
        lateinit var view: PreviewView
        lateinit var manager: CameraXManager
        val initialized = AtomicReference<Boolean?>(null)
        rule.runOnUiThread { view = PreviewView(rule.activity); manager = CameraXManager(rule.activity) }
        rule.runOnUiThread { rule.activity.setContent { AndroidView(factory = { view }) } }
        rule.runOnUiThread { manager.initialize(onReady = {
            val lens = resolveCameraLens(CameraLens.BACK, manager.availableLenses())
            initialized.set(lens != null && manager.bindLens(rule.activity, view, lens))
            manager.setAnalyzer { it.close() }
        }, onError = { initialized.set(false) }) }
        rule.waitUntil(20_000) { initialized.get() != null }
        assertEquals(true, initialized.get())
        rule.waitUntil(25_000) { manager.isCameraOpen() && view.previewStreamState.value == PreviewView.StreamState.STREAMING }
        val output = File(rule.activity.cacheDir, "t16-owned-${UUID.randomUUID()}.jpg")
        val count = AtomicInteger()
        val outcome = AtomicReference<Boolean?>(null)
        try {
            rule.runOnUiThread { manager.takePicture(output,
                { count.incrementAndGet(); outcome.set(true) }, { count.incrementAndGet(); outcome.set(false) }) }
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.waitUntil(25_000) { count.get() > 0 }
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.waitUntil(25_000) { manager.isCameraOpen() && view.previewStreamState.value == PreviewView.StreamState.STREAMING }
            assertEquals(1, count.get())
            if (outcome.get() == true) assertTrue(output.isFile && output.length() > 0)
            publish("T16_BACKGROUND_CAPTURE_SETTLEMENT", if (outcome.get() == true) "SAVED_CALLBACK" else "ERROR_CALLBACK")
        } finally {
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.runOnUiThread { manager.shutdown(); rule.activity.setContent {} }
            if (count.get() == 1 && output.exists()) assertTrue(output.delete())
        }
    }

    private fun waitReady() {
        rule.waitUntil(25_000) {
            rule.onAllNodesWithContentDescription("拍摄").fetchSemanticsNodes().any { isEnabled().matches(it) }
        }
        var streaming = false
        rule.waitUntil(25_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                streaming = previewViews(rule.activity.window.decorView).any {
                    it.previewStreamState.value == PreviewView.StreamState.STREAMING
                }
            }
            streaming
        }
    }
    private fun previewViews(view: View): List<PreviewView> = when (view) {
        is PreviewView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { previewViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun publish(key: String, value: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString(key, value) })
    }
}
