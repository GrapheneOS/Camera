package app.grapheneos.camera.ui.viewfinder.screen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.permission.model.AppPermission
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraControlsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCaptureDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderDialogDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderGalleryDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderLocationDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeSwitchDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderOrientationDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderPermissionDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderPreviewControlsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderRecordingDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderScreenWakeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeOutcomeMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.mapper.VolumeKeyActionMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.LevelUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.DialogAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LocationAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.PermissionAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.RecordingAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.ZoomUiState
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCameraEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCaptureEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderRecordingEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderCaptureResult
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderLevelState
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface ViewfinderScreenModel {

    val uiState: StateFlow<ViewfinderUiState>

    val zoomUiState: StateFlow<ZoomUiState>
    val levelUiState: StateFlow<LevelUiState>

    val effects: Flow<Effect>
    val navigationEvents: Flow<NavEvent>

    fun onAction(action: ViewfinderAction)
}

@HiltViewModel(assistedFactory = ViewfinderViewModel.Factory::class)
class ViewfinderViewModel @AssistedInject constructor(
    @Assisted private val outputUri: Uri?,
    private val entryPoint: CameraEntryPoint,
    private val settingsDelegate: ViewfinderSettingsDelegate,
    private val modeDelegate: ViewfinderModeDelegate,
    private val cameraDelegate: ViewfinderCameraDelegate,
    private val cameraControlsDelegate: ViewfinderCameraControlsDelegate,
    private val captureDelegate: ViewfinderCaptureDelegate,
    private val recordingDelegate: ViewfinderRecordingDelegate,
    private val permissionDelegate: ViewfinderPermissionDelegate,
    private val locationDelegate: ViewfinderLocationDelegate,
    private val orientationDelegate: ViewfinderOrientationDelegate,
    private val screenWakeDelegate: ViewfinderScreenWakeDelegate,
    private val galleryDelegate: ViewfinderGalleryDelegate,
    private val dialogDelegate: ViewfinderDialogDelegate,
    private val modeSwitchDelegate: ViewfinderModeSwitchDelegate,
    private val previewControlsDelegate: ViewfinderPreviewControlsDelegate,
    private val cameraBindSettingsMapper: CameraBindSettingsMapper,
    private val volumeKeyActionMapper: VolumeKeyActionMapper,
    private val swipeOutcomeMapper: SwipeOutcomeMapper,
    uiStateMapper: ViewfinderUiStateMapper,
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

    private val levelStateHolder = StateHolder(
        initial = ViewfinderLevelState(),
    )

    override val uiState: StateFlow<ViewfinderUiState> = stateHolder.derive { state ->
        uiStateMapper.map(state)
    }

    override val zoomUiState: StateFlow<ZoomUiState> = stateHolder.derive { state ->
        uiStateMapper.mapZoom(state)
    }

    override val levelUiState: StateFlow<LevelUiState> = levelStateHolder.derive { state ->
        uiStateMapper.mapLevel(state)
    }

    override val effects: Flow<Effect> = stateHolder.effects
    override val navigationEvents: Flow<NavEvent> = stateHolder.navigationEvents

    private var resumedWork: Job? = null

    init {
        bindDelegates()
        prepareCaptureSession()
        trackSources()
        collectDelegateEvents()
    }

    private fun bindDelegates() {
        modeDelegate.bind(stateHolder)
        permissionDelegate.bind(stateHolder)
        screenWakeDelegate.bind(stateHolder)
        settingsDelegate.bind(stateHolder)
        locationDelegate.bind(stateHolder)
        recordingDelegate.bind(stateHolder)
        dialogDelegate.bind(stateHolder)
        modeSwitchDelegate.bind(stateHolder)
        cameraDelegate.bind(stateHolder)

        orientationDelegate.bind(
            stateHolder = stateHolder,
            levelStateHolder = levelStateHolder,
        )
        cameraControlsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        captureDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        galleryDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
        previewControlsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
    }

    private fun prepareCaptureSession() {
        if (entryPoint.isCaptureSession) {
            captureDelegate.holdShutter()
        }
    }

    private fun trackSources() {
        viewModelScope.launch { cameraDelegate.trackSession() }
        viewModelScope.launch { cameraControlsDelegate.trackZoom() }
        viewModelScope.launch { settingsDelegate.trackSettings() }
        viewModelScope.launch { settingsDelegate.trackModeSettings() }
        viewModelScope.launch { settingsDelegate.trackSettingsPanelAvailability() }
        viewModelScope.launch { locationDelegate.trackGeoTagging() }
        viewModelScope.launch { recordingDelegate.trackRecorder() }
        viewModelScope.launch { galleryDelegate.trackLastCapturedItem() }
    }

    private fun collectDelegateEvents() {
        viewModelScope.launch {
            cameraDelegate.cameraEvents.collect { event ->
                onCameraEvent(event)
            }
        }

        viewModelScope.launch {
            cameraControlsDelegate.zoomChanges.collect {
                previewControlsDelegate.showZoomPanel()
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
            is LocationAction -> onLocationAction(action)
            is SettingsAction -> onSettingsAction(action)
            is PermissionAction -> onPermissionAction(action)
            is DialogAction -> onDialogAction(action)
        }
    }

    private fun onCameraAction(action: CameraAction) {
        when (action) {
            is CameraAction.ModeSelected -> switchMode(action.mode)
            is CameraAction.ModeStripReleased -> modeSwitchDelegate.onStripReleased(action.mode)
            is CameraAction.ModeStripSettled -> onModeStripSettled(action.mode)
            is CameraAction.FlipCameraClicked -> onFlipCameraClicked()
            is CameraAction.ZoomStepKey -> onZoomStepKey(action)
            is CameraAction.Preview -> onPreviewAction(action)
            is CameraAction.ZoomInKeyPressed -> cameraControlsDelegate.stepZoom(ZOOM_KEY_STEP)
            is CameraAction.ZoomOutKeyPressed -> cameraControlsDelegate.stepZoom(-ZOOM_KEY_STEP)
            is CameraAction.FocusKeyPressed -> cameraControlsDelegate.cancelFocus()
            is CameraAction.ZoomSliderDragged -> {
                cameraControlsDelegate.setLinearZoom(action.linearZoom)
            }

            is CameraAction.ExposureSliderDragged -> {
                cameraControlsDelegate.setExposureCompensation(action.compensationIndex)
                previewControlsDelegate.showExposurePanel()
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
            is CaptureAction.RetakeClicked -> retake()
            is CaptureAction.ConfirmClicked -> captureDelegate.confirmResult(outputUri)

            is CaptureAction.RecordingResultShown -> {
                showCaptureResult(ViewfinderCaptureResult.Video(image = action.image))
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
            is LifecycleAction.PreviewStreamingStopped -> {
                modeSwitchDelegate.onPreviewStreaming(false)
            }

            is LifecycleAction.ScreenStarted -> captureDelegate.onScreenStarted()
            is LifecycleAction.ScreenStopped -> captureDelegate.onScreenStopped()

            is LifecycleAction.ScreenResumed -> onScreenResumed()
            is LifecycleAction.ScreenPaused -> onScreenPaused()

            is LifecycleAction.ScreenInteracted -> screenWakeDelegate.onScreenInteracted()
        }
    }

    private fun onLocationAction(action: LocationAction) {
        when (action) {
            is LocationAction.EnableLocationClicked -> emitEffect(Effect.Location.OpenSettings)
        }
    }

    private fun onSettingsAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.StatusBarClicked -> settingsDelegate.openSettingsPanel(action.setting)
            is SettingsAction.QuickSettingsButtonClicked -> settingsDelegate.openSettingsPanel()
            is SettingsAction.SettingsPanelVisibilityChanged -> {
                settingsDelegate.onSettingsPanelVisibilityChanged(action.visible)
            }
        }
    }

    private fun onPermissionAction(action: PermissionAction) {
        when (action) {
            is PermissionAction.RequestAnswered -> {
                onPermissionRequestAnswered(action.permission)
            }

            is PermissionAction.RationaleRequired -> {
                permissionDelegate.showDialog(action.permission)
            }
        }
    }

    private fun onDialogAction(action: DialogAction) {
        when (action) {
            is DialogAction.Dismissed -> dialogDelegate.dismiss()
            is DialogAction.QrResultDismissed -> dismissQrResult()
            is DialogAction.QrResultCopyClicked -> dialogDelegate.copy(action.text)
            is DialogAction.Choice -> onDialogChoice(action)

            is DialogAction.PictureFailureDetailsClicked -> {
                dialogDelegate.showPictureFailureDetails()
            }

            is DialogAction.PictureFailureDetailsCopyClicked -> {
                dialogDelegate.copyPictureFailureDetails()
            }
        }
    }

    private fun onZoomStepKey(action: CameraAction.ZoomStepKey) {
        when (action) {
            is CameraAction.ZoomStepKey.Released -> {
                cameraControlsDelegate.stopZoomStepping()
            }

            is CameraAction.ZoomStepKey.Pressed -> {
                cameraControlsDelegate.startZoomStepping(action.direction)
            }
        }
    }

    private fun onPreviewAction(action: CameraAction.Preview) {
        when (action) {
            is CameraAction.Preview.Pinched -> cameraControlsDelegate.scaleZoom(action.scaleFactor)
            is CameraAction.Preview.Swiped -> onPreviewSwiped(action.direction)
            is CameraAction.Preview.Tapped -> when {
                state().isSettingsPanelOpen -> settingsDelegate.closeSettingsPanel()
                else -> focusAt(x = action.x, y = action.y)
            }
        }
    }

    private fun onDialogChoice(action: DialogAction.Choice) {
        dialogDelegate.close()

        when (action) {
            is DialogAction.Choice.PermissionSettingsClicked -> {
                permissionDelegate.openSettings()
            }

            is DialogAction.Choice.RecordWithoutAudioClicked -> {
                recordWithoutAudio()
            }

            is DialogAction.Choice.CameraSettingsClicked -> {
                settingsDelegate.openCameraSettings(cameraDelegate.capabilities())
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
            is ViewfinderCaptureEvent.SelfTimerFinished -> takePicture()

            is ViewfinderCaptureEvent.PhotoCaptured -> {
                showCaptureResult(ViewfinderCaptureResult.Photo(image = event.photo))
            }

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

    private fun switchMode(mode: CameraMode) {
        if (!modeDelegate.select(mode)) return

        startCamera(forced = true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - the camera's mode, because an extension that fails to bind falls back
        // to another mode from inside startCamera(). Left until after that rebind, which blocks
        // the main thread for long enough to swallow the animation whole.
        modeSwitchDelegate.followCamera()
    }

    private fun onPreviewSwiped(direction: SwipeDirection) {
        when (val outcome = swipeOutcomeMapper.map(direction = direction, state = state())) {
            null -> Unit
            is SwipeOutcome.ShowQrFormats -> emitEffect(Effect.Settings.ShowQrFormats)
            is SwipeOutcome.SelectAdjacentMode -> {
                modeSwitchDelegate.onAdjacentModeSwiped(outcome.offset)
            }
        }
    }

    private fun onModeStripSettled(mode: CameraMode) {
        val state = state()

        when {
            state.modeStrip.pendingMode != mode -> Unit
            mode != state.mode -> switchMode(mode)
            else -> modeSwitchDelegate.cancelSwitch()
        }
    }

    private fun onFlipCameraClicked() {
        val state = state()

        when {
            state.isQrMode() -> settingsDelegate.toggleScanAllCodes()
            state.recording.isActive() -> setRecordingPaused(!state.recording.isPaused)

            else -> {
                if (cameraDelegate.switchLens(state.mode.extensionMode)) {
                    startCamera(forced = true)
                }
            }
        }
    }

    private fun onCaptureButtonClicked() {
        val state = state()

        when {
            state.isVideoMode() -> toggleRecording(state)
            state.isQrMode() -> cameraDelegate.toggleTorch()
            state.settings.selfTimerDuration == Duration.ZERO -> takePicture()
            state.capture.isSelfTimerRunning() -> captureDelegate.cancelSelfTimer()
            else -> captureDelegate.startSelfTimer()
        }
    }

    private fun focusAt(
        x: Float,
        y: Float,
    ) {
        if (state().isQrMode()) return

        cameraControlsDelegate.focusAt(x = x, y = y)
        previewControlsDelegate.showFocus(x = x, y = y)
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

    private fun showCaptureResult(result: ViewfinderCaptureResult) {
        captureDelegate.showResult(result)
        cameraDelegate.unbindCamera()
    }

    private fun retake() {
        captureDelegate.discardResult()
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
            launch { settingsDelegate.rebindRequests().collect { startCamera(forced = true) } }
            launch { settingsDelegate.videoLightWrites().collect(cameraDelegate::applyVideoLight) }
        }
    }

    private fun onScreenPaused() {
        captureDelegate.cancelSelfTimer()

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
        modeSwitchDelegate.onPreviewStreaming(true)
        applyModeSettings()

        if (recordingDelegate.takeRetry()) {
            startRecording()
        }
    }

    private fun applyModeSettings() {
        slotCurrentMode()

        val modeSettings = state().modeSettings
        cameraDelegate.applyFlashMode(modeSettings.flashMode)
        cameraDelegate.applyVideoLight(modeSettings.isVideoLightOn())
    }

    private fun dismissQrResult() {
        cameraDelegate.dismissQrResult()

        startCamera(forced = true)
    }

    private fun startCamera(forced: Boolean) {
        if (!cameraDelegate.canBeginBind(forced)) return

        captureDelegate.cancelPictureCapture()

        previewControlsDelegate.hideExposurePanel()

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
                previewControlsDelegate.hideZoomPanel()
            }
        }
    }

    private fun slotCurrentMode() {
        settingsDelegate.selectModeSlot(
            ModeSlot(
                mode = state().mode,
                lensFacing = cameraDelegate.lensFacing,
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
