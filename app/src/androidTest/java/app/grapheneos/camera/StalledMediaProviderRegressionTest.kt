package app.grapheneos.camera

import android.Manifest
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.VideoCaptureActivity
import app.grapheneos.camera.ui.activities.VideoOnlyActivity
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StalledMediaProviderRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    /** Recording only starts once the preview window is visible and producing frames. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /**
     * Stands in for a MediaProvider that stops answering, and passes everything to the real one.
     * Creating the output, opening it and making the finished video visible are separate
     * synchronous binder transactions into it, so a stall in any of them holds whichever thread
     * made the call.
     */
    private class StallingMediaProvider(private val real: ContentResolver) : ContentProvider() {
        /** Holds each call until [answer]; from then on, calls go straight through. */
        class Stall {
            private val callMade = CountDownLatch(1)
            private val answered = CountDownLatch(1)

            fun hold() {
                callMade.countDown()
                answered.await()
            }

            fun awaitCall() = callMade.await(TIMEOUT_S, TimeUnit.SECONDS)

            fun answer() = answered.countDown()
        }

        val insertStall = Stall()
        val openStall = Stall()
        val updateStall = Stall()
        private val inserted = CountDownLatch(1)
        private val outputs = CopyOnWriteArrayList<Uri>()

        /** Answers every update with no row updated, which is how a failed one answers. */
        @Volatile
        var refuseUpdates = false

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            insertStall.hold()
            return real.insert(uri, values)?.also {
                outputs.add(it)
                inserted.countDown()
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            openStall.hold()
            return real.openFileDescriptor(uri, mode)
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) =
            real.delete(uri, selection, selectionArgs)

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?,
        ): Int {
            updateStall.hold()
            return if (refuseUpdates) 0 else real.update(uri, values, selection, selectionArgs)
        }

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?,
        ): Cursor? = real.query(uri, projection, selection, selectionArgs, sortOrder)

        override fun getType(uri: Uri) = real.getType(uri)

        fun awaitInsert() = inserted.await(TIMEOUT_S, TimeUnit.SECONDS)

        /** Lets recordings start, for tests of what comes after. */
        fun answerOutputCreation() {
            insertStall.answer()
            openStall.answer()
        }

        /** Answers every call, from now on. */
        fun recover() {
            answerOutputCreation()
            updateStall.answer()
            refuseUpdates = false
        }

        /** Deletes what is left of every output created, which a refused update leaves pending. */
        fun deleteOutputs() {
            outputs.forEach { runCatching { real.delete(it, null, null) } }
        }
    }

    /**
     * The output for a recording was created and opened on the main thread, so a MediaProvider
     * that took seconds to answer froze the camera from the press of the record button, and a key
     * pressed meanwhile was reported as an ANR.
     */
    @Test
    fun startingARecording_whileMediaProviderStalls_leavesTheMainThreadFree() {
        stalledRecordingTest { scenario, camera, mediaProvider ->
            onMainThread { camera.videoCapturer.startRecording() }
            assertTrue(
                "The recording never asked for its output",
                mediaProvider.insertStall.awaitCall()
            )
            assertTrue(
                "Starting a recording held the main thread while MediaProvider was stalled",
                mainThreadIsFree()
            )

            mediaProvider.insertStall.answer()
            assertTrue("The recording never opened its output", mediaProvider.openStall.awaitCall())
            assertTrue(
                "Opening its output held the main thread while MediaProvider was stalled",
                mainThreadIsFree()
            )

            // The output made off the main thread still has to take the recording
            val capturedBefore = lastCapturedUri(scenario)
            mediaProvider.recover()
            recordASecond(scenario)
            scenario.onActivity { it.videoCapturer.stopRecording() }
            waitUntil(scenario, "recording is finalized") { !it.videoCapturer.isRecording }
            awaitOutputSettled(scenario)
            assertNotEquals(
                "The recording was not saved",
                capturedBefore,
                lastCapturedUri(scenario)
            )
        }
    }

    /**
     * With the main thread free, the record button can be pressed again before MediaProvider
     * answers. That press stops the start, whose output then has to go, and another press must
     * still record.
     */
    @Test
    fun stoppingTheRecording_whileMediaProviderStalls_discardsItsOutput() {
        stalledRecordingTest { scenario, camera, mediaProvider ->
            val pendingBefore = pendingVideoCount()

            onMainThread { camera.videoCapturer.startRecording() }
            assertTrue(
                "The recording never asked for its output",
                mediaProvider.insertStall.awaitCall()
            )
            assertTrue(
                "Starting a recording held the main thread while MediaProvider was stalled",
                mainThreadIsFree()
            )

            scenario.onActivity {
                it.videoCapturer.stopRecording()
                assertFalse("The stop did not take", it.videoCapturer.isRecording)
                it.videoCapturer.startRecording()
            }

            mediaProvider.recover()
            awaitRecordingStarted(scenario)
            // The running recording holds the one pending entry that should be left
            waitUntil(scenario, "the stopped recording's output is deleted") {
                pendingVideoCount() == pendingBefore + 1
            }
        }
    }

    /** Leaving the camera stops a recording, including one still waiting on its output. */
    @Test
    fun leavingTheCamera_whileMediaProviderStalls_discardsTheOutput() {
        stalledRecordingTest { scenario, camera, mediaProvider ->
            val pendingBefore = pendingVideoCount()

            onMainThread { camera.videoCapturer.startRecording() }
            assertTrue(
                "The recording never asked for its output",
                mediaProvider.insertStall.awaitCall()
            )
            assertTrue(
                "Starting a recording held the main thread while MediaProvider was stalled",
                mainThreadIsFree()
            )

            scenario.moveToState(Lifecycle.State.CREATED)

            mediaProvider.recover()
            assertTrue("The output was never created", mediaProvider.awaitInsert())
            waitUntil(scenario, "the abandoned output is deleted") {
                pendingVideoCount() == pendingBefore
            }
            scenario.onActivity {
                assertFalse("A recording started", it.videoCapturer.isRecording)
            }

            // Closing from a stopped state waits out the whole activity lifecycle timeout: the
            // empty activity the framework stopped us with is already resumed, so the resume it
            // blocks on before finishing us never arrives.
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    /**
     * A finished recording only becomes visible to other apps with one more call into
     * MediaProvider, which was made on the main thread, so a stall froze the camera from the press
     * of the stop button and a key pressed meanwhile was reported as an ANR.
     */
    @Test
    fun stoppingARecording_whileMediaProviderStalls_leavesTheMainThreadFree() {
        stalledRecordingTest { scenario, camera, mediaProvider ->
            val capturedBefore = lastCapturedUri(scenario)
            mediaProvider.answerOutputCreation()
            scenario.onActivity { it.videoCapturer.startRecording() }
            recordASecond(scenario)

            onMainThread { camera.videoCapturer.stopRecording() }
            assertTrue("The recording was never saved", mediaProvider.updateStall.awaitCall())
            assertTrue(
                "Saving the recording held the main thread while MediaProvider was stalled",
                mainThreadIsFree()
            )
            assertEquals(
                "The recording was published before it was saved",
                capturedBefore,
                lastCapturedUri(scenario)
            )

            mediaProvider.updateStall.answer()
            awaitOutputSettled(scenario)
            assertNotEquals(
                "The saved recording was not published",
                capturedBefore,
                lastCapturedUri(scenario)
            )
        }
    }

    /** A recording MediaProvider never made visible is not saved, so it must not show as captured. */
    @Test
    fun stoppingARecording_whenMediaProviderStallsThenRefusesTheSave_doesNotPublishIt() {
        stalledRecordingTest { scenario, camera, mediaProvider ->
            val capturedBefore = lastCapturedUri(scenario)
            mediaProvider.answerOutputCreation()
            scenario.onActivity { it.videoCapturer.startRecording() }
            recordASecond(scenario)

            onMainThread { camera.videoCapturer.stopRecording() }
            assertTrue("The recording was never saved", mediaProvider.updateStall.awaitCall())
            mediaProvider.refuseUpdates = true
            mediaProvider.updateStall.answer()
            awaitOutputSettled(scenario)

            waitUntil(scenario, "the failed save is reported") {
                it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    ?.text?.toString() == it.getString(R.string.unable_to_save_video)
            }
            assertEquals(
                "The unsaved recording was published",
                capturedBefore,
                lastCapturedUri(scenario)
            )
        }
    }

    /**
     * A capture session shows the recording it saved, and stops the camera to do so, so a recording
     * started while MediaProvider stalled the save of the one before was cut short when that save
     * went through.
     */
    @Test
    fun recordingAgainInACaptureSession_whileMediaProviderStallsTheLastSave_isNotCutShort() {
        val intent = Intent(targetContext, VideoCaptureActivity::class.java)
            .setAction(MediaStore.ACTION_VIDEO_CAPTURE)
        val launch = { ActivityScenario.launch<VideoCaptureActivity>(intent) }

        stalledRecordingTest(launch) { scenario, camera, mediaProvider ->
            mediaProvider.answerOutputCreation()
            scenario.onActivity { it.videoCapturer.startRecording() }
            recordASecond(scenario)

            onMainThread { camera.videoCapturer.stopRecording() }
            assertTrue("The recording was never saved", mediaProvider.updateStall.awaitCall())
            onMainThread { camera.videoCapturer.startRecording() }
            mediaProvider.updateStall.answer()
            awaitOutputSettled(scenario)

            var previewShown = true
            scenario.onActivity { previewShown = it.confirmButton.visibility == View.VISIBLE }
            assertFalse("The saved recording was shown over the new one", previewShown)
            recordASecond(scenario)
            var recording = false
            scenario.onActivity { recording = it.videoCapturer.isRecording }
            assertTrue("The new recording was cut short", recording)
        }
    }

    private fun stalledRecordingTest(
        body: (
            ActivityScenario<VideoOnlyActivity>,
            VideoOnlyActivity,
            StallingMediaProvider,
        ) -> Unit,
    ) = stalledRecordingTest({ ActivityScenario.launch(VideoOnlyActivity::class.java) }, body)

    /**
     * Hands [body] a camera that records through a [StallingMediaProvider], and cleans up after it
     * either way.
     */
    private fun <A : MainActivity> stalledRecordingTest(
        launch: () -> ActivityScenario<A>,
        body: (ActivityScenario<A>, A, StallingMediaProvider) -> Unit,
    ) {
        val mediaProvider = StallingMediaProvider(targetContext.contentResolver)
        launch().use { scenario ->
            waitUntil(scenario, "video use case is bound") {
                it.camConfig.session.camera != null && it.camConfig.session.videoCapture != null
            }
            lateinit var camera: A
            scenario.onActivity {
                camera = it
                it.camConfig.mPlayer = ImmediateTunePlayer(it)
                it.videoCapturer.contentResolver = ContentResolver.wrap(mediaProvider)
            }
            val capturedBefore = lastCapturedUri(scenario)
            try {
                body(scenario, camera, mediaProvider)
            } finally {
                // Before anything waits on the main thread, which the stall may be holding
                mediaProvider.recover()
                stopAndDeleteRecording(scenario, capturedBefore)
                mediaProvider.deleteOutputs()
            }
        }
    }

    private fun <A : MainActivity> recordASecond(scenario: ActivityScenario<A>) {
        waitUntil(scenario, "a second of video is recorded") {
            it.timerView.visibility == View.VISIBLE &&
                    it.timerView.text != it.getString(R.string.start_value_timer)
        }
    }

    /** Posts rather than runs: the test thread must not wait on a main thread a stall is holding. */
    private fun onMainThread(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private fun mainThreadIsFree(): Boolean {
        val ran = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { ran.countDown() }
        return ran.await(INPUT_DISPATCH_TIMEOUT_S, TimeUnit.SECONDS)
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        // Twice the input dispatch timeout
        const val TIMEOUT_S = 10L

        // The system reports an ANR once a key has waited this long for the main thread
        const val INPUT_DISPATCH_TIMEOUT_S = 5L
    }
}
