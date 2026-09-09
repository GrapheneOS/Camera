package app.grapheneos.camera.ui.viewfinder.screen

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.video.Quality
import app.grapheneos.camera.R
import app.grapheneos.camera.TunePlayer
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.gallery.usecase.RevertToMediaStoreLocation
import dagger.hilt.android.scopes.ActivityScoped
import java.io.IOException
import javax.inject.Inject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@ActivityScoped
class ViewfinderController @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val resolveAvailableModes: ResolveAvailableModes,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
    private val revertToMediaStoreLocation: RevertToMediaStoreLocation,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : CameraSession.Listener {

    private var attachment: Attachment? = null

    @set:VisibleForTesting
    var mPlayer: TunePlayer? = null

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

    private val effects: ViewfinderEffects
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

    private var settings: CameraSettings = runBlocking {
        settingsRepository.settings.first()
    }

    private var modeSettings: ModeSettings = ModeSettings()

    private val preferencesScope = CoroutineScope(mainDispatcher)

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

    val canTakePicture: Boolean
        get() {
            return session.imageCapture != null
        }

    private val isInPhotoMode: Boolean
        get() {
            return !(isQRMode || isVideoMode)
        }

    var gridType: GridType by setting(
        read = { it.gridType },
        write = { current, value -> current.copy(gridType = value) },
    )

    var focusTimeout: Long by setting(
        read = { it.focusTimeoutSeconds },
        write = { current, value -> current.copy(focusTimeoutSeconds = value) },
    )

    var selfTimerDuration: Int by setting(
        read = { it.selfTimerDurationSeconds },
        write = { current, value -> current.copy(selfTimerDurationSeconds = value) },
    )

    var enableCameraSounds: Boolean by setting(
        read = { it.enableCameraSounds },
        write = { current, value -> current.copy(enableCameraSounds = value) },
    )

    var enableEIS: Boolean by setting(
        read = { it.enableEis },
        write = { current, value -> current.copy(enableEis = value) },
    )

    var saveImageAsPreviewed: Boolean by setting(
        read = { it.saveImageAsPreviewed },
        write = { current, value -> current.copy(saveImageAsPreviewed = value) },
    )

    var photoQuality: Int by setting(
        read = { it.photoQuality },
        write = { current, value -> current.copy(photoQuality = value) },
    )

    var removeExifAfterCapture: Boolean by setting(
        read = { it.removeExifAfterCapture },
        write = { current, value -> current.copy(removeExifAfterCapture = value) },
    )

    var waitForFocusLock: Boolean by setting(
        read = { it.waitForFocusLock },
        write = { current, value -> current.copy(waitForFocusLock = value) },
    )

    var aspectRatio: Int
        get() {
            return when {
                isVideoMode -> AspectRatio.RATIO_16_9
                isQRMode -> AspectRatio.RATIO_4_3
                else -> settings.aspectRatio
            }
        }
        set(value) {
            settings = runBlocking {
                settingsRepository.update { it.copy(aspectRatio = value) }
            }
        }

    var scanAllCodes: Boolean by setting(
        read = { it.scanAllCodes },
        write = { current, value -> current.copy(scanAllCodes = value) },
        onChanged = { value ->
            if (isQRMode) {
                chrome.applyScanAllCodesChrome(value)
            }

            session.refreshQrHints()
        },
    )

    var includeAudio: Boolean by setting(
        read = { it.includeAudio },
        write = { current, value -> current.copy(includeAudio = value) },
        onChanged = { value -> chrome.onIncludeAudioChanged(value) },
    )

    var flashMode: Int = SettingsDefaults.FLASH_MODE
        private set

    var videoQuality: Quality
        get() = modeSettings.videoQuality
        set(value) {
            runBlocking {
                settingsRepository.setVideoQuality(value)
            }?.let { modeSettings = it }
        }

    // Session state rather than the stored value: geo-tagging is only ever on once the permission
    // is actually granted, and reloadSettings() is what settles a stored "on" against that. Reading
    // the preference back here would resurrect the very stale "on" the coercion exists to drop.
    var requireLocation: Boolean = false
        set(value) {
            chrome.onRequireLocationChanged(value)

            // A permission result is delivered before the first onResume of an activity the system
            // recreated, so this can run before a mode has been slotted — see modeSettings.
            runBlocking {
                settingsRepository.setGeoTagging(value)
            }?.let { modeSettings = it }

            chrome.onGeoTaggingChanged(value)

            field = value
        }

    var selfIlluminate: Boolean
        get() {
            return modeSettings.selfIllumination &&
                session.lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        set(value) {
            runBlocking {
                settingsRepository.setSelfIllumination(value)
            }?.let { modeSettings = it }

            chrome.onSelfIlluminationChanged(value)
        }

    init {
        preferencesScope.launch {
            settingsRepository.settings.collect { settings = it }
        }
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

        mPlayer = environment.createTunePlayer()

        session.listener = this
    }

    fun detach() {
        attachment?.session?.listener = null

        attachment = null
        mPlayer = null

        preferencesScope.cancel()
    }

    override fun onZoomStateChanged() {
        chrome.updateZoomThumb(shouldShowPanel = true)
    }

    override fun onCameraProviderUnavailable() {
        effects.showMessage(R.string.camera_provider_init_failure)
    }

    override fun onExtensionsUnavailable() {
        effects.showMessage(R.string.extensions_manager_init_failure)
    }

    override fun onProviderReady(forced: Boolean) {
        startCamera(forced = forced)
    }

    fun setFlashMode(value: Int) {
        runBlocking {
            settingsRepository.setFlashMode(value)
        }?.let { modeSettings = it }

        applyFlashMode(value)
    }

    fun shouldShowGyroscope(): Boolean {
        return isInPhotoMode && settings.gyroscopeSuggestions
    }

    fun reloadSettings() {
        slotCurrentMode()

        if (isVideoMode) {
            chrome.reloadVideoQualities()
        }

        applyFlashMode(modeSettings.flashMode)

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        requireLocation = modeSettings.geoTagging &&
            !environment.shouldAskForLocationPermission()

        selfIlluminate = modeSettings.selfIllumination

        chrome.showOnlyRelevantSettings()
    }

    fun loadSettings() {
        includeAudio = settings.includeAudio
    }

    fun toggleFlashMode() {
        when {
            session.isFlashAvailable -> {
                val next = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }

                setFlashMode(next)
            }

            else -> effects.showMessage(R.string.flash_unavailable_in_selected_mode)
        }
    }

    fun toggleAspectRatio() {
        aspectRatio = when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
            else -> AspectRatio.RATIO_16_9
        }

        startCamera(true)
    }

    fun toggleCameraSelector() {
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
                        effects.showMessage(R.string.rear_camera_unavailable)
                        CameraSelector.LENS_FACING_FRONT
                    }

                    else -> {
                        effects.showMessage(R.string.front_camera_unavailable)
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

        effects.showVideoQualityUnsupported(droppedQuality)
    }

    fun initializeCamera(forced: Boolean = false) {
        session.initialize(forced = forced, extensionMode = currentMode.extensionMode)
    }

    // Start the camera with latest hard configuration
    fun startCamera(forced: Boolean = false) {
        if ((!forced && session.camera != null) || session.cameraProvider == null) return

        // Cancel any pending capture requests
        effects.cancelPendingCapture()

        chrome.hideExposurePanel()
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
        effects.updateLastFrame()

        val qrLensFacing = when {
            isQRMode -> {
                effects.startFocusTimer()
                qrLensFacing()
            }

            else -> null
        }

        if (isVideoMode) {
            chrome.setMicMutedIconVisible(!includeAudio)
        }

        effects.forceUpdateOrientationSensor()

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

        return when (session.bind(bindSettings)) {
            BindOutcome.FAILED -> effects.showMessage(R.string.bind_failure)

            BindOutcome.EXTENSION_UNUSABLE -> {
                effects.showMessage(R.string.extension_mode_unavailable)

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
    fun onStorageLocationNotFound() {
        runBlocking { revertToMediaStoreLocation() }

        effects.showStorageLocationNotFound()
    }

    fun snapPreview() {
        effects.flashPreview(selfIlluminate)
    }

    fun switchMode(mode: CameraMode) {
        if (currentMode == mode) {
            return
        }

        currentMode = mode

        effects.cancelFocusTimer()

        chrome.applyModeChrome(
            mode = mode,
            isVideoMode = isVideoMode,
            scanAllCodes = scanAllCodes,
        )

        startCamera(true)

        // A mode can change with no touch involved, so the strip follows the camera and not the
        // other way round - currentMode, because an extension that fails to bind falls back to
        // another mode from inside startCamera(). Left until after that rebind, which blocks the
        // main thread for long enough to swallow the animation whole.
        if (entryPoint.showsCameraModeTabs) {
            chrome.goToModeTab(currentMode)
        }
    }

    private fun applyFlashMode(value: Int) {
        flashMode = value
        session.imageCapture?.flashMode = value
        chrome.onFlashModeChanged()
    }

    private fun slotCurrentMode() {
        modeSettings = runBlocking {
            settingsRepository.selectMode(
                mode = currentMode,
                isFrontFacing = session.lensFacing == CameraSelector.LENS_FACING_FRONT,
            )
        }
    }

    private fun qrLensFacing(): Int {
        val isRearLensSupported = session.isLensFacingSupported(
            lensFacing = CameraSelector.LENS_FACING_BACK,
            extensionMode = currentMode.extensionMode,
        )

        if (isRearLensSupported) {
            return CameraSelector.LENS_FACING_BACK
        }

        effects.showMessage(R.string.qr_rear_camera_unavailable)

        return CameraSelector.LENS_FACING_FRONT
    }

    private fun announceBind() {
        loadTabs()

        session.reattachZoomState()

        chrome.updateZoomThumb(shouldShowPanel = false)

        session.camera?.cameraInfo?.exposureState?.let { chrome.applyExposureState(it) }

        chrome.resetTorchToggle()

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
                settings = runBlocking { settingsRepository.update { write(it, value) } }

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
        private const val TAG = "ViewfinderController"

        val DEFAULT_CAMERA_MODE = CameraMode.CAMERA
    }
}
