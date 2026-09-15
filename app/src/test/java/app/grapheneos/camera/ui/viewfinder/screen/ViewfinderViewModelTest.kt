package app.grapheneos.camera.ui.viewfinder.screen

import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
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
    fun providerReady_fromTheAttachedSession_startsTheBind() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            attach(viewModel)

            sessionEvents.emit(CameraSessionEvent.ProviderReady(forced = true))

            verify(exactly = 1) { cameraDelegate.beginBind(forced = true) }
        }
    }

    @Test
    fun sessionEvents_afterDetach_areIgnored() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            attach(viewModel)
            viewModel.detach()

            sessionEvents.emit(CameraSessionEvent.ProviderReady(forced = true))

            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
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
                it.copy(modeSettings = ModeSettings(videoQuality = Quality.UHD))
            }

            viewModel.onAction(SettingsAction.VideoQualitySelected(Quality.UHD))

            verify(exactly = 0) { settingsDelegate.setVideoQuality(any()) }
            verify(exactly = 0) { cameraDelegate.beginBind(forced = any()) }
        }
    }

    @Test
    fun videoQualitySelected_anotherOne_storesItBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(modeSettings = ModeSettings(videoQuality = Quality.UHD))
            }

            viewModel.onAction(SettingsAction.VideoQualitySelected(Quality.FHD))

            verifyOrder {
                settingsDelegate.setVideoQuality(Quality.FHD)
                cameraDelegate.beginBind(forced = true)
            }
        }
    }

    @Test
    fun scanAllCodesToggleClicked_refreshesTheHintsOnlyAfterStoring() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.ScanAllCodesToggleClicked)

            verifyOrder {
                settingsDelegate.toggleScanAllCodes()
                cameraDelegate.refreshQrHints()
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
                    flashMode = ImageCapture.FLASH_MODE_OFF,
                )
            }

            viewModel.onAction(CameraAction.FlashToggleClicked)

            verifyOrder {
                settingsDelegate.setFlashMode(ImageCapture.FLASH_MODE_ON)
                cameraDelegate.applyFlashMode(ImageCapture.FLASH_MODE_ON)
            }
        }
    }

    @Test
    fun previewStreamingStarted_storedGeoTaggingWithoutPermission_staysOff() {
        runTest {
            every { cameraDelegate.shouldAskForLocationPermission() } returns true

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
    fun previewStreamingStarted_storedGeoTaggingWithPermission_turnsLocationUpdatesOn() {
        runTest {
            every { cameraDelegate.shouldAskForLocationPermission() } returns false

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
                ViewfinderBindTarget(rotation = 0, qrLensFacing = null)
            every { cameraDelegate.bindCamera(any()) } returnsMany listOf(
                BindOutcome.EXTENSION_UNUSABLE,
                BindOutcome.BOUND,
            )

            viewModel.onAction(CameraAction.ModeSelected(CameraMode.NIGHT))

            assertEquals(CameraMode.CAMERA, stateHolder.state.value.mode)
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(R.string.extension_mode_unavailable),
                    ViewfinderScreenEffect.GoToModeTab(CameraMode.CAMERA),
                    ViewfinderScreenEffect.GoToModeTab(CameraMode.CAMERA),
                ),
                effects,
            )
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
            resolveDroppedVideoQuality = mockk(),
            revertToMediaStoreLocation = revertToMediaStoreLocation,
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

    private fun attach(viewModel: ViewfinderViewModel) {
        viewModel.attach(
            environment = mockk(relaxed = true),
            chrome = mockk(relaxed = true),
            session = mockk(relaxed = true),
        )
    }

    private companion object {
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
