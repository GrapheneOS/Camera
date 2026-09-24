package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelInputTest : ViewfinderViewModelTestBase() {

    @Test
    fun previewTapped_showsTheFocusWithTheSoundOnlyForPhotos() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.PreviewTapped(x = 10f, y = 20f))
            stateHolder.update { it.copy(mode = CameraMode.VIDEO) }
            viewModel.onAction(CameraAction.PreviewTapped(x = 10f, y = 20f))

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowFocus(x = 10f, y = 20f, playsSound = true),
                    ViewfinderScreenEffect.ShowFocus(x = 10f, y = 20f, playsSound = false),
                ),
                effects,
            )
        }
    }

    @Test
    fun previewTapped_inQrMode_leavesTheFocusAlone() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CameraAction.PreviewTapped(x = 10f, y = 20f))

            verify(exactly = 0) { cameraDelegate.focusAt(any(), any(), any()) }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun keys_inQrMode_doNothing() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CaptureAction.CaptureKeyPressed)
            viewModel.onAction(CameraAction.ZoomInKeyPressed)
            viewModel.onAction(CameraAction.FocusKeyPressed)

            verify(exactly = 0) { cameraDelegate.toggleTorch() }
            verify(exactly = 0) { cameraDelegate.stepZoom(any()) }
            verify(exactly = 0) { cameraDelegate.cancelFocus() }
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
            every { captureDelegate.selfTimerCountdown(any()) } returns endlessSelfTimer()

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(settings = CameraSettings(selfTimerDurationSeconds = 3)) }

            viewModel.onAction(CaptureAction.CaptureButtonClicked)
            stateHolder.update {
                it.copy(capture = it.capture.copy(isSelfTimerRunning = true))
            }
            viewModel.onAction(CaptureAction.CaptureButtonClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
            assertEquals(ViewfinderScreenEffect.SelfTimer.Started, effects.first())
            assertEquals(ViewfinderScreenEffect.SelfTimer.Cancelled, effects.last())
        }
    }

    @Test
    fun previewSwiped_sidewaysMovesAcrossTheModeTabs() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.LEFT))
            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.RIGHT))

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.SelectAdjacentModeTab(offset = 1),
                    ViewfinderScreenEffect.SelectAdjacentModeTab(offset = -1),
                ),
                effects,
            )
        }
    }

    @Test
    fun previewSwiped_upAndDownCloseAndOpenTheSettings() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.DOWN))
            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.UP))

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.OpenSettingsSheet,
                    ViewfinderScreenEffect.CloseSettingsSheet,
                ),
                effects,
            )
        }
    }

    @Test
    fun previewSwipedDown_inQrMode_offersTheFormatsUnlessAllAreScanned() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.DOWN))
            stateHolder.update { it.copy(settings = CameraSettings(scanAllCodes = true)) }
            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.DOWN))

            assertEquals(listOf(ViewfinderScreenEffect.ShowQrFormats), effects)
        }
    }

    @Test
    fun previewSwiped_whileRecording_stillOpensTheSettingsButKeepsTheMode() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(recording = it.recording.copy(phase = RecordingPhase.RECORDING))
            }

            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.LEFT))
            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.UP))
            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.DOWN))

            assertEquals(listOf(ViewfinderScreenEffect.OpenSettingsSheet), effects)
        }
    }

    @Test
    fun previewSwiped_duringTheSelfTimer_doesNothing() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(capture = it.capture.copy(isSelfTimerRunning = true))
            }

            SwipeDirection.entries.forEach { direction ->
                viewModel.onAction(CameraAction.PreviewSwiped(direction))
            }

            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun previewSwipedSideways_withoutModeTabs_doesNothing() {
        runTest {
            val viewModel = createViewModel(
                applicationScope = backgroundScope,
                entryPoint = cameraEntryPoint(showsCameraModeTabs = false),
            )
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.PreviewSwiped(SwipeDirection.LEFT))

            assertTrue(effects.isEmpty())
        }
    }
}
