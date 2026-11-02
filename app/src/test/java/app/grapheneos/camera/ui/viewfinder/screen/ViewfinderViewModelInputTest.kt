package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.quicksettings.navigation.QuickSetting
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
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
class ViewfinderViewModelInputTest : ViewfinderViewModelTestBase() {

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
    fun flipCameraClicked_towardsAnUnavailableLens_doesNotRebind() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)
            every { cameraDelegate.switchLens(extensionMode = any()) } returns false

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            assertEquals(listOf(ViewfinderScreenEffect.AnimateLensSwitch), effects)
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
    fun settingsPanel_followsTheStatusBarTheButtonSwipesAndTheHost() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(SettingsAction.StatusBarClicked(QuickSetting.SELF_TIMER))
            viewModel.onAction(SettingsAction.QuickSettingsButtonClicked)
            viewModel.onAction(CameraAction.Preview.Swiped(SwipeDirection.UP))
            viewModel.onAction(SettingsAction.SettingsPanelVisibilityChanged(visible = true))

            verifyOrder {
                settingsDelegate.openSettingsPanel(QuickSetting.SELF_TIMER)
                settingsDelegate.openSettingsPanel()
                gestureDelegate.onPreviewSwiped(SwipeDirection.UP)
                settingsDelegate.onSettingsPanelVisibilityChanged(true)
            }
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
