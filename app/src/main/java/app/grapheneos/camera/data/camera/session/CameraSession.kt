package app.grapheneos.camera.data.camera.session

import android.annotation.SuppressLint
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCase
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.lifecycle.Lifecycle
import app.grapheneos.camera.data.camera.mapper.CameraFlashMapper
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapper
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.CameraBindRequest
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.CameraSessionPlan
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.model.FeatureGroupRequest
import app.grapheneos.camera.data.camera.model.HighFrameRateSupport
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import app.grapheneos.camera.data.camera.model.LensFeatures
import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.data.camera.model.QR_SCAN_AREA_RATIO
import app.grapheneos.camera.data.camera.model.QrBindSettings
import app.grapheneos.camera.data.camera.model.SnapshotProbeKey
import app.grapheneos.camera.data.camera.model.VideoFeatureGroup
import app.grapheneos.camera.data.camera.repository.CameraProviderSource
import app.grapheneos.camera.data.camera.repository.ExtensionAvailabilityRepository
import app.grapheneos.camera.data.camera.session.ZoomControl.Restoration
import app.grapheneos.camera.data.core.model.DeviceOrientation
import app.grapheneos.camera.data.core.model.ExtensionMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.di.core.CameraSessionScope
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.di.core.QrAnalysisDispatcher
import com.google.zxing.BarcodeFormat
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val CAMERA_EVENT_BUFFER_CAPACITY = 64

interface CameraSession {
    val events: Flow<CameraSessionEvent>
    val boundCamera: StateFlow<BoundCamera?>

    val cameraProvider: ProcessCameraProvider?
    val extensionsAvailable: Boolean
    val isActive: Boolean

    var lensFacing: LensFacing
    val camera: Camera?
    val imageCapture: ImageCapture?
    val videoCapture: VideoCapture<Recorder>?

    val sensorOrientationDegrees: Int?

    fun setPreviewTarget(target: PreviewTarget?)
    fun initialize(forced: Boolean, extensionMode: ExtensionMode?)
    fun probeUnknownExtensions(onRestart: () -> Unit, onSettled: () -> Unit)

    fun isLensFacingSupported(lensFacing: LensFacing, extensionMode: ExtensionMode?): Boolean
    fun selectLensFacing(lensFacing: LensFacing)

    fun bind(settings: CameraBindSettings): BindOutcome
    fun unbind()
    fun capabilities(): CameraCapabilities

    fun setPreviewRotation(rotation: DeviceOrientation)
    fun setCaptureOrientation(orientation: DeviceOrientation)

    fun setFlashMode(flashMode: FlashMode)

    fun setBarcodeFormats(barcodeFormats: Set<BarcodeFormat>)
}

@SuppressLint("UnsafeOptInUsageError")
internal class CameraSessionImpl @Inject constructor(
    private val cameraProviderSource: CameraProviderSource,
    private val extensionAvailabilityRepository: ExtensionAvailabilityRepository,
    private val extensionUsability: ExtensionUsability,
    private val cameraFeatureSupport: CameraFeatureSupport,
    private val featureGroupRequestResolver: FeatureGroupRequestResolver,
    private val snapshotSupportProbe: SnapshotSupportProbe,
    private val snapshotProbeCache: SnapshotProbeCache,
    private val cameraSessionPlanFactory: CameraSessionPlanFactory,
    private val photoResolutionSupport: PhotoResolutionSupport,
    private val videoBitrateSupport: VideoBitrateSupport,
    private val lowLightBoostSupport: LowLightBoostSupport,
    private val videoQualitySupport: VideoQualitySupport,
    private val cameraXConstantsMapper: CameraXConstantsMapper,
    private val flashMapper: CameraFlashMapper,
    private val zoomControl: ZoomControl,
    private val focusControl: FocusControl,
    private val exposureControl: ExposureControl,
    private val sceneLightControl: SceneLightControl,
    @QrAnalysisDispatcher qrAnalysisDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
    @CameraSessionScope private val scope: CoroutineScope,
) : CameraSession {

    private val _events = MutableSharedFlow<CameraSessionEvent>(
        extraBufferCapacity = CAMERA_EVENT_BUFFER_CAPACITY,
    )
    override val events: Flow<CameraSessionEvent> = _events.asSharedFlow()

    private val _boundCamera = MutableStateFlow<BoundCamera?>(null)
    override val boundCamera: StateFlow<BoundCamera?> = _boundCamera.asStateFlow()

    override var cameraProvider: ProcessCameraProvider? = null

    override val extensionsAvailable: Boolean
        get() {
            return extensionsManager != null && cameraProvider != null
        }

    override val isActive: Boolean
        get() {
            val lifecycle = previewTarget?.lifecycleOwner?.lifecycle ?: return false

            return lifecycle.currentState != Lifecycle.State.DESTROYED
        }

    override var lensFacing = DEFAULT_LENS_FACING

    override var camera: Camera? = null

    override var imageCapture: ImageCapture? = null

    override var videoCapture: VideoCapture<Recorder>? = null

    override val sensorOrientationDegrees: Int?
        get() {
            return camera?.cameraInfo?.sensorRotationDegrees
        }

    private var extensionsManager: ExtensionsManager? = null

    private var previewTarget: PreviewTarget? = null

    private var cameraSelector: CameraSelector = cameraSelectorFor(DEFAULT_LENS_FACING)

    private var preview: Preview? = null

    private var iAnalyzer: ImageAnalysis? = null
    private var qrAnalyzer: QrCodeAnalyzer? = null

    private var captureRotation: Int? = null
    private var previewRotation: Int? = null

    private var requestedFlashMode: FlashMode = FlashMode.OFF
    private var videoFrameRate: VideoFrameRate = VideoFrameRate.FPS_30

    private val mainExecutor: Executor = mainDispatcher.asExecutor()
    private val qrAnalysisExecutor: Executor = qrAnalysisDispatcher.asExecutor()

    private var qrAutofocusJob: Job? = null

    init {
        scope.launch(mainDispatcher) {
            exposureControl.exposure
                .map { exposure -> exposure?.isCompensated() == true }
                .distinctUntilChanged()
                .collect { applyExposureCompensation() }
        }
    }

    override fun setPreviewTarget(target: PreviewTarget?) {
        previewTarget = target

        if (target == null) {
            forgetBoundCamera()
        }
    }

    override fun initialize(
        forced: Boolean,
        extensionMode: ExtensionMode?,
    ) {
        if (cameraProvider != null) return

        cameraProviderSource.acquireProvider { provider ->
            when (provider) {
                null -> _events.tryEmit(CameraSessionEvent.CameraProviderUnavailable)
                else -> onCameraProviderReady(provider, forced, extensionMode)
            }
        }
    }

    override fun probeUnknownExtensions(
        onRestart: () -> Unit,
        onSettled: () -> Unit,
    ) {
        val provider = cameraProvider
        val em = extensionsManager
        if (provider == null || em == null) {
            onSettled()
            return
        }

        extensionUsability.probeUnknown(
            provider = provider,
            em = em,
            isActive = { isActive },
            onRestart = onRestart,
            onSettled = onSettled,
        )
    }

    override fun isLensFacingSupported(
        lensFacing: LensFacing,
        extensionMode: ExtensionMode?,
    ): Boolean {
        var tCameraSelector = cameraSelectorFor(lensFacing)

        if (extensionMode != null) {
            extensionsManager?.let { em ->
                if (!isExtensionUsable(tCameraSelector, lensFacing, extensionMode)) {
                    return false
                }

                try {
                    tCameraSelector = em.getExtensionEnabledCameraSelector(
                        tCameraSelector,
                        cameraXConstantsMapper.map(extensionMode),
                    )
                } catch (_: IllegalArgumentException) {
                    return false
                }
            }
        }

        return cameraProvider?.hasCamera(tCameraSelector) ?: false
    }

    override fun selectLensFacing(lensFacing: LensFacing) {
        cameraSelector = cameraSelectorFor(lensFacing)
    }

    override fun bind(settings: CameraBindSettings): BindOutcome {
        val provider = requireNotNull(cameraProvider) { "Camera provider is not ready yet" }
        val target = requireNotNull(previewTarget) { "No screen to show the camera on" }

        unbind()

        videoFrameRate = settings.video?.frameRate ?: VideoFrameRate.FPS_30

        val appliedExtension = settings.mode.extensionMode?.let(::applyExtension)

        // Asked of the selector this bind uses, extensions included: an extension mode decides for
        // itself whether it can produce Ultra HDR.
        val cameraInfo = currentCameraInfoOrNull()
        val features = cameraInfo?.let(cameraFeatureSupport::features) ?: LensFeatures()
        val videoFeatureGroup = resolveFeatureGroup(
            provider = provider,
            settings = settings,
            features = features,
        )
        val plan = createPlan(
            settings = settings,
            features = features,
            featureGroup = videoFeatureGroup.request,
        )
        val useCases = useCasesFor(
            settings = settings,
            plan = plan,
            target = target,
        )

        val snapshotsResolved = dropUnsupportedSnapshots(
            provider = provider,
            settings = settings,
            featureGroup = videoFeatureGroup.request,
            useCases = useCases,
        )
        if (!snapshotsResolved) return BindOutcome.FAILED

        applyCaptureRotation()

        val outcome = bindUseCases(
            provider = provider,
            target = target,
            useCases = useCases,
            plan = plan,
            featureGroup = videoFeatureGroup.request,
            appliedExtension = appliedExtension,
        )
        if (outcome == BindOutcome.BOUND) {
            onBound(
                settings = settings,
                highFrameRate = videoFeatureGroup.highFrameRate,
                appliedExtension = appliedExtension,
            )
        }

        return outcome
    }

    private fun onBound(
        settings: CameraBindSettings,
        highFrameRate: HighFrameRateSupport?,
        appliedExtension: ExtensionKey?,
    ) {
        _boundCamera.value = boundCameraOf(
            cameraInfo = requireNotNull(camera) { "A bound session has a camera" }.cameraInfo,
            settings = settings,
            highFrameRate = highFrameRate,
            appliedExtensionMode = appliedExtension?.extensionMode,
        )
        val zoomRestoration = when {
            settings.qr != null -> Restoration.DISABLED
            appliedExtension != null -> Restoration.WHEN_ZOOM_LOADS
            else -> Restoration.BEFORE_PREVIEW
        }
        attachControls(zoomRestoration)
        applyExposureCompensation()
    }

    override fun unbind() {
        detachControls()
        qrAutofocusJob?.cancel()
        cameraProvider?.unbindAll()
        _boundCamera.value = null
    }

    override fun capabilities(): CameraCapabilities {
        val cameraInfo = camera?.cameraInfo
        val provider = cameraProvider

        if (cameraInfo == null || provider == null) return CameraCapabilities()

        return CameraCapabilities(
            lensFacing = lensFacing,
            lensFeatures = cameraFeatureSupport.featuresByLens(provider),
            photoMegapixels = photoResolutionSupport.standardMegapixels(cameraInfo),
            photoResolutions = photoResolutionSupport.resolutions(cameraInfo),
            videoBitrates = videoBitrateSupport.bitrates(cameraInfo),
        )
    }

    override fun setPreviewRotation(rotation: DeviceOrientation) {
        previewRotation = cameraXConstantsMapper.map(rotation)
        preview?.targetRotation = displayRotation()
    }

    override fun setCaptureOrientation(orientation: DeviceOrientation) {
        captureRotation = cameraXConstantsMapper.map(orientation)

        applyCaptureRotation()
    }

    override fun setFlashMode(flashMode: FlashMode) {
        requestedFlashMode = flashMode
        applyFlashMode()
    }

    override fun setBarcodeFormats(barcodeFormats: Set<BarcodeFormat>) {
        qrAnalyzer?.setBarcodeFormats(barcodeFormats)
    }

    // CameraX unbinds every use case of a lifecycle that reaches ON_DESTROY, and this session
    // outlives the screen whose lifecycle that was.
    private fun forgetBoundCamera() {
        detachControls()
        qrAutofocusJob?.cancel()
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
        iAnalyzer = null
        qrAnalyzer = null
        _boundCamera.value = null
    }

    private fun onCameraProviderReady(
        provider: ProcessCameraProvider,
        forced: Boolean,
        extensionMode: ExtensionMode?,
    ) {
        if (!snapshotProbeCache.isProbedThrough(provider)) {
            // A different provider instance means the camera stack was reinitialized:
            // extension verdicts probed through the previous instance (including bind-time
            // blacklists, see bind) describe vendor state that no longer exists.
            extensionAvailabilityRepository.clear()
            snapshotProbeCache.adopt(provider)
        }
        cameraProvider = provider

        lensFacing = lensFacing.supportedOrOpposite {
            isLensFacingSupported(
                lensFacing = it,
                extensionMode = extensionMode,
            )
        }

        cameraProviderSource.acquireExtensionsManager(provider) { manager ->
            when (manager) {
                null -> _events.tryEmit(CameraSessionEvent.ExtensionsUnavailable)
                else -> extensionsManager = manager
            }

            _events.tryEmit(CameraSessionEvent.ProviderReady(forced = forced))
        }
    }

    private fun isExtensionUsable(
        selector: CameraSelector,
        lensFacing: LensFacing,
        extensionMode: ExtensionMode,
    ): Boolean {
        val em = extensionsManager
        val provider = cameraProvider

        return when {
            em == null || provider == null -> false

            else -> extensionUsability.isUsable(
                provider = provider,
                em = em,
                selector = selector,
                key = ExtensionKey(
                    lensFacing = lensFacing,
                    extensionMode = extensionMode,
                ),
            )
        }
    }

    private fun applyExtension(extensionMode: ExtensionMode): ExtensionKey? {
        val em = extensionsManager
        if (em == null || !isExtensionUsable(cameraSelector, lensFacing, extensionMode)) {
            Log.e(TAG, "Mode $extensionMode isn't available for this device")
            return null
        }

        cameraSelector = em.getExtensionEnabledCameraSelector(
            cameraSelector,
            cameraXConstantsMapper.map(extensionMode),
        )

        return ExtensionKey(
            lensFacing = lensFacing,
            extensionMode = extensionMode,
        )
    }

    private fun resolveFeatureGroup(
        provider: ProcessCameraProvider,
        settings: CameraBindSettings,
        features: LensFeatures,
    ): VideoFeatureGroup {
        val cameraInfo = currentCameraInfoOrNull()
        val videoQualityFeature = settings.video?.let { video ->
            videoQualitySupport.groupableFeature(
                quality = video.quality,
                cameraInfo = cameraInfo,
            )
        }

        return featureGroupRequestResolver.resolve(
            provider = provider,
            cameraInfo = cameraInfo,
            lensFacing = lensFacing,
            aspectRatio = settings.aspectRatio,
            video = settings.video,
            videoQualityFeature = videoQualityFeature,
            features = features,
        )
    }

    private fun createPlan(
        settings: CameraBindSettings,
        features: LensFeatures,
        featureGroup: FeatureGroupRequest,
    ): CameraSessionPlan {
        return cameraSessionPlanFactory.create(
            CameraBindRequest(
                aspectRatio = settings.aspectRatio,
                imageCaptureTargetRotation = captureRotation ?: displayRotation(),
                previewTargetRotation = displayRotation(),
                photo = settings.photo,
                video = settings.video,
                lensFeatures = features,
                featureGroup = featureGroup,
            ),
        )
    }

    private fun useCasesFor(
        settings: CameraBindSettings,
        plan: CameraSessionPlan,
        target: PreviewTarget,
    ): MutableList<UseCase> {
        val useCases = arrayListOf<UseCase>()

        when (val qr = settings.qr) {
            null -> addCaptureUseCases(plan = plan, useCases = useCases)
            else -> addQrAnalysis(qr = qr, useCases = useCases)
        }
        preview = plan.preview.also {
            useCases.add(it)
            it.surfaceProvider = target.surfaceProvider
        }

        return useCases
    }

    private fun addCaptureUseCases(
        plan: CameraSessionPlan,
        useCases: MutableList<UseCase>,
    ) {
        plan.videoCapture?.let {
            videoCapture = it
            useCases.add(it)
        }

        plan.imageCapture?.let {
            imageCapture = it
            useCases.add(it)
        }
    }

    private fun addQrAnalysis(
        qr: QrBindSettings,
        useCases: MutableList<UseCase>,
    ) {
        val analyzer = QrCodeAnalyzer(
            barcodeFormats = qr.barcodeFormats,
            onCodeScanned = { text ->
                _events.tryEmit(CameraSessionEvent.QrCodeScanned(text))
            },
        )
        val analysis = cameraSessionPlanFactory.createQrAnalysis()
        qrAnalyzer = analyzer
        iAnalyzer = analysis
        analysis.setAnalyzer(qrAnalysisExecutor, analyzer)
        cameraSelector = cameraSelectorFor(
            requireNotNull(qr.lensFacing) {
                "QR mode needs a lens facing"
            }
        )

        useCases.add(analysis)
        qrAutofocusJob = scope.launch(mainDispatcher) {
            do {
                delay(QR_AUTOFOCUS_INTERVAL)
            } while (focusOnQrScanAreaIfResumed())
        }
    }

    // False when the camera could not be asked, which fails the bind.
    private fun dropUnsupportedSnapshots(
        provider: ProcessCameraProvider,
        settings: CameraBindSettings,
        featureGroup: FeatureGroupRequest,
        useCases: MutableList<UseCase>,
    ): Boolean {
        val video = settings.video
        val photo = settings.photo
        val snapshotUseCase = imageCapture
        if (video == null || photo == null || snapshotUseCase == null) return true

        val support = snapshotSupportProbe.support(
            provider = provider,
            cameraSelector = cameraSelector,
            key = SnapshotProbeKey(
                lensFacing = lensFacing,
                videoQuality = video.quality,
                usesFeatureGroup = featureGroup is FeatureGroupRequest.Requested,
                hdrVideo = GroupableFeature.HDR_HLG10 in featureGroup.chosenFeatures(),
                highFrameRate = GroupableFeature.FPS_60 in featureGroup.chosenFeatures(),
                captureMode = photo.captureMode,
                selectHighestResolution = photo.selectHighestResolution,
            ),
            useCases = useCases,
            snapshotUseCase = snapshotUseCase,
            featuresByPriority = featureGroup.chosenFeatures(),
        )

        if (support is InVideoSnapshotSupport.Unsupported) {
            Log.i(TAG, "${support.reason}; disabling snapshots while recording")
            useCases.remove(snapshotUseCase)
            imageCapture = null
        }

        return support != null
    }

    @SuppressLint("RestrictedApi")
    private fun bindUseCases(
        provider: ProcessCameraProvider,
        target: PreviewTarget,
        useCases: List<UseCase>,
        plan: CameraSessionPlan,
        featureGroup: FeatureGroupRequest,
        appliedExtension: ExtensionKey?,
    ): BindOutcome {
        val sessionConfig = SessionConfig(
            useCases = useCases,
            preferredFeatureGroup = plan.preferredFeatures,
        )

        if (plan.preferredFeatures.isNotEmpty()) {
            reportSelectedFeatures(
                sessionConfig = sessionConfig,
                requested = plan.preferredFeatures.toList(),
                featureGroup = featureGroup,
            )
        }

        return try {
            camera = provider.bindToLifecycle(
                target.lifecycleOwner,
                cameraSelector,
                sessionConfig,
            )
            BindOutcome.BOUND
        } catch (exception: RuntimeException) {
            bindFailure(exception = exception, appliedExtension = appliedExtension)
        }
    }

    // What was asked for -- including which camera it was asked of -- is captured here
    // rather than read back from a field in the listener: it is called asynchronously,
    // so a field could already describe a later bind by then, and the event would name
    // settings (or dedup against a camera) that this result never involved.
    private fun reportSelectedFeatures(
        sessionConfig: SessionConfig,
        requested: List<GroupableFeature>,
        featureGroup: FeatureGroupRequest,
    ) {
        val boundLensFacing = lensFacing
        val qualityFeature = (featureGroup as? FeatureGroupRequest.Requested)?.videoQualityFeature

        sessionConfig.setFeatureSelectionListener(mainExecutor) { selected ->
            _events.tryEmit(
                CameraSessionEvent.FeaturesSelected(
                    boundLensFacing = boundLensFacing,
                    requested = requested,
                    qualityFeature = qualityFeature,
                    selected = selected,
                ),
            )
        }
    }

    private fun bindFailure(
        exception: RuntimeException,
        appliedExtension: ExtensionKey?,
    ): BindOutcome {
        // A vendor extension can still fail to initialize at bind time even though the
        // pre-flight probe in isExtensionUsable() passed. When one was applied, record the
        // failure so the mode stops being offered, rather than letting the exception kill the
        // process or -- equally bad -- retrying the same doomed bind on every resume.
        if (appliedExtension == null) {
            // No extension in play: an IllegalArgumentException is a plain unsupported
            // configuration (reported and swallowed); anything else is a real bug that must
            // stay visible.
            if (exception is IllegalArgumentException) {
                Log.e(TAG, "Failed to bind use cases", exception)
                return BindOutcome.FAILED
            }
            throw exception
        }

        // With an extension applied, only the vendor's known-permanent signatures mean "this
        // mode is unusable here": UnsupportedOperationException (a vendor extender that
        // advertised the mode and then threw from its own init -- Pixels: "Framework size list
        // map not supported in pixel path") and IllegalArgumentException (an extension bind
        // always uses the same fixed pair of use cases, so an invalid configuration is as
        // permanent as any other vendor failure). Anything else is a real bug and is rethrown
        // rather than hidden behind a silent mode switch.
        if (exception !is UnsupportedOperationException &&
            exception !is IllegalArgumentException
        ) {
            throw exception
        }

        Log.e(
            TAG,
            "Extension mode ${appliedExtension.extensionMode} failed to bind; disabling it",
            exception,
        )
        extensionAvailabilityRepository.record(appliedExtension, usable = false)

        return BindOutcome.EXTENSION_UNUSABLE
    }

    private fun boundCameraOf(
        cameraInfo: CameraInfo,
        settings: CameraBindSettings,
        highFrameRate: HighFrameRateSupport?,
        appliedExtensionMode: ExtensionMode?,
    ): BoundCamera {
        val isVideoMode = settings.video != null

        return BoundCamera(
            mode = settings.mode,
            isVideoMode = isVideoMode,
            lensFacing = lensFacing,
            hasFlashUnit = cameraInfo.hasFlashUnit(),
            isLowLightBoostSupported = lowLightBoostSupport.isSupported(
                cameraInfo = cameraInfo,
                extensionMode = appliedExtensionMode,
            ),
            videoQualities = when {
                isVideoMode -> videoQualitySupport.supported(cameraInfo)
                else -> emptyList()
            },
            highFrameRate = highFrameRate,
        )
    }

    private fun currentCameraInfoOrNull(): CameraInfo? {
        return try {
            cameraProvider?.getCameraInfo(cameraSelector)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unable to resolve camera info", exception)
            null
        }
    }

    // The screen's own rotation, reported by the viewfinder before the first bind. The default
    // display is not necessarily the one the viewfinder is on.
    private fun displayRotation(): Int {
        return previewRotation ?: Surface.ROTATION_0
    }

    private fun applyCaptureRotation() {
        val rotation = captureRotation ?: return

        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
        iAnalyzer?.targetRotation = rotation
    }

    private fun applyExposureCompensation() {
        val isCompensated = exposureControl.exposure.value?.isCompensated() == true

        _boundCamera.update { camera -> camera?.copy(isExposureCompensated = isCompensated) }
        applyFlashMode()
    }

    private fun applyFlashMode() {
        val boundCamera = _boundCamera.value ?: return
        val flash = flashMapper.map(
            camera = boundCamera,
            requestedMode = requestedFlashMode,
            frameRate = videoFrameRate,
        )
        imageCapture?.flashMode = cameraXConstantsMapper.map(flash.selectedMode)
        sceneLightControl.setFlashMode(flash.selectedMode)
    }

    private fun attachControls(zoomRestoration: Restoration) {
        val attachedCamera = camera
        val target = previewTarget
        val boundCamera = _boundCamera.value
        if (attachedCamera == null || target == null || boundCamera == null) return

        focusControl.attach(
            control = attachedCamera.cameraControl,
            meteringPointFactory = target.meteringPointFactory,
        )
        zoomControl.attach(
            camera = attachedCamera,
            lensFacing = lensFacing,
            restoration = zoomRestoration,
        )
        exposureControl.attach(attachedCamera)
        sceneLightControl.attach(
            control = attachedCamera.cameraControl,
            boundCamera = boundCamera,
        )
    }

    private fun detachControls() {
        focusControl.detach()
        zoomControl.detach()
        exposureControl.detach()
        sceneLightControl.detach()
    }

    private fun focusOnQrScanAreaIfResumed(): Boolean {
        val isResumed = previewTarget
            ?.lifecycleOwner
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED) == true

        if (isResumed) {
            val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(0.5f, 0.5f, QR_SCAN_AREA_RATIO)

            camera?.cameraControl?.startFocusAndMetering(
                FocusMeteringAction.Builder(point).disableAutoCancel().build()
            )
        }

        return isResumed
    }

    companion object {
        private const val TAG = "CameraSession"

        private val QR_AUTOFOCUS_INTERVAL = 2.seconds
        private val DEFAULT_LENS_FACING = LensFacing.BACK
    }
}
