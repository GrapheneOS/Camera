package app.grapheneos.camera.ui.viewfinder.screen

import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Quality
import androidx.lifecycle.ViewModel
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import app.grapheneos.camera.ui.viewfinder.screen.mapper.ViewfinderUiStateMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CameraAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.CaptureAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderAction.SettingsAction
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import javax.inject.Inject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking

class ViewfinderViewModel @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val resolveAvailableModes: ResolveAvailableModes,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
    private val uiStateMapper: ViewfinderUiStateMapper,
) : ViewModel(),
    CameraSession.Listener {

    private var attachment: Attachment? = null

    private val attached: Attachment
        get() {
            return requireNotNull(attachment) {
                "No Activity is attached to the viewfinder"
            }
        }

    private val environment: CameraSessionEnvironment
        get() {
            return attached.environment
        }

    private val bindEffects: ViewfinderEffects
        get() {
            return attached.effects
        }

    private val chrome: ViewfinderChrome
        get() {
            return attached.chrome
        }

    private val session: CameraSession
        get() {
            return attached.session
        }

    private val settings: CameraSettings
        get() {
            return settingsRepository.settings.value
        }

    private var modeSettings: ModeSettings = ModeSettings()

    private var slot: ModeSlot? = null

    private val sessionState = MutableStateFlow(ViewfinderSessionState())

    private val screenEffects = Channel<Effect>(capacity = Channel.BUFFERED)

    val effects: Flow<Effect> = screenEffects.receiveAsFlow()

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    private val renderedState: ViewfinderUiState
        get() {
            return uiStateMapper.map(
                mode = currentMode,
                isVideoMode = isVideoMode,
                flashMode = flashMode,
                aspectRatio = aspectRatio,
                requireLocation = requireLocation,
                settings = settings,
                modeSettings = modeSettings,
                session = sessionState.value,
            )
        }

    var currentMode: CameraMode = DEFAULT_CAMERA_MODE
        private set

    val isQRMode: Boolean
        get() {
            return currentMode.isQr
        }

    val isVideoMode: Boolean
        get() {
            return currentMode.isVideo || entryPoint.requiresVideoModeOnly
        }

    private val isInPhotoMode: Boolean
        get() {
            return !(isQRMode || isVideoMode)
        }

    private var gridType: GridType by setting(
        read = { it.gridType },
        write = { current, value -> current.copy(gridType = value) },
    )

    private var focusTimeout: Long by setting(
        read = { it.focusTimeoutSeconds },
        write = { current, value -> current.copy(focusTimeoutSeconds = value) },
    )

    private var selfTimerDuration: Int by setting(
        read = { it.selfTimerDurationSeconds },
        write = { current, value -> current.copy(selfTimerDurationSeconds = value) },
    )

    private var enableEIS: Boolean by setting(
        read = { it.enableEis },
        write = { current, value -> current.copy(enableEis = value) },
    )

    var photoQuality: Int by setting(
        read = { it.photoQuality },
        write = { current, value -> current.copy(photoQuality = value) },
    )

    private var waitForFocusLock: Boolean by setting(
        read = { it.waitForFocusLock },
        write = { current, value -> current.copy(waitForFocusLock = value) },
    )

    private var aspectRatio: Int
        get() {
            return when {
                isVideoMode -> AspectRatio.RATIO_16_9
                isQRMode -> AspectRatio.RATIO_4_3
                else -> settings.aspectRatio
            }
        }
        set(value) {
            runBlocking { settingsRepository.update { it.copy(aspectRatio = value) } }

            publishUiState()
        }

    private var scanAllCodes: Boolean by setting(
        read = { it.scanAllCodes },
        write = { current, value -> current.copy(scanAllCodes = value) },
        onChanged = { session.refreshQrHints() },
    )

    private var includeAudio: Boolean by setting(
        read = { it.includeAudio },
        write = { current, value -> current.copy(includeAudio = value) },
    )

    private var flashMode: Int = SettingsDefaults.FLASH_MODE

    var videoQuality: Quality
        get() = modeSettings.videoQuality
        set(value) {
            writeMode { slot -> settingsRepository.setVideoQuality(slot, value) }
        }

    // Session state rather than the stored value: geo-tagging is only ever on once the permission
    // is actually granted, and reloadSettings() is what settles a stored "on" against that. Reading
    // the preference back here would resurrect the very stale "on" the coercion exists to drop.
    private var requireLocation: Boolean = false
        set(value) {
            when {
                value -> emitEffect(Effect.StartLocationUpdates)
                else -> emitEffect(Effect.StopLocationUpdates)
            }

            // A permission result is delivered before the first onResume of an activity the system
            // recreated, so this can run before a mode has been slotted — see modeSettings.
            writeMode { slot -> settingsRepository.setGeoTagging(slot, value) }

            field = value

            publishUiState()
        }

    private var selfIlluminate: Boolean
        get() {
            return modeSettings.selfIllumination &&
                sessionState.value.lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        set(value) {
            writeMode { slot -> settingsRepository.setSelfIllumination(slot, value) }

            publishUiState()
            emitEffect(Effect.ApplySelfIllumination(selfIlluminate))
        }

    fun attach(
        environment: CameraSessionEnvironment,
        effects: ViewfinderEffects,
        chrome: ViewfinderChrome,
        session: CameraSession,
    ) {
        attachment = Attachment(
            environment = environment,
            effects = effects,
            chrome = chrome,
            session = session,
        )

        session.listener = this

        refreshSessionState()
    }

    fun detach() {
        attachment?.session?.listener = null

        attachment = null

        sessionState.value = ViewfinderSessionState()
    }

    override fun onZoomStateChanged() {
        chrome.updateZoomThumb()
        emitEffect(Effect.ShowZoomPanel)
    }

    override fun onCameraProviderUnavailable() {
        emitEffect(Effect.ShowMessage(R.string.camera_provider_init_failure))
    }

    override fun onExtensionsUnavailable() {
        emitEffect(
            Effect.ShowMessage(R.string.extensions_manager_init_failure),
        )
    }

    override fun onProviderReady(forced: Boolean) {
        startCamera(forced = forced)
    }

    private fun setFlashMode(value: Int) {
        writeMode { slot -> settingsRepository.setFlashMode(slot, value) }

        applyFlashMode(value)
    }

    fun reloadSettings() {
        slotCurrentMode()

        if (isVideoMode) {
            emitEffect(Effect.ReloadVideoQualities)
        }

        applyFlashMode(modeSettings.flashMode)

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        requireLocation = modeSettings.geoTagging &&
            !environment.shouldAskForLocationPermission()

        selfIlluminate = modeSettings.selfIllumination

        publishUiState()
    }

    fun onAction(action: ViewfinderAction) {
        when (action) {
            is CameraAction -> onCameraAction(action)
            is CaptureAction -> onCaptureAction(action)
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

    private fun onSettingsAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.ScanAllCodesToggleClicked -> {
                scanAllCodes = !scanAllCodes
            }

            is SettingsAction.GridToggleClicked -> {
                gridType = nextGridType()
            }

            is SettingsAction.AudioToggled -> {
                includeAudio = action.enabled
            }

            is SettingsAction.GeoTaggingToggled -> {
                requireLocation = action.enabled
            }

            is SettingsAction.SelfIlluminationToggled -> {
                selfIlluminate = action.enabled
            }

            is SettingsAction.StabilizationToggled -> {
                enableEIS = action.enabled
                startCamera(forced = true)
            }

            is SettingsAction.FocusLockToggled -> {
                waitForFocusLock = action.enabled
                startCamera(forced = true)
            }

            is SettingsAction.FocusTimeoutSelected -> {
                focusTimeout = action.seconds
            }

            is SettingsAction.SelfTimerSelected -> {
                selfTimerDuration = action.seconds
            }

            is SettingsAction.VideoQualitySelected -> {
                onVideoQualitySelected(action.quality)
            }
        }
    }

    private fun nextGridType(): GridType {
        return when (gridType) {
            GridType.NONE -> GridType.THREE_BY_THREE
            GridType.THREE_BY_THREE -> GridType.FOUR_BY_FOUR
            GridType.FOUR_BY_FOUR -> GridType.GOLDEN_RATIO
            GridType.GOLDEN_RATIO -> GridType.NONE
        }
    }

    private fun onVideoQualitySelected(quality: Quality) {
        if (quality == videoQuality) return

        videoQuality = quality

        startCamera(forced = true)
    }

    private fun toggleFlashMode() {
        when {
            sessionState.value.isFlashAvailable -> {
                val next = when (flashMode) {
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
        aspectRatio = when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
            else -> AspectRatio.RATIO_16_9
        }

        startCamera(true)
    }

    private fun toggleCameraSelector() {
        // Manually switch to the opposite lens facing
        session.lensFacing = when (session.lensFacing) {
            CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
            else -> CameraSelector.LENS_FACING_BACK
        }

        val isNewLensSupported = session.isLensFacingSupported(
            lensFacing = session.lensFacing,
            extensionMode = currentMode.extensionMode,
        )

        when {
            isNewLensSupported -> startCamera(true)

            // Else revert back to the old facing (while displaying an error message
            // to the user)
            else -> {
                session.lensFacing = when (session.lensFacing) {
                    CameraSelector.LENS_FACING_BACK -> {
                        emitEffect(
                            Effect.ShowMessage(R.string.rear_camera_unavailable),
                        )
                        CameraSelector.LENS_FACING_FRONT
                    }

                    else -> {
                        emitEffect(
                            Effect.ShowMessage(R.string.front_camera_unavailable),
                        )
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            }
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

    fun initializeCamera(forced: Boolean = false) {
        session.initialize(forced = forced, extensionMode = currentMode.extensionMode)
    }

    // Start the camera with latest hard configuration
    fun startCamera(forced: Boolean = false) {
        if ((!forced && session.camera != null) || session.cameraProvider == null) return

        // Cancel any pending capture requests
        bindEffects.cancelPendingCapture()

        bindEffects.hideExposurePanel()
        slotCurrentMode()

        // Before the builder below reads it: the mode just slotted may store a different flash mode
        // than the one that was bound, and the ImageCapture is configured once, at build time.
        applyFlashMode(modeSettings.flashMode)

        val rotation = environment.displayRotation

        if (!environment.isSessionActive) return

        // Test whether the current lens facing is supported by the current device
        // If not then silently switch to the other lens facing
        // (Snackbar/popup message can be shown before startCamera is called
        // in specific cases of explicitly switching to another side or if
        // the camera is expected)
        val isCurrentLensSupported = session.isLensFacingSupported(
            lensFacing = session.lensFacing,
            extensionMode = currentMode.extensionMode,
        )

        if (!isCurrentLensSupported) {
            session.lensFacing = when (session.lensFacing) {
                CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
                else -> CameraSelector.LENS_FACING_BACK
            }
        }

        session.selectLensFacing(session.lensFacing)

        // To use the last frame instead of showing a blank screen when
        // the camera that is being currently used gets unbind
        bindEffects.updateLastFrame()

        val qrLensFacing = when {
            isQRMode -> {
                bindEffects.startFocusTimer()
                qrLensFacing()
            }

            else -> null
        }

        publishUiState()

        bindEffects.forceUpdateOrientationSensor()

        val bindSettings = CameraBindSettings(
            mode = currentMode,
            isQrMode = isQRMode,
            isVideoMode = isVideoMode,
            requiresVideoModeOnly = entryPoint.requiresVideoModeOnly,
            qrLensFacing = qrLensFacing,
            rotation = rotation,
            aspectRatio = aspectRatio,
            flashMode = flashMode,
            photoQuality = photoQuality,
            videoQuality = videoQuality,
            waitForFocusLock = waitForFocusLock,
            enableZsl = settings.enableZsl,
            enableEis = enableEIS,
            selectHighestResolution = settings.selectHighestResolution,
            mirrorVideoOnFrontCamera = settings.saveVideoAsPreviewed,
        )

        val outcome = session.bind(bindSettings)

        refreshSessionState()

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
                switchMode(DEFAULT_CAMERA_MODE)
            }

            BindOutcome.BOUND -> announceBind()
        }
    }

    private fun onStorageLocationNotFound() {
        runBlocking { revertToMediaStoreLocation() }

        emitEffect(Effect.ShowStorageLocationNotFound)
    }

    private fun flashPreview() {
        emitEffect(Effect.FlashPreview(selfIlluminate))
    }

    fun switchMode(mode: CameraMode) {
        if (currentMode == mode) {
            return
        }

        currentMode = mode

        bindEffects.cancelFocusTimer()

        publishUiState()

        startCamera(true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (entryPoint.showsCameraModeTabs) {
            emitEffect(Effect.GoToModeTab(currentMode))
        }
    }

    private fun applyFlashMode(value: Int) {
        flashMode = value
        session.imageCapture?.flashMode = value
        publishUiState()
    }

    private fun emitEffect(effect: Effect) {
        screenEffects.trySend(effect)
    }

    private fun publishUiState() {
        _uiState.value = renderedState
    }

    private fun refreshSessionState() {
        sessionState.value = ViewfinderSessionState(
            lensFacing = session.lensFacing,
            canTakePicture = session.imageCapture != null,
            isFlashAvailable = session.isFlashAvailable,
            canApplyVideoStabilization = session.canApplyVideoStabilization(),
        )

        publishUiState()
    }

    private fun slotCurrentMode() {
        val current = ModeSlot(
            mode = currentMode,
            isFrontFacing = session.lensFacing == CameraSelector.LENS_FACING_FRONT,
        )

        slot = current

        modeSettings = runBlocking { settingsRepository.modeSettings(current) }
    }

    private fun writeMode(write: suspend (ModeSlot) -> ModeSettings) {
        val current = slot ?: return

        modeSettings = runBlocking { write(current) }
    }

    private fun qrLensFacing(): Int {
        val isRearLensSupported = session.isLensFacingSupported(
            lensFacing = CameraSelector.LENS_FACING_BACK,
            extensionMode = currentMode.extensionMode,
        )

        if (isRearLensSupported) {
            return CameraSelector.LENS_FACING_BACK
        }

        emitEffect(Effect.ShowMessage(R.string.qr_rear_camera_unavailable))

        return CameraSelector.LENS_FACING_FRONT
    }

    private fun announceBind() {
        loadTabs()

        session.reattachZoomState()

        chrome.updateZoomThumb()
        emitEffect(Effect.HideZoomPanel)

        session.camera?.cameraInfo?.exposureState?.let { chrome.applyExposureState(it) }

        emitEffect(Effect.ResetTorchToggle)

        session.camera?.cameraInfo?.let { chrome.onPreviewBound(aspectRatio, it) }

        chrome.updateGyroscopeIndicator(isInPhotoMode)
    }

    private fun loadTabs() {
        if (!entryPoint.showsCameraModeTabs) {
            return
        }

        session.probeUnknownExtensions(
            onRestart = ::loadTabs,
            onSettled = ::buildTabs,
        )
    }

    private fun buildTabs() {
        chrome.setCameraModeTabs(
            modes = resolveAvailableModes(
                allowsQrScanning = entryPoint.allowsQrScanning,
                extensionsAvailable = session.extensionsAvailable,
            ),
            currentMode = currentMode,
        )
    }

    private fun <T> setting(
        read: (CameraSettings) -> T,
        write: (CameraSettings, T) -> CameraSettings,
        onChanged: (T) -> Unit = {},
    ): ReadWriteProperty<Any?, T> {
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return read(settings)
            }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                runBlocking { settingsRepository.update { write(it, value) } }

                publishUiState()
                onChanged(value)
            }
        }
    }

    private class Attachment(
        val environment: CameraSessionEnvironment,
        val effects: ViewfinderEffects,
        val chrome: ViewfinderChrome,
        val session: CameraSession,
    )

    companion object {
        private const val TAG = "ViewfinderViewModel"

        val DEFAULT_CAMERA_MODE = CameraMode.CAMERA
    }
}
