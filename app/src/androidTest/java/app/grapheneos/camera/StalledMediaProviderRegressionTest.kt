package app.grapheneos.camera

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.media.repository.CaptureOutputRepositoryImpl
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorder
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorderImpl
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutputImpl
import app.grapheneos.camera.domain.capture.usecase.DiscardRecordingImpl
import app.grapheneos.camera.domain.capture.usecase.PublishRecordingImpl
import app.grapheneos.camera.domain.capture.usecase.ResolveCaptureLocation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StalledMediaProviderRegressionTest {

    /**
     * Stands in for a MediaProvider that stops answering, and passes everything to the real one.
     * A stall holds whichever thread made the call.
     */
    private class StallingMediaProvider(
        private val real: ContentResolver,
    ) : ContentProvider() {
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
        private val outputs = CopyOnWriteArrayList<Uri>()

        /** Answers every update with no row updated, which is how a failed one answers. */
        @Volatile
        var refuseUpdates = false

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            insertStall.hold()
            return real.insert(uri, values)?.also { outputs.add(it) }
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

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mediaProvider = StallingMediaProvider(targetContext.contentResolver)

    private val session = mockk<VideoRecordingSession>(relaxed = true)
    private val onRecordingEvent = slot<(RecordingEvent) -> Unit>()
    private val storage = Executors.newCachedThreadPool()
    private val events = CopyOnWriteArrayList<RecordedVideoEvent>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var isStillWanted = true

    private lateinit var recorder: VideoRecorder

    @Before
    fun createRecorder() {
        every { session.start(any(), capture(onRecordingEvent)) } returns true

        val repository = CaptureOutputRepositoryImpl(
            contentResolver = ContentResolver.wrap(mediaProvider),
            ioDispatcher = storage.asCoroutineDispatcher(),
        )
        val resolveCaptureLocation = mockk<ResolveCaptureLocation>()
        coEvery { resolveCaptureLocation(includeLocation = any()) } returns
            CaptureLocation.NotRequested

        recorder = VideoRecorderImpl(
            recordingSession = session,
            createRecordingOutput = CreateRecordingOutputImpl(repository),
            publishRecording = PublishRecordingImpl(repository),
            discardRecording = DiscardRecordingImpl(repository),
            resolveCaptureLocation = resolveCaptureLocation,
            playCameraSound = mockk(relaxed = true),
            capturedItemRepository = mockk<CapturedItemRepository>(relaxed = true),
            applicationScope = scope,
            mainDispatcher = Dispatchers.Main.immediate,
        )

        onMainThread { scope.launch { recorder.events.collect { events += it } } }
    }

    @After
    fun cleanUp() {
        // Before anything waits on the main thread, which the stall may be holding
        mediaProvider.recover()
        mainThreadIsFree()
        scope.cancel()
        storage.shutdown()
        mediaProvider.deleteOutputs()
    }

    @Test
    fun startingARecording_whileMediaProviderStalls_leavesTheMainThreadFree() {
        prepare()
        assertTrue(
            "The recording never asked for its output",
            mediaProvider.insertStall.awaitCall(),
        )
        assertTrue(
            "Creating its output held the main thread while MediaProvider was stalled",
            mainThreadIsFree(),
        )

        mediaProvider.insertStall.answer()
        assertTrue("The recording never opened its output", mediaProvider.openStall.awaitCall())
        assertTrue(
            "Opening its output held the main thread while MediaProvider was stalled",
            mainThreadIsFree(),
        )

        // The output made off the main thread still has to take the recording
        mediaProvider.recover()
        awaitEvent("the recording is ready to start") { it == RecordedVideoEvent.ReadyToStart }
    }

    /**
     * With the main thread free, the record button can be pressed again before MediaProvider
     * answers. That press stops the start at once, and the output it was waiting on has to go.
     */
    @Test
    fun stoppingARecording_whileMediaProviderStalls_abandonsItAndDiscardsItsOutput() {
        val pendingBefore = pendingVideoCount()

        prepare()
        assertTrue(
            "The recording never asked for its output",
            mediaProvider.insertStall.awaitCall(),
        )

        onMainThread { recorder.stop() }
        awaitEvent("the stop takes") { it == RecordedVideoEvent.Abandoned }

        mediaProvider.recover()
        awaitCondition("the abandoned output is deleted") { pendingVideoCount() == pendingBefore }
        assertFalse("A recording started", RecordedVideoEvent.ReadyToStart in events)
    }

    /** Leaving the camera stops a recording, including one still waiting on its output. */
    @Test
    fun leavingTheCamera_whileMediaProviderStalls_discardsTheOutput() {
        val pendingBefore = pendingVideoCount()

        prepare()
        assertTrue(
            "The recording never asked for its output",
            mediaProvider.insertStall.awaitCall(),
        )

        isStillWanted = false
        mediaProvider.recover()

        awaitCondition("the abandoned output is deleted") { pendingVideoCount() == pendingBefore }
        assertFalse("A recording started", RecordedVideoEvent.ReadyToStart in events)
    }

    @Test
    fun savingARecording_whileMediaProviderStalls_leavesTheMainThreadFree() {
        finishARecording()

        assertTrue("The recording was never saved", mediaProvider.updateStall.awaitCall())
        assertTrue(
            "Saving the recording held the main thread while MediaProvider was stalled",
            mainThreadIsFree(),
        )
        assertFalse(
            "The recording was published before it was saved",
            events.any { it is RecordedVideoEvent.Saved },
        )

        mediaProvider.updateStall.answer()
        awaitEvent("the saved recording is published") { it is RecordedVideoEvent.Saved }
    }

    /** A recording MediaProvider never made visible is not saved, so it must not show as captured. */
    @Test
    fun savingARecording_thatMediaProviderRefuses_doesNotPublishIt() {
        finishARecording()

        assertTrue("The recording was never saved", mediaProvider.updateStall.awaitCall())
        mediaProvider.refuseUpdates = true
        mediaProvider.updateStall.answer()

        awaitEvent("the failed save is reported") { it == RecordedVideoEvent.SaveFailed }
        mainThreadIsFree()
        assertFalse(
            "The unsaved recording was published",
            events.any { it is RecordedVideoEvent.Saved },
        )
    }

    private fun prepare() {
        onMainThread {
            scope.launch {
                recorder.prepare(request = REQUEST, isStillWanted = { isStillWanted })
            }
        }
    }

    private fun finishARecording() {
        mediaProvider.answerOutputCreation()
        prepare()
        awaitEvent("the recording is ready to start") { it == RecordedVideoEvent.ReadyToStart }

        onMainThread {
            recorder.start(muted = false, paused = false)
            onRecordingEvent.captured(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))
        }
    }

    private fun awaitEvent(description: String, matches: (RecordedVideoEvent) -> Boolean) {
        awaitCondition(description) { events.any(matches) }
    }

    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_S)
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= deadline) {
                throw AssertionError("Timed out waiting until $description")
            }
            Thread.sleep(50)
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

    private companion object {
        // Twice the input dispatch timeout
        const val TIMEOUT_S = 10L

        // The system reports an ANR once a key has waited this long for the main thread
        const val INPUT_DISPATCH_TIMEOUT_S = 5L

        val REQUEST = RecordVideoRequest(
            storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
            foreignUri = null,
            includeLocation = false,
            includeAudio = false,
        )
    }
}
