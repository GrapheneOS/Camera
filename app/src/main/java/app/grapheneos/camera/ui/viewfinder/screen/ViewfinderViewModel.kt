package app.grapheneos.camera.ui.viewfinder.screen

import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
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
import app.grapheneos.camera.ui.viewfinder.screen.mapper.CameraBindSettingsMapper
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

class ViewfinderViewModel @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val settingsDelegate: ViewfinderSettingsDelegate,
    private val modeDelegate: ViewfinderModeDelegate,
    private val cameraDelegate: ViewfinderCameraDelegate,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
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
        ),
        render = uiStateMapper::map,
    )
    override val uiState: StateFlow<ViewfinderUiState> = stateHolder.uiState

    private val screenEffects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = screenEffects.receiveAsFlow()

    private var sessionEvents: Job? = null

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
        chrome: ViewfinderChrome,
        session: CameraSession,
    ) {
        cameraDelegate.attach(
            environment = environment,
            chrome = chrome,
            session = session,
            emitEffect = ::emitEffect,
        )

        sessionEvents?.cancel()
        sessionEvents = viewModelScope.launch(mainDispatcher) {
            cameraDelegate.sessionEvents.collect { event ->
                onSessionEvent(event)
            }
        }
    }

    fun detach() {
        sessionEvents?.cancel()
        sessionEvents = null

        cameraDelegate.detach()
    }

    private fun onSessionEvent(event: CameraSessionEvent) {
        when (event) {
            is CameraSessionEvent.ZoomStateChanged -> {
                cameraDelegate.onZoomStateChanged()
            }

            is CameraSessionEvent.CameraProviderUnavailable -> {
                emitEffect(Effect.ShowMessage(R.string.camera_provider_init_failure))
            }

            is CameraSessionEvent.ExtensionsUnavailable -> {
                emitEffect(Effect.ShowMessage(R.string.extensions_manager_init_failure))
            }

            is CameraSessionEvent.ProviderReady -> {
                startCamera(forced = event.forced)
            }

            is CameraSessionEvent.FeaturesSelected -> {
                onFeaturesSelected(event)
            }
        }
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

        emitEffect(Effect.ApplySelfIllumination(state().selfIlluminate()))
    }

    private fun applyModeSettings() {
        slotCurrentMode()

        val slotted = state()

        if (slotted.isVideoMode()) {
            emitEffect(Effect.ReloadVideoQualities)
        }

        cameraDelegate.applyFlashMode(slotted.modeSettings.flashMode)

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        setRequireLocation(
            enabled = slotted.modeSettings.geoTagging &&
                !cameraDelegate.shouldAskForLocationPermission(),
        )

        setSelfIllumination(slotted.modeSettings.selfIllumination)
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
            is CameraAction.LensSwitchClicked -> switchLens()
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
            is LifecycleAction.PreviewStreamingStarted -> applyModeSettings()
            is LifecycleAction.CameraPermissionGranted -> initializeCamera(forced = false)
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
        if (quality == state().modeSettings.videoQuality) return

        settingsDelegate.setVideoQuality(quality)

        startCamera(forced = true)
    }

    private fun toggleFlashMode() {
        val currentState = state()

        when {
            currentState.requiresVideoModeOnly -> {
                emitEffect(Effect.ShowMessage(R.string.flash_switch_unsupported))
            }

            currentState.session.isFlashAvailable -> {
                val next = when (currentState.flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }

                setFlashMode(next)
            }

            else -> {
                emitEffect(Effect.ShowMessage(R.string.flash_unavailable_in_selected_mode))
            }
        }
    }

    private fun toggleAspectRatio() {
        val next = when (state().aspectRatio()) {
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
            else -> AspectRatio.RATIO_16_9
        }

        settingsDelegate.setAspectRatio(next)

        startCamera(forced = true)
    }

    private fun switchLens() {
        if (cameraDelegate.toggleLensFacing(state().mode.extensionMode)) {
            startCamera(forced = true)
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

    private fun initializeCamera(forced: Boolean) {
        when {
            cameraDelegate.isProviderReady -> startCamera(forced = forced)
            else -> cameraDelegate.initialize(
                forced = forced,
                extensionMode = state().mode.extensionMode,
            )
        }
    }

    private fun startCamera(forced: Boolean) {
        if (!cameraDelegate.beginBind(forced)) return

        slotCurrentMode()

        // Before the builder below reads it: the mode just slotted may store a different flash mode
        // than the one that was bound, and the ImageCapture is configured once, at build time.
        cameraDelegate.applyFlashMode(state().modeSettings.flashMode)

        val bindState = state()

        val target = cameraDelegate.selectLens(
            isQrMode = bindState.isQrMode(),
            extensionMode = bindState.mode.extensionMode,
        ) ?: return

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

            BindOutcome.BOUND -> {
                val boundState = state()

                cameraDelegate.announceBind(
                    aspectRatio = boundState.aspectRatio(),
                    isInPhotoMode = boundState.isInPhotoMode(),
                    currentMode = { state().mode },
                )
            }
        }
    }

    private fun onStorageLocationNotFound() {
        applicationScope.launch(mainDispatcher) {
            revertToMediaStoreLocation()
            emitEffect(Effect.ShowStorageLocationNotFound)
        }
    }

    private fun flashPreview() {
        emitEffect(Effect.FlashPreview(state().selfIlluminate()))
    }

    private fun switchMode(mode: CameraMode) {
        if (!modeDelegate.select(mode)) return

        cameraDelegate.cancelFocusTimer()

        startCamera(forced = true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (entryPoint.showsCameraModeTabs) {
            emitEffect(Effect.GoToModeTab(state().mode))
        }
    }

    private fun emitEffect(effect: Effect) {
        screenEffects.trySend(effect)
    }

    private fun slotCurrentMode() {
        settingsDelegate.selectModeSlot(
            ModeSlot(
                mode = state().mode,
                isFrontFacing = cameraDelegate.lensFacing == CameraSelector.LENS_FACING_FRONT,
            ),
        )
    }

    private fun state(): ViewfinderState {
        return stateHolder.state.value
    }

    private companion object {
        const val TAG = "ViewfinderViewModel"
    }
}
