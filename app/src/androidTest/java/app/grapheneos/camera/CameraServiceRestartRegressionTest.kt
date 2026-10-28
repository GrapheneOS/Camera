package app.grapheneos.camera

import android.Manifest
import android.net.Uri
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * After the camera service died and restarted, the camera stayed dead until the activity was
 * paused. Killing the service takes root, so these are skipped without it.
 */
@RunWith(AndroidJUnit4::class)
class CameraServiceRestartRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @Before
    fun requireRoot() {
        assumeTrue("killing the camera service takes root", shell("id -u").trim() == "0")
    }

    @Test
    fun preview_afterTheCameraServiceRestarts_streamsAgain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStreaming(scenario)

            killCameraService(scenario)

            awaitStreaming(scenario, RECOVERY_TIMEOUT_MS)
        }
    }

    @Test
    fun shutter_pressedWhileTheCameraServiceIsGone_takesAPhotoOnceItIsBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitStreaming(scenario)
            val capturedBefore = lastCapturedUri(scenario)
            try {
                killCameraService(scenario)
                scenario.onActivity { it.captureButton.performClick() }

                awaitStreaming(scenario, RECOVERY_TIMEOUT_MS)
                waitUntil(scenario, "the shutter can be pressed again") {
                    it.captureButton.isEnabled
                }
                scenario.onActivity { it.captureButton.performClick() }
                waitUntil(scenario, "the photo is saved") {
                    it.camConfig.lastCapturedItem?.uri != capturedBefore
                }
            } finally {
                runCatching { deleteNewCapture(scenario, capturedBefore) }
            }
        }
    }

    @Test
    fun recording_whenTheCameraServiceDies_stopsAndIsSaved() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            awaitStreaming(scenario)
            scenario.onActivity { it.camConfig.mPlayer = ImmediateTunePlayer(it) }
            val capturedBefore = lastCapturedUri(scenario)
            try {
                scenario.onActivity { it.videoCapturer.startRecording() }
                recordASecond(scenario)

                killCameraService(scenario)

                waitUntil(scenario, "the recording stops") { !it.videoCapturer.isRecording }
                awaitOutputSettled(scenario)
                waitUntil(scenario, "the stop is explained") {
                    snackbarText(it) == it.getString(R.string.recording_stopped_camera_lost)
                }
                assertNotEquals(
                    "What was recorded was not saved",
                    capturedBefore,
                    lastCapturedUri(scenario)
                )

                awaitStreaming(scenario, RECOVERY_TIMEOUT_MS)
            } finally {
                stopAndDeleteRecording(scenario, capturedBefore)
            }
        }
    }

    @Test
    fun recording_whenTheCameraServiceDiesBeforeItStarts_isSaidNotToHaveStarted() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            awaitStreaming(scenario)
            scenario.onActivity { it.camConfig.mPlayer = HeldTunePlayer(it) }
            val capturedBefore = lastCapturedUri(scenario)
            val pendingBefore = pendingVideoCount()
            try {
                scenario.onActivity { it.videoCapturer.startRecording() }
                awaitOutputSettled(scenario)
                scenario.onActivity { assertTrue(it.videoCapturer.isStartPending) }

                killCameraService(scenario)

                waitUntil(scenario, "the start is called off") { !it.videoCapturer.isRecording }
                awaitOutputSettled(scenario)
                waitUntil(scenario, "the camera's loss is explained") {
                    snackbarText(it) == it.getString(R.string.recording_not_started_camera_lost)
                }
                assertEquals(capturedBefore, lastCapturedUri(scenario))
                assertEquals("The unused output was kept", pendingBefore, pendingVideoCount())

                awaitStreaming(scenario, RECOVERY_TIMEOUT_MS)
            } finally {
                stopAndDeleteRecording(scenario, capturedBefore)
            }
        }
    }

    /** Holds a recording's start on a sound that never ends. */
    private class HeldTunePlayer(activity: MainActivity) : TunePlayer(activity) {
        override fun playVRStartSound(handler: Handler, onPlayed: Runnable) {}
    }

    private fun <A : MainActivity> awaitStreaming(
        scenario: ActivityScenario<A>,
        timeoutMs: Long = 10_000,
    ) {
        waitUntil(scenario, "the preview streams", timeoutMs) {
            it.previewView.previewStreamState.value == PreviewView.StreamState.STREAMING
        }
    }

    private fun <A : MainActivity> killCameraService(scenario: ActivityScenario<A>) {
        val pid = shell("pidof cameraserver").trim()
        check(pid.isNotEmpty()) { "the camera service isn't running" }
        shell("kill -9 $pid")
        waitUntil(scenario, "the preview stops") {
            it.previewView.previewStreamState.value != PreviewView.StreamState.STREAMING
        }
    }

    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { String(it.readBytes()) }
    }

    private fun snackbarText(activity: MainActivity) =
        activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.text?.toString()

    /** Keeps test runs from accumulating images on the device. */
    private fun deleteNewCapture(scenario: ActivityScenario<MainActivity>, previous: Uri?) {
        scenario.onActivity { activity ->
            val item = activity.camConfig.lastCapturedItem ?: return@onActivity
            if (item.uri != previous) {
                activity.contentResolver.delete(item.uri, null, null)
            }
        }
    }

    private companion object {
        const val RECOVERY_TIMEOUT_MS = 20_000L
    }
}
