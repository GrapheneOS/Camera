package app.grapheneos.camera.domain.capture.coordinator

import android.net.Uri
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.CaptureLocation
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutput
import app.grapheneos.camera.domain.capture.usecase.DiscardRecording
import app.grapheneos.camera.domain.capture.usecase.PlayRecordingStopSound
import app.grapheneos.camera.domain.capture.usecase.PublishRecording
import app.grapheneos.camera.domain.capture.usecase.ResolveCaptureLocation
import app.grapheneos.camera.testutil.recordingOutput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
    private val playRecordingStopSound = mockk<PlayRecordingStopSound>(relaxed = true)
    private val capturedItemRepository = mockk<CapturedItemRepository>(relaxed = true)

    private val output = recordingOutput(uri = Uri.EMPTY, dateString = "20260922_120000")

    private val nextOutput = recordingOutput(
        uri = Uri.parse("content://media/external/video/media/2"),
        dateString = "20260922_120100",
    )

    @Before
    fun setUp() {
        coEvery { createRecordingOutput(any(), any()) } returns output
        coEvery { publishRecording(any()) } returns true
        coEvery { resolveCaptureLocation(includeLocation = any()) } returns
            CaptureLocation.NotRequested
    }

    @Test
    fun prepare_withTheOutputReady_isReadyToStart() {
        runTest {
            val recorder = createRecorder()
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST, isStillWanted = { true })

            assertEquals(listOf(RecordedVideoEvent.ReadyToStart), events)
        }
    }

    @Test
    fun prepare_withoutAnOutput_saysSo() {
        runTest {
            coEvery { createRecordingOutput(any(), any()) } returns null

            val recorder = createRecorder()
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST, isStillWanted = { true })

            assertEquals(listOf(RecordedVideoEvent.OutputUnavailable), events)
        }
    }

    @Test
    fun prepare_stoppedWhileTheOutputWasCreated_throwsTheOutputAway() {
        runTest {
            val recorder = createRecorder()
            val events = collectEvents(recorder)

            recorder.prepare(REQUEST, isStillWanted = { false })

            coVerify(exactly = 1) { discardRecording(output) }
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun stop_whileTheOutputIsCreated_abandonsTheRecording() {
        runTest {
            val outputCreated = CompletableDeferred<Unit>()
            coEvery { createRecordingOutput(any(), any()) } coAnswers {
                outputCreated.await()
                output
            }

            val recorder = createRecorder()
            val events = collectEvents(recorder)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                recorder.prepare(REQUEST, isStillWanted = { true })
            }

            recorder.stop()
            outputCreated.complete(Unit)

            coVerify(exactly = 1) { discardRecording(output) }
            assertEquals(listOf(RecordedVideoEvent.Abandoned), events)
        }
    }

    @Test
    fun prepare_withoutALocationFix_saysSoAndStillGetsReady() {
        runTest {
            coEvery { resolveCaptureLocation(includeLocation = true) } returns
                CaptureLocation.Unavailable

            val recorder = createRecorder()
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
            val recorder = createRecorder()
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.start(muted = false, paused = false)
            recorder.start(muted = false, paused = false)

            verify(exactly = 1) { recordingSession.start(any(), any()) }
        }
    }

    @Test
    fun start_appliesThePauseAndMuteSetBeforeIt() {
        runTest {
            val recorder = createRecorder()
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.start(muted = true, paused = true)

            verify(exactly = 1) { recordingSession.setMuted(true) }
            verify(exactly = 1) { recordingSession.setPaused(true) }
        }
    }

    @Test
    fun stop_beforeTheStartSoundEnded_throwsTheOutputAway() {
        runTest {
            val recorder = createRecorder()
            val events = collectEvents(recorder)
            recorder.prepare(REQUEST, isStillWanted = { true })

            recorder.stop()

            coVerify(exactly = 1) { discardRecording(output) }
            verify(exactly = 0) { recordingSession.stop() }
            assertTrue(RecordedVideoEvent.Abandoned in events)
        }
    }

    @Test
    fun finalized_handsBackTheRecordingForTheGallery() {
        runTest {
            val recorder = createRecorder()
            val events = collectEvents(recorder)
            val onEvent = startRecording(recorder)

            onEvent(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))

            val saved = events.filterIsInstance<RecordedVideoEvent.Saved>().single()
            assertEquals(output.uri, saved.item?.uri)
        }
    }

    @Test
    fun finalized_storesTheRecordingAsTheLastCaptureWithNobodyListening() {
        runTest {
            val recorder = createRecorder()
            val onEvent = startRecording(recorder)

            onEvent(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))

            val stored = slot<CapturedItem>()
            coVerify(exactly = 1) { capturedItemRepository.saveLastCapturedItem(capture(stored)) }
            assertEquals(output.uri, stored.captured.uri)
        }
    }

    @Test
    fun finalized_playsTheStopSoundWithNobodyListening() {
        runTest {
            val recorder = createRecorder()
            val onEvent = startRecording(recorder)

            onEvent(RecordingEvent.Finalized(outcome = RecordingOutcome.NothingPlayableWritten))

            coVerify(exactly = 1) { playRecordingStopSound() }
        }
    }

    @Test
    fun abandoned_playsNoStopSound() {
        runTest {
            val recorder = createRecorder()

            recorder.prepare(REQUEST, isStillWanted = { true })
            recorder.stop()

            coVerify(exactly = 0) { playRecordingStopSound() }
        }
    }

    @Test
    fun finalized_thatCannotPublish_saysSoButKeepsTheRecording() {
        runTest {
            coEvery { publishRecording(any()) } returns false

            val recorder = createRecorder()
            val events = collectEvents(recorder)
            val onEvent = startRecording(recorder)

            onEvent(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))

            assertTrue(RecordedVideoEvent.SaveFailed in events)
            assertTrue(events.any { it is RecordedVideoEvent.Saved })
        }
    }

    @Test
    fun finalized_withNothingPlayable_throwsTheOutputAway() {
        runTest {
            val recorder = createRecorder()
            val onEvent = startRecording(recorder)

            onEvent(RecordingEvent.Finalized(outcome = RecordingOutcome.NothingPlayableWritten))

            coVerify(exactly = 1) { discardRecording(output) }
            coVerify(exactly = 0) { publishRecording(any()) }
        }
    }

    @Test
    fun finalized_afterAnInterruption_keepsWhatWasWritten() {
        runTest {
            val recorder = createRecorder()
            val onEvent = startRecording(recorder)

            onEvent(
                RecordingEvent.Finalized(
                    outcome = RecordingOutcome.Interrupted(errorCode = 7, hasContent = true),
                ),
            )

            coVerify(exactly = 1) { publishRecording(output) }
        }
    }

    @Test
    fun finalized_afterAnInterruptionBeforeAnythingWasWritten_throwsTheOutputAway() {
        runTest {
            val recorder = createRecorder()
            val onEvent = startRecording(recorder)

            onEvent(
                RecordingEvent.Finalized(
                    outcome = RecordingOutcome.Interrupted(errorCode = 7, hasContent = false),
                ),
            )

            coVerify(exactly = 1) { discardRecording(output) }
        }
    }

    @Test
    fun finalized_afterTheNextRecordingWasPrepared_savesItWithoutReportingIt() {
        runTest {
            coEvery { createRecordingOutput(any(), any()) } returnsMany listOf(output, nextOutput)

            val recorder = createRecorder()
            val events = collectEvents(recorder)
            val onEvent = startRecording(recorder)
            recorder.stop()
            recorder.prepare(REQUEST, isStillWanted = { true })
            events.clear()

            onEvent(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))

            coVerify(exactly = 1) { publishRecording(output) }
            coVerify(exactly = 0) { publishRecording(nextOutput) }
            assertTrue(events.none { it is RecordedVideoEvent.Finished })
            assertTrue(events.any { it is RecordedVideoEvent.Saved })

            recorder.start(muted = false, paused = false)

            verify(exactly = 2) { recordingSession.start(any(), any()) }
        }
    }

    private suspend fun startRecording(recorder: VideoRecorder): (RecordingEvent) -> Unit {
        val onEvent = slot<(RecordingEvent) -> Unit>()
        every { recordingSession.start(any(), capture(onEvent)) } returns true

        recorder.prepare(REQUEST, isStillWanted = { true })
        recorder.start(muted = false, paused = false)

        return onEvent.captured
    }

    private fun TestScope.collectEvents(recorder: VideoRecorder): MutableList<RecordedVideoEvent> {
        val events = mutableListOf<RecordedVideoEvent>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder.events.collect { events += it }
        }

        return events
    }

    private fun TestScope.createRecorder(): VideoRecorder {
        return VideoRecorderImpl(
            recordingSession = recordingSession,
            createRecordingOutput = createRecordingOutput,
            publishRecording = publishRecording,
            discardRecording = discardRecording,
            resolveCaptureLocation = resolveCaptureLocation,
            playRecordingStopSound = playRecordingStopSound,
            capturedItemRepository = capturedItemRepository,
            applicationScope = backgroundScope,
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
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
