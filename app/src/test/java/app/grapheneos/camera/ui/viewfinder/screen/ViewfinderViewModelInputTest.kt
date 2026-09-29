package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.testutil.cameraEntryPoint
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
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CaptureAction.CaptureKeyPressed)

            verify(exactly = 0) { cameraDelegate.toggleTorch() }
        }
    }

    @Test
    fun captureButtonClicked_inQrMode_togglesTheTorch() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 1) { cameraDelegate.toggleTorch() }
        }
    }

    @Test
    fun captureButtonClicked_inVideoMode_startsAndStopsTheRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
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

            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 1) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun captureButtonClicked_withASelfTimer_startsAndCancelsTheCountdown() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(settings = CameraSettings(selfTimerDurationSeconds = 3)) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            stateHolder.update {
                it.copy(capture = it.capture.copy(isSelfTimerRunning = true))
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
    fun previewSwiped_sidewaysMovesAcrossTheModeTabs() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.Preview.Swiped(SwipeDirection.LEFT))
            viewModel.onAction(CameraAction.Preview.Swiped(SwipeDirection.RIGHT))

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ModeTab.SelectAdjacent(offset = 1),
                    ViewfinderScreenEffect.ModeTab.SelectAdjacent(offset = -1),
                ),
                effects,
            )
        }
    }

    @Test
    fun flipCameraClicked_towardsAnUnavailableLens_saysSoAndDoesNotRebind() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            every { cameraDelegate.lensFacing } returns LensFacing.BACK
            every { cameraDelegate.switchLensFacing(lensFacing = any(), extensionMode = any()) }
                .returns(false)

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.AnimateLensSwitch,
                    ViewfinderScreenEffect.ShowMessage(R.string.front_camera_unavailable),
                ),
                effects,
            )
        }
    }

    @Test
    fun flipCameraClicked_inQrMode_togglesScanningAllCodes() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { state -> state.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 1) { settingsDelegate.toggleScanAllCodes() }
            verify(exactly = 0) {
                cameraDelegate.switchLensFacing(lensFacing = any(), extensionMode = any())
            }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun flipCameraClicked_whileRecording_pausesTheRecording() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { state ->
                state.copy(recording = state.recording.copy(phase = RecordingPhase.RECORDING))
            }

            viewModel.onAction(CameraAction.FlipCameraClicked)

            verify(exactly = 1) { recordingDelegate.setPaused(paused = true) }
            verify(exactly = 0) {
                cameraDelegate.switchLensFacing(lensFacing = any(), extensionMode = any())
            }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun muteToggleClicked_togglesTheRecordingMute() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.MuteToggleClicked)

            verify(exactly = 1) { recordingDelegate.toggleMute() }
        }
    }

    @Test
    fun thirdCircle_whileRecording_takesAPicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
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
            val viewModel = createViewModel(applicationScope = backgroundScope)

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
    fun settingsIconClicked_opensTheSheetOutsideQrMode() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(SettingsAction.SettingsIconClicked)
            stateHolder.update { state -> state.copy(mode = CameraMode.QR_SCAN) }
            viewModel.onAction(SettingsAction.SettingsIconClicked)

            assertEquals(listOf(ViewfinderScreenEffect.Settings.OpenSheet), effects)
        }
    }

    @Test
    fun screenPaused_cancelsAPictureCaptureOutsideQrMode() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

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
                applicationScope = backgroundScope,
                entryPoint = cameraEntryPoint(isCaptureSession = true),
            )
            viewModel.onAction(CaptureAction.ThirdCircleClicked)
            viewModel.onAction(CaptureAction.ThirdCircleLongClicked)

            verify(exactly = 0) { galleryDelegate.openGallery() }
            verify(exactly = 0) { galleryDelegate.shareLastCapturedItem() }
        }
    }
}
