package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_VIDEO
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingEvent
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

            val viewModel = createViewModel(applicationScope = backgroundScope)

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
    fun recordingRequested_withoutTheMicrophone_asksForItInsteadOfRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
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

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { recordingDelegate.requestRecording() }
        }
    }

    @Test
    fun recordWithoutAudioClicked_turnsAudioOffAndRecords() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(RecordingAction.RecordWithoutAudioClicked)

            verifyOrder {
                permissionDelegate.dismissDialog()
                settingsDelegate.setIncludeAudio(false)
                recordingDelegate.requestRecording()
            }
        }
    }

    @Test
    fun recordingRequested_withoutACameraToRecordWith_doesNothing() {
        runTest {
            every { cameraDelegate.canRecord } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingRequested)

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
    fun pause_outsideARecording_isIgnored() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))

            verify(exactly = 0) { recordingDelegate.setPaused(paused = any()) }
        }
    }

    @Test
    fun anUnusableOutput_revertsTheStorageLocation() {
        runTest {
            createViewModel(applicationScope = backgroundScope)

            recordingEvents.emit(ViewfinderRecordingEvent.OutputUnavailable)
            reverted.complete(Unit)

            assertTrue(revertFinished)
        }
    }

    @Test
    fun anUnusableOutput_inACaptureSession_leavesTheStorageLocationAlone() {
        runTest {
            createViewModel(
                applicationScope = backgroundScope,
                entryPoint = cameraEntryPoint(isCaptureSession = true),
            )

            recordingEvents.emit(ViewfinderRecordingEvent.OutputUnavailable)

            coVerify(exactly = 0) { revertToMediaStoreLocation() }
        }
    }

    @Test
    fun recordingSaved_recordsTheItem() {
        runTest {
            createViewModel(applicationScope = backgroundScope)
            val item = CapturedItem(
                type = ITEM_TYPE_VIDEO,
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
