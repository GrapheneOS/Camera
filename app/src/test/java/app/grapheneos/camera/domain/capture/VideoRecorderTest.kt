package app.grapheneos.camera.domain.capture

import android.net.Uri
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutput
import app.grapheneos.camera.domain.capture.usecase.DiscardRecording
import app.grapheneos.camera.domain.capture.usecase.PublishRecording
import app.grapheneos.camera.domain.capture.usecase.ResolveCaptureLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoRecorderTest {

    private val recordingSession = mockk<VideoRecordingSession>(relaxed = true)
    private val createRecordingOutput = mockk<CreateRecordingOutput>()
    private val publishRecording = mockk<PublishRecording>()
    private val discardRecording = mockk<DiscardRecording>(relaxed = true)
    private val resolveCaptureLocation = mockk<ResolveCaptureLocation>()

    private val output = RecordingOutput(
        uri = Uri.EMPTY,
        dateString = "20260922_120000",
        fileDescriptor = mockk(relaxed = true),
        isOwnFile = true,
        isPendingMediaStoreUri = false,
    )

    @Before
    fun setUp() {
        coEvery { createRecordingOutput(any(), any()) } returns output
        coEvery { publishRecording(any()) } returns true
        coEvery { resolveCaptureLocation(any()) } returns CaptureLocation.NotRequested
    }

    @Test
    fun prepare_withTheOutputReady_isReadyToStart() {
        runTest {
            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST, isStillWanted = { true })

            assertEquals(listOf(RecordedVideoEvent.ReadyToStart), events)
        }
    }

    @Test
    fun prepare_withoutAnOutput_saysSo() {
        runTest {
            coEvery { createRecordingOutput(any(), any()) } returns null

            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST, isStillWanted = { true })

            assertEquals(listOf(RecordedVideoEvent.OutputUnavailable), events)
        }
    }

    @Test
    fun prepare_stoppedWhileTheOutputWasCreated_throwsTheOutputAway() {
        runTest {
            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST, isStillWanted = { false })

            coVerify(exactly = 1) { discardRecording(output) }
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun prepare_withoutALocationFix_saysSoAndStillGetsReady() {
        runTest {
            coEvery { resolveCaptureLocation(true) } returns CaptureLocation.Unavailable

            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST.copy(includeLocation = true), isStillWanted = { true })

            assertEquals(
                listOf(
                    RecordedVideoEvent.LocationUnavailable,
                    RecordedVideoEvent.ReadyToStart,
                ),
                events,
            )
        }
    }

    @Test
    fun start_startsOnlyTheFirstTimeTheSoundReportsBack() {
        runTest {
            val recorder = createRecorder(scope = backgroundScope)
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.start(muted = false, paused = false)
            recorder.start(muted = false, paused = false)

            verify(exactly = 1) { recordingSession.start(any(), any()) }
        }
    }

    @Test
    fun start_appliesThePauseAndMuteSetBeforeIt() {
        runTest {
            val recorder = createRecorder(scope = backgroundScope)
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.start(muted = true, paused = true)

            verify(exactly = 1) { recordingSession.setMuted(true) }
            verify(exactly = 1) { recordingSession.setPaused(true) }
        }
    }

    @Test
    fun stop_beforeTheStartSoundEnded_throwsTheOutputAway() {
        runTest {
            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.stop()

            coVerify(exactly = 1) { discardRecording(output) }
            verify(exactly = 0) { recordingSession.stop() }
            assertTrue(RecordedVideoEvent.Abandoned in events)
        }
    }

    @Test
    fun save_handsBackTheRecordingForTheGallery() {
        runTest {
            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.save()

            val saved = events.filterIsInstance<RecordedVideoEvent.Saved>().single()
            assertEquals(output.uri, saved.item?.uri)
        }
    }

    @Test
    fun save_thatCannotPublish_saysSoButKeepsTheRecording() {
        runTest {
            coEvery { publishRecording(any()) } returns false

            val recorder = createRecorder(scope = backgroundScope)
            val events = collectEvents(recorder)
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.save()

            assertTrue(RecordedVideoEvent.SaveFailed in events)
            assertTrue(events.any { it is RecordedVideoEvent.Saved })
        }
    }

    private fun TestScope.collectEvents(recorder: VideoRecorder): List<RecordedVideoEvent> {
        val events = mutableListOf<RecordedVideoEvent>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder.events.collect { events += it }
        }

        return events
    }

    private fun createRecorder(scope: CoroutineScope): VideoRecorder {
        return VideoRecorderImpl(
            recordingSession = recordingSession,
            createRecordingOutput = createRecordingOutput,
            publishRecording = publishRecording,
            discardRecording = discardRecording,
            resolveCaptureLocation = resolveCaptureLocation,
            applicationScope = scope,
            mainDispatcher = UnconfinedTestDispatcher(),
        )
    }

    private companion object {
        val REQUEST = RecordVideoRequest(
            storageLocation = "MediaStore",
            foreignUri = null,
            includeLocation = false,
            includeAudio = false,
        )
    }
}
