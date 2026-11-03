package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.TorchControl
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.capture.coordinator.VideoRecorderImpl
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutputImpl
import app.grapheneos.camera.domain.capture.usecase.DiscardRecordingImpl
import app.grapheneos.camera.domain.capture.usecase.PublishRecordingImpl
import app.grapheneos.camera.domain.capture.usecase.ResolveCaptureLocationImpl
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.testutil.FakeVideoRecordingSession
import app.grapheneos.camera.testutil.MainDispatcherRule
import app.grapheneos.camera.testutil.cameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegateImpl
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegateImpl
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegateImpl
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegateImpl
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegateImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import app.grapheneos.camera.ui.viewfinder.screen.model.input.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewfinderRecordingFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recordingSession = FakeVideoRecordingSession()

    private val mappedState = slot<ViewfinderState>()
    private val uiStateMapper = mockk<ViewfinderUiStateMapper>(relaxed = true) {
        every { map(capture(mappedState)) } returns mockk()
    }
    private val bindSettings = mockk<CameraBindSettings>()
    private val cameraBindSettingsMapper = mockk<CameraBindSettingsMapper> {
        every { map(any(), any()) } returns bindSettings
    }

    private var outputCreated = CompletableDeferred(Unit)
    private var outputPublished = CompletableDeferred(Unit)

    private val torch = mockk<TorchControl>(relaxed = true)
    private val captureOutputRepository = mockk<CaptureOutputRepository>(relaxed = true) {
        coEvery { createVideo(any(), any(), any()) } coAnswers {
            outputCreated.await()
            Result.success(OUTPUT_URI)
        }
        coEvery { openForWriting(any()) } returns Result.success(mockk(relaxed = true))
        coEvery { publish(any()) } coAnswers {
            outputPublished.await()
            Result.success(Unit)
        }
    }

    private val cameraSession = mockk<CameraSession>(relaxed = true) {
        every { camera } returns mockk()
        every { videoCapture } returns mockk()
        every { events } returns emptyFlow()
        every { isActive } returns true
        every { bind(any()) } returns BindOutcome.BOUND
    }

    private val capturedItemRepository = mockk<CapturedItemRepository>(relaxed = true) {
        every { storageLocation } returns flowOf(CapturedItemRepository.MEDIA_STORE_LOCATION)
    }

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { settings } returns MutableStateFlow(CameraSettings())
        every { modeSettings(any()) } returns ModeSettings()
    }

    private val host = ViewfinderHost(
        previewTarget = mockk(relaxed = true),
        previewFrames = mockk(relaxed = true),
        thumbnailSize = ThumbnailSize(width = 1, height = 1),
    )

    @Test
    fun videoOnlySession_startsInVideoModeAndLoadsTheVideoSlot() {
        runTest {
            assertVideoOnlyModeAndSettings(isCaptureSession = false)
        }
    }

    @Test
    fun videoCaptureSession_startsInVideoModeAndLoadsTheVideoSlot() {
        runTest {
            assertVideoOnlyModeAndSettings(isCaptureSession = true)
        }
    }

    @Test
    fun videoOnlySession_anUnusableExtension_fallsBackToVideo() {
        runTest {
            prepareVideoOnlyCamera()
            every { cameraSession.bind(any()) } returnsMany listOf(
                BindOutcome.EXTENSION_UNUSABLE,
                BindOutcome.BOUND,
            )
            val viewModel = createViewModel(
                entryPoint = cameraEntryPoint(
                    isVideoOnlySession = true,
                    requiresVideoModeOnly = true,
                    showsCameraModeTabs = false,
                ),
            )
            viewModel.onAction(LifecycleAction.ScreenCreated(host))

            viewModel.onAction(CameraAction.ModeSelected(CameraMode.NIGHT))

            assertEquals(CameraMode.VIDEO, state().mode)
            verify(exactly = 1) {
                cameraBindSettingsMapper.map(match { it.mode == CameraMode.NIGHT }, any())
            }
            verify(exactly = 1) {
                cameraBindSettingsMapper.map(match { it.mode == CameraMode.VIDEO }, any())
            }
            verify(exactly = 2) { cameraSession.bind(bindSettings) }
        }
    }

    @Test
    fun aStopBeforeTheOutputIsCreated_startsNothingAndThrowsTheOutputAway() {
        runTest {
            outputCreated = CompletableDeferred()
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(RecordingAction.RecordingRequested)
            viewModel.onAction(RecordingAction.RecordingStopRequested)
            outputCreated.complete(Unit)

            assertEquals(0, recordingSession.startCount)
            assertFalse(state().recording.isActive())
            assertFalse(effects.any { it is ViewfinderScreenEffect.Recording.PlayStartSound })
            coVerify(exactly = 1) { captureOutputRepository.delete(OUTPUT_URI) }
        }
    }

    @Test
    fun aStopWhileTheStartSoundPlays_startsNothingAndThrowsTheOutputAway() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(RecordingAction.RecordingRequested)
            assertTrue(effects.any { it is ViewfinderScreenEffect.Recording.PlayStartSound })

            viewModel.onAction(RecordingAction.RecordingStopRequested)
            viewModel.onAction(RecordingAction.StartSoundPlayed)

            assertEquals(0, recordingSession.startCount)
            assertFalse(state().recording.isActive())
            coVerify(exactly = 1) { captureOutputRepository.delete(OUTPUT_URI) }
        }
    }

    @Test
    fun aPauseAndMuteBeforeTheStart_reachTheRecordingOnceItStarts() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(RecordingAction.RecordingRequested)
            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(RecordingAction.MuteToggleClicked)
            viewModel.onAction(RecordingAction.StartSoundPlayed)

            assertEquals(1, recordingSession.startCount)
            assertTrue(recordingSession.isPaused)
            assertTrue(recordingSession.isMuted)
        }
    }

    @Test
    fun aCaptureSessionRecording_keepsTheCameraUnboundUntilItIsRetaken() {
        runTest {
            outputPublished = CompletableDeferred()
            val viewModel = createViewModel(
                entryPoint = cameraEntryPoint(
                    isCaptureSession = true,
                    requiresVideoModeOnly = true,
                    showsCameraModeTabs = false,
                ),
            )
            assertEquals(CameraMode.VIDEO, state().mode)
            viewModel.onAction(LifecycleAction.ScreenCreated(host))
            advanceTimeBy(1.seconds)
            runCurrent()

            viewModel.onAction(RecordingAction.RecordingRequested)
            viewModel.onAction(RecordingAction.StartSoundPlayed)
            recordingSession.emit(RecordingEvent.Started)
            viewModel.onAction(RecordingAction.RecordingStopRequested)
            recordingSession.emit(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))

            viewModel.onAction(LifecycleAction.ScreenResumed)

            assertEquals(1, recordingSession.stopCount)
            assertFalse(state().capture.hasResult())
            verify(exactly = 0) { cameraSession.bind(any()) }

            outputPublished.complete(Unit)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            assertTrue(state().capture.hasResult())
            verify(exactly = 0) { cameraSession.bind(any()) }

            viewModel.onAction(CaptureAction.RetakeClicked)

            assertFalse(state().capture.hasResult())
            verify(exactly = 1) { cameraSession.bind(any()) }
        }
    }

    private fun TestScope.assertVideoOnlyModeAndSettings(isCaptureSession: Boolean) {
        prepareVideoOnlyCamera()
        val videoSlot = ModeSlot(mode = CameraMode.VIDEO, lensFacing = LensFacing.FRONT)
        val videoSettings = ModeSettings(
            flashMode = FlashMode.ON,
            selfIllumination = true,
            videoQuality = VideoQuality.UHD,
            videoFrameRate = VideoFrameRate.FPS_60,
        )
        every { settingsRepository.modeSettings(videoSlot) } returns videoSettings
        every { settingsRepository.observeModeSettings(videoSlot) } returns flowOf(videoSettings)
        val viewModel = createViewModel(
            entryPoint = cameraEntryPoint(
                isVideoOnlySession = !isCaptureSession,
                isCaptureSession = isCaptureSession,
                requiresVideoModeOnly = true,
                showsCameraModeTabs = false,
            ),
        )

        assertEquals(CameraMode.VIDEO, state().mode)

        viewModel.onAction(LifecycleAction.ScreenCreated(host))
        viewModel.onAction(LifecycleAction.ScreenResumed)
        viewModel.onAction(LifecycleAction.PreviewStreamingStarted)

        assertTrue(state().selfIlluminates())
        verify { settingsRepository.modeSettings(videoSlot) }
        verify(exactly = 0) {
            settingsRepository.modeSettings(match { it.mode != CameraMode.VIDEO })
        }
        verify(exactly = 1) {
            cameraBindSettingsMapper.map(
                state = match {
                    it.mode == CameraMode.VIDEO &&
                        it.requiresVideoModeOnly &&
                        it.modeSettings == videoSettings
                },
                target = any(),
            )
        }
        verify(exactly = 1) { cameraSession.bind(bindSettings) }
        verify { cameraSession.setFlashMode(FlashMode.ON) }
        verify { torch.enable(true) }
    }

    private fun state(): ViewfinderState {
        return mappedState.captured
    }

    private fun prepareVideoOnlyCamera() {
        every { cameraSession.cameraProvider } returns mockk()
        every { cameraSession.lensFacing } returns LensFacing.FRONT
        every { cameraSession.isLensFacingSupported(any(), any()) } returns true
        every { cameraSession.imageCapture } returns null
    }

    private fun TestScope.createViewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): ViewfinderViewModel {
        val mainDispatcher = mainDispatcherRule.testDispatcher

        return ViewfinderViewModel(
            outputUri = null,
            entryPoint = entryPoint,
            settingsDelegate = ViewfinderSettingsDelegateImpl(settingsRepository),
            modeDelegate = ViewfinderModeDelegateImpl(entryPoint = entryPoint),
            cameraDelegate = ViewfinderCameraDelegateImpl(
                session = cameraSession,
                torch = torch,
                entryPoint = entryPoint,
                resolveAvailableModes = mockk(relaxed = true),
                resolveDroppedVideoQuality = mockk(relaxed = true),
            ),
            cameraControlsDelegate = mockk(relaxed = true),
            captureDelegate = ViewfinderCaptureDelegateImpl(
                captureImage = mockk(relaxed = true),
                capturePreviewImage = mockk(relaxed = true),
                storeCapturedPreview = mockk(relaxed = true),
                capturedItemRepository = capturedItemRepository,
                notifyPictureSaveFailed = mockk(relaxed = true),
                playCameraSound = mockk(relaxed = true),
                blinkTimerLight = mockk(relaxed = true),
                applicationScope = backgroundScope,
                mainDispatcher = mainDispatcher,
                defaultDispatcher = mainDispatcher,
            ),
            permissionDelegate = mockk(relaxed = true),
            recordingDelegate = ViewfinderRecordingDelegateImpl(
                videoRecorder = videoRecorder(),
                capturedItemRepository = capturedItemRepository,
                applicationScope = backgroundScope,
                mainDispatcher = mainDispatcher,
            ),
            orientationDelegate = mockk(relaxed = true),
            screenWakeDelegate = mockk(relaxed = true),
            galleryDelegate = mockk(relaxed = true),
            dialogDelegate = mockk(relaxed = true),
            modeSwitchDelegate = mockk(relaxed = true),
            previewControlsDelegate = mockk(relaxed = true),
            locationDelegate = mockk(relaxed = true),
            uiStateMapper = uiStateMapper,
            cameraBindSettingsMapper = cameraBindSettingsMapper,
            volumeKeyActionMapper = mockk(relaxed = true),
            swipeOutcomeMapper = mockk(relaxed = true),
        )
    }

    private fun TestScope.videoRecorder(): VideoRecorderImpl {
        return VideoRecorderImpl(
            recordingSession = recordingSession,
            createRecordingOutput = CreateRecordingOutputImpl(captureOutputRepository),
            publishRecording = PublishRecordingImpl(captureOutputRepository),
            discardRecording = DiscardRecordingImpl(captureOutputRepository),
            resolveCaptureLocation = ResolveCaptureLocationImpl(mockk(relaxed = true)),
            playCameraSound = mockk(relaxed = true),
            capturedItemRepository = capturedItemRepository,
            applicationScope = backgroundScope,
            mainDispatcher = mainDispatcherRule.testDispatcher,
        )
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
        val OUTPUT_URI: Uri = Uri.parse("content://media/external_primary/video/media/1")
    }
}
