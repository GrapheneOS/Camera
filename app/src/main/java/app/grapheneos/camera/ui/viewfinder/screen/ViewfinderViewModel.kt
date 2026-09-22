package app.grapheneos.camera.ui.viewfinder.screen

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.RecordedVideoEvent
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.PictureFailureDetails
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.util.printStackTraceToString
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderScreenModel {
    val uiState: StateFlow<ViewfinderUiState>
    val effects: Flow<Effect>

    fun onAction(action: ViewfinderAction)
}

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val settingsDelegate: ViewfinderSettingsDelegate,
    private val modeDelegate: ViewfinderModeDelegate,
    private val cameraDelegate: ViewfinderCameraDelegate,
    private val captureDelegate: ViewfinderCaptureDelegate,
    private val recordingDelegate: ViewfinderRecordingDelegate,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
    private val locationRepository: LocationRepository,
    private val uiStateMapper: ViewfinderUiStateMapper,
    private val cameraBindSettingsMapper: CameraBindSettingsMapper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel(),
    ViewfinderScreenModel {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(
            mode = modeDelegate.defaultMode,
            requiresVideoModeOnly = entryPoint.requiresVideoModeOnly,
            isCaptureSession = entryPoint.isCaptureSession,
            showsCameraModeTabs = entryPoint.showsCameraModeTabs,
        ),
        render = uiStateMapper::map,
    )
    override val uiState: StateFlow<ViewfinderUiState> = stateHolder.uiState

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var selfTimer: Job? = null

    init {
        modeDelegate.bind(stateHolder)
        cameraDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        captureDelegate.bind(stateHolder)
        settingsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        recordingDelegate.bind(stateHolder)

        viewModelScope.launch(mainDispatcher) {
            cameraDelegate.sessionEvents.collect { event ->
                onSessionEvent(event)
            }
        }

        viewModelScope.launch(mainDispatcher) {
            captureDelegate.captureEvents.collect { event ->
                onCapturedImageEvent(event)
            }
        }

        viewModelScope.launch(mainDispatcher) {
            recordingDelegate.recordingEvents.collect { event ->
                onRecordedVideoEvent(event)
            }
        }
    }

    override fun onAction(action: ViewfinderAction) {
        when (action) {
            is CameraAction -> onCameraAction(action)
            is CaptureAction -> onCaptureAction(action)
            is RecordingAction -> onRecordingAction(action)
            is LifecycleAction -> onLifecycleAction(action)
            is SettingsAction -> onSettingsAction(action)
        }
    }

    private fun onCameraAction(action: CameraAction) {
        when (action) {
            is CameraAction.ModeSelected -> switchMode(action.mode)
            is CameraAction.LensSwitchClicked -> switchLens()
            is CameraAction.FlashToggleClicked -> toggleFlashMode()
            is CameraAction.TorchToggleClicked -> cameraDelegate.toggleTorch()
            is CameraAction.AspectRatioToggleClicked -> toggleAspectRatio()
            is CameraAction.ZoomInKeyPressed -> cameraDelegate.stepZoom(ZOOM_KEY_STEP)
            is CameraAction.ZoomOutKeyPressed -> cameraDelegate.stepZoom(-ZOOM_KEY_STEP)
            is CameraAction.FocusKeyPressed -> cameraDelegate.cancelFocus()
            is CameraAction.PreviewPinched -> cameraDelegate.scaleZoom(action.scaleFactor)
            is CameraAction.ZoomSliderDragged -> cameraDelegate.setLinearZoom(action.linearZoom)

            is CameraAction.ExposureSliderDragged -> {
                cameraDelegate.setExposureCompensation(action.compensationIndex)
            }

            is CameraAction.PreviewTapped -> {
                cameraDelegate.focusAt(
                    x = action.x,
                    y = action.y,
                    autoCancelSeconds = state().settings.focusTimeoutSeconds,
                )
            }
        }
    }

    private fun onCaptureAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.ShutterClicked -> takePicture()
            is CaptureAction.PictureCaptureCancelled -> captureDelegate.cancelPictureCapture()
            is CaptureAction.SelfTimerStartClicked -> startSelfTimer()
            is CaptureAction.SelfTimerCancelClicked -> cancelSelfTimer()
            is CaptureAction.StorageLocationNotFound -> onStorageLocationNotFound()
            is CaptureAction.CapturedPreviewShown -> captureDelegate.showCapturedPreview()

            is CaptureAction.CapturedPreviewConfirmed -> {
                captureDelegate.confirmPreviewPicture(action.bitmap)
            }
        }
    }

    private fun onRecordingAction(action: RecordingAction) {
        when (action) {
            is RecordingAction.RecordingRequested -> startRecording(action.hasAudioPermission)
            is RecordingAction.RecordingStopRequested -> recordingDelegate.requestStop()
            is RecordingAction.StartSoundPlayed -> recordingDelegate.startPreparedRecording()

            is RecordingAction.RecordingPauseToggled -> {
                if (state().recording.isActive()) {
                    recordingDelegate.setPaused(action.paused)
                }
            }

            is RecordingAction.RecordingMuteToggled -> {
                if (state().recording.isActive()) {
                    recordingDelegate.setMuted(action.muted)
                }
            }
        }
    }

    private fun onLifecycleAction(action: LifecycleAction) {
        when (action) {
            is LifecycleAction.ScreenCreated -> onScreenCreated(action.host)
            is LifecycleAction.ScreenDestroyed -> onScreenDestroyed()
            is LifecycleAction.PreviewStreamingStarted -> applyModeSettings()
            is LifecycleAction.CameraPermissionGranted -> initializeCamera(forced = false)
            is LifecycleAction.ScreenResumed -> initializeCamera(forced = true)
            is LifecycleAction.RecordAudioPermissionGranted -> startCamera(forced = true)
            is LifecycleAction.CapturedPreviewDismissed -> dismissCapturedPreview()
            is LifecycleAction.QrResultDismissed -> dismissQrResult()
        }
    }

    private fun onSettingsAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ScanAllCodesToggleClicked -> settingsDelegate.toggleScanAllCodes()
            is SettingsAction.GridToggleClicked -> settingsDelegate.cycleGridType()
            is SettingsAction.AudioToggled -> settingsDelegate.setIncludeAudio(action.enabled)
            is SettingsAction.GeoTaggingToggled -> setRequireLocation(action.enabled)
            is SettingsAction.SelfIlluminationToggled -> setSelfIllumination(action.enabled)
            is SettingsAction.VideoQualitySelected -> onVideoQualitySelected(action.quality)

            is SettingsAction.FocusTimeoutSelected -> {
                settingsDelegate.setFocusTimeout(action.seconds)
            }

            is SettingsAction.SelfTimerSelected -> {
                settingsDelegate.setSelfTimerDuration(action.seconds)
            }

            is SettingsAction.StabilizationToggled -> {
                settingsDelegate.setEnableEis(action.enabled)
                startCamera(forced = true)
            }

            is SettingsAction.FocusLockToggled -> {
                settingsDelegate.setWaitForFocusLock(action.enabled)
                startCamera(forced = true)
            }
        }
    }

    private fun onSessionEvent(event: CameraSessionEvent) {
        when (event) {
            is CameraSessionEvent.ProviderReady -> startCamera(forced = event.forced)
            is CameraSessionEvent.FeaturesSelected -> onFeaturesSelected(event)
            is CameraSessionEvent.ZoomStateLoaded -> cameraDelegate.refreshZoom()

            is CameraSessionEvent.ZoomStateChanged -> {
                cameraDelegate.refreshZoom()
                emitEffect(Effect.Panel.ShowZoom)
            }

            is CameraSessionEvent.CameraProviderUnavailable -> {
                emitEffect(Effect.ShowMessage(R.string.camera_provider_init_failure))
            }

            is CameraSessionEvent.ExtensionsUnavailable -> {
                emitEffect(Effect.ShowMessage(R.string.extensions_manager_init_failure))
            }

            is CameraSessionEvent.QrCodeScanned -> {
                if (cameraDelegate.showQrResult()) {
                    emitEffect(Effect.ShowQrResult(event.text))
                }
            }
        }
    }

    private fun onFeaturesSelected(event: CameraSessionEvent.FeaturesSelected) {
        // The full request-vs-result picture (including which stabilization feature, if any,
        // survived) is only ever logged, never shown: the lead wants EIS left silently in its
        // known state -- 4K keeps priority and stabilization is given up without a notice.
        Log.i(TAG, "Requested ${event.requested} but got ${event.selected}")

        val droppedQuality = resolveDroppedVideoQuality(
            lensFacing = event.boundLensFacing,
            requestedQualityFeature = event.qualityFeature,
            selected = event.selected,
        ) ?: return

        emitEffect(Effect.ShowVideoQualityUnsupported(droppedQuality))
    }

    private fun onCapturedImageEvent(event: CapturedImageEvent) {
        when (event) {
            is CapturedImageEvent.Captured -> onPictureCaptured()
            is CapturedImageEvent.ThumbnailReady -> onPictureThumbnailReady(event.thumbnail)
            is CapturedImageEvent.Saved -> emitEffect(Effect.Picture.Saved(item = event.item))
            is CapturedImageEvent.CaptureFailed -> onPictureCaptureFailed(event)
            is CapturedImageEvent.SaveFailed -> onPictureSaveFailed(event)
            is CapturedImageEvent.StorageLocationNotFound -> onStorageLocationNotFound()
            is CapturedImageEvent.PreviewCaptured -> onPreviewCaptured(event.bitmap)
            is CapturedImageEvent.PreviewFailed -> onPreviewFailed()
            is CapturedImageEvent.PreviewReturned -> emitEffect(Effect.Picture.PreviewReturned)
            is CapturedImageEvent.PreviewStored -> emitEffect(Effect.Picture.PreviewStored)

            is CapturedImageEvent.PreviewStoreFailed -> {
                emitEffect(Effect.Picture.PreviewStoreFailed)
            }

            is CapturedImageEvent.LocationUnavailable -> {
                emitEffect(Effect.ShowMessage(R.string.location_unavailable))
            }
        }
    }

    private fun onRecordedVideoEvent(event: RecordedVideoEvent) {
        when (event) {
            is RecordedVideoEvent.OutputUnavailable -> onRecordingOutputUnavailable()
            is RecordedVideoEvent.ReadyToStart -> emitEffect(Effect.Recording.PlayStartSound)
            is RecordedVideoEvent.Abandoned -> onRecordingStopped()
            is RecordedVideoEvent.Started -> recordingDelegate.startRecording()
            is RecordedVideoEvent.Finished -> onRecordingFinished(event.outcome)

            is RecordedVideoEvent.Progressed -> {
                recordingDelegate.setRecordedDuration(event.duration)
            }

            is RecordedVideoEvent.LocationUnavailable -> {
                emitEffect(Effect.ShowMessage(R.string.location_unavailable))
            }

            is RecordedVideoEvent.SaveFailed -> {
                emitEffect(Effect.ShowMessage(R.string.unable_to_save_video))
            }

            is RecordedVideoEvent.Saved -> {
                emitEffect(Effect.Recording.Saved(uri = event.uri, item = event.item))
            }
        }
    }

    private fun switchMode(mode: CameraMode) {
        if (!modeDelegate.select(mode)) return

        startCamera(forced = true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (entryPoint.showsCameraModeTabs) {
            emitEffect(Effect.GoToModeTab(state().mode))
        }
    }

    private fun switchLens() {
        val lensFacing = cameraDelegate.lensFacing.opposite()
        val isSwitched = cameraDelegate.switchLensFacing(
            lensFacing = lensFacing,
            extensionMode = state().mode.extensionMode,
        )

        when {
            isSwitched -> startCamera(forced = true)
            else -> emitEffect(Effect.ShowMessage(lensUnavailableMessage(lensFacing)))
        }
    }

    @StringRes
    private fun lensUnavailableMessage(lensFacing: LensFacing): Int {
        return when (lensFacing) {
            LensFacing.BACK -> R.string.rear_camera_unavailable
            LensFacing.FRONT -> R.string.front_camera_unavailable
        }
    }

    private fun toggleFlashMode() {
        val state = state()

        when {
            state.requiresVideoModeOnly -> {
                emitEffect(Effect.ShowMessage(R.string.flash_switch_unsupported))
            }

            state.session.isFlashAvailable -> {
                val next = when (state.flashMode) {
                    FlashMode.OFF -> FlashMode.ON
                    FlashMode.ON -> FlashMode.AUTO
                    FlashMode.AUTO -> FlashMode.OFF
                }

                setFlashMode(next)
            }

            else -> {
                emitEffect(Effect.ShowMessage(R.string.flash_unavailable_in_selected_mode))
            }
        }
    }

    private fun setFlashMode(value: FlashMode) {
        settingsDelegate.setFlashMode(value)
        cameraDelegate.applyFlashMode(value)
    }

    private fun toggleAspectRatio() {
        val next = when (state().aspectRatio()) {
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
            AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
        }

        settingsDelegate.setAspectRatio(next)

        startCamera(forced = true)
    }

    private fun onVideoQualitySelected(quality: VideoQuality) {
        if (quality == state().modeSettings.videoQuality) return

        settingsDelegate.setVideoQuality(quality)

        startCamera(forced = true)
    }

    private fun takePicture() {
        val state = state()

        when {
            !cameraDelegate.isCameraReady -> Unit

            !state.session.canTakePicture -> {
                emitEffect(
                    Effect.ShowMessage(R.string.unsupported_taking_picture_while_recording),
                )
            }

            state.capture.isTakingPicture -> Unit

            state.isCaptureSession -> {
                emitEffect(Effect.ShowMessage(R.string.capturing_image))
                captureDelegate.takePreviewPicture()
            }

            else -> captureDelegate.takePicture()
        }
    }

    private fun onPictureCaptured() {
        captureDelegate.startPictureSave()

        emitEffect(Effect.Picture.Captured)
        emitEffect(Effect.FlashPreview(state().selfIlluminate()))
    }

    private fun onPictureThumbnailReady(thumbnail: Bitmap) {
        captureDelegate.finishPictureSave()

        emitEffect(Effect.Picture.ThumbnailReady(thumbnail = thumbnail))
    }

    private fun onPictureCaptureFailed(event: CapturedImageEvent.CaptureFailed) {
        Log.e(TAG, "unable to capture a picture", event.cause)

        captureDelegate.finishPictureSave()

        emitEffect(
            Effect.Picture.CaptureFailed(
                errorCode = event.errorCode,
                details = detailsOf(event.cause),
            ),
        )
    }

    private fun onPictureSaveFailed(event: CapturedImageEvent.SaveFailed) {
        Log.e(TAG, "unable to save a picture", event.cause)

        captureDelegate.finishPictureSave()

        emitEffect(
            Effect.Picture.SaveFailed(
                stage = event.cause.place.name,
                details = detailsOf(event.cause),
                alreadyReported = event.alreadyReported,
            ),
        )
    }

    private fun detailsOf(exception: Throwable): PictureFailureDetails {
        return PictureFailureDetails(
            name = exception.javaClass.name,
            stackTrace = exception.printStackTraceToString(),
        )
    }

    private fun onPreviewCaptured(bitmap: Bitmap) {
        captureDelegate.finishPictureSave()

        emitEffect(Effect.Picture.PreviewCaptured(bitmap = bitmap))
        emitEffect(Effect.ShowMessage(R.string.image_captured_successfully))
    }

    private fun onPreviewFailed() {
        captureDelegate.finishPictureSave()

        emitEffect(Effect.Picture.PreviewFailed)
    }

    private fun onStorageLocationNotFound() {
        applicationScope.launch(mainDispatcher) {
            revertToMediaStoreLocation()
            emitEffect(Effect.ShowStorageLocationNotFound)
        }
    }

    private fun startSelfTimer() {
        cancelSelfTimer()

        emitEffect(Effect.SelfTimer.Started)
        captureDelegate.setSelfTimerRunning(true)

        val seconds = state().settings.selfTimerDurationSeconds
        selfTimer = viewModelScope.launch(mainDispatcher) {
            captureDelegate.selfTimerCountdown(seconds).collect { secondsLeft ->
                emitEffect(Effect.SelfTimer.Ticked(secondsLeft))
            }

            captureDelegate.setSelfTimerRunning(false)
            emitEffect(Effect.SelfTimer.Finished)
        }
    }

    private fun cancelSelfTimer() {
        // Cancelling puts back the controls the countdown hid. Doing that when no countdown is up
        // would resurrect the ones the current mode hid for its own reasons: QR mode hides
        // thirdOption and cancelButtonView, and the badge stays hidden with no timer set.
        if (selfTimer?.isActive != true) return

        selfTimer?.cancel()
        captureDelegate.setSelfTimerRunning(false)
        emitEffect(Effect.SelfTimer.Cancelled)
    }

    private fun startRecording(hasAudioPermission: Boolean) {
        val state = state()

        if (!cameraDelegate.canRecord || state.recording.isActive()) {
            return
        }

        recordingDelegate.requestRecording()

        if (state.settings.includeAudio && !hasAudioPermission) {
            emitEffect(Effect.Recording.RequestAudioPermission)
            recordingDelegate.markStopped()
            return
        }

        recordingDelegate.prepareRecording(
            includeLocation = state.requireLocation,
            includeAudio = state.settings.includeAudio,
        )
    }

    private fun onRecordingOutputUnavailable() {
        if (!entryPoint.isCaptureSession) {
            onStorageLocationNotFound()
        }

        emitEffect(Effect.ShowMessage(R.string.unable_to_access_output_file))
        recordingDelegate.markStopped()
    }

    private fun onRecordingStopped() {
        recordingDelegate.markStopped()

        emitEffect(Effect.Recording.Stopped)
    }

    private fun onRecordingFinished(outcome: RecordingOutcome) {
        onRecordingStopped()

        emitEffect(Effect.Recording.PlayStopSound)

        when (outcome) {
            is RecordingOutcome.Saved -> recordingDelegate.saveRecording()

            is RecordingOutcome.NothingPlayableWritten -> {
                recordingDelegate.discardRecording()
                emitEffect(Effect.ShowMessage(R.string.recording_too_short_to_be_saved))
            }

            is RecordingOutcome.Failed -> {
                recordingDelegate.discardRecording()
                emitEffect(Effect.Recording.SaveFailed(errorCode = outcome.errorCode))
            }

            is RecordingOutcome.Interrupted -> {
                emitEffect(Effect.Recording.Interrupted(errorCode = outcome.errorCode))

                when {
                    outcome.hasContent -> recordingDelegate.saveRecording()
                    else -> recordingDelegate.discardRecording()
                }
            }
        }
    }

    private fun onScreenCreated(host: ViewfinderHost) {
        cameraDelegate.onScreenCreated(host)
        captureDelegate.onScreenCreated(host)
        recordingDelegate.onScreenCreated(host)
    }

    private fun onScreenDestroyed() {
        selfTimer?.cancel()
        cameraDelegate.onScreenDestroyed()
        captureDelegate.onScreenDestroyed()
        recordingDelegate.onScreenDestroyed()
    }

    private fun initializeCamera(forced: Boolean) {
        when {
            cameraDelegate.isProviderReady -> startCamera(forced = forced)
            else -> cameraDelegate.initialize(
                forced = forced,
                extensionMode = state().mode.extensionMode,
            )
        }
    }

    private fun applyModeSettings() {
        slotCurrentMode()

        val slotted = state()

        if (slotted.isVideoMode()) {
            cameraDelegate.refreshVideoQualities()
        }

        cameraDelegate.applyFlashMode(slotted.modeSettings.flashMode)

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        setRequireLocation(
            enabled = slotted.modeSettings.geoTagging &&
                !locationRepository.shouldAskForPermission(),
        )

        setSelfIllumination(slotted.modeSettings.selfIllumination)
    }

    private fun setRequireLocation(enabled: Boolean) {
        settingsDelegate.setGeoTagging(enabled)

        emitEffect(Effect.SetLocationUpdates(enabled = enabled))
    }

    private fun setSelfIllumination(enabled: Boolean) {
        settingsDelegate.setSelfIllumination(enabled)

        emitEffect(Effect.ApplySelfIllumination(state().selfIlluminate()))
    }

    private fun dismissCapturedPreview() {
        captureDelegate.dismissCapturedPreview()

        startCamera(forced = true)
    }

    private fun dismissQrResult() {
        cameraDelegate.dismissQrResult()

        startCamera(forced = true)
    }

    private fun startCamera(forced: Boolean) {
        if (!cameraDelegate.canBeginBind(forced)) return

        captureDelegate.cancelPictureCapture()

        emitEffect(Effect.Panel.HideExposure)

        slotCurrentMode()

        // Before the builder below reads it: the mode just slotted may store a different flash mode
        // than the one that was bound, and the ImageCapture is configured once, at build time.
        cameraDelegate.applyFlashMode(state().modeSettings.flashMode)

        val bindState = state()

        val target = cameraDelegate.selectLens(
            isQrMode = bindState.isQrMode(),
            extensionMode = bindState.mode.extensionMode,
        ) ?: return

        if (target.qrLensFacing == LensFacing.FRONT) {
            emitEffect(Effect.ShowMessage(R.string.qr_rear_camera_unavailable))
        }

        val outcome = cameraDelegate.bindCamera(
            cameraBindSettingsMapper.map(
                state = bindState,
                target = target,
            ),
        )

        onBindOutcome(outcome)
    }

    private fun onBindOutcome(outcome: BindOutcome) {
        when (outcome) {
            BindOutcome.FAILED -> emitEffect(Effect.ShowMessage(R.string.bind_failure))

            BindOutcome.EXTENSION_UNUSABLE -> {
                emitEffect(Effect.ShowMessage(R.string.extension_mode_unavailable))

                // The bind never completed: currentMode still names the mode that was just
                // disabled and nothing is rendering into the preview. Refreshing the tabs
                // alone would only *visually* select another tab, leaving a frozen preview
                // behind a lying tab bar. Switch for real -- switchMode() rebinds, moves the
                // highlight and refreshes the tabs. Recursion stops because the default mode
                // uses no extension.
                switchMode(modeDelegate.defaultMode)
            }

            BindOutcome.BOUND -> {
                cameraDelegate.announceBind()
                emitEffect(Effect.Panel.HideZoom)
            }
        }
    }

    private fun slotCurrentMode() {
        settingsDelegate.selectModeSlot(
            ModeSlot(
                mode = state().mode,
                isFrontFacing = cameraDelegate.lensFacing == LensFacing.FRONT,
            ),
        )
    }

    private fun emitEffect(effect: Effect) {
        _effects.trySend(effect)
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    companion object {
        private const val TAG = "ViewfinderViewModel"

        private const val ZOOM_KEY_STEP = 1f

        private const val IS_SECURE_SESSION = "entry_point_is_secure_session"
        private const val IS_CAPTURE_SESSION = "entry_point_is_capture_session"
        private const val IS_VIDEO_ONLY_SESSION = "entry_point_is_video_only_session"
        private const val REQUIRES_VIDEO_MODE_ONLY = "entry_point_requires_video_mode_only"
        private const val ALLOWS_QR_SCANNING = "entry_point_allows_qr_scanning"
        private const val SHOWS_CAMERA_MODE_TABS = "entry_point_shows_camera_mode_tabs"

        fun arguments(entryPoint: CameraEntryPoint): Bundle {
            return Bundle().apply {
                putBoolean(IS_SECURE_SESSION, entryPoint.isSecureSession)
                putBoolean(IS_CAPTURE_SESSION, entryPoint.isCaptureSession)
                putBoolean(IS_VIDEO_ONLY_SESSION, entryPoint.isVideoOnlySession)
                putBoolean(REQUIRES_VIDEO_MODE_ONLY, entryPoint.requiresVideoModeOnly)
                putBoolean(ALLOWS_QR_SCANNING, entryPoint.allowsQrScanning)
                putBoolean(SHOWS_CAMERA_MODE_TABS, entryPoint.showsCameraModeTabs)
            }
        }

        fun entryPoint(arguments: SavedStateHandle): CameraEntryPoint {
            return CameraEntryPoint(
                isSecureSession = arguments.requireFlag(IS_SECURE_SESSION),
                isCaptureSession = arguments.requireFlag(IS_CAPTURE_SESSION),
                isVideoOnlySession = arguments.requireFlag(IS_VIDEO_ONLY_SESSION),
                requiresVideoModeOnly = arguments.requireFlag(REQUIRES_VIDEO_MODE_ONLY),
                allowsQrScanning = arguments.requireFlag(ALLOWS_QR_SCANNING),
                showsCameraModeTabs = arguments.requireFlag(SHOWS_CAMERA_MODE_TABS),
            )
        }

        private fun SavedStateHandle.requireFlag(key: String): Boolean {
            return requireNotNull(get<Boolean>(key)) {
                "The ViewModel was created without its entry point ($key)"
            }
        }
    }
}
