package app.grapheneos.camera

import android.Manifest
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoCapturerRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

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
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            val capturedBefore = lastCapturedUri(scenario)

            scenario.onActivity { activity ->
                activity.camConfig.mPlayer = DoubleFiringTunePlayer(activity)
                activity.videoCapturer.startRecording()
            }

            waitUntil(scenario, "recording is running") { it.videoCapturer.isRecording }

            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }

            deleteNewCapture(scenario, capturedBefore)
        }
    }

    /**
     * Stopping while the start is still queued behind the sound must abandon it: no recording,
     * no leftover output entry, and the recorder stays usable.
     */
    @Test
    fun stopRecording_duringDeferredStart_cancelsTheStart() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            val capturedBefore = lastCapturedUri(scenario)
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

            deleteNewCapture(scenario, capturedBefore)
        }
    }

    /** A pause requested while the start is queued must apply once the recording starts. */
    @Test
    fun pauseDuringDeferredStart_appliesWhenRecordingStarts() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            val capturedBefore = lastCapturedUri(scenario)

            lateinit var player: ManualTunePlayer
            scenario.onActivity { activity ->
                player = ManualTunePlayer(activity)
                activity.camConfig.mPlayer = player
                activity.videoCapturer.startRecording()
                activity.videoCapturer.isPaused = true
                player.deferred!!.run()
            }

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

            deleteNewCapture(scenario, capturedBefore)
        }
    }

    /**
     * A recording that finalizes without usable output must not leave an orphaned pending
     * entry. Paused from the start, it produces no frames, so ERROR_NO_VALID_DATA is
     * deterministic.
     */
    @Test
    fun failedRecording_doesNotLeaveOrphanedOutputEntry() {
        ActivityScenario.launch(VideoOnlyActivity::class.java).use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.camera != null && it.camConfig.videoCapture != null
            }
            val capturedBefore = lastCapturedUri(scenario)
            val pendingBefore = pendingVideoCount()

            lateinit var player: ManualTunePlayer
            scenario.onActivity { activity ->
                player = ManualTunePlayer(activity)
                activity.camConfig.mPlayer = player
                activity.videoCapturer.startRecording()
                activity.videoCapturer.isPaused = true
                player.deferred!!.run()
            }
            Thread.sleep(300)
            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }

            assertEquals(pendingBefore, pendingVideoCount())

            deleteNewCapture(scenario, capturedBefore)
        }
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

    private fun <A : MainActivity> waitUntil(
        scenario: ActivityScenario<A>,
        description: String,
        timeoutMs: Long = 10_000,
        condition: (A) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var satisfied = false
            scenario.onActivity { satisfied = condition(it) }
            if (satisfied) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out after ${timeoutMs}ms waiting until $description")
    }
}
