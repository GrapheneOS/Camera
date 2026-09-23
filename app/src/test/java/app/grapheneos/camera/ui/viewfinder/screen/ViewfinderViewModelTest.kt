package app.grapheneos.camera.ui.viewfinder.screen

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.location.repository.LocationRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.ImageSaverException
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.model.RecordingPhase
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
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
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val recordingDelegate = mockk<ViewfinderRecordingDelegate>(relaxed = true)
    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    private val sessionEvents = MutableSharedFlow<CameraSessionEvent>()
    private val captureEvents = MutableSharedFlow<CapturedImageEvent>()
    private val recordingEvents = MutableSharedFlow<RecordedVideoEvent>()

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
        every { captureDelegate.captureEvents } returns captureEvents
        every { recordingDelegate.recordingEvents } returns recordingEvents
    }

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
    fun stabilizationToggled_storesTheSettingBeforeRebinding() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(SettingsAction.StabilizationToggled(enabled = true))

            verifyOrder {
                settingsDelegate.setEnableEis(true)
                cameraDelegate.canBeginBind(forced = true)
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
                cameraDelegate.canBeginBind(forced = true)
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
                cameraDelegate.canBeginBind(forced = true)
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
            verify(exactly = 0) { cameraDelegate.canBeginBind(forced = any()) }
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
                cameraDelegate.canBeginBind(forced = true)
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
                cameraDelegate.canBeginBind(forced = true)
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
            assertTrue(ViewfinderScreenEffect.SetLocationUpdates(enabled = false) in effects)
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
            assertTrue(ViewfinderScreenEffect.SetLocationUpdates(enabled = true) in effects)
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
    fun capturedPreviewDismissed_forgetsThePreviewBeforeRebinding() {
        runTest {
            every { cameraDelegate.canBeginBind(forced = true) } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            viewModel.onAction(LifecycleAction.CapturedPreviewDismissed)

            verifyOrder {
                captureDelegate.dismissCapturedPreview()
                cameraDelegate.canBeginBind(forced = true)
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
    fun recordingRequested_withTheCameraReady_prepticksTheRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingRequested(hasAudioPermission = true))

            verifyOrder {
                recordingDelegate.requestRecording()
                recordingDelegate.prepareRecording(includeLocation = false, includeAudio = true)
            }
        }
    }

    @Test
    fun recordingRequested_withoutTheMicrophone_asksForItInsteadOfRecording() {
        runTest {
            every { cameraDelegate.canRecord } returns true

            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(RecordingAction.RecordingRequested(hasAudioPermission = false))

            verify(exactly = 0) {
                recordingDelegate.prepareRecording(includeLocation = any(), includeAudio = any())
            }
            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertEquals(
                listOf(ViewfinderScreenEffect.Recording.RequestAudioPermission),
                effects,
            )
        }
    }

    @Test
    fun recordingRequested_withoutACameraToRecordWith_doesNothing() {
        runTest {
            every { cameraDelegate.canRecord } returns false

            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingRequested(hasAudioPermission = true))

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
            viewModel.onAction(RecordingAction.RecordingMuteToggled(muted = true))
            viewModel.onAction(RecordingAction.StartSoundPlayed)
            viewModel.onAction(RecordingAction.RecordingStopRequested)

            verifyOrder {
                recordingDelegate.setPaused(paused = true)
                recordingDelegate.setMuted(muted = true)
                recordingDelegate.startPreparedRecording()
                recordingDelegate.requestStop()
            }
        }
    }

    @Test
    fun pauseAndMute_outsideARecording_areIgnored() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)

            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(RecordingAction.RecordingMuteToggled(muted = true))

            verify(exactly = 0) { recordingDelegate.setPaused(paused = any()) }
            verify(exactly = 0) { recordingDelegate.setMuted(muted = any()) }
        }
    }

    @Test
    fun recordingEvents_soundTheStartAndDriveTheState() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.ReadyToStart)
            recordingEvents.emit(RecordedVideoEvent.Started)
            recordingEvents.emit(RecordedVideoEvent.Progressed(duration = 5.seconds))

            verifyOrder {
                recordingDelegate.startRecording()
                recordingDelegate.setRecordedDuration(5.seconds)
            }
            assertEquals(listOf(ViewfinderScreenEffect.Recording.PlayStartSound), effects)
        }
    }

    @Test
    fun anAbandonedRecording_stopsWithoutTheStopSound() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.Abandoned)

            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertEquals(listOf(ViewfinderScreenEffect.Recording.Stopped), effects)
        }
    }

    @Test
    fun aFinishedRecording_isAnnounced() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.Finished(outcome = RecordingOutcome.Saved))

            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Recording.Stopped,
                    ViewfinderScreenEffect.Recording.PlayStopSound,
                ),
                effects,
            )
        }
    }

    @Test
    fun aRecordingTooShortToPlay_saysSo() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(
                RecordedVideoEvent.Finished(outcome = RecordingOutcome.NothingPlayableWritten),
            )

            assertTrue(
                ViewfinderScreenEffect.ShowMessage(
                    R.string.recording_too_short_to_be_saved,
                ) in effects,
            )
        }
    }

    @Test
    fun anInterruptedRecording_saysSo() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(
                RecordedVideoEvent.Finished(
                    outcome = RecordingOutcome.Interrupted(errorCode = 7, hasContent = true),
                ),
            )

            assertTrue(ViewfinderScreenEffect.Recording.Interrupted(errorCode = 7) in effects)
        }
    }

    @Test
    fun anUnusableOutput_revertsTheStorageLocationAndStops() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            recordingEvents.emit(RecordedVideoEvent.OutputUnavailable)

            verify(exactly = 1) { recordingDelegate.markStopped() }
            assertTrue(
                ViewfinderScreenEffect.ShowMessage(
                    R.string.unable_to_access_output_file,
                ) in effects,
            )
        }
    }

    @Test
    fun shutterClicked_withTheCameraReady_takesThePicture() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 1) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun shutterClicked_inACaptureSession_takesAPictureToHandBack() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true

            val viewModel = createViewModel(
                applicationScope = backgroundScope,
                entryPoint = ENTRY_POINT.copy(isCaptureSession = true),
            )
            stateHolder.update { it.copy(session = it.session.copy(canTakePicture = true)) }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 1) { captureDelegate.takePreviewPicture() }
            verify(exactly = 0) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun previewCaptured_showsItAndStopsTheLoader() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            val bitmap = createBitmap(1, 1)

            captureEvents.emit(CapturedImageEvent.PreviewCaptured(bitmap = bitmap))

            verify(exactly = 1) { captureDelegate.finishPictureSave() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Picture.PreviewCaptured(bitmap),
                    ViewfinderScreenEffect.ShowMessage(R.string.image_captured_successfully),
                ),
                effects,
            )
        }
    }

    @Test
    fun previewFailed_reportsItAndStopsTheLoader() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.PreviewFailed)

            verify(exactly = 1) { captureDelegate.finishPictureSave() }
            assertEquals(listOf(ViewfinderScreenEffect.Picture.PreviewFailed), effects)
        }
    }

    @Test
    fun shutterClicked_whileTheCameraCannotCapture_saysSoAndTakesNothing() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.ShowMessage(
                        R.string.unsupported_taking_picture_while_recording,
                    ),
                ),
                effects,
            )
        }
    }

    @Test
    fun shutterClicked_whileACaptureIsInFlight_takesNothing() {
        runTest {
            every { cameraDelegate.isCameraReady } returns true
            val viewModel = createViewModel(applicationScope = backgroundScope)
            stateHolder.update {
                it.copy(
                    session = it.session.copy(canTakePicture = true),
                    capture = it.capture.copy(isTakingPicture = true),
                )
            }

            viewModel.onAction(CaptureAction.ShutterClicked)

            verify(exactly = 0) { captureDelegate.takePicture() }
        }
    }

    @Test
    fun captured_startsSavingItAndSoundsTheShutterBeforeFlashingThePreview() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.Captured)

            verify(exactly = 1) { captureDelegate.startPictureSave() }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.Picture.Captured,
                    ViewfinderScreenEffect.FlashPreview(selfIlluminate = false),
                ),
                effects,
            )
        }
    }

    @Test
    fun saved_handsTheItemOver() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            val item = CapturedItem(ITEM_TYPE_IMAGE, "20260920_120000_000", Uri.EMPTY)

            captureEvents.emit(CapturedImageEvent.Saved(item = item))

            assertEquals(listOf(ViewfinderScreenEffect.Picture.Saved(item)), effects)
        }
    }

    @Test
    fun thumbnailReady_showsItAndFinishesTheSave() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.ThumbnailReady(thumbnail = THUMBNAIL))

            verify(exactly = 1) { captureDelegate.finishPictureSave() }
            assertEquals(
                listOf(ViewfinderScreenEffect.Picture.ThumbnailReady(THUMBNAIL)),
                effects,
            )
        }
    }

    @Test
    fun locationUnavailable_saysSo() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(CapturedImageEvent.LocationUnavailable)

            assertEquals(
                listOf(ViewfinderScreenEffect.ShowMessage(R.string.location_unavailable)),
                effects,
            )
        }
    }

    @Test
    fun captureFailed_reportsTheFailureAndEndsBothStages() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)
            val cause = IOException("no camera")

            captureEvents.emit(
                CapturedImageEvent.CaptureFailed(errorCode = CAPTURE_ERROR_CODE, cause = cause),
            )

            verify(exactly = 1) { captureDelegate.finishPictureSave() }

            val failure = effects.single() as ViewfinderScreenEffect.Picture.CaptureFailed
            assertEquals(CAPTURE_ERROR_CODE, failure.errorCode)
            assertEquals(cause.javaClass.name, failure.details.name)
        }
    }

    @Test
    fun saveFailed_reportsTheStageThatFailed() {
        runTest {
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            captureEvents.emit(
                CapturedImageEvent.SaveFailed(
                    cause = ImageSaverException(ImageSaverException.Place.FILE_WRITE),
                    alreadyReported = true,
                ),
            )

            verify(exactly = 1) { captureDelegate.finishPictureSave() }

            val failure = effects.single() as ViewfinderScreenEffect.Picture.SaveFailed
            assertEquals(SAVE_FAILURE_STAGE, failure.stage)
            assertTrue(failure.alreadyReported)
        }
    }

    @Test
    fun selfTimerStartClicked_countsDownTheStoredDurationAndReportsTheEnd() {
        runTest {
            val storedSeconds = CameraSettings().selfTimerDurationSeconds
            every {
                captureDelegate.selfTimerCountdown(seconds = storedSeconds)
            } returns flowOf(2, 1)
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CaptureAction.SelfTimerStartClicked)

            verifyOrder {
                captureDelegate.setSelfTimerRunning(true)
                captureDelegate.setSelfTimerRunning(false)
            }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.SelfTimer.Started,
                    ViewfinderScreenEffect.SelfTimer.Ticked(secondsLeft = 2),
                    ViewfinderScreenEffect.SelfTimer.Ticked(secondsLeft = 1),
                    ViewfinderScreenEffect.SelfTimer.Finished,
                ),
                effects,
            )
        }
    }

    @Test
    fun selfTimerCancelClicked_putsTheControlsBackOnlyWhileACountdownIsUp() {
        runTest {
            every { captureDelegate.selfTimerCountdown(any()) } returns endlessSelfTimer()
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CaptureAction.SelfTimerStartClicked)
            viewModel.onAction(CaptureAction.SelfTimerCancelClicked)
            viewModel.onAction(CaptureAction.SelfTimerCancelClicked)

            verifyOrder {
                captureDelegate.setSelfTimerRunning(true)
                captureDelegate.setSelfTimerRunning(false)
            }
            verify(exactly = 1) { captureDelegate.setSelfTimerRunning(false) }
            assertEquals(
                listOf(
                    ViewfinderScreenEffect.SelfTimer.Started,
                    ViewfinderScreenEffect.SelfTimer.Ticked(secondsLeft = 3),
                    ViewfinderScreenEffect.SelfTimer.Cancelled,
                ),
                effects,
            )
        }
    }

    @Test
    fun screenDestroyed_dropsTheCountdownWithoutPuttingTheControlsBack() {
        runTest {
            every { captureDelegate.selfTimerCountdown(any()) } returns endlessSelfTimer()
            val viewModel = createViewModel(applicationScope = backgroundScope)
            val effects = collectEffects(viewModel)

            viewModel.onAction(CaptureAction.SelfTimerStartClicked)
            viewModel.onAction(LifecycleAction.ScreenDestroyed)
            viewModel.onAction(CaptureAction.SelfTimerCancelClicked)

            assertEquals(
                listOf(
                    ViewfinderScreenEffect.SelfTimer.Started,
                    ViewfinderScreenEffect.SelfTimer.Ticked(secondsLeft = 3),
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
            recordingDelegate = recordingDelegate,
            resolveDroppedVideoQuality = mockk(),
            revertToMediaStoreLocation = revertToMediaStoreLocation,
            locationRepository = locationRepository,
            uiStateMapper = mockk(relaxed = true),
            cameraBindSettingsMapper = mockk(relaxed = true),
            applicationScope = applicationScope,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )

        val boundStateHolder = slot<ViewfinderStateHolder>()
        verify { modeDelegate.bind(capture(boundStateHolder)) }
        stateHolder = boundStateHolder.captured

        return viewModel
    }

    private fun endlessSelfTimer(): Flow<Int> {
        return flow {
            emit(3)
            awaitCancellation()
        }
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
        const val CAPTURE_ERROR_CODE = 2
        const val SAVE_FAILURE_STAGE = "FILE_WRITE"

        val THUMBNAIL: Bitmap = createBitmap(1, 1)

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
