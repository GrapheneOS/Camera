package app.grapheneos.camera

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.util.StateSet
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.capturer.deleteStalePendingRecordings
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class VideoCapturerRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    /** Recording only starts once the preview window is visible and producing frames. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /** Fires the callback twice, like a MediaPlayer error followed by normal completion. */
    private class DoubleFiringTunePlayer(activity: MainActivity) : TunePlayer(activity) {
        override fun playVRStartSound(handler: Handler, onPlayed: Runnable) {
            onPlayed.run()
            onPlayed.run()
        }
    }

    /** Runs the callback synchronously, skipping the sound. */
    private class ImmediateTunePlayer(activity: MainActivity) : TunePlayer(activity) {
        override fun playVRStartSound(handler: Handler, onPlayed: Runnable) {
            onPlayed.run()
        }
    }

    /** Holds the callback until the test releases it. */
    private class ManualTunePlayer(activity: MainActivity) : TunePlayer(activity) {
        var deferred: Runnable? = null
        override fun playVRStartSound(handler: Handler, onPlayed: Runnable) {
            deferred = onPlayed
        }
    }

    /**
     * Regression test for the production crash: a duplicated start-sound callback must not
     * reach PendingRecording.start twice ("A recording is already in progress").
     */
    @Test
    fun startRecording_toleratesDuplicateStartSoundCallback() {
        recordingTest { scenario ->
            scenario.onActivity { activity ->
                activity.camConfig.mPlayer = DoubleFiringTunePlayer(activity)
                activity.videoCapturer.startRecording()
            }

            waitUntil(scenario, "recording is running") { it.videoCapturer.isRecording }

            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
        }
    }

    /**
     * Stopping while the start is still queued behind the sound must abandon it: no recording,
     * no leftover output entry, and the recorder stays usable.
     */
    @Test
    fun stopRecording_duringDeferredStart_cancelsTheStart() {
        recordingTest { scenario ->
            val pendingBefore = pendingVideoCount()

            lateinit var player: ManualTunePlayer
            scenario.onActivity { activity ->
                player = ManualTunePlayer(activity)
                activity.camConfig.mPlayer = player
                activity.videoCapturer.startRecording()
                assertTrue(activity.videoCapturer.isRecording)
            }

            scenario.onActivity { activity ->
                activity.videoCapturer.stopRecording()
                player.deferred!!.run()
                assertFalse(activity.videoCapturer.isRecording)
            }

            assertEquals(pendingBefore, pendingVideoCount())

            // A fresh recording must still work after the abandoned one.
            scenario.onActivity { activity ->
                activity.camConfig.mPlayer = ImmediateTunePlayer(activity)
                activity.videoCapturer.startRecording()
            }
            waitUntil(scenario, "recording is running") { it.videoCapturer.isRecording }
            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
        }
    }

    /** A pause requested while the start is queued must apply once the recording starts. */
    @Test
    fun pauseDuringDeferredStart_appliesWhenRecordingStarts() {
        recordingTest { scenario ->
            lateinit var player: ManualTunePlayer
            scenario.onActivity { activity ->
                player = ManualTunePlayer(activity)
                activity.camConfig.mPlayer = player
                activity.videoCapturer.startRecording()
                activity.videoCapturer.isPaused = true
                player.deferred!!.run()
            }

            // A fixed dwell, not a waitUntil: the assertion below is that the timer does *not*
            // tick, and an absence cannot be polled for. Three seconds spans several tick periods.
            Thread.sleep(3000)

            scenario.onActivity { activity ->
                assertTrue(activity.videoCapturer.isRecording)
                assertEquals(
                    activity.getString(R.string.start_value_timer),
                    activity.timerView.text.toString(),
                )
                activity.videoCapturer.isPaused = false
                activity.videoCapturer.stopRecording()
            }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
        }
    }

    /**
     * A recording that finalizes without usable output must not leave an orphaned pending
     * entry. Paused from the start, it produces no frames, so ERROR_NO_VALID_DATA is
     * deterministic.
     */
    @Test
    fun failedRecording_doesNotLeaveOrphanedOutputEntry() {
        recordingTest { scenario ->
            val pendingBefore = pendingVideoCount()

            lateinit var player: ManualTunePlayer
            scenario.onActivity { activity ->
                player = ManualTunePlayer(activity)
                activity.camConfig.mPlayer = player
                activity.videoCapturer.startRecording()
                activity.videoCapturer.isPaused = true
                player.deferred!!.run()
            }
            // Let the recorder reach paused-recording, so that stopping it finalizes with
            // ERROR_NO_VALID_DATA rather than racing the start
            Thread.sleep(300)
            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }

            assertEquals(pendingBefore, pendingVideoCount())
        }
    }

    /**
     * Regression test for the production crash: skinned devices wrap the capture button shape
     * in a selector or layer-list, and the corner-radius animation cast the wrapper straight
     * to GradientDrawable (ClassCastException in onRecordingStart/afterRecordingStops).
     */
    @Test
    fun recordingUiAnimations_unwrapWrappedCaptureButtonDrawable() {
        recordingTest { scenario ->
            val shape = GradientDrawable()
            var dp8 = 0f
            var dp16 = 0f
            scenario.onActivity { activity ->
                dp8 = 8 * activity.resources.displayMetrics.density
                dp16 = 16 * activity.resources.displayMetrics.density
                val selector = StateListDrawable().apply { addState(StateSet.WILD_CARD, shape) }
                activity.captureButton.setImageDrawable(LayerDrawable(arrayOf(selector)))
                activity.camConfig.mPlayer = ImmediateTunePlayer(activity)
                activity.videoCapturer.startRecording()
            }

            // The animation reaching the nested shape proves the unwrapping worked.
            waitUntil(scenario, "corner radius animated to the recording shape") {
                abs(shape.cornerRadius - dp8) < 0.5f
            }

            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
            waitUntil(scenario, "corner radius animated back") {
                abs(shape.cornerRadius - dp16) < 0.5f
            }
        }
    }

    /** A drawable with no shape inside must skip the animation, not crash the recording. */
    @Test
    fun recordingUi_toleratesUnknownCaptureButtonDrawable() {
        recordingTest { scenario ->
            scenario.onActivity { activity ->
                activity.captureButton.setImageDrawable(ColorDrawable(Color.RED))
                activity.camConfig.mPlayer = ImmediateTunePlayer(activity)
                activity.videoCapturer.startRecording()
            }
            waitUntil(scenario, "recording UI is shown") {
                it.timerView.visibility == View.VISIBLE
            }

            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
        }
    }

    /**
     * A recording killed with the process leaves its output entry pending forever, holding the name
     * and the space. Nothing used to clean those up.
     */
    @Test
    fun stalePendingRecordings_areReaped() {
        val uri = insertPendingRecording()
        try {
            // A cutoff in the future stands in for an entry old enough to be an orphan
            deleteStalePendingRecordings(targetContext, maxAge = -2000L)
            assertFalse(pendingRecordingExists(uri))
        } finally {
            deletePendingRecording(uri)
        }
    }

    /** An in-flight recording is pending too, and reaping one would throw away the capture. */
    @Test
    fun freshPendingRecordings_surviveTheReaper() {
        val uri = insertPendingRecording()
        try {
            deleteStalePendingRecordings(targetContext)
            assertTrue(pendingRecordingExists(uri))
        } finally {
            deletePendingRecording(uri)
        }
    }

    /**
     * A fling used to rebind the camera out from under a running recorder. VIDEO is the last mode
     * in the strip, so the reachable direction out of it is the one onSwipeRight() serves; that is
     * where the guard has to hold.
     */
    @Test
    fun flingWhileRecording_leavesTheRecordingAlone() {
        // Not recordingTest(): that waits for a bound video use case at launch, and MainActivity
        // starts in whichever mode was last used -- the switch below is what binds the recorder.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitModeTabs(scenario)

            scenario.onActivity { it.camConfig.switchMode(CameraMode.VIDEO) }
            waitUntil(scenario, "video use case is bound") { it.camConfig.videoCapture != null }

            val capturedBefore = lastCapturedUri(scenario)

            try {
                scenario.onActivity { activity ->
                    activity.camConfig.mPlayer = ImmediateTunePlayer(activity)
                    activity.videoCapturer.startRecording()
                }
                waitUntil(scenario, "recording is running") { it.videoCapturer.isRecording }

                var mode: CameraMode? = null
                var stillRecording = false
                scenario.onActivity { activity ->
                    // Prove the fling reaches a mode it could switch to, so the assertions below
                    // cannot pass merely because there was nowhere to go.
                    val tabs = activity.tabLayout
                    assertNotNull(
                        "no mode to the right of ${activity.camConfig.currentMode}",
                        tabs.getTabAt(tabs.selectedTabPosition - 1)
                    )

                    flingRight(activity)
                    mode = activity.camConfig.currentMode
                    stillRecording = activity.videoCapturer.isRecording
                }

                assertEquals(CameraMode.VIDEO, mode)
                assertTrue("the recording was cut short", stillRecording)
            } finally {
                stopAndDeleteRecording(scenario, capturedBefore)
            }
        }
    }

    /**
     * Leaving a capture session while it records finalizes the recording after the preview is gone:
     * the confirm UI used to be built there and then, off a PreviewView with no bitmap left to give.
     */
    @Test
    fun leavingACaptureSessionWhileRecording_defersThePreview() {
        val intent = Intent(targetContext, VideoCaptureActivity::class.java)
            .setAction(MediaStore.ACTION_VIDEO_CAPTURE)

        recordingTest({ ActivityScenario.launch<VideoCaptureActivity>(intent) }) { scenario ->
            scenario.onActivity { activity ->
                activity.camConfig.mPlayer = ImmediateTunePlayer(activity)
                activity.videoCapturer.startRecording()
            }
            waitUntil(scenario, "recording is running") { it.videoCapturer.isRecording }

            // Stops the activity, which stops the recording; the finalize event that used to crash
            // lands afterwards, on the main thread of a stopped activity.
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }

            scenario.moveToState(Lifecycle.State.RESUMED)

            var confirmVisibility = View.GONE
            scenario.onActivity { confirmVisibility = it.confirmButton.visibility }
            assertEquals(
                "the preview the finalize deferred never arrived",
                View.VISIBLE, confirmVisibility
            )
        }
    }

    /**
     * The strip is only hidden once a recording really starts, so a tap on it while the start sound
     * still played rebound the camera and left the queued recording to start on a dead recorder.
     */
    @Test
    fun tappingAModeTabDuringTheDeferredStart_leavesTheModeAlone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }
            waitUntil(scenario, "mode tabs are built") { it.tabLayout.tabCount > 0 }

            scenario.onActivity { it.camConfig.switchMode(CameraMode.VIDEO) }
            waitUntil(scenario, "video use case is bound") { it.camConfig.videoCapture != null }

            lateinit var player: ManualTunePlayer
            var mode: CameraMode? = null
            var highlighted: CameraMode? = null
            val capturedBefore = lastCapturedUri(scenario)

            try {
                scenario.onActivity { activity ->
                    player = ManualTunePlayer(activity)
                    activity.camConfig.mPlayer = player
                    activity.videoCapturer.startRecording()
                    assertTrue(activity.videoCapturer.isRecording)

                    val cameraTab = activity.tabLayout.getTabForMode(CameraMode.CAMERA)
                    assertNotNull("no CAMERA tab to tap", cameraTab)

                    // What both of the strip's touch listeners do with a tap
                    activity.finalizeMode(cameraTab)

                    mode = activity.camConfig.currentMode
                    highlighted = activity.tabLayout.selectedTab?.tag as CameraMode?
                }

                // Abandon the queued start rather than record for real: the damage is done or not
                // by now, and a cancelled start leaves nothing behind to clean up.
                scenario.onActivity { activity ->
                    activity.videoCapturer.stopRecording()
                    player.deferred!!.run()
                    assertFalse(activity.videoCapturer.isRecording)
                }

                assertEquals(CameraMode.VIDEO, mode)
                assertEquals(CameraMode.VIDEO, highlighted)
            } finally {
                stopAndDeleteRecording(scenario, capturedBefore)
            }
        }
    }

    private fun recordingTest(body: (ActivityScenario<VideoOnlyActivity>) -> Unit) {
        recordingTest({ ActivityScenario.launch(VideoOnlyActivity::class.java) }, body)
    }

    /**
     * Hands [body] an activity whose recorder is ready, and cleans up after it either way: a failed
     * assertion used to leave the recording it had started behind on the device.
     */
    private fun <A : MainActivity> recordingTest(
        launch: () -> ActivityScenario<A>,
        body: (ActivityScenario<A>) -> Unit,
    ) {
        launch().use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            val capturedBefore = lastCapturedUri(scenario)
            try {
                body(scenario)
            } finally {
                stopAndDeleteRecording(scenario, capturedBefore)
            }
        }
    }

    /**
     * Every step is best-effort: this also runs after a failed assertion, where the activity may
     * already be gone and where throwing would replace the failure the test is reporting.
     */
    private fun <A : MainActivity> stopAndDeleteRecording(
        scenario: ActivityScenario<A>,
        capturedBefore: Uri?,
    ) {
        runCatching { scenario.onActivity { it.videoCapturer.stopRecording() } }
        runCatching {
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
        }
        runCatching { deleteNewCapture(scenario, capturedBefore) }
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun insertPendingRecording(): Uri {
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, "${VIDEO_NAME_PREFIX}20260724_153012_stale.mp4")
            put(MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaColumns.IS_PENDING, 1)
        }
        return targetContext.contentResolver.insert(CamConfig.videoCollectionUri, values)!!
    }

    /** Deleting an entry the reaper already took throws, so only clean up what is still there. */
    private fun deletePendingRecording(uri: Uri) {
        if (pendingRecordingExists(uri)) {
            targetContext.contentResolver.delete(uri, null, null)
        }
    }

    private fun pendingRecordingExists(uri: Uri): Boolean {
        @Suppress("DEPRECATION")
        val pendingUri = MediaStore.setIncludePending(uri)
        return targetContext.contentResolver
            .query(pendingUri, arrayOf(MediaColumns._ID), null, null, null)
            ?.use { it.count == 1 } ?: false
    }

    /** Pending entries are how in-flight and orphaned outputs appear in MediaStore. */
    private fun pendingVideoCount(): Int {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaColumns.IS_PENDING} = 1" +
                        " AND ${MediaColumns.DISPLAY_NAME} LIKE '$VIDEO_NAME_PREFIX%'",
            )
        }
        return resolver.query(CamConfig.videoCollectionUri, arrayOf(MediaColumns._ID), args, null)
            ?.use { it.count } ?: 0
    }

    private fun <A : MainActivity> lastCapturedUri(scenario: ActivityScenario<A>): Uri? {
        var uri: Uri? = null
        scenario.onActivity { uri = it.camConfig.lastCapturedItem?.uri }
        return uri
    }

    /** Keeps test runs from accumulating videos on the device. */
    private fun <A : MainActivity> deleteNewCapture(scenario: ActivityScenario<A>, previous: Uri?) {
        scenario.onActivity { activity ->
            val item = activity.camConfig.lastCapturedItem ?: return@onActivity
            if (item.uri != previous) {
                try {
                    activity.contentResolver.delete(item.uri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
