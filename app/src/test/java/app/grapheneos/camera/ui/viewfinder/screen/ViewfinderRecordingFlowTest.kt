package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.RecordingEvent
import app.grapheneos.camera.data.camera.model.RecordingOutcome
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.media.model.CaptureOutputResult
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
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CaptureUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SettingsSheetUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapperImpl
import app.grapheneos.camera.ui.viewfinder.screen.model.ThumbnailSize
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewfinderRecordingFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recordingSession = FakeVideoRecordingSession()

    private var outputCreated = CompletableDeferred(Unit)
    private var outputPublished = CompletableDeferred(Unit)

    private val captureOutputRepository = mockk<CaptureOutputRepository>(relaxed = true) {
        coEvery { createVideo(any(), any(), any()) } coAnswers {
            outputCreated.await()
            CaptureOutputResult.Success(OUTPUT_URI)
        }
        coEvery { openForWriting(any()) } returns CaptureOutputResult.Success(mockk(relaxed = true))
        coEvery { publish(any()) } coAnswers {
            outputPublished.await()
            CaptureOutputResult.Success(Unit)
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
    fun aStopBeforeTheOutputIsCreated_startsNothingAndThrowsTheOutputAway() {
        runTest {
            outputCreated = CompletableDeferred()
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(RecordingAction.RecordingRequested)
            viewModel.onAction(RecordingAction.RecordingStopRequested)
            outputCreated.complete(Unit)

            assertEquals(0, recordingSession.startCount)
            assertFalse(viewModel.uiState.value.isRecordingActive)
            assertFalse(ViewfinderScreenEffect.Recording.PlayStartSound in effects)
            coVerify(exactly = 1) { captureOutputRepository.delete(OUTPUT_URI) }
        }
    }

    @Test
    fun aStopWhileTheStartSoundPlays_startsNothingAndThrowsTheOutputAway() {
        runTest {
            val viewModel = createViewModel()
            val effects = collectEffects(viewModel)

            viewModel.onAction(RecordingAction.RecordingRequested)
            assertTrue(ViewfinderScreenEffect.Recording.PlayStartSound in effects)

            viewModel.onAction(RecordingAction.RecordingStopRequested)
            viewModel.onAction(RecordingAction.StartSoundPlayed)

            assertEquals(0, recordingSession.startCount)
            assertFalse(viewModel.uiState.value.isRecordingActive)
            coVerify(exactly = 1) { captureOutputRepository.delete(OUTPUT_URI) }
        }
    }

    @Test
    fun aPauseAndMuteBeforeTheStart_reachTheRecordingOnceItStarts() {
        runTest {
            val viewModel = createViewModel()

            viewModel.onAction(RecordingAction.RecordingRequested)
            viewModel.onAction(RecordingAction.RecordingPauseToggled(paused = true))
            viewModel.onAction(RecordingAction.RecordingMuteToggled(muted = true))
            viewModel.onAction(RecordingAction.StartSoundPlayed)

            assertEquals(1, recordingSession.startCount)
            assertTrue(recordingSession.isPaused)
            assertTrue(recordingSession.isMuted)
        }
    }

    @Test
    fun aCaptureSessionRecording_keepsTheCameraUnboundUntilItCanBeReviewed() {
        runTest {
            outputPublished = CompletableDeferred()
            val viewModel = createViewModel(
                entryPoint = cameraEntryPoint(
                    isCaptureSession = true,
                    requiresVideoModeOnly = true,
                    showsCameraModeTabs = false,
                ),
            )
            val effects = collectEffects(viewModel)
            viewModel.onAction(LifecycleAction.ScreenCreated(host))

            viewModel.onAction(RecordingAction.RecordingRequested)
            viewModel.onAction(RecordingAction.StartSoundPlayed)
            recordingSession.emit(RecordingEvent.Started)
            viewModel.onAction(RecordingAction.RecordingStopRequested)
            recordingSession.emit(RecordingEvent.Finalized(outcome = RecordingOutcome.Saved))

            viewModel.onAction(LifecycleAction.ScreenResumed)

            assertEquals(1, recordingSession.stopCount)
            assertFalse(effects.any { it is ViewfinderScreenEffect.Recording.Saved })
            verify(exactly = 0) { cameraSession.bind(any()) }

            outputPublished.complete(Unit)
            viewModel.onAction(LifecycleAction.ScreenResumed)

            assertTrue(effects.any { it is ViewfinderScreenEffect.Recording.Saved })
            verify(exactly = 1) { cameraSession.bind(any()) }
        }
    }

    private fun TestScope.createViewModel(
        entryPoint: CameraEntryPoint = cameraEntryPoint(),
    ): ViewfinderViewModel {
        val mainDispatcher = mainDispatcherRule.testDispatcher
        val videoRecorder = VideoRecorderImpl(
            recordingSession = recordingSession,
            createRecordingOutput = CreateRecordingOutputImpl(captureOutputRepository),
            publishRecording = PublishRecordingImpl(captureOutputRepository),
            discardRecording = DiscardRecordingImpl(captureOutputRepository),
            resolveCaptureLocation = ResolveCaptureLocationImpl(mockk(relaxed = true)),
            playRecordingStopSound = mockk(relaxed = true),
            capturedItemRepository = capturedItemRepository,
            applicationScope = backgroundScope,
            mainDispatcher = mainDispatcher,
        )

        return ViewfinderViewModel(
            outputUri = null,
            entryPoint = entryPoint,
            settingsDelegate = ViewfinderSettingsDelegateImpl(
                settingsRepository = settingsRepository,
                mainDispatcher = mainDispatcher,
            ),
            modeDelegate = ViewfinderModeDelegateImpl(),
            cameraDelegate = ViewfinderCameraDelegateImpl(
                session = cameraSession,
                entryPoint = entryPoint,
                resolveAvailableModes = mockk(relaxed = true),
                mainDispatcher = mainDispatcher,
            ),
            captureDelegate = ViewfinderCaptureDelegateImpl(
                captureImage = mockk(relaxed = true),
                capturePreviewImage = mockk(relaxed = true),
                storeCapturedPreview = mockk(relaxed = true),
                capturedItemRepository = capturedItemRepository,
                notifyPictureSaveFailed = mockk(relaxed = true),
                applicationScope = backgroundScope,
                mainDispatcher = mainDispatcher,
            ),
            permissionDelegate = mockk(relaxed = true),
            recordingDelegate = ViewfinderRecordingDelegateImpl(
                videoRecorder = videoRecorder,
                capturedItemRepository = capturedItemRepository,
                applicationScope = backgroundScope,
                mainDispatcher = mainDispatcher,
            ),
            resolveDroppedVideoQuality = mockk(relaxed = true),
            revertToMediaStoreLocation = mockk(relaxed = true),
            orientationDelegate = mockk(relaxed = true),
            locationDelegate = mockk(relaxed = true) {
                every { providersDisabledEvents } returns emptyFlow()
            },
            uiStateMapper = ViewfinderUiStateMapperImpl(
                settingsSheetUiStateMapper = SettingsSheetUiStateMapperImpl(),
                captureUiStateMapper = CaptureUiStateMapperImpl(),
            ),
            cameraBindSettingsMapper = CameraBindSettingsMapperImpl(),
            applicationScope = backgroundScope,
            mainDispatcher = mainDispatcher,
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
