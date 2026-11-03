package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.core.model.QuickSetting
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.input.VolumeKey
import app.grapheneos.camera.ui.viewfinder.screen.model.input.ZoomDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.state.RecordingPhase
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
class ViewfinderViewModelInputTest : ViewfinderViewModelTestBase() {

    @Test
    fun volumeKeyEvent_mapsWithTheCurrentSettingAndDispatchesTheResult() {
        runTest {
            val event = CaptureAction.VolumeKeyEvent.Pressed(VolumeKey.UP)
            every {
                volumeKeyActionMapper.map(event = event, volumeKeyAction = VolumeKeyAction.ZOOM)
            } returns CameraAction.ZoomStepKey.Pressed(ZoomDirection.IN)
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(settings = CameraSettings(volumeKeyAction = VolumeKeyAction.ZOOM))
            }

            viewModel.onAction(event)

            verify(exactly = 1) { cameraControlsDelegate.startZoomStepping(ZoomDirection.IN) }
        }
    }

    @Test
    fun volumeKeyEvent_withoutAMappedAction_doesNothing() {
        runTest {
            val event = CaptureAction.VolumeKeyEvent.Released(VolumeKey.DOWN)
            every {
                volumeKeyActionMapper.map(event = event, volumeKeyAction = VolumeKeyAction.OFF)
            } returns null
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(settings = CameraSettings(volumeKeyAction = VolumeKeyAction.OFF))
            }

            viewModel.onAction(event)

            verify(exactly = 1) {
                volumeKeyActionMapper.map(event = event, volumeKeyAction = VolumeKeyAction.OFF)
            }
            verify(exactly = 0) { captureDelegate.takePicture() }
            verify(exactly = 0) { recordingDelegate.requestRecording() }
            verify(exactly = 0) { cameraControlsDelegate.stopZoomStepping() }
        }
    }

    @Test
    fun captureKey_inQrMode_leavesTheTorchAlone() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CaptureAction.CaptureKeyPressed)

            verify(exactly = 0) { cameraDelegate.toggleTorch() }
        }
    }

    @Test
    fun captureButtonClicked_inQrMode_togglesTheTorch() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 1) { cameraDelegate.toggleTorch() }
        }
    }

    @Test
    fun captureButtonClicked_inVideoMode_startsAndStopsTheRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel()
            stateHolder.update { it.copy(mode = CameraMode.VIDEO) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            verify(exactly = 1) { recordingDelegate.requestRecording() }

            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }
            viewModel.onAction(CaptureAction.CaptureKeyPressed)
            verify(exactly = 1) { recordingDelegate.requestStop() }
        }
    }

    @Test
    fun captureButtonClicked_withoutASelfTimer_takesThePicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true

            val viewModel = createViewModel()
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 1) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun captureButtonClicked_withASelfTimer_startsAndCancelsTheCountdown() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update { it.copy(settings = CameraSettings(selfTimerDuration = 3.seconds)) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            stateHolder.update {
                it.copy(capture = it.capture.copy(selfTimerRemaining = 3.seconds))
            }
            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
            verifyOrder {
                captureDelegate.startSelfTimer()
                captureDelegate.cancelSelfTimer()
            }
        }
    }

    @Test
    fun heldShutter_blocksPhotoInputsUntilReleased() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(entryPoint = cameraEntryPoint(isCaptureSession = true))
            stateHolder.update {
                it.copy(
                    session = it.session.copy(canTakePicture = true),
                    capture = it.capture.copy(isShutterHeld = true),
                )
            }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            viewModel.onAction(CaptureAction.CaptureKeyPressed)
            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 0) { captureDelegate.takePreviewPicture() }
            verify(exactly = 0) { captureDelegate.takePicture() }

            stateHolder.update { it.copy(capture = it.capture.copy(isShutterHeld = false)) }
            viewModel.onAction(CaptureAction.CaptureKeyPressed)

            verify(exactly = 1) { captureDelegate.takePreviewPicture() }
        }
    }

    @Test
    fun heldShutter_blocksStartingTheCountdown() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update {
                it.copy(
                    settings = CameraSettings(selfTimerDuration = 3.seconds),
                    capture = it.capture.copy(isShutterHeld = true),
                )
            }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            viewModel.onAction(CaptureAction.CaptureKeyPressed)

            verify(exactly = 0) { captureDelegate.startSelfTimer() }

            stateHolder.update { it.copy(capture = it.capture.copy(isShutterHeld = false)) }
            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 1) { captureDelegate.startSelfTimer() }
        }
    }

    @Test
    fun flipCameraClicked_towardsAnUnavailableLens_doesNotRebind() {
        runTest {
            val viewModel = createViewModel()
            every { cameraDelegate.switchLens(extensionMode = any()) } returns false

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
        }
    }

    @Test
    fun flipCameraClicked_inQrMode_togglesScanningAllCodes() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)
            stateHolder.update { state -> state.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 1) { settingsDelegate.toggleScanAllCodes() }
            verify(exactly = 0) { cameraDelegate.switchLens(extensionMode = any()) }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun flipCameraClicked_whileRecording_pausesTheRecording() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)
            stateHolder.update { state ->
                state.copy(recording = state.recording.copy(phase = RecordingPhase.RECORDING))
            }

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 1) { recordingDelegate.setPaused(paused = true) }
            verify(exactly = 0) { cameraDelegate.switchLens(extensionMode = any()) }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun muteToggleClicked_togglesTheRecordingMute() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(RecordingAction.MuteToggleClicked)

            verify(exactly = 1) { recordingDelegate.toggleMute() }
        }
    }

    @Test
    fun thirdCircle_whileRecording_takesAPicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)
            stateHolder.update { state ->
                state.copy(
                    session = state.session.copy(canTakePicture = true),
                    recording = state.recording.copy(phase = RecordingPhase.RECORDING),
                )
            }

            viewModel.onAction(CaptureAction.ThirdCircleClicked)
            viewModel.onAction(CaptureAction.ThirdCircleLongClicked)

            verify(exactly = 2) { captureDelegate.takePicture() }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun thirdCircle_outsideARecording_opensTheGalleryOrSharesTheLatestItem() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CaptureAction.ThirdCircleClicked)
            viewModel.onAction(CaptureAction.ThirdCircleLongClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
            verifyOrder {
                galleryDelegate.openGallery()
                galleryDelegate.shareLastCapturedItem()
            }
        }
    }

    @Test
    fun previewTapped_withTheSettingsPanelOpen_onlyClosesIt() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update { it.copy(isSettingsPanelOpen = true) }

            viewModel.onAction(CameraAction.Preview.Tapped(x = 1f, y = 2f))

            verify(exactly = 1) { settingsDelegate.closeSettingsPanel() }
            verify(exactly = 0) { cameraControlsDelegate.focusAt(x = any(), y = any()) }
        }
    }

    @Test
    fun settingsPanel_followsTheStatusBarTheButtonAndTheHost() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(SettingsAction.StatusBarClicked(QuickSetting.SELF_TIMER))
            viewModel.onAction(SettingsAction.QuickSettingsButtonClicked)
            viewModel.onAction(SettingsAction.SettingsPanelVisibilityChanged(visible = true))

            verifyOrder {
                settingsDelegate.openSettingsPanel(QuickSetting.SELF_TIMER)
                settingsDelegate.openSettingsPanel()
                settingsDelegate.onSettingsPanelVisibilityChanged(true)
            }
        }
    }

    @Test
    fun sidewaysSwipes_moveTheModeStrip() {
        runTest {
            every {
                swipeOutcomeMapper.map(direction = SwipeDirection.LEFT, state = any())
            } returns SwipeOutcome.SelectAdjacentMode(offset = 1)
            every {
                swipeOutcomeMapper.map(direction = SwipeDirection.RIGHT, state = any())
            } returns SwipeOutcome.SelectAdjacentMode(offset = -1)
            val viewModel = createViewModel()
            stateHolder.update { it.copy(showsCameraModeTabs = true) }

            viewModel.onAction(CameraAction.Preview.Swiped(SwipeDirection.LEFT))
            viewModel.onAction(CameraAction.Preview.Swiped(SwipeDirection.RIGHT))

            verifyOrder {
                modeSwitchDelegate.onAdjacentModeSwiped(1)
                modeSwitchDelegate.onAdjacentModeSwiped(-1)
            }
        }
    }

    @Test
    fun swipeUp_inQrMode_offersTheBarcodeFormats() {
        runTest {
            every {
                swipeOutcomeMapper.map(direction = SwipeDirection.UP, state = any())
            } returns SwipeOutcome.ShowQrFormats
            val viewModel = createViewModel()
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.Preview.Swiped(SwipeDirection.UP))

            assertEquals(listOf(ViewfinderScreenEffect.Settings.ShowQrFormats), effects)
            verify(exactly = 0) { modeSwitchDelegate.onAdjacentModeSwiped(any()) }
        }
    }

    @Test
    fun screenPaused_cancelsTheSelfTimer() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenPaused)

            verify(exactly = 1) { captureDelegate.cancelSelfTimer() }
        }
    }

    @Test
    fun screenPaused_cancelsAPictureCaptureOutsideQrMode() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenPaused)
            stateHolder.update { state -> state.copy(mode = CameraMode.QR_SCAN) }
            viewModel.onAction(LifecycleAction.ScreenPaused)

            verify(exactly = 1) { captureDelegate.cancelPictureCapture() }
        }
    }

    @Test
    fun thirdCircle_inACaptureSession_leavesTheGalleryShut() {
        runTest {
            val viewModel = createViewModel(
                entryPoint = cameraEntryPoint(isCaptureSession = true),
            )
            viewModel.onAction(CaptureAction.ThirdCircleClicked)
            viewModel.onAction(CaptureAction.ThirdCircleLongClicked)

            verify(exactly = 0) { galleryDelegate.openGallery() }
            verify(exactly = 0) { galleryDelegate.shareLastCapturedItem() }
        }
    }
}
