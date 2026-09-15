package app.grapheneos.camera.ui.viewfinder.screen

import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Quality
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderCameraDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderModeDelegate
import app.grapheneos.camera.ui.viewfinder.screen.delegate.ViewfinderSettingsDelegate
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.LifecycleAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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

class ViewfinderViewModel @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val settingsDelegate: ViewfinderSettingsDelegate,
    private val modeDelegate: ViewfinderModeDelegate,
    private val cameraDelegate: ViewfinderCameraDelegate,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
    private val uiStateMapper: ViewfinderUiStateMapper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel(),
    ViewfinderScreenModel,
    CameraSession.Listener {

    private val stateHolder = ViewfinderStateHolder(
        initial = ViewfinderState(mode = modeDelegate.defaultMode),
        render = ::renderUiState,
    )
    override val uiState: StateFlow<ViewfinderUiState> = stateHolder.uiState

    private val screenEffects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = screenEffects.receiveAsFlow()

    init {
        modeDelegate.bind(stateHolder)
        cameraDelegate.bind(stateHolder)
        settingsDelegate.bind(
            scope = viewModelScope,
            stateHolder = stateHolder,
        )
    }

    fun attach(
        environment: CameraSessionEnvironment,
        effects: ViewfinderEffects,
        chrome: ViewfinderChrome,
        session: CameraSession,
    ) {
        cameraDelegate.attach(
            environment = environment,
            effects = effects,
            chrome = chrome,
            session = session,
            listener = this,
            emitEffect = ::emitEffect,
        )
    }

    fun detach() {
        cameraDelegate.detach()
    }

    override fun onZoomStateChanged() {
        cameraDelegate.onZoomStateChanged()
    }

    override fun onCameraProviderUnavailable() {
        emitEffect(Effect.ShowMessage(R.string.camera_provider_init_failure))
    }

    override fun onExtensionsUnavailable() {
        emitEffect(Effect.ShowMessage(R.string.extensions_manager_init_failure))
    }

    override fun onProviderReady(forced: Boolean) {
        startCamera(forced = forced)
    }

    private fun setFlashMode(value: Int) {
        settingsDelegate.setFlashMode(value)
        cameraDelegate.applyFlashMode(value)
    }

    private fun setRequireLocation(enabled: Boolean) {
        when {
            enabled -> emitEffect(Effect.StartLocationUpdates)
            else -> emitEffect(Effect.StopLocationUpdates)
        }

        settingsDelegate.setGeoTagging(enabled)
    }

    private fun setSelfIllumination(enabled: Boolean) {
        settingsDelegate.setSelfIllumination(enabled)

        emitEffect(Effect.ApplySelfIllumination(uiState.value.capture.selfIlluminate))
    }

    private fun reloadSettings() {
        slotCurrentMode()

        if (modeDelegate.isVideoMode) {
            emitEffect(Effect.ReloadVideoQualities)
        }

        cameraDelegate.applyFlashMode(settingsDelegate.modeSettings.flashMode)

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        setRequireLocation(
            enabled = settingsDelegate.modeSettings.geoTagging &&
                !cameraDelegate.shouldAskForLocationPermission(),
        )

        setSelfIllumination(settingsDelegate.modeSettings.selfIllumination)
    }

    override fun onAction(action: ViewfinderAction) {
        when (action) {
            is CameraAction -> onCameraAction(action)
            is CaptureAction -> onCaptureAction(action)
            is LifecycleAction -> onLifecycleAction(action)
            is SettingsAction -> onSettingsAction(action)
        }
    }

    private fun onCameraAction(action: CameraAction) {
        when (action) {
            is CameraAction.ModeSelected -> switchMode(action.mode)
            is CameraAction.LensSwitchClicked -> toggleCameraSelector()
            is CameraAction.FlashToggleClicked -> toggleFlashMode()
            is CameraAction.AspectRatioToggleClicked -> toggleAspectRatio()
        }
    }

    private fun onCaptureAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.PictureCaptured -> flashPreview()
            is CaptureAction.StorageLocationNotFound -> onStorageLocationNotFound()
        }
    }

    private fun onLifecycleAction(action: LifecycleAction) {
        when (action) {
            is LifecycleAction.CameraPermissionGranted -> initializeCamera()
            is LifecycleAction.PreviewStreamingStarted -> reloadSettings()
            is LifecycleAction.ScreenResumed -> initializeCamera(forced = true)

            is LifecycleAction.RecordAudioPermissionGranted,
            is LifecycleAction.QrResultDismissed,
            is LifecycleAction.CapturedPreviewDismissed,
            -> startCamera(forced = true)
        }
    }

    private fun onSettingsAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ScanAllCodesToggleClicked -> {
                settingsDelegate.toggleScanAllCodes()
                cameraDelegate.refreshQrHints()
            }

            is SettingsAction.GridToggleClicked -> {
                settingsDelegate.cycleGridType()
            }

            is SettingsAction.AudioToggled -> {
                settingsDelegate.setIncludeAudio(action.enabled)
            }

            is SettingsAction.GeoTaggingToggled -> {
                setRequireLocation(action.enabled)
            }

            is SettingsAction.SelfIlluminationToggled -> {
                setSelfIllumination(action.enabled)
            }

            is SettingsAction.StabilizationToggled -> {
                settingsDelegate.setEnableEis(action.enabled)
                startCamera(forced = true)
            }

            is SettingsAction.FocusLockToggled -> {
                settingsDelegate.setWaitForFocusLock(action.enabled)
                startCamera(forced = true)
            }

            is SettingsAction.FocusTimeoutSelected -> {
                settingsDelegate.setFocusTimeout(action.seconds)
            }

            is SettingsAction.SelfTimerSelected -> {
                settingsDelegate.setSelfTimerDuration(action.seconds)
            }

            is SettingsAction.VideoQualitySelected -> {
                onVideoQualitySelected(action.quality)
            }
        }
    }

    private fun onVideoQualitySelected(quality: Quality) {
        if (quality == settingsDelegate.modeSettings.videoQuality) return

        settingsDelegate.setVideoQuality(quality)

        startCamera(forced = true)
    }

    private fun toggleFlashMode() {
        when {
            cameraDelegate.sessionState.isFlashAvailable -> {
                val next = when (cameraDelegate.flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }

                setFlashMode(next)
            }

            else -> emitEffect(
                Effect.ShowMessage(R.string.flash_unavailable_in_selected_mode),
            )
        }
    }

    private fun toggleAspectRatio() {
        val current = modeDelegate.aspectRatio(settingsDelegate.settings.aspectRatio)

        val next = when (current) {
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
            else -> AspectRatio.RATIO_16_9
        }

        settingsDelegate.setAspectRatio(next)

        startCamera(true)
    }

    private fun toggleCameraSelector() {
        if (cameraDelegate.toggleLensFacing(modeDelegate.currentMode.extensionMode)) {
            startCamera(true)
        }
    }

    override fun onFeaturesSelected(
        boundLensFacing: Int,
        requested: List<GroupableFeature>,
        qualityFeature: GroupableFeature?,
        selected: Set<GroupableFeature>,
    ) {
        // The full request-vs-result picture (including which stabilization feature, if any,
        // survived) is only ever logged, never shown: the lead wants EIS left silently in its
        // known state -- 4K keeps priority and stabilization is given up without a notice.
        Log.i(TAG, "Requested $requested but got $selected")

        val droppedQuality = resolveDroppedVideoQuality(
            lensFacing = boundLensFacing,
            requestedQualityFeature = qualityFeature,
            selected = selected,
        ) ?: return

        emitEffect(Effect.ShowVideoQualityUnsupported(droppedQuality))
    }

    private fun initializeCamera(forced: Boolean = false) {
        cameraDelegate.initialize(
            forced = forced,
            extensionMode = modeDelegate.currentMode.extensionMode,
        )
    }

    // Start the camera with latest hard configuration
    private fun startCamera(forced: Boolean = false) {
        if (!cameraDelegate.beginBind(forced)) return

        slotCurrentMode()

        // Before the builder below reads it: the mode just slotted may store a different flash mode
        // than the one that was bound, and the ImageCapture is configured once, at build time.
        cameraDelegate.applyFlashMode(settingsDelegate.modeSettings.flashMode)

        val target = cameraDelegate.selectLens(
            isQrMode = modeDelegate.isQrMode,
            extensionMode = modeDelegate.currentMode.extensionMode,
        ) ?: return

        val settings = settingsDelegate.settings

        val outcome = cameraDelegate.bind(
            CameraBindSettings(
                mode = modeDelegate.currentMode,
                isQrMode = modeDelegate.isQrMode,
                isVideoMode = modeDelegate.isVideoMode,
                requiresVideoModeOnly = entryPoint.requiresVideoModeOnly,
                qrLensFacing = target.qrLensFacing,
                rotation = target.rotation,
                aspectRatio = modeDelegate.aspectRatio(settings.aspectRatio),
                flashMode = cameraDelegate.flashMode,
                photoQuality = settings.photoQuality,
                videoQuality = settingsDelegate.modeSettings.videoQuality,
                waitForFocusLock = settings.waitForFocusLock,
                enableZsl = settings.enableZsl,
                enableEis = settings.enableEis,
                selectHighestResolution = settings.selectHighestResolution,
                mirrorVideoOnFrontCamera = settings.saveVideoAsPreviewed,
            ),
        )

        onBindOutcome(outcome)
    }

    private fun onBindOutcome(outcome: BindOutcome) {
        when (outcome) {
            BindOutcome.FAILED -> emitEffect(
                Effect.ShowMessage(R.string.bind_failure),
            )

            BindOutcome.EXTENSION_UNUSABLE -> {
                emitEffect(
                    Effect.ShowMessage(R.string.extension_mode_unavailable),
                )

                // The bind never completed: currentMode still names the mode that was just
                // disabled and nothing is rendering into the preview. Refreshing the tabs
                // alone would only *visually* select another tab, leaving a frozen preview
                // behind a lying tab bar. Switch for real -- switchMode() rebinds, moves the
                // highlight and refreshes the tabs. Recursion stops because the default mode
                // uses no extension.
                switchMode(modeDelegate.defaultMode)
            }

            BindOutcome.BOUND -> cameraDelegate.announceBind(
                aspectRatio = modeDelegate.aspectRatio(settingsDelegate.settings.aspectRatio),
                isInPhotoMode = modeDelegate.isInPhotoMode,
                currentMode = { modeDelegate.currentMode },
            )
        }
    }

    private fun onStorageLocationNotFound() {
        applicationScope.launch(mainDispatcher) {
            revertToMediaStoreLocation()
            emitEffect(Effect.ShowStorageLocationNotFound)
        }
    }

    private fun flashPreview() {
        emitEffect(Effect.FlashPreview(uiState.value.capture.selfIlluminate))
    }

    private fun switchMode(mode: CameraMode) {
        if (!modeDelegate.select(mode)) {
            return
        }

        cameraDelegate.cancelFocusTimer()

        startCamera(true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (entryPoint.showsCameraModeTabs) {
            emitEffect(Effect.GoToModeTab(modeDelegate.currentMode))
        }
    }

    private fun emitEffect(effect: Effect) {
        screenEffects.trySend(effect)
    }

    private fun renderUiState(state: ViewfinderState): ViewfinderUiState {
        return uiStateMapper.map(
            mode = state.mode,
            isVideoMode = modeDelegate.isVideoMode,
            flashMode = state.flashMode,
            aspectRatio = modeDelegate.aspectRatio(state.settings.aspectRatio),
            requireLocation = state.requireLocation,
            settings = state.settings,
            modeSettings = state.modeSettings,
            session = state.session,
        )
    }

    private fun slotCurrentMode() {
        settingsDelegate.selectModeSlot(
            ModeSlot(
                mode = modeDelegate.currentMode,
                isFrontFacing = cameraDelegate.lensFacing == CameraSelector.LENS_FACING_FRONT,
            ),
        )
    }

    companion object {
        private const val TAG = "ViewfinderViewModel"
    }
}
