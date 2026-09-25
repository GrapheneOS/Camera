package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelRecordingTest : ViewfinderViewModelTestBase() {

    @Test
    fun recordingRequested_withTheCameraReady_prepticksTheRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingRequested(hasAudioPermission = true))

            verifyOrder {
                recordingDelegate.requestRecording()
                recordingDelegate.prepareRecording(includeLocation = false, includeAudio = true)
            }
        }
    }

    @Test
    fun recordingRequested_withoutTheMicrophone_asksForItInsteadOfRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(RecordingAction.RecordingRequested(hasAudioPermission = false))

            verify(exactly = 0) {
                recordingDelegate.prepareRecording(includeLocation = any(), includeAudio = any())
            }
            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertEquals(
                listOf(ViewfinderScreenEffect.Recording.RequestAudioPermission),
                effects,
            )
        }
    }

    @Test
    fun recordingRequested_withoutACameraToRecordWith_doesNothing() {
        runTest {
            every { cameraDelegate.canRecord } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingRequested(hasAudioPermission = true))

            verify(exactly = 0) { recordingDelegate.requestRecording() }
        }
    }

    @Test
    fun recordingActions_reachTheRecordingDelegate() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }

            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(RecordingAction.RecordingMuteToggled(muted = true))
            viewModel.onAction(RecordingAction.StartSoundPlayed)
            viewModel.onAction(RecordingAction.RecordingStopRequested)

            verifyOrder {
                recordingDelegate.setPaused(paused = true)
                recordingDelegate.setMuted(muted = true)
                recordingDelegate.startPreparedRecording()
                recordingDelegate.requestStop()
            }
        }
    }

    @Test
    fun pauseAndMute_outsideARecording_areIgnored() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(RecordingAction.RecordingMuteToggled(muted = true))

            verify(exactly = 0) { recordingDelegate.setPaused(paused = any()) }
            verify(exactly = 0) { recordingDelegate.setMuted(muted = any()) }
        }
    }

    @Test
    fun recordingEvents_soundTheStartAndDriveTheState() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.ReadyToStart)
            recordingEvents.emit(RecordedVideoEvent.Started)
            recordingEvents.emit(RecordedVideoEvent.Progressed(duration = 5.seconds))

            verifyOrder {
                recordingDelegate.startRecording()
                recordingDelegate.setRecordedDuration(5.seconds)
            }
            assertEquals(listOf(ViewfinderScreenEffect.Recording.PlayStartSound), effects)
        }
    }

    @Test
    fun anAbandonedRecording_stopsWithoutTheStopSound() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.Abandoned)

            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertEquals(listOf(ViewfinderScreenEffect.Recording.Stopped), effects)
        }
    }

    @Test
    fun aFinishedRecording_isAnnounced() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.Finished(outcome = RecordingOutcome.Saved))

            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Recording.Stopped,
                    ViewfinderScreenEffect.Recording.PlayStopSound,
                ),
                effects,
            )
        }
    }

    @Test
    fun aRecordingThatKeepsItsContent_isSavingUntilItIsSaved() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.Finished(outcome = RecordingOutcome.Saved))
            recordingEvents.emit(RecordedVideoEvent.Saved(uri = Uri.EMPTY, item = null))

            verifyOrder {
                captureDelegate.startRecordingSave()
                captureDelegate.finishRecordingSave()
            }

            val saved = ViewfinderScreenEffect.Recording.Saved(uri = Uri.EMPTY, item = null)
            assertTrue(saved in effects)
        }
    }

    @Test
    fun aRecordingThatIsThrownAway_isNeverSaving() {
        runTest {
            createViewModel(applicationScope = backgroundScope)

            recordingEvents.emit(
                RecordedVideoEvent.Finished(outcome = RecordingOutcome.NothingPlayableWritten),
            )

            verify(exactly = 0) { captureDelegate.startRecordingSave() }
        }
    }

    @Test
    fun aRecordingTooShortToPlay_saysSo() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(
                RecordedVideoEvent.Finished(outcome = RecordingOutcome.NothingPlayableWritten),
            )

            assertTrue(
                ViewfinderScreenEffect.ShowMessage(
                    R.string.recording_too_short_to_be_saved,
                ) in effects,
            )
        }
    }

    @Test
    fun anInterruptedRecording_saysSo() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(
                RecordedVideoEvent.Finished(
                    outcome = RecordingOutcome.Interrupted(errorCode = 7, hasContent = true),
                ),
            )

            assertTrue(ViewfinderScreenEffect.Recording.Interrupted(errorCode = 7) in effects)
        }
    }

    @Test
    fun anUnusableOutput_revertsTheStorageLocationAndStops() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.OutputUnavailable)

            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertTrue(
                ViewfinderScreenEffect.ShowMessage(
                    R.string.unable_to_access_output_file,
                ) in effects,
            )
        }
    }
}
