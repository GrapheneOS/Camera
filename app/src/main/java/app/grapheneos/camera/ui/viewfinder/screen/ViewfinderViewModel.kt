package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderGalleryDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderLocationDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderOrientationDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderPermissionDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderScreenWakeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeEffectMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.VolumeKeyActionMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ZoomUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCameraEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface ViewfinderScreenModel {

    val uiState: StateFlow<ViewfinderUiState>

    val zoomUiState: StateFlow<ZoomUiState>
    val levelUiState: StateFlow<LevelUiState>

    val effects: Flow<Effect>

    fun onAction(action: ViewfinderAction)
}

@HiltViewModel(assistedFactory = ViewfinderViewModel.Factory::class)
class ViewfinderViewModel @AssistedInject constructor(
    @Assisted private val outputUri: Uri?,
    private val entryPoint: CameraEntryPoint,
    private val settingsDelegate: ViewfinderSettingsDelegate,
    private val modeDelegate: ViewfinderModeDelegate,
    private val cameraDelegate: ViewfinderCameraDelegate,
    private val captureDelegate: ViewfinderCaptureDelegate,
    private val recordingDelegate: ViewfinderRecordingDelegate,
    private val permissionDelegate: ViewfinderPermissionDelegate,
    private val locationDelegate: ViewfinderLocationDelegate,
    private val orientationDelegate: ViewfinderOrientationDelegate,
    private val screenWakeDelegate: ViewfinderScreenWakeDelegate,
    private val galleryDelegate: ViewfinderGalleryDelegate,
    private val cameraBindSettingsMapper: CameraBindSettingsMapper,
    private val swipeEffectMapper: SwipeEffectMapper,
    private val volumeKeyActionMapper: VolumeKeyActionMapper,
    uiStateMapper: ViewfinderUiStateMapper,
    zoomUiStateMapper: ZoomUiStateMapper,
) : ViewModel(),
    ViewfinderScreenModel {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(
            mode = modeDelegate.defaultMode,
            requiresVideoModeOnly = entryPoint.requiresVideoModeOnly,
            isCaptureSession = entryPoint.isCaptureSession,
            showsCameraModeTabs = entryPoint.showsCameraModeTabs,
        ),
    )

    override val uiState: StateFlow<ViewfinderUiState> = stateHolder.derive { state ->
        uiStateMapper.map(state)
    }

    override val zoomUiState: StateFlow<ZoomUiState> = stateHolder.derive { state ->
        zoomUiStateMapper.map(state)
    }

    override val levelUiState: StateFlow<LevelUiState> = orientationDelegate.levelUiState

    override val effects: Flow<Effect> = stateHolder.effects

    private var resumedWork: Job? = null

    init {
        modeDelegate.bind(stateHolder)
        permissionDelegate.bind(stateHolder)
        orientationDelegate.bind(stateHolder)
        screenWakeDelegate.bind(stateHolder)

        cameraDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        settingsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        locationDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        captureDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        recordingDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        galleryDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )

        viewModelScope.launch {
            cameraDelegate.cameraEvents.collect { event ->
                onCameraEvent(event)
            }
        }

        viewModelScope.launch {
            captureDelegate.captureEvents.collect { event ->
                onCaptureEvent(event)
            }
        }

        viewModelScope.launch {
            recordingDelegate.recordingEvents.collect { event ->
                onRecordingEvent(event)
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
            is PermissionAction -> onPermissionAction(action)
        }
    }

    private fun onCameraAction(action: CameraAction) {
        when (action) {
            is CameraAction.ModeSelected -> switchMode(action.mode)
            is CameraAction.FlipCameraClicked -> onFlipCameraClicked()
            is CameraAction.FlashToggleClicked -> toggleFlashMode()
            is CameraAction.TorchToggleClicked -> cameraDelegate.toggleTorch()
            is CameraAction.AspectRatioToggleClicked -> toggleAspectRatio()
            is CameraAction.ZoomInKeyPressed -> cameraDelegate.stepZoom(ZOOM_KEY_STEP)
            is CameraAction.ZoomOutKeyPressed -> cameraDelegate.stepZoom(-ZOOM_KEY_STEP)
            is CameraAction.FocusKeyPressed -> cameraDelegate.cancelFocus()
            is CameraAction.ZoomStepKey -> onZoomStepKey(action)
            is CameraAction.Preview -> onPreviewAction(action)
            is CameraAction.ZoomSliderDragged -> cameraDelegate.setLinearZoom(action.linearZoom)

            is CameraAction.ExposureSliderDragged -> {
                cameraDelegate.setExposureCompensation(action.compensationIndex)
            }

            is CameraAction.DisplayRotationChanged -> {
                onDisplayRotationChanged(action.rotation)
            }
        }
    }

    private fun onCaptureAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.ShutterClicked -> takePicture()
            is CaptureAction.CaptureButtonClicked -> onCaptureButtonClicked()
            is CaptureAction.CaptureKeyPressed -> onCaptureKeyPressed()
            is CaptureAction.ThirdCircleClicked -> onThirdCircleClicked()
            is CaptureAction.ThirdCircleLongClicked -> onThirdCircleLongClicked()
            is CaptureAction.SelfTimerStartClicked -> captureDelegate.startSelfTimer()
            is CaptureAction.SelfTimerCancelClicked -> captureDelegate.cancelSelfTimer()
            is CaptureAction.StorageLocationNotFound -> galleryDelegate.onStorageLocationNotFound()
            is CaptureAction.CapturedPreviewShown -> showCapturedPreview()
            is CaptureAction.CapturedPreviewDismissed -> dismissCapturedPreview()

            is CaptureAction.CapturedPreviewConfirmed -> {
                captureDelegate.confirmPreviewPicture(
                    bitmap = action.bitmap,
                    outputUri = outputUri,
                )
            }

            is CaptureAction.VolumeKeyEvent -> {
                volumeKeyActionMapper
                    .map(
                        event = action,
                        volumeKeyAction = state().settings.volumeKeyAction,
                    )
                    ?.let(::onAction)
            }
        }
    }

    private fun onRecordingAction(action: RecordingAction) {
        when (action) {
            is RecordingAction.RecordingRequested -> startRecording()
            is RecordingAction.RecordWithoutAudioClicked -> recordWithoutAudio()
            is RecordingAction.RecordingStopRequested -> recordingDelegate.requestStop()
            is RecordingAction.StartSoundPlayed -> recordingDelegate.startPreparedRecording()
            is RecordingAction.MuteToggleClicked -> recordingDelegate.toggleMute()
            is RecordingAction.RecordingPauseToggled -> setRecordingPaused(action.paused)
        }
    }

    private fun onLifecycleAction(action: LifecycleAction) {
        when (action) {
            is LifecycleAction.ScreenCreated -> onScreenCreated(action.host)
            is LifecycleAction.ScreenDestroyed -> onScreenDestroyed()
            is LifecycleAction.PreviewStreamingStarted -> onPreviewStreamingStarted()
            is LifecycleAction.ScreenStarted -> captureDelegate.onScreenStarted()
            is LifecycleAction.ScreenStopped -> captureDelegate.onScreenStopped()
            is LifecycleAction.ScreenResumed -> onScreenResumed()
            is LifecycleAction.ScreenPaused -> onScreenPaused()
            is LifecycleAction.ScreenInteracted -> screenWakeDelegate.onScreenInteracted()
            is LifecycleAction.QrResultDismissed -> dismissQrResult()
        }
    }

    private fun onSettingsAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SettingsIconClicked -> onSettingsIconClicked()
            is SettingsAction.AudioToggled -> settingsDelegate.setIncludeAudio(action.enabled)
            is SettingsAction.SelfIlluminationToggled -> setSelfIllumination(action.enabled)
            is SettingsAction.VideoQualitySelected -> onVideoQualitySelected(action.quality)
            is SettingsAction.EnableLocationClicked -> emitEffect(Effect.Location.OpenSettings)

            is SettingsAction.CameraSettingsClicked -> {
                settingsDelegate.openCameraSettings(cameraDelegate.capabilities())
            }

            is SettingsAction.SelfTimerSelected -> {
                settingsDelegate.setSelfTimerDuration(action.seconds)
            }
        }
    }

    private fun onPermissionAction(action: PermissionAction) {
        when (action) {
            is PermissionAction.RequestAnswered -> onPermissionRequestAnswered(action.permission)
            is PermissionAction.SettingsClicked -> permissionDelegate.openSettings()

            is PermissionAction.RationaleRequired -> {
                permissionDelegate.showDialog(action.permission)
            }

            is PermissionAction.DialogDismissed -> {
                onPermissionDialogDismissed(action.permission)
            }
        }
    }

    private fun onZoomStepKey(action: CameraAction.ZoomStepKey) {
        when (action) {
            is CameraAction.ZoomStepKey.Released -> {
                cameraDelegate.stopZoomStepping()
            }

            is CameraAction.ZoomStepKey.Pressed -> {
                cameraDelegate.startZoomStepping(action.direction)
            }
        }
    }

    private fun onPreviewAction(action: CameraAction.Preview) {
        when (action) {
            is CameraAction.Preview.Pinched -> cameraDelegate.scaleZoom(action.scaleFactor)
            is CameraAction.Preview.Swiped -> onPreviewSwiped(action.direction)

            is CameraAction.Preview.Tapped -> {
                cameraDelegate.focusAt(
                    x = action.x,
                    y = action.y,
                )
            }
        }
    }

    private fun onCameraEvent(event: ViewfinderCameraEvent) {
        when (event) {
            is ViewfinderCameraEvent.ProviderReady -> startCamera(forced = event.forced)
        }
    }

    private fun onCaptureEvent(event: ViewfinderCaptureEvent) {
        when (event) {
            is ViewfinderCaptureEvent.Saved -> {
                galleryDelegate.recordCapturedItem(event.item)
            }

            is ViewfinderCaptureEvent.StorageLocationNotFound -> {
                galleryDelegate.onStorageLocationNotFound()
            }

            is ViewfinderCaptureEvent.ThumbnailReady -> {
                galleryDelegate.showThumbnail(event.thumbnail)
            }
        }
    }

    private fun onRecordingEvent(event: ViewfinderRecordingEvent) {
        when (event) {
            is ViewfinderRecordingEvent.OutputUnavailable -> onRecordingOutputUnavailable()

            is ViewfinderRecordingEvent.Saved -> {
                galleryDelegate.recordCapturedItem(event.item)
                galleryDelegate.refreshThumbnail()
            }
        }
    }

    private fun onDisplayRotationChanged(rotation: Int) {
        cameraDelegate.setPreviewRotation(rotation)
        orientationDelegate.setDisplayRotation(rotation)
    }

    private fun onPreviewSwiped(direction: SwipeDirection) {
        val effect = swipeEffectMapper.map(
            direction = direction,
            state = state(),
        )

        effect?.let(::emitEffect)
    }

    private fun switchMode(mode: CameraMode) {
        if (!modeDelegate.select(mode)) return

        startCamera(forced = true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (entryPoint.showsCameraModeTabs) {
            emitEffect(Effect.ModeTab.GoTo(state().mode))
        }
    }

    private fun onFlipCameraClicked() {
        val state = state()

        when {
            state.isQrMode() -> settingsDelegate.toggleScanAllCodes()
            state.recording.isActive() -> setRecordingPaused(!state.recording.isPaused)

            else -> {
                emitEffect(Effect.AnimateLensSwitch)
                switchLens()
            }
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
                emitEffect(
                    Effect.ShowMessage(R.string.flash_unavailable_in_selected_mode),
                )
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

    private fun onCaptureButtonClicked() {
        val state = state()

        when {
            state.isVideoMode() -> toggleRecording(state)
            state.isQrMode() -> cameraDelegate.toggleTorch()
            state.settings.selfTimerDurationSeconds == 0 -> takePicture()
            state.capture.isSelfTimerRunning -> captureDelegate.cancelSelfTimer()
            else -> captureDelegate.startSelfTimer()
        }
    }

    private fun onCaptureKeyPressed() {
        if (state().isQrMode()) return

        onCaptureButtonClicked()
    }

    private fun onThirdCircleClicked() {
        val state = state()

        when {
            state.recording.isActive() -> takePicture()
            state.isCaptureSession -> Unit
            else -> galleryDelegate.openGallery()
        }
    }

    private fun onThirdCircleLongClicked() {
        val state = state()

        when {
            state.recording.isActive() -> takePicture()
            state.isCaptureSession -> Unit
            else -> galleryDelegate.shareLastCapturedItem()
        }
    }

    private fun toggleRecording(state: ViewfinderState) {
        when {
            state.recording.isActive() -> recordingDelegate.requestStop()
            else -> startRecording()
        }
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

    private fun showCapturedPreview() {
        captureDelegate.showCapturedPreview()
        cameraDelegate.unbindCamera()
    }

    private fun dismissCapturedPreview() {
        captureDelegate.dismissCapturedPreview()

        startCamera(forced = true)
    }

    private fun startRecording() {
        val state = state()

        if (!cameraDelegate.canRecord || state.recording.isActive()) {
            return
        }

        recordingDelegate.requestRecording()

        if (state.settings.includeAudio && AppPermission.MICROPHONE in state.missingPermissions) {
            permissionDelegate.request(
                permission = AppPermission.MICROPHONE,
                explainsFirst = false,
            )
            recordingDelegate.markStopped()
            return
        }

        recordingDelegate.prepareRecording(
            includeLocation = state.includesLocation(),
            includeAudio = state.settings.includeAudio,
            outputUri = outputUri,
        )
    }

    private fun onMicrophonePermissionAnswered() {
        when {
            AppPermission.MICROPHONE in state().missingPermissions -> {
                permissionDelegate.showDialog(AppPermission.MICROPHONE)
            }

            else -> {
                recordingDelegate.retryOnceStreaming()
                startCamera(forced = true)
            }
        }
    }

    private fun recordWithoutAudio() {
        permissionDelegate.dismissDialog()
        settingsDelegate.setIncludeAudio(false)

        startRecording()
    }

    private fun setRecordingPaused(paused: Boolean) {
        if (state().recording.isActive()) {
            recordingDelegate.setPaused(paused)
        }
    }

    private fun onRecordingOutputUnavailable() {
        if (!entryPoint.isCaptureSession) {
            galleryDelegate.onStorageLocationNotFound()
        }
    }

    private fun onScreenCreated(host: ViewfinderHost) {
        cameraDelegate.onScreenCreated(host)
        captureDelegate.onScreenCreated(host)
        galleryDelegate.onScreenCreated(host)
    }

    private fun onScreenDestroyed() {
        cameraDelegate.onScreenDestroyed()
        captureDelegate.onScreenDestroyed()
        galleryDelegate.onScreenDestroyed()
        recordingDelegate.onScreenDestroyed()
        permissionDelegate.dismissDialog()
    }

    private fun onScreenResumed() {
        permissionDelegate.refresh()
        galleryDelegate.refreshThumbnail()

        startResumedWork()

        val state = state()

        when {
            AppPermission.CAMERA in state.missingPermissions -> {
                permissionDelegate.request(
                    permission = AppPermission.CAMERA,
                    explainsFirst = true,
                )
            }

            else -> {
                val keepsBoundCamera = state.session.isQrResultShown ||
                    state.isReviewingRecordedVideo()

                initializeCamera(forced = !keepsBoundCamera)
            }
        }
    }

    private fun startResumedWork() {
        resumedWork?.cancel()
        resumedWork = viewModelScope.launch {
            launch { orientationDelegate.trackOrientation() }
            launch { locationDelegate.trackLocation() }
            launch { screenWakeDelegate.keepScreenAwake() }
        }
    }

    private fun onScreenPaused() {
        if (!state().isQrMode()) {
            captureDelegate.cancelPictureCapture()
        }

        resumedWork?.cancel()
    }

    private fun onPermissionRequestAnswered(permission: AppPermission) {
        permissionDelegate.refresh()

        when (permission) {
            AppPermission.MICROPHONE -> onMicrophonePermissionAnswered()
            AppPermission.CAMERA -> Unit
            AppPermission.LOCATION -> Unit
        }
    }

    private fun onPermissionDialogDismissed(permission: AppPermission) {
        if (state().permissionDialog != permission) return

        permissionDelegate.onDialogDismissed(permission)
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

    private fun onPreviewStreamingStarted() {
        applyModeSettings()

        if (recordingDelegate.takeRetry()) {
            startRecording()
        }
    }

    private fun applyModeSettings() {
        slotCurrentMode()

        val slotted = state()

        if (slotted.isVideoMode()) {
            cameraDelegate.refreshVideoQualities()
        }

        cameraDelegate.applyFlashMode(slotted.modeSettings.flashMode)

        setSelfIllumination(slotted.modeSettings.selfIllumination)
    }

    private fun onSettingsIconClicked() {
        if (!state().isQrMode()) {
            emitEffect(Effect.Settings.OpenSheet)
        }
    }

    private fun setSelfIllumination(enabled: Boolean) {
        settingsDelegate.setSelfIllumination(enabled)

        emitEffect(Effect.Preview.ApplySelfIllumination(state().selfIlluminate()))
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

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private fun emitEffect(effect: Effect) {
        stateHolder.postEffect(effect)
    }

    @AssistedFactory
    interface Factory {
        fun create(outputUri: Uri?): ViewfinderViewModel
    }

    companion object {
        private const val ZOOM_KEY_STEP = 1f
    }
}
