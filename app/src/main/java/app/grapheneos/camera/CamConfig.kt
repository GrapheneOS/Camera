package app.grapheneos.camera

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.qr.BarcodeFormats
import app.grapheneos.camera.ui.videoQualityTitle
import app.grapheneos.camera.ui.viewfinder.ViewfinderEffects
import com.google.zxing.BarcodeFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@SuppressLint("UnsafeOptInUsageError")
class CamConfig @AssistedInject constructor(
    @Assisted private val environment: CameraSessionEnvironment,
    @Assisted private val effects: ViewfinderEffects,
    private val entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val capturedItemRepository: CapturedItemRepository,
    private val resolveAvailableModes: ResolveAvailableModes,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
    private val barcodeFormats: BarcodeFormats,
    cameraSessionFactory: CameraSession.Factory,
) {

    private val session = cameraSessionFactory.create(
        environment = environment,
        listener = object : CameraSession.Listener {
            override fun onZoomStateChanged() {
                effects.updateZoomThumb(shouldShowPanel = true)
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

            override fun onFeaturesSelected(
                boundLensFacing: Int,
                requested: List<GroupableFeature>,
                qualityFeature: GroupableFeature?,
                selected: Set<GroupableFeature>,
            ) {
                this@CamConfig.onFeaturesSelected(
                    boundLensFacing,
                    requested,
                    qualityFeature,
                    selected,
                )
            }
        },
    )

    var camera: Camera?
        get() = session.camera
        set(value) {
            session.camera = value
        }

    val zoomState: ZoomState?
        get() = session.zoomState

    val cameraProvider: ProcessCameraProvider?
        get() = session.cameraProvider

    val imageCapture: ImageCapture?
        get() = session.imageCapture

    val preview: Preview?
        get() = session.preview

    val videoCapture: VideoCapture<Recorder>?
        get() = session.videoCapture

    val iAnalyzer: ImageAnalysis?
        get() = session.iAnalyzer

    var lensFacing: Int
        get() = session.lensFacing
        set(value) {
            session.lensFacing = value
        }

    val isFlashAvailable: Boolean
        get() = session.isFlashAvailable

    var isTorchOn: Boolean
        get() = session.isTorchOn
        set(value) {
            session.isTorchOn = value
        }

    val isZslSupported: Boolean
        get() = session.isZslSupported

    val allowedFormats: List<BarcodeFormat>
        get() = barcodeFormats.enabled

    @set:VisibleForTesting
    var mPlayer = environment.createTunePlayer()

    private var settings: CameraSettings = runBlocking { settingsRepository.settings.first() }

    private var currentStorageLocation: String = runBlocking {
        capturedItemRepository.storageLocation.first()
    }

    private var modeSettings: ModeSettings = ModeSettings()

    private val preferencesScope = CoroutineScope(Dispatchers.Main.immediate)

    var lastCapturedItem: CapturedItem? = null

    init {
        preferencesScope.launch {
            settingsRepository.settings.collect { settings = it }
        }

        preferencesScope.launch {
            capturedItemRepository.storageLocation.collect { currentStorageLocation = it }
        }

        if (!entryPoint.isSecureSession) {
            try {
                runBlocking {
                    capturedItemRepository.migrateStoredCaptures(::updateLastCapturedItem)
                    capturedItemRepository.releaseUntrackedSafTrees()
                }
            } catch (e: IOException) {
                Log.e(TAG, "unable to migrate the stored captures", e)
            }

            fetchLastCapturedItem()
        }
    }

    var isVideoMode = false
        private set
        get() {
            return field || entryPoint.requiresVideoModeOnly
        }

    val canTakePicture: Boolean
        get() {
            return imageCapture != null
        }

    var isQRMode = false
        private set

    var currentMode: CameraMode = DEFAULT_CAMERA_MODE
        private set

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

    var gridType: GridType by setting(
        read = { it.gridType },
        write = { current, value -> current.copy(gridType = value) },
    )

    var videoQuality: Quality
        get() {
            return modeSettings.videoQuality
        }
        set(value) {
            runBlocking {
                settingsRepository.setVideoQuality(value)
            }?.let { modeSettings = it }
        }

    var flashMode: Int = SettingsDefaults.FLASH_MODE
        private set

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

    var scanAllCodes: Boolean
        get() {
            return settings.scanAllCodes
        }
        set(value) {
            settings = runBlocking {
                settingsRepository.update { it.copy(scanAllCodes = value) }
            }

            if (isQRMode) {
                effects.applyScanAllCodesChrome(value)
            }

            session.refreshQrHints()
        }

    var includeAudio: Boolean
        get() {
            return settings.includeAudio
        }
        set(value) {
            settings = runBlocking {
                settingsRepository.update { it.copy(includeAudio = value) }
            }

            effects.onIncludeAudioChanged(value)
        }

    var enableEIS: Boolean by setting(
        read = { it.enableEis },
        write = { current, value -> current.copy(enableEis = value) },
    )

    var saveImageAsPreviewed: Boolean by setting(
        read = { it.saveImageAsPreviewed },
        write = { current, value -> current.copy(saveImageAsPreviewed = value) },
    )

    var storageLocation: String
        get() {
            return currentStorageLocation
        }
        set(value) {
            runBlocking {
                capturedItemRepository.setStorageLocation(value)
                capturedItemRepository.releaseUntrackedSafTrees()
            }

            currentStorageLocation = value
        }

    var photoQuality: Int by setting(
        read = { it.photoQuality },
        write = { current, value -> current.copy(photoQuality = value) },
    )

    var removeExifAfterCapture: Boolean by setting(
        read = { it.removeExifAfterCapture },
        write = { current, value -> current.copy(removeExifAfterCapture = value) },
    )

    private val isInPhotoMode: Boolean
        get() {
            return !(isQRMode || isVideoMode)
        }

    val isInCaptureMode: Boolean
        get() {
            return entryPoint.isCaptureSession
        }

    // Session state rather than the stored value: geo-tagging is only ever on once the permission
    // is actually granted, and reloadSettings() is what settles a stored "on" against that. Reading
    // the preference back here would resurrect the very stale "on" the coercion exists to drop.
    var requireLocation: Boolean = false
        set(value) {
            effects.locationCamConfigChanged(value)

            // A permission result is delivered before the first onResume of an activity the system
            // recreated, so this can run before a mode has been slotted — see modeSettings.
            runBlocking {
                settingsRepository.setGeoTagging(value)
            }?.let { modeSettings = it }

            effects.onGeoTaggingChanged(value)

            field = value
        }

    var selfIlluminate: Boolean
        get() {
            return modeSettings.selfIllumination &&
                lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        set(value) {
            runBlocking {
                settingsRepository.setSelfIllumination(value)
            }?.let { modeSettings = it }

            effects.onSelfIlluminationChanged(value)
        }

    var waitForFocusLock: Boolean by setting(
        read = { it.waitForFocusLock },
        write = { current, value -> current.copy(waitForFocusLock = value) },
    )

    fun canApplyVideoStabilization(): Boolean {
        return session.canApplyVideoStabilization()
    }

    fun toggleTorchState() {
        session.toggleTorchState()
    }

    private fun <T> setting(
        read: (CameraSettings) -> T,
        write: (CameraSettings, T) -> CameraSettings,
    ): ReadWriteProperty<Any?, T> {
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                return read(settings)
            }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                settings = runBlocking { settingsRepository.update { write(it, value) } }
            }
        }
    }

    fun onDestroy() {
        preferencesScope.cancel()
    }

    fun fetchLastCapturedItem() {
        val item = try {
            runBlocking { capturedItemRepository.lastCapturedItem() }
        } catch (e: IOException) {
            Log.e(TAG, "unable to read the last captured item", e)
            null
        }
        val skip = item?.type == ITEM_TYPE_IMAGE && entryPoint.isVideoOnlySession

        lastCapturedItem = if (skip) null else item
    }

    fun setFlashMode(value: Int) {
        runBlocking {
            settingsRepository.setFlashMode(value)
        }?.let { modeSettings = it }

        applyFlashMode(value)
    }

    private fun applyFlashMode(value: Int) {
        flashMode = value
        imageCapture?.flashMode = value
        effects.onFlashModeChanged()
    }

    fun shouldShowGyroscope(): Boolean {
        return isInPhotoMode && settings.gyroscopeSuggestions
    }

    fun updateLastCapturedItem(item: CapturedItem) {
        lastCapturedItem = item

        try {
            runBlocking { capturedItemRepository.saveLastCapturedItem(item) }
        } catch (e: IOException) {
            Log.e(TAG, "unable to store the last captured item", e)
        }
    }

    fun setQRScanningFor(format: String, selected: Boolean) {
        if (!barcodeFormats.setEnabled(formatName = format, enabled = selected)) {
            effects.showMessage(R.string.no_barcode_selected)
        }

        session.refreshQrHints()
    }

    private fun slotCurrentMode() {
        modeSettings = runBlocking {
            settingsRepository.selectMode(
                mode = currentMode,
                isFrontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT,
            )
        }
    }

    fun reloadSettings() {
        slotCurrentMode()

        if (isVideoMode) {
            effects.reloadVideoQualities()
        }

        applyFlashMode(modeSettings.flashMode)

        // A stored "on" is written before a permission request resolves, and it outlives a later
        // revocation, so it cannot be asserted on its own: doing so opened a permission dialog on
        // startup that the user never asked for. Coercing it here settles the stale value through
        // the setter, and leaves every dialog in the app originating from an explicit toggle.
        requireLocation = modeSettings.geoTagging &&
            !environment.shouldAskForLocationPermission()

        selfIlluminate = modeSettings.selfIllumination

        effects.showOnlyRelevantSettings()
    }

    fun loadSettings() {
        includeAudio = settings.includeAudio

        barcodeFormats.load(settings.enabledBarcodeFormats)

        effects.selectBarcodeFormatToggles(allowedFormats)

        session.refreshQrHints()
    }

    fun toggleFlashMode() {
        if (isFlashAvailable) {
            val next = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }

            setFlashMode(next)
        } else {
            effects.showMessage(R.string.flash_unavailable_in_selected_mode)
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
        lensFacing =
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

        // Test whether the new lens facing is supported by the current device
        // If it is supported then restart the camera with the new configuration
        if (session.isLensFacingSupported(lensFacing, currentMode.extensionMode)) {
            startCamera(true)
        } else {
            // Else revert back to the old facing (while displaying an error message
            // to the user)
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                effects.showMessage(R.string.rear_camera_unavailable)
                CameraSelector.LENS_FACING_FRONT
            } else {
                effects.showMessage(R.string.front_camera_unavailable)
                CameraSelector.LENS_FACING_BACK
            }
        }
    }

    private fun onFeaturesSelected(
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

        effects.showMessage(
            environment.sessionContext.getString(
                R.string.quality_unsupported,
                videoQualityTitle(environment.sessionContext, droppedQuality),
            )
        )
    }

    fun initializeCamera(forced: Boolean = false) {
        session.initialize(forced = forced, extensionMode = currentMode.extensionMode)
    }

    // Start the camera with latest hard configuration
    fun startCamera(forced: Boolean = false) {
        if ((!forced && camera != null) || cameraProvider == null) return

        // Cancel any pending capture requests
        effects.cancelPendingCapture()

        effects.hideExposurePanel()
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
        if (!session.isLensFacingSupported(lensFacing, currentMode.extensionMode)) {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        }

        session.selectLensFacing(lensFacing)

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
            effects.setMicMutedIconVisible(!includeAudio)
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

    private fun qrLensFacing(): Int {
        if (session.isLensFacingSupported(
                lensFacing = CameraSelector.LENS_FACING_BACK,
                extensionMode = currentMode.extensionMode,
            )
        ) {
            return CameraSelector.LENS_FACING_BACK
        }

        effects.showMessage(R.string.qr_rear_camera_unavailable)

        return CameraSelector.LENS_FACING_FRONT
    }

    private fun announceBind() {
        loadTabs()

        session.reattachZoomState()

        effects.updateZoomThumb(shouldShowPanel = false)

        camera?.cameraInfo?.exposureState?.let { effects.applyExposureState(it) }

        effects.resetTorchToggle()

        camera?.cameraInfo?.let { effects.onPreviewBound(aspectRatio, it) }

        effects.updateGyroscopeIndicator(isInPhotoMode)
    }

    fun snapPreview() {
        effects.flashPreview(selfIlluminate)
    }

    // probeOnMiss is false because tab refreshes must never pay for a vendor probe on the main
    // thread (see loadTabs); an unprobed mode is left out of the tabs for now, exactly like a
    // transiently-failed probe always was, and comes back on the refresh that follows its probe.
    private fun availableModes(): Set<CameraMode> {
        return resolveAvailableModes(
            allowsQrScanning = entryPoint.allowsQrScanning,
            extensionsAvailable = session.extensionsAvailable,
        )
    }

    private fun loadTabs() {
        if (!entryPoint.showsCameraModeTabs) {
            return
        }

        // Refreshing the tabs must not run extension probes on the calling (main) thread: the
        // first refresh after process start needs one vendor-extender init round trip over
        // binder per advertised mode per lens, which measures at over half a second of blocked
        // main thread -- more than a hundred dropped frames -- during startup on a Pixel 7 Pro.
        // The probes run on their own short-lived thread instead (not cameraExecutor, which the
        // QR analyzer may be draining) and the tabs are built once every verdict is in, so the
        // tab bar still appears exactly once, fully formed, at the same time it used to; until
        // then swipes and taps resolve to no tab and the app simply stays in the current mode.
        // Later refreshes find the cache warm and rebuild synchronously, exactly as before.
        session.probeUnknownExtensions(onRestart = ::loadTabs, onSettled = ::buildTabs)
    }

    private fun buildTabs() {
        effects.setCameraModeTabs(
            modes = availableModes(),
            currentMode = currentMode,
        )
    }

    fun switchMode(mode: CameraMode) {
        if (currentMode == mode) {
            return
        }

        currentMode = mode

        effects.cancelFocusTimer()

        isQRMode = mode == CameraMode.QR_SCAN

        isVideoMode = mode == CameraMode.VIDEO

        effects.applyModeChrome(
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
            effects.goToModeTab(currentMode)
        }
    }

    fun showMoreOptionsForQR() {
        val optionNames = barcodeFormats.uncommonNames()

        effects.showBarcodeFormatPicker(
            optionNames = optionNames,
            initialValues = optionNames.map { barcodeFormats.isEnabled(it) },
            onConfirm = { values -> applyBarcodeFormats(optionNames, values) },
        )
    }

    private fun applyBarcodeFormats(optionNames: List<String>, values: List<Boolean>) {
        val selection = optionNames.withIndex().associate { (index, name) -> name to values[index] }

        if (!barcodeFormats.apply(selection)) {
            effects.showMessage(R.string.no_barcode_selected)
            return
        }

        session.refreshQrHints()
    }

    fun onStorageLocationNotFound() {
        // Reverting back to DEFAULT_MEDIA_STORE_CAPTURE_PATH
        storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION

        effects.showStorageLocationNotFound()
    }

    @AssistedFactory
    interface Factory {
        fun create(
            environment: CameraSessionEnvironment,
            effects: ViewfinderEffects,
        ): CamConfig
    }

    companion object {
        private const val TAG = "CamConfig"

        val DEFAULT_CAMERA_MODE = CameraMode.CAMERA

        val imageCollectionUri: Uri = requireNotNull(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        ) { "the primary external volume has no image collection" }

        val videoCollectionUri: Uri = requireNotNull(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        ) { "the primary external volume has no video collection" }

        @VisibleForTesting
        val snapshotProbeCount: Int
            get() = CameraSession.snapshotProbeCount

        @VisibleForTesting
        fun clearSnapshotProbeCache() {
            CameraSession.clearSnapshotProbeCache()
        }
    }
}
