package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemType
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.state.RecordingPhase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
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

            val viewModel = createViewModel()

            viewModel.onAction(RecordingAction.RecordingRequested)

            verifyOrder {
                recordingDelegate.requestRecording()
                recordingDelegate.prepareRecording(
                    includeLocation = false,
                    includeAudio = true,
                    outputUri = null,
                )
            }
        }
    }

    @Test
    fun heldShutter_blocksRecordingStartsUntilReleased() {
        runTest {
            every { cameraDelegate.canRecord } returns true
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(mode = CameraMode.VIDEO, capture = it.capture.copy(isShutterHeld = true))
            }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            viewModel.onAction(CaptureAction.CaptureKeyPressed)
            viewModel.onAction(RecordingAction.RecordingRequested)

            verify(exactly = 0) { recordingDelegate.requestRecording() }

            stateHolder.update { it.copy(capture = it.capture.copy(isShutterHeld = false)) }
            viewModel.onAction(RecordingAction.RecordingRequested)

            verify(exactly = 1) { recordingDelegate.requestRecording() }
        }
    }

    @Test
    fun heldShutter_doesNotBlockStoppingARecording() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(
                    recording = it.recording.copy(phase = RecordingPhase.RECORDING),
                    capture = it.capture.copy(isShutterHeld = true),
                )
            }

            viewModel.onAction(RecordingAction.RecordingStopRequested)

            verify(exactly = 1) { recordingDelegate.requestStop() }
        }
    }

    @Test
    fun recordingRequested_withoutTheMicrophone_asksForItInsteadOfRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel()
            stateHolder.update { it.copy(missingPermissions = setOf(AppPermission.MICROPHONE)) }

            viewModel.onAction(RecordingAction.RecordingRequested)

            verify(exactly = 0) {
                recordingDelegate.prepareRecording(
                    includeLocation = any(),
                    includeAudio = any(),
                    outputUri = any(),
                )
            }
            verify(exactly = 1) { recordingDelegate.markStopped() }
            verify(exactly = 1) {
                permissionDelegate.request(
                    permission = AppPermission.MICROPHONE,
                    explainsFirst = false,
                )
            }
        }
    }

    @Test
    fun previewStreamingStarted_withARetryPending_records() {
        runTest {
            every { cameraDelegate.canRecord } returns true
            every { recordingDelegate.takeRetry() } returns true

            val viewModel = createViewModel()
            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { recordingDelegate.requestRecording() }
        }
    }

    @Test
    fun recordWithoutAudioClicked_turnsAudioOffAndRecords() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel()
            viewModel.onAction(DialogAction.Choice.RecordWithoutAudioClicked)

            verifyOrder {
                dialogDelegate.close()
                settingsDelegate.setIncludeAudio(false)
                recordingDelegate.requestRecording()
            }
        }
    }

    @Test
    fun recordingRequested_withoutACameraToRecordWith_doesNothing() {
        runTest {
            every { cameraDelegate.canRecord } returns false

            val viewModel = createViewModel()

            viewModel.onAction(RecordingAction.RecordingRequested)

            verify(exactly = 0) { recordingDelegate.requestRecording() }
        }
    }

    @Test
    fun recordingActions_reachTheRecordingDelegate() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }

            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(RecordingAction.MuteToggleClicked)
            viewModel.onAction(RecordingAction.StartSoundPlayed)
            viewModel.onAction(RecordingAction.RecordingStopRequested)

            verifyOrder {
                recordingDelegate.setPaused(paused = true)
                recordingDelegate.toggleMute()
                recordingDelegate.startPreparedRecording()
                recordingDelegate.requestStop()
            }
        }
    }

    @Test
    fun anUnusableOutput_isHandedToTheGallery() {
        runTest {
            createViewModel()

            recordingEvents.emit(ViewfinderRecordingEvent.OutputUnavailable)

            verify(exactly = 1) { galleryDelegate.onStorageLocationNotFound() }
        }
    }

    @Test
    fun screenStopped_whileRecording_holdsTheFrameAndStops() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }

            viewModel.onAction(LifecycleAction.ScreenStopped)

            verifyOrder {
                cameraDelegate.holdPreviewFrame()
                recordingDelegate.requestStop()
            }
        }
    }

    @Test
    fun screenStopped_withoutARecording_leavesTheCameraAlone() {
        runTest {
            val viewModel = createViewModel()
            viewModel.onAction(LifecycleAction.ScreenStopped)

            verify(exactly = 0) { cameraDelegate.holdPreviewFrame() }
            verify(exactly = 0) { recordingDelegate.requestStop() }
        }
    }

    @Test
    fun recordingSaved_recordsTheItem() {
        runTest {
            createViewModel()
            val item = CapturedItem(
                type = CapturedItemType.VIDEO,
                dateString = "20260920_120000_000",
                uri = Uri.EMPTY,
            )

            recordingEvents.emit(ViewfinderRecordingEvent.Saved(item = item))

            verifyOrder {
                galleryDelegate.recordCapturedItem(item)
                galleryDelegate.refreshThumbnail()
            }
        }
    }
}
