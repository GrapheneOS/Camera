package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.net.Uri
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorder
import app.grapheneos.camera.domain.capture.model.RecordVideoRequest
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.testutil.collectEffects
import app.grapheneos.camera.testutil.viewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderRecordingDelegateTest {

    private val videoRecorder = mockk<VideoRecorder>(relaxed = true)

    private val recorderEvents = MutableSharedFlow<RecordedVideoEvent>()

    private val stateHolder = viewfinderStateHolder(mode = CameraMode.VIDEO)

    @Test
    fun aPauseBeforeTheRecordingStarts_survivesTheStart() {
        runTest {
            val delegate = createDelegate()

            delegate.setPaused(paused = true)
            recorderEvents.emit(RecordedVideoEvent.Started)

            assertEquals(
                ViewfinderRecordingState(
                    phase = RecordingPhase.RECORDING,
                    isPaused = true,
                ),
                recording(),
            )
        }
    }

    @Test
    fun recording_goesFromRequestedToStartedToStopped() {
        runTest {
            val delegate = createDelegate()

            delegate.requestRecording()
            assertEquals(RecordingPhase.STARTING, recording().phase)

            recorderEvents.emit(RecordedVideoEvent.Started)
            assertEquals(RecordingPhase.RECORDING, recording().phase)

            delegate.markStopped()
            assertEquals(RecordingPhase.IDLE, recording().phase)
        }
    }

    @Test
    fun markStopped_beforeTheRecordingStarted_returnsToIdle() {
        runTest {
            val delegate = createDelegate()

            delegate.requestRecording()
            delegate.markStopped()

            assertEquals(RecordingPhase.IDLE, recording().phase)
        }
    }

    @Test
    fun requestRecording_doesNotCarryOverThePreviousPauseOrMute() {
        runTest {
            val delegate = createDelegate()

            delegate.setPaused(paused = true)
            delegate.setMuted(muted = true)
            delegate.requestRecording()

            assertFalse(recording().isPaused)
            assertFalse(recording().isMuted)
        }
    }

    @Test
    fun markStopped_forgetsEverythingTheRecordingHeld() {
        runTest {
            val delegate = createDelegate()

            recorderEvents.emit(RecordedVideoEvent.Started)
            delegate.setPaused(paused = true)
            delegate.setMuted(muted = true)
            recorderEvents.emit(RecordedVideoEvent.Progressed(duration = 42.seconds))
            delegate.markStopped()

            assertEquals(ViewfinderRecordingState(), recording())
        }
    }

    @Test
    fun prepareRecording_handsTheRecorderWhereToWriteAndWhetherItIsStillWanted() {
        runTest {
            val isStillWanted = slot<() -> Boolean>()
            val delegate = createDelegate()
            delegate.requestRecording()
            delegate.prepareRecording(
                includeLocation = true,
                includeAudio = false,
                outputUri = FOREIGN_URI,
            )

            coVerify(exactly = 1) {
                videoRecorder.prepare(
                    request = RecordVideoRequest(
                        storageLocation = STORAGE_LOCATION,
                        foreignUri = FOREIGN_URI,
                        includeLocation = true,
                        includeAudio = false,
                    ),
                    isStillWanted = capture(isStillWanted),
                )
            }
            assertTrue(isStillWanted.captured())

            delegate.markStopped()
            assertFalse(isStillWanted.captured())
        }
    }

    @Test
    fun retry_isTakenOnlyOnce() {
        runTest {
            val delegate = createDelegate()

            delegate.retryOnceStreaming()

            assertTrue(delegate.takeRetry())
            assertFalse(delegate.takeRetry())
        }
    }

    @Test
    fun startPreparedRecording_passesOnThePauseAndMuteSetBeforeTheStart() {
        runTest {
            val delegate = createDelegate()

            delegate.requestRecording()
            delegate.setPaused(paused = true)
            delegate.setMuted(muted = true)
            delegate.startPreparedRecording()

            verify(exactly = 1) { videoRecorder.start(muted = true, paused = true) }
        }
    }

    @Test
    fun requestRecording_locksTheOrientationItStartsIn() {
        runTest {
            val delegate = createDelegate()
            stateHolder.update { it.copy(deviceOrientation = DeviceOrientation.DEGREES_270) }

            delegate.requestRecording()
            stateHolder.update { it.copy(deviceOrientation = DeviceOrientation.DEGREES_0) }

            assertEquals(DeviceOrientation.DEGREES_270, recording().orientationAtStart)
        }
    }

    @Test
    fun recorderEvents_soundTheStartAndTrackTheDuration() {
        runTest {
            createDelegate()
            val effects = collectEffects(stateHolder)

            recorderEvents.emit(RecordedVideoEvent.ReadyToStart)
            recorderEvents.emit(RecordedVideoEvent.Started)
            recorderEvents.emit(RecordedVideoEvent.Progressed(duration = 5.seconds))

            assertEquals(RecordingPhase.RECORDING, recording().phase)
            assertEquals(5.seconds, recording().duration)
            assertEquals(listOf(Effect.Recording.PlayStartSound), effects)
        }
    }

    @Test
    fun anAbandonedRecording_stopsWithoutTheStopSound() {
        runTest {
            val delegate = createDelegate()
            val effects = collectEffects(stateHolder)
            delegate.requestRecording()

            recorderEvents.emit(RecordedVideoEvent.Abandoned)

            assertEquals(RecordingPhase.IDLE, recording().phase)
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun aRecordingThatKeepsItsContent_isSavingUntilItIsSaved() {
        runTest {
            createDelegate()
            val effects = collectEffects(stateHolder)
            recorderEvents.emit(RecordedVideoEvent.Started)

            recorderEvents.emit(RecordedVideoEvent.Finished(outcome = RecordingOutcome.Saved))
            assertEquals(RecordingPhase.IDLE, recording().phase)
            assertTrue(recording().isSaving)

            recorderEvents.emit(RecordedVideoEvent.Saved(uri = Uri.EMPTY, item = null))
            assertFalse(recording().isSaving)
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun aRecordingBeingSaved_staysSavingWhileTheNextOneStartsAndStops() {
        runTest {
            val delegate = createDelegate()
            recorderEvents.emit(RecordedVideoEvent.Finished(outcome = RecordingOutcome.Saved))

            delegate.requestRecording()
            delegate.markStopped()

            assertTrue(recording().isSaving)
        }
    }

    @Test
    fun aRecordingTooShortToPlay_isNeverSavingAndSaysSo() {
        runTest {
            createDelegate()
            val effects = collectEffects(stateHolder)

            recorderEvents.emit(
                RecordedVideoEvent.Finished(outcome = RecordingOutcome.NothingPlayableWritten),
            )

            assertFalse(recording().isSaving)
            assertTrue(Effect.ShowMessage(R.string.recording_too_short_to_be_saved) in effects)
        }
    }

    @Test
    fun anInterruptedRecording_saysSo() {
        runTest {
            createDelegate()
            val effects = collectEffects(stateHolder)

            recorderEvents.emit(
                RecordedVideoEvent.Finished(
                    outcome = RecordingOutcome.Interrupted(errorCode = 7, hasContent = true),
                ),
            )

            assertTrue(Effect.Recording.Interrupted(errorCode = 7) in effects)
        }
    }

    @Test
    fun anUnusableOutput_isHandedOnAndStops() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            val effects = collectEffects(stateHolder)
            delegate.requestRecording()

            recorderEvents.emit(RecordedVideoEvent.OutputUnavailable)

            assertEquals(RecordingPhase.IDLE, recording().phase)
            assertEquals(listOf(ViewfinderRecordingEvent.OutputUnavailable), events)
            assertEquals(
                listOf(Effect.ShowMessage(R.string.unable_to_access_output_file)),
                effects,
            )
        }
    }

    @Test
    fun aSavedRecording_handsItsItemOnWithoutAReview() {
        runTest {
            val delegate = createDelegate()
            val events = collectEvents(delegate)
            val effects = collectEffects(stateHolder)

            recorderEvents.emit(RecordedVideoEvent.Saved(uri = Uri.EMPTY, item = ITEM))

            assertEquals(listOf(ViewfinderRecordingEvent.Saved(ITEM)), events)
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun aSavedRecording_inACaptureSession_isShownForReview() {
        runTest {
            createDelegate()
            val effects = collectEffects(stateHolder)
            stateHolder.update { state -> state.copy(isCaptureSession = true) }

            recorderEvents.emit(RecordedVideoEvent.Saved(uri = Uri.EMPTY, item = null))

            assertEquals(listOf(Effect.Recording.ShowForReview(uri = Uri.EMPTY)), effects)
        }
    }

    @Test
    fun recorderLocationAndSaveFailures_saySo() {
        runTest {
            createDelegate()
            val effects = collectEffects(stateHolder)

            recorderEvents.emit(RecordedVideoEvent.LocationUnavailable)
            recorderEvents.emit(RecordedVideoEvent.SaveFailed)

            assertEquals(
                listOf(
                    Effect.ShowMessage(R.string.location_unavailable),
                    Effect.ShowMessage(R.string.unable_to_save_video),
                ),
                effects,
            )
        }
    }

    private fun recording(): ViewfinderRecordingState {
        return stateHolder.state.value.recording
    }

    private fun TestScope.collectEvents(
        delegate: ViewfinderRecordingDelegate,
    ): List<ViewfinderRecordingEvent> {
        val events = mutableListOf<ViewfinderRecordingEvent>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.recordingEvents.collect { events += it }
        }

        return events
    }

    private fun TestScope.createDelegate(): ViewfinderRecordingDelegate {
        every { videoRecorder.events } returns recorderEvents

        val delegate = ViewfinderRecordingDelegateImpl(
            videoRecorder = videoRecorder,
            capturedItemRepository = mockk {
                every { storageLocation } returns flowOf(STORAGE_LOCATION)
            },
            applicationScope = backgroundScope,
            mainDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        delegate.bind(
            scope = backgroundScope,
            stateHolder = stateHolder,
        )

        return delegate
    }

    private companion object {
        const val STORAGE_LOCATION = "MediaStore"

        val FOREIGN_URI: Uri = Uri.parse("content://com.example.app/videos/1")
        val ITEM = CapturedItem(ITEM_TYPE_VIDEO, "20260920_120000_000", Uri.EMPTY)
    }
}
