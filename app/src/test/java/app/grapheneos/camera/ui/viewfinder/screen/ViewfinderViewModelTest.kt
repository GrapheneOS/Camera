package app.grapheneos.camera.ui.viewfinder.screen

import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val revertToMediaStoreLocation = mockk<RevertToMediaStoreLocation>()
    private val settingsDelegate = mockk<ViewfinderSettingsDelegate>(relaxed = true)
    private val modeDelegate = mockk<ViewfinderModeDelegate>(relaxed = true)
    private val cameraDelegate = mockk<ViewfinderCameraDelegate>(relaxed = true)
    private val captureDelegate = mockk<ViewfinderCaptureDelegate>(relaxed = true)
    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    private val sessionEvents = MutableSharedFlow<CameraSessionEvent>()

    private val reverted = CompletableDeferred<Unit>()

    private var revertFinished = false

    private lateinit var stateHolder: ViewfinderStateHolder

    @Before
    fun setUp() {
        coEvery { revertToMediaStoreLocation() } coAnswers {
            reverted.await()
            revertFinished = true
        }
        every { modeDelegate.defaultMode } returns CameraMode.CAMERA
        every { cameraDelegate.sessionEvents } returns sessionEvents
    }

    @Test
    fun screenResumed_withTheProviderReady_startsTheBindBeforeReturning() {
        runTest {
            every { cameraDelegate.isProviderReady } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            verify(exactly = 1) { cameraDelegate.beginBind(forced = true) }
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
            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
        }
    }

    @Test
    fun providerReady_startsTheBind() {
        runTest {
            createViewModel(applicationScope = backgroundScope)
            sessionEvents.emit(CameraSessionEvent.ProviderReady(forced = true))

            verify(exactly = 1) { cameraDelegate.beginBind(forced = true) }
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

            verify(exactly = 1) { cameraDelegate.onZoomStateChanged() }
            assertEquals(listOf(ViewfinderScreenEffect.ShowZoomPanel), effects)
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

            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
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
            every { cameraDelegate.beginBind(forced = any()) } returns true
            every { cameraDelegate.selectLens(isQrMode = any(), extensionMode = any()) } returns
                ViewfinderBindTarget(qrLensFacing = LensFacing.FRONT)
            every { cameraDelegate.bindCamera(any()) } returns BindOutcome.BOUND

            viewModel.onAction(LifecycleAction.QrResultDismissed)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.HideExposurePanel,
                    ViewfinderScreenEffect.ShowMessage(R.string.qr_rear_camera_unavailable),
                    ViewfinderScreenEffect.HideZoomPanel,
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
    fun stabilizationToggled_storesTheSettingBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.StabilizationToggled(enabled = true))

            verifyOrder {
                settingsDelegate.setEnableEis(true)
                cameraDelegate.beginBind(forced = true)
            }
        }
    }

    @Test
    fun focusLockToggled_storesTheSettingBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.FocusLockToggled(enabled = true))

            verifyOrder {
                settingsDelegate.setWaitForFocusLock(true)
                cameraDelegate.beginBind(forced = true)
            }
        }
    }

    @Test
    fun aspectRatioToggleClicked_storesTheOtherRatioBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(settings = CameraSettings(aspectRatio = AspectRatio.RATIO_4_3))
            }

            viewModel.onAction(CameraAction.AspectRatioToggleClicked)

            verifyOrder {
                settingsDelegate.setAspectRatio(AspectRatio.RATIO_16_9)
                cameraDelegate.beginBind(forced = true)
            }
        }
    }

    @Test
    fun videoQualitySelected_theOneAlreadyInUse_leavesTheCameraAlone() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(videoQuality = VideoQuality.UHD))
            }

            viewModel.onAction(SettingsAction.VideoQualitySelected(VideoQuality.UHD))

            verify(exactly = 0) { settingsDelegate.setVideoQuality(any()) }
            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
        }
    }

    @Test
    fun videoQualitySelected_anotherOne_storesItBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(videoQuality = VideoQuality.UHD))
            }

            viewModel.onAction(SettingsAction.VideoQualitySelected(VideoQuality.FHD))

            verifyOrder {
                settingsDelegate.setVideoQuality(VideoQuality.FHD)
                cameraDelegate.beginBind(forced = true)
            }
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
                cameraDelegate.beginBind(forced = true)
            }
        }
    }

    @Test
    fun flashToggleClicked_withoutAFlash_saysSoAndStoresNothing() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CameraAction.FlashToggleClicked)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(R.string.flash_unavailable_in_selected_mode),
                ),
                effects,
            )
            verify(exactly = 0) { settingsDelegate.setFlashMode(any()) }
        }
    }

    @Test
    fun flashToggleClicked_inAVideoOnlyEntryPoint_saysSoEvenWithAFlash() {
        runTest {
            val viewModel = createViewModel(
                applicationScope = backgroundScope,
                entryPoint = ENTRY_POINT.copy(requiresVideoModeOnly = true),
            )
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(session = ViewfinderSessionState(isFlashAvailable = true))
            }

            viewModel.onAction(CameraAction.FlashToggleClicked)

            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.flash_switch_unsupported)),
                effects,
            )
            verify(exactly = 0) { settingsDelegate.setFlashMode(any()) }
        }
    }

    @Test
    fun flashToggleClicked_withAFlashThatIsOff_turnsItOn() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(
                    session = ViewfinderSessionState(isFlashAvailable = true),
                    flashMode = FlashMode.OFF,
                )
            }

            viewModel.onAction(CameraAction.FlashToggleClicked)

            verifyOrder {
                settingsDelegate.setFlashMode(FlashMode.ON)
                cameraDelegate.applyFlashMode(FlashMode.ON)
            }
        }
    }

    @Test
    fun previewStreamingStarted_storedGeoTaggingWithoutPermission_staysOff() {
        runTest {
            every { locationRepository.shouldAskForPermission() } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(geoTagging = true))
            }

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { settingsDelegate.setGeoTagging(false) }
            assertTrue(ViewfinderScreenEffect.StopLocationUpdates in effects)
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
    fun previewStreamingStarted_storedGeoTaggingWithPermission_turnsLocationUpdatesOn() {
        runTest {
            every { locationRepository.shouldAskForPermission() } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(geoTagging = true))
            }

            viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

            verify(exactly = 1) { settingsDelegate.setGeoTagging(true) }
            assertTrue(ViewfinderScreenEffect.StartLocationUpdates in effects)
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
            every { cameraDelegate.beginBind(forced = any()) } returns true
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
                    ViewfinderScreenEffect.HideExposurePanel,
                    ViewfinderScreenEffect.ShowMessage(R.string.extension_mode_unavailable),
                    ViewfinderScreenEffect.HideExposurePanel,
                    ViewfinderScreenEffect.HideZoomPanel,
                    ViewfinderScreenEffect.GoToModeTab(CameraMode.CAMERA),
                    ViewfinderScreenEffect.GoToModeTab(CameraMode.CAMERA),
                ),
                effects,
            )
        }
    }

    @Test
    fun capturedPreviewDismissed_forgetsThePreviewBeforeRebinding() {
        runTest {
            every { cameraDelegate.beginBind(forced = true) } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.CapturedPreviewDismissed)

            verifyOrder {
                captureDelegate.dismissCapturedPreview()
                cameraDelegate.beginBind(forced = true)
            }
        }
    }

    @Test
    fun capturedPreviewShown_isRecorded() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(CaptureAction.CapturedPreviewShown)

            verify(exactly = 1) { captureDelegate.showCapturedPreview() }
        }
    }

    @Test
    fun recordingActions_reachTheCaptureDelegate() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(CaptureAction.RecordingStarted)
            viewModel.onAction(CaptureAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(CaptureAction.RecordingStopped)

            verifyOrder {
                captureDelegate.startRecording()
                captureDelegate.setRecordingPaused(paused = true)
                captureDelegate.stopRecording()
            }
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

    private fun createViewModel(
        applicationScope: CoroutineScope,
        entryPoint: CameraEntryPoint = ENTRY_POINT,
    ): ViewfinderViewModel {
        val viewModel = ViewfinderViewModel(
            entryPoint = entryPoint,
            settingsDelegate = settingsDelegate,
            modeDelegate = modeDelegate,
            cameraDelegate = cameraDelegate,
            captureDelegate = captureDelegate,
            resolveDroppedVideoQuality = mockk(),
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            locationRepository = locationRepository,
            uiStateMapper = mockk(relaxed = true),
            cameraBindSettingsMapper = mockk(relaxed = true),
            applicationScope = applicationScope,
            mainDispatcher = mainDispatcherRule.testDispatcher,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
        )

        val boundStateHolder = slot<ViewfinderStateHolder>()
        verify { modeDelegate.bind(capture(boundStateHolder)) }
        stateHolder = boundStateHolder.captured

        return viewModel
    }

    private fun TestScope.collectEffects(
        viewModel: ViewfinderViewModel,
    ): List<ViewfinderScreenEffect> {
        val effects = mutableListOf<ViewfinderScreenEffect>()

        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            viewModel.effects.collect { effects += it }
        }

        return effects
    }

    private companion object {
        const val FOCUS_TIMEOUT_SECONDS = 3L
        const val QR_TEXT = "https://grapheneos.org"

        val ENTRY_POINT = CameraEntryPoint(
            isSecureSession = false,
            isCaptureSession = false,
            isVideoOnlySession = false,
            requiresVideoModeOnly = false,
            allowsQrScanning = true,
            showsCameraModeTabs = true,
        )
    }
}
