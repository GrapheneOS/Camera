package app.grapheneos.camera.ui.viewfinder.screen

import android.view.Surface
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureCause
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderDialog
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCameraEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderLevelState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelTest : ViewfinderViewModelTestBase() {

    @Test
    fun levelState_isMappedInitiallyAndWhenTheLevelChanges() {
        runTest {
            val initial = ViewfinderLevelState()
            val changed = initial.copy(visible = true, tiltDegrees = -3, horizonDegrees = -12)
            val hidden = changed.copy(visible = false)
            val initialUiState = mockk<LevelUiState>()
            val changedUiState = mockk<LevelUiState>()
            val hiddenUiState = mockk<LevelUiState>()
            every { uiStateMapper.mapLevel(initial) } returns initialUiState
            every { uiStateMapper.mapLevel(changed) } returns changedUiState
            every { uiStateMapper.mapLevel(hidden) } returns hiddenUiState
            val viewModel = createViewModel()
            val screenState = stateHolder.state.value
            assertSame(initialUiState, viewModel.levelUiState.value)

            val levels = mutableListOf<LevelUiState>()
            val collection = backgroundScope.launch(mainDispatcherRule.testDispatcher) {
                viewModel.levelUiState.collect { level -> levels += level }
            }

            assertEquals(listOf(initialUiState), levels)

            levelStateHolder.update { changed }

            assertSame(changedUiState, viewModel.levelUiState.value)
            assertSame(changedUiState, levels.last())
            assertSame(screenState, stateHolder.state.value)

            collection.cancel()
            levelStateHolder.update { hidden }

            assertSame(hiddenUiState, viewModel.levelUiState.value)
        }
    }

    @Test
    fun screenResumed_withTheProviderReady_startsTheBindBeforeReturning() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel()
            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = true) }
            verify(exactly = 0) { cameraDelegate.initialize(forced = any(), extensionMode = any()) }
        }
    }

    @Test
    fun screenResumed_withoutAProvider_asksTheSessionForOne() {
        runTest {
            every { cameraDelegate.isProviderReady } returns false

            val viewModel = createViewModel()
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
            createViewModel()
            cameraEvents.emit(ViewfinderCameraEvent.ProviderReady(forced = true))

            verify(exactly = 1) { cameraDelegate.canBeginBind(forced = true) }
        }
    }

    @Test
    fun screenCreated_lendsTheScreenToTheCameraUntilTheScreenIsDestroyed() {
        runTest {
            val viewModel = createViewModel()
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
    fun startCamera_forQrWithoutARearLens_saysItScansWithTheFrontOne() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)
            every { cameraDelegate.canBeginBind(forced = any()) } returns true
            every { cameraDelegate.selectLens(isQrMode = any(), extensionMode = any()) } returns
                ViewfinderBindTarget(qrLensFacing = LensFacing.FRONT)
            every { cameraDelegate.bindCamera(any()) } returns BindOutcome.BOUND

            viewModel.onAction(DialogAction.QrResultDismissed)

            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.qr_rear_camera_unavailable)),
                effects,
            )
            verifyOrder {
                previewControlsDelegate.hideExposurePanel()
                cameraDelegate.bindCamera(any())
                previewControlsDelegate.hideZoomPanel()
            }
        }
    }

    @Test
    fun storageLocationNotFound_isHandedToTheGallery() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CaptureAction.StorageLocationNotFound)

            verify(exactly = 1) { galleryDelegate.onStorageLocationNotFound() }
        }
    }

    @Test
    fun qrResultDismissed_rebindsOnlyOnceTheResultIsDismissed() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(DialogAction.QrResultDismissed)

            verifyOrder {
                cameraDelegate.dismissQrResult()
                cameraDelegate.canBeginBind(forced = true)
            }
        }
    }

    @Test
    fun previewTapped_focusesThereAndShowsThePanels() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CameraAction.Preview.Tapped(x = 10f, y = 20f))

            verify(exactly = 1) { cameraControlsDelegate.focusAt(x = 10f, y = 20f) }
            verify(exactly = 1) { previewControlsDelegate.showFocusPanels() }
        }
    }

    @Test
    fun previewTapped_inQrMode_neitherFocusesNorShowsThePanels() {
        runTest {
            val viewModel = createViewModel()
            stateHolder.update { it.copy(mode = CameraMode.QR_SCAN) }

            viewModel.onAction(CameraAction.Preview.Tapped(x = 10f, y = 20f))

            verify(exactly = 0) { cameraControlsDelegate.focusAt(x = any(), y = any()) }
            verify(exactly = 0) { previewControlsDelegate.showFocusPanels() }
        }
    }

    @Test
    fun exposureSliderDragged_appliesItAndKeepsThePanelShown() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CameraAction.ExposureSliderDragged(compensationIndex = 2))

            verifyOrder {
                cameraControlsDelegate.setExposureCompensation(2)
                previewControlsDelegate.showExposurePanel()
            }
        }
    }

    @Test
    fun zoomChanged_showsTheZoomPanel() {
        runTest {
            createViewModel()

            zoomChanges.emit(Unit)

            verify(exactly = 1) { previewControlsDelegate.showZoomPanel() }
        }
    }

    @Test
    fun zoomKeys_stepTheZoomByOneInEitherDirection() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CameraAction.ZoomInKeyPressed)
            viewModel.onAction(CameraAction.ZoomOutKeyPressed)

            verifyOrder {
                cameraControlsDelegate.stepZoom(1f)
                cameraControlsDelegate.stepZoom(-1f)
            }
        }
    }

    @Test
    fun bindOutcome_extensionUnusable_fallsBackToTheDefaultModeAndMovesTheTabs() {
        runTest {
            val viewModel = createViewModel()
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
                    ViewfinderScreenEffect.ShowMessage(R.string.extension_mode_unavailable),
                    ViewfinderScreenEffect.ModeTab.GoTo(CameraMode.CAMERA),
                    ViewfinderScreenEffect.ModeTab.GoTo(CameraMode.CAMERA),
                ),
                effects,
            )
        }
    }

    @Test
    fun displayRotationChanged_turnsThePreviewAndTheIcons() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(CameraAction.DisplayRotationChanged(rotation = Surface.ROTATION_90))

            verify(exactly = 1) { cameraDelegate.setPreviewRotation(Surface.ROTATION_90) }
            verify(exactly = 1) { orientationDelegate.setDisplayRotation(Surface.ROTATION_90) }
        }
    }

    @Test
    fun permissionSettingsClicked_closesTheDialogAndOpensTheSettings() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(DialogAction.Choice.PermissionSettingsClicked)

            verifyOrder {
                dialogDelegate.close()
                permissionDelegate.openSettings()
            }
        }
    }

    @Test
    fun screenResumed_tracksOrientationAndLocationAndKeepsTheScreenAwakeUntilPaused() {
        runTest {
            val tracking = mutableSetOf<String>()
            coEvery { orientationDelegate.trackOrientation() } coAnswers {
                trackUntilCancelled(tracking, name = "orientation")
            }
            coEvery { locationDelegate.trackLocation() } coAnswers {
                trackUntilCancelled(tracking, name = "location")
            }
            coEvery { screenWakeDelegate.keepScreenAwake() } coAnswers {
                trackUntilCancelled(tracking, name = "screen wake")
            }

            val viewModel = createViewModel()
            viewModel.onAction(LifecycleAction.ScreenResumed)
            assertEquals(setOf("orientation", "location", "screen wake"), tracking)

            viewModel.onAction(LifecycleAction.ScreenPaused)
            assertTrue(tracking.isEmpty())
        }
    }

    @Test
    fun screenInteracted_extendsTheScreenWake() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenInteracted)

            verify(exactly = 1) { screenWakeDelegate.onScreenInteracted() }
        }
    }

    @Test
    fun screenResumed_refreshesTheThumbnail() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { galleryDelegate.refreshThumbnail() }
        }
    }

    private suspend fun trackUntilCancelled(
        tracking: MutableSet<String>,
        name: String,
    ) {
        tracking += name

        try {
            awaitCancellation()
        } finally {
            tracking -= name
        }
    }
}
