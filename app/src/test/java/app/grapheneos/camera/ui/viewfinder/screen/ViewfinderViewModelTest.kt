package app.grapheneos.camera.ui.viewfinder.screen

import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelTest : ViewfinderViewModelTestBase() {

    @Test
    fun screenResumed_withTheProviderReady_startsTheBindBeforeReturning() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = true) }
            verify(exactly = 0) { cameraDelegate.initialize(forced = any(), extensionMode = any()) }
        }
    }

    @Test
    fun screenResumed_withoutAProvider_asksTheSessionForOne() {
        runTest {
            every { cameraDelegate.isProviderReady } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) {
                cameraDelegate.initialize(
                    forced = true,
                    extensionMode = CameraMode.CAMERA.extensionMode,
                )
            }
            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
        }
    }

    @Test
    fun providerReady_startsTheBind() {
        runTest {
            createViewModel(applicationScope = backgroundScope)
            sessionEvents.emit(CameraSessionEvent.ProviderReady(forced = true))

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = true) }
        }
    }

    @Test
    fun screenCreated_lendsTheScreenToTheCameraUntilTheScreenIsDestroyed() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val host = mockk<ViewfinderHost>()

            viewModel.onAction(LifecycleAction.ScreenCreated(host))
            viewModel.onAction(LifecycleAction.ScreenDestroyed)

            verifyOrder {
                cameraDelegate.onScreenCreated(host)
                cameraDelegate.onScreenDestroyed()
                captureDelegate.onScreenDestroyed()
            }
        }
    }

    @Test
    fun zoomStateChanged_publishesTheZoomAndShowsItsPanel() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            sessionEvents.emit(CameraSessionEvent.ZoomStateChanged)

            verify(exactly = 1) { cameraDelegate.refreshZoom() }
            assertEquals(listOf(ViewfinderScreenEffect.Panel.ShowZoom), effects)
        }
    }

    @Test
    fun zoomStateLoaded_publishesTheZoomWithoutShowingItsPanel() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            sessionEvents.emit(CameraSessionEvent.ZoomStateLoaded)

            verify(exactly = 1) { cameraDelegate.refreshZoom() }
            assertTrue(effects.isEmpty())
        }
    }

    @Test
    fun lensSwitchClicked_towardsAnUnavailableLens_saysSoAndDoesNotRebind() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            every { cameraDelegate.lensFacing } returns LensFacing.BACK
            every { cameraDelegate.switchLensFacing(lensFacing = any(), extensionMode = any()) }
                .returns(false)

            viewModel.onAction(CameraAction.LensSwitchClicked)

            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.front_camera_unavailable)),
                effects,
            )
        }
    }

    @Test
    fun startCamera_forQrWithoutARearLens_saysItScansWithTheFrontOne() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            every { cameraDelegate.canBeginBind(forced = any()) } returns true
            every { cameraDelegate.selectLens(isQrMode = any(), extensionMode = any()) } returns
                ViewfinderBindTarget(qrLensFacing = LensFacing.FRONT)
            every { cameraDelegate.bindCamera(any()) } returns BindOutcome.BOUND

            viewModel.onAction(LifecycleAction.QrResultDismissed)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Panel.HideExposure,
                    ViewfinderScreenEffect.ShowMessage(R.string.qr_rear_camera_unavailable),
                    ViewfinderScreenEffect.Panel.HideZoom,
                ),
                effects,
            )
        }
    }

    @Test
    fun storageLocationNotFound_showsTheDialogOnlyOnceTheLocationIsReverted() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = mutableListOf<ViewfinderScreenEffect>()
            backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.effects.collect { effects += it }
            }

            viewModel.onAction(CaptureAction.StorageLocationNotFound)

            assertTrue(effects.isEmpty())

            reverted.complete(Unit)

            assertEquals(listOf(ViewfinderScreenEffect.ShowStorageLocationNotFound), effects)
            coVerify(exactly = 1) { revertToMediaStoreLocation() }
        }
    }

    @Test
    fun storageLocationNotFound_finishesTheRevertEvenWhenTheScreenGoesAway() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(CaptureAction.StorageLocationNotFound)
            viewModel.viewModelScope.cancel()
            reverted.complete(Unit)

            assertTrue(revertFinished)
        }
    }

    @Test
    fun qrCodeScanned_showsTheResultOnlyWhenNoneIsShown() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            every { cameraDelegate.showQrResult() } returnsMany listOf(true, false)

            sessionEvents.emit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))
            sessionEvents.emit(CameraSessionEvent.QrCodeScanned(text = QR_TEXT))

            assertEquals(listOf(ViewfinderScreenEffect.ShowQrResult(QR_TEXT)), effects)
        }
    }

    @Test
    fun qrResultDismissed_rebindsOnlyOnceTheResultIsDismissed() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(LifecycleAction.QrResultDismissed)

            verifyOrder {
                cameraDelegate.dismissQrResult()
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun previewTapped_focusesThereForTheChosenTimeout() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(settings = CameraSettings(focusTimeoutSeconds = FOCUS_TIMEOUT_SECONDS))
            }

            viewModel.onAction(CameraAction.PreviewTapped(x = 10f, y = 20f))

            verify(exactly = 1) {
                cameraDelegate.focusAt(x = 10f, y = 20f, autoCancelSeconds = FOCUS_TIMEOUT_SECONDS)
            }
        }
    }

    @Test
    fun zoomKeys_stepTheZoomByOneInEitherDirection() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(CameraAction.ZoomInKeyPressed)
            viewModel.onAction(CameraAction.ZoomOutKeyPressed)

            verifyOrder {
                cameraDelegate.stepZoom(1f)
                cameraDelegate.stepZoom(-1f)
            }
        }
    }

    @Test
    fun previewStreamingStarted_inVideoMode_refreshesTheVideoQualities() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(mode = CameraMode.VIDEO) }

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { cameraDelegate.refreshVideoQualities() }
        }
    }

    @Test
    fun previewStreamingStarted_inPhotoMode_leavesTheVideoQualitiesAlone() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 0) { cameraDelegate.refreshVideoQualities() }
        }
    }

    @Test
    fun bindOutcome_extensionUnusable_fallsBackToTheDefaultModeAndMovesTheTabs() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            every { modeDelegate.select(any()) } answers {
                val mode = firstArg<CameraMode>()
                stateHolder.update { state -> state.copy(mode = mode) }
                true
            }
            every { cameraDelegate.canBeginBind(forced = any()) } returns true
            every { cameraDelegate.selectLens(isQrMode = any(), extensionMode = any()) } returns
                ViewfinderBindTarget(qrLensFacing = null)
            every { cameraDelegate.bindCamera(any()) } returnsMany listOf(
                BindOutcome.EXTENSION_UNUSABLE,
                BindOutcome.BOUND,
            )

            viewModel.onAction(CameraAction.ModeSelected(CameraMode.NIGHT))

            assertEquals(CameraMode.CAMERA, stateHolder.state.value.mode)
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Panel.HideExposure,
                    ViewfinderScreenEffect.ShowMessage(R.string.extension_mode_unavailable),
                    ViewfinderScreenEffect.Panel.HideExposure,
                    ViewfinderScreenEffect.Panel.HideZoom,
                    ViewfinderScreenEffect.GoToModeTab(CameraMode.CAMERA),
                    ViewfinderScreenEffect.GoToModeTab(CameraMode.CAMERA),
                ),
                effects,
            )
        }
    }

    @Test
    fun torchToggleClicked_togglesTheTorch() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(CameraAction.TorchToggleClicked)

            verify(exactly = 1) { cameraDelegate.toggleTorch() }
        }
    }

    private companion object {
        const val FOCUS_TIMEOUT_SECONDS = 3L
        const val QR_TEXT = "https://grapheneos.org"
    }
}
