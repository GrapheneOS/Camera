package app.grapheneos.camera.data.camera.session

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.ZoomState
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import app.grapheneos.camera.data.camera.mapper.CameraXConstantsMapper
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.mapper.VideoQualityFeatureMapper
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindRequest
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraExposure
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.model.FeatureGroupRequest
import app.grapheneos.camera.data.camera.model.InVideoSnapshotSupport
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.model.PreviewTarget
import app.grapheneos.camera.data.camera.model.QR_SCAN_AREA_RATIO
import app.grapheneos.camera.data.camera.model.SnapshotProbeKey
import app.grapheneos.camera.data.camera.repository.CameraProviderSource
import app.grapheneos.camera.data.camera.repository.ExtensionAvailabilityRepository
import app.grapheneos.camera.data.core.model.ExtensionMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import com.google.zxing.BarcodeFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface CameraSession {
    val events: Flow<CameraSessionEvent>

    val cameraProvider: ProcessCameraProvider?
    val isActive: Boolean
    var lensFacing: LensFacing

    val camera: Camera?
    val preview: Preview?
    val imageCapture: ImageCapture?
    val videoCapture: VideoCapture<Recorder>?
    val iAnalyzer: ImageAnalysis?

    val extensionsAvailable: Boolean
    val isFlashAvailable: Boolean
    val isZslSupported: Boolean
    val isTorchOn: Boolean
    val exposure: CameraExposure?
    val sensorOrientationDegrees: Int?
    val zoom: CameraZoom?

    fun setPreviewTarget(target: PreviewTarget?)
    fun initialize(forced: Boolean, extensionMode: ExtensionMode?)
    fun isLensFacingSupported(lensFacing: LensFacing, extensionMode: ExtensionMode?): Boolean
    fun selectLensFacing(lensFacing: LensFacing)
    fun bind(settings: CameraBindSettings): BindOutcome
    fun unbind()
    fun probeUnknownExtensions(onRestart: () -> Unit, onSettled: () -> Unit)
    fun supportedVideoQualities(): List<VideoQuality>
    fun canApplyVideoStabilization(): Boolean
    fun setFlashMode(flashMode: FlashMode)
    fun toggleTorchState()
    fun setZoomRatio(zoomRatio: Float)
    fun setLinearZoom(linearZoom: Float)
    fun setExposureCompensationIndex(index: Int)
    fun startFocusAndMetering(x: Float, y: Float, autoCancelSeconds: Long)
    fun cancelFocusAndMetering()
    fun reattachZoomState()
    fun setBarcodeFormats(barcodeFormats: Set<BarcodeFormat>)
}

@SuppressLint("UnsafeOptInUsageError")
internal class CameraSessionImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayManager: DisplayManager,
    private val cameraProviderSource: CameraProviderSource,
    private val extensionAvailabilityRepository: ExtensionAvailabilityRepository,
    private val featureCombinationSupport: FeatureCombinationSupport,
    private val cameraSessionPlanFactory: CameraSessionPlanFactory,
    private val videoQualityFeatureMapper: VideoQualityFeatureMapper,
    private val cameraXConstantsMapper: CameraXConstantsMapper,
    private val cameraXStateMapper: CameraXStateMapper,
    private val inVideoSnapshotSupportResolver: InVideoSnapshotSupportResolver,
    private val snapshotProbeCache: SnapshotProbeCache,
) : CameraSession {

    private val sessionEvents = MutableSharedFlow<CameraSessionEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
    )

    override val events: Flow<CameraSessionEvent> = sessionEvents.asSharedFlow()

    override var cameraProvider: ProcessCameraProvider? = null

    private var extensionsManager: ExtensionsManager? = null

    private var extensionProbesInFlight = false

    private var previewTarget: PreviewTarget? = null

    override val isActive: Boolean
        get() {
            val lifecycle = previewTarget?.lifecycleOwner?.lifecycle ?: return false

            return lifecycle.currentState != Lifecycle.State.DESTROYED
        }

    override var lensFacing = DEFAULT_LENS_FACING

    private var cameraSelector: CameraSelector = selectorFor(DEFAULT_LENS_FACING)

    override var camera: Camera? = null

    override var preview: Preview? = null

    override var imageCapture: ImageCapture? = null

    override var videoCapture: VideoCapture<Recorder>? = null

    override var iAnalyzer: ImageAnalysis? = null

    private var qrAnalyzer: QrCodeAnalyzer? = null

    override val extensionsAvailable: Boolean
        get() {
            return extensionsManager != null && cameraProvider != null
        }

    override val isFlashAvailable: Boolean
        get() {
            return camera?.cameraInfo?.hasFlashUnit() ?: false
        }

    override val isZslSupported: Boolean
        get() {
            return camera?.cameraInfo?.isZslSupported ?: false
        }

    override var isTorchOn: Boolean = false
        get() {
            return camera?.cameraInfo?.torchState?.value == TorchState.ON
        }
        set(value) {
            field = when {
                isFlashAvailable -> {
                    camera?.cameraControl?.enableTorch(value)
                    value
                }

                else -> false
            }
        }

    override val exposure: CameraExposure?
        get() {
            return camera?.cameraInfo?.exposureState?.let { cameraXStateMapper.map(it) }
        }

    override val sensorOrientationDegrees: Int?
        get() {
            return camera?.cameraInfo?.sensorRotationDegrees
        }

    override val zoom: CameraZoom?
        get() {
            return zoomState?.let { cameraXStateMapper.map(it) }
        }

    // Asking CameraInfo for the zoom state is cheap for a plain camera but costs ~100 ms once an
    // extension is bound, because CameraX then queries the extension's zoom range through
    // CameraExtensionCharacteristics, which enumerates every vendor key. Read this snapshot instead
    // of the camera on any path that runs more than once per bind. It is null from the moment a
    // bind starts until attachZoomState has run, which is where that query was moved to.
    private var zoomState: ZoomState? = null

    private var zoomStateSource: LiveData<ZoomState>? = null

    private val zoomStateObserver = Observer<ZoomState> {
        zoomState = it
        if (it.linearZoom != 0f || it.zoomRatio != 1f) {
            sessionEvents.tryEmit(CameraSessionEvent.ZoomStateChanged)
        }
    }

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())

    private val attachZoomState = Runnable {
        if (!isActive) return@Runnable

        val lifecycleOwner = previewTarget?.lifecycleOwner ?: return@Runnable

        zoomStateSource = camera?.cameraInfo?.zoomState?.also {
            it.observe(lifecycleOwner, zoomStateObserver)
        }

        zoomState = zoomStateSource?.value
    }

    private val qrAutofocus = Runnable { focusOnQrScanArea() }

    override fun setPreviewTarget(target: PreviewTarget?) {
        previewTarget = target

        if (target == null) {
            forgetBoundCamera()
        }
    }

    fun close() {
        setPreviewTarget(null)

        cameraExecutor.shutdown()
    }

    // CameraX unbinds every use case of a lifecycle that reaches ON_DESTROY, and this session
    // outlives the screen whose lifecycle that was.
    private fun forgetBoundCamera() {
        handler.removeCallbacks(attachZoomState)
        handler.removeCallbacks(qrAutofocus)
        zoomStateSource?.removeObserver(zoomStateObserver)
        zoomStateSource = null
        zoomState = null
        camera = null
        preview = null
        imageCapture = null
        videoCapture = null
        iAnalyzer = null
        qrAnalyzer = null
    }

    override fun initialize(forced: Boolean, extensionMode: ExtensionMode?) {
        if (cameraProvider != null) return

        cameraProviderSource.acquireProvider(context) { provider ->
            when (provider) {
                null -> sessionEvents.tryEmit(CameraSessionEvent.CameraProviderUnavailable)
                else -> onCameraProviderReady(provider, forced, extensionMode)
            }
        }
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

        cameraProviderSource.acquireExtensionsManager(
            context,
            provider,
        ) { manager ->
            when (manager) {
                null -> sessionEvents.tryEmit(CameraSessionEvent.ExtensionsUnavailable)
                else -> extensionsManager = manager
            }

            sessionEvents.tryEmit(CameraSessionEvent.ProviderReady(forced = forced))
        }
    }

    override fun isLensFacingSupported(
        lensFacing: LensFacing,
        extensionMode: ExtensionMode?,
    ): Boolean {
        var tCameraSelector = selectorFor(lensFacing)

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
        cameraSelector = selectorFor(lensFacing)
    }

    private fun isExtensionUsable(
        selector: CameraSelector,
        lensFacing: LensFacing,
        extensionMode: ExtensionMode,
        probeOnMiss: Boolean = true,
    ): Boolean {
        val em = extensionsManager ?: return false
        val provider = cameraProvider ?: return false

        val key = ExtensionKey(
            lensFacing = lensFacing,
            extensionMode = extensionMode,
        )
        extensionAvailabilityRepository.verdict(key)?.let { return it }

        if (!probeOnMiss) return false

        // A background probe round (probeUnknownExtensions) may already be asking the vendor about
        // this very key. Probing inline here would run a second synchronous vendor round trip on
        // the main thread and race that round's verdict write. While a round is in flight, treat
        // the miss as "unusable for now"; the round fills the cache and the next rebind/tab refresh
        // picks up the real verdict. The inline probe stays for the not-in-flight cold case, where
        // it is the only path to a verdict.
        if (extensionProbesInFlight) return false

        val verdict = probeExtension(provider, em, selector, extensionMode) ?: return false
        extensionAvailabilityRepository.record(key, usable = verdict)
        return verdict
    }

    // ExtensionsManager.isExtensionAvailable() answers from CameraExtensionCharacteristics'
    // static advertisement data. Actually *binding* an advertised extension additionally makes
    // CameraX initialize a Camera2ExtensionsVendorExtender, which calls into the vendor's
    // advanced extender over binder. Some vendors advertise a mode there and then throw from
    // that init - Pixels raise "Framework size list map not supported in pixel path"
    // - which used to kill the process from inside bindToLifecycle(). CameraX 1.6 removed the
    // legacy OEM extender path, so there is no longer a working fallback on those devices and the
    // only safe option is to stop offering the mode.
    //
    // getCameraInfo() performs exactly the same vendor init that bindToLifecycle() does, so it is
    // a faithful probe. Verdicts are cached (ExtensionAvailabilityRepository) because every step of
    // the probe -- including the availability query, see probeExtension -- costs a binder round
    // trip. A negative verdict is only cached when the failure is known to be persistent: caching
    // a transient probe failure would make the mode's tab vanish for the rest of the process
    // lifetime over a condition that clears seconds later.
    //
    // The cache is read and written on the main thread only, which is also what keeps the two
    // activities that can share it (a secure session over a running one) from racing each other.
    // probeExtension() itself also runs on the probe thread that probeUnknownExtensions() spawns,
    // but its results come back through the main executor.
    //
    // true/false is a verdict that is safe to cache; null is a failure that may be transient and
    // must not be (see ExtensionAvailabilityRepository). The provider and manager are parameters
    // rather than the fields because this also runs off the main thread, where the fields could
    // be swapped out mid-probe.
    @Suppress("TooGenericExceptionCaught")
    private fun probeExtension(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        extensionMode: ExtensionMode,
    ): Boolean? {
        // What the vendor advertises is as static as the vendor init verdict below, so a
        // negative answer is cached the same way -- notably, isExtensionAvailable() is *not*
        // the cheap in-process lookup it appears to be: on Android 17 every call costs a round
        // trip to the vendor's extensions proxy service, which is exactly the kind of work this
        // probe exists to keep off the main thread (see probeUnknownExtensions). It sits inside the
        // try below so that a transient failure of that round trip is treated like any other --
        // returning null to retry later -- rather than propagating out: on the background probe
        // thread an escaping exception would strand the round with extensionProbesInFlight still
        // set, blocking every later tab refresh.
        val cameraXExtensionMode = cameraXConstantsMapper.map(extensionMode)

        return try {
            when {
                !em.isExtensionAvailable(selector, cameraXExtensionMode) -> false
                else -> {
                    provider.getCameraInfo(
                        em.getExtensionEnabledCameraSelector(selector, cameraXExtensionMode)
                    )
                    true
                }
            }
        } catch (exception: UnsupportedOperationException) {
            // The signature of a vendor extender that advertises the mode and then throws from
            // its own init (Pixels: "Framework size list map not supported in pixel path").
            // Nothing about it changes within a process lifetime, so this verdict is safe to
            // remember.
            Log.w(TAG, "Extension mode $extensionMode is advertised but unusable here", exception)
            false
        } catch (exception: Exception) {
            // Anything else may be transient — the camera service restarting, the camera briefly
            // held by another process. Fail this probe but leave the cache alone so the mode is
            // offered again once the underlying condition clears.
            Log.w(TAG, "Probing extension mode $extensionMode failed, will retry later", exception)
            null
        }
    }

    @SuppressLint("RestrictedApi")
    override fun bind(settings: CameraBindSettings): BindOutcome {
        val provider = requireNotNull(cameraProvider) {
            "Camera provider is not ready yet"
        }
        val target = requireNotNull(previewTarget) {
            "No screen to show the camera on"
        }

        // Unbind/close all other camera(s) [if any]
        provider.unbindAll()
        handler.removeCallbacks(qrAutofocus)

        val extMode = settings.mode.extensionMode
        var appliedExtension: ExtensionKey? = null
        if (extMode != null) {
            val em = extensionsManager
            if (em != null && isExtensionUsable(cameraSelector, lensFacing, extMode)) {
                appliedExtension = ExtensionKey(
                    lensFacing = lensFacing,
                    extensionMode = extMode,
                )
                cameraSelector = em.getExtensionEnabledCameraSelector(
                    cameraSelector,
                    cameraXConstantsMapper.map(extMode),
                )
            } else {
                Log.e(TAG, "Mode $settings.mode isn't available for this device")
            }
        }

        val useCasesList = arrayListOf<UseCase>()

        // CameraX 1.6.0's SessionConfig throws an IllegalArgumentException at construction time
        // if any use case configures a groupable feature through a non-groupable API while a
        // feature group is in use. Recorder.Builder.setQualitySelector() is such an API since
        // 1.6.0 introduced GroupableFeatures.*_RECORDING, so when EIS is requested through
        // GroupableFeature.PREVIEW_STABILIZATION (the only case where this app uses a feature
        // group), the video quality has to be requested through the feature group as well.
        //
        // The validation triggers on setQualitySelector() having been called at all, not on the
        // quality it was given, so this flag -- not the resolved feature below -- is what decides
        // whether that setter may be used. videoQualityAsGroupableFeature() can legitimately fail
        // to map the current quality, and falling back to setQualitySelector() in that case would
        // reintroduce the very exception this works around.
        //
        // Both halves of canApplyVideoStabilization() gate the group. The platform has to be able
        // to verify feature combinations: where it cannot, the resolver would conflate "could not
        // check" with "unsupported", quietly discard the stored video quality and produce
        // untruthful notices. Cameras behind that gate use the pre-1.6 non-groupable setters in
        // the session plan instead, which are legal exactly because no feature group is in use
        // then. And the group exists solely to negotiate stabilization against the quality, so a
        // camera that supports no stabilization at all has nothing to negotiate: it takes the
        // plain fallback path directly, which produces the identical output without a needless
        // feature-group round.
        val featureGroup: FeatureGroupRequest = when {
            settings.isVideoMode && settings.enableEis && canApplyVideoStabilization() -> {
                FeatureGroupRequest.Requested(videoQualityAsGroupableFeature(settings.videoQuality))
            }

            else -> FeatureGroupRequest.Unused
        }

        val requiredQualityFeature = when (featureGroup) {
            is FeatureGroupRequest.Requested -> featureGroup.videoQualityFeature
            FeatureGroupRequest.Unused -> null
        }

        val bindRequest = CameraBindRequest(
            includesVideoCapture = !settings.isQrMode && settings.isVideoMode,
            includesImageCapture = !settings.isQrMode && !settings.requiresVideoModeOnly,
            aspectRatio = settings.aspectRatio,
            imageCaptureTargetRotation = imageCapture?.targetRotation ?: displayRotation(),
            previewTargetRotation = preview?.targetRotation ?: displayRotation(),
            flashMode = settings.flashMode,
            photoQuality = settings.photoQuality,
            waitForFocusLock = settings.waitForFocusLock,
            enableZsl = settings.enableZsl,
            selectHighestResolution = settings.selectHighestResolution,
            videoQuality = settings.videoQuality,
            mirrorVideoOnFrontCamera = settings.mirrorVideoOnFrontCamera,
            featureGroup = featureGroup,
        )

        val plan = cameraSessionPlanFactory.create(bindRequest)

        if (settings.isQrMode) {
            val analyzer = QrCodeAnalyzer(
                barcodeFormats = settings.barcodeFormats,
                onCodeScanned = { text ->
                    sessionEvents.tryEmit(CameraSessionEvent.QrCodeScanned(text))
                },
            )
            val strategy = ResolutionStrategy(
                Size(960, 960),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
            val mIAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(strategy).build()
                )
                .setOutputImageRotationEnabled(true)
                .build()
            qrAnalyzer = analyzer
            iAnalyzer = mIAnalyzer
            mIAnalyzer.setAnalyzer(cameraExecutor, analyzer)
            cameraSelector = selectorFor(
                requireNotNull(settings.qrLensFacing) {
                    "QR mode needs a lens facing"
                }
            )

            useCasesList.add(mIAnalyzer)
            handler.postDelayed(qrAutofocus, QR_AUTOFOCUS_INTERVAL_MILLIS)
        } else {
            plan.videoCapture?.let {
                videoCapture = it
                useCasesList.add(it)
            }

            plan.imageCapture?.let {
                imageCapture = it
                useCasesList.add(it)
            }
        }

        preview = plan.preview.also {
            useCasesList.add(it)
            it.surfaceProvider = target.surfaceProvider
        }

        // Not every camera can run video, photo and preview at once. Ask before binding rather
        // than binding and retrying without the photo use case when it throws: an
        // IllegalArgumentException from bindToLifecycle() carries no indication of which
        // constraint was violated, so the retry could not tell "this camera can't do video plus
        // photo" apart from any other misconfiguration, and would answer both by silently
        // dropping in-video snapshots. A genuine bug then looked like a missing feature. This
        // asks the specific question, and leaves unexpected exceptions to surface as failures.
        //
        // Asking costs 80-140 ms, because the camera service resolves the whole feature group to
        // answer it, so the verdict is cached (SnapshotProbeCache) and every entry into video mode
        // after the first is free.
        val snapshotUseCase = imageCapture
        if (settings.isVideoMode && snapshotUseCase != null) {
            val probeKey = SnapshotProbeKey(
                lensFacing = lensFacing,
                videoQuality = settings.videoQuality,
                usesFeatureGroup = featureGroup is FeatureGroupRequest.Requested,
                captureMode = plan.captureMode,
                selectHighestResolution = settings.selectHighestResolution,
            )

            if (snapshotProbeCache[probeKey] == null) {
                snapshotProbeCache.recordProbe()

                val cameraInfo = try {
                    provider.getCameraInfo(cameraSelector)
                } catch (exception: IllegalArgumentException) {
                    Log.e(TAG, "Failed to query camera info", exception)
                    return BindOutcome.FAILED
                }

                snapshotProbeCache[probeKey] = inVideoSnapshotSupportResolver.resolve(
                    videoQualityFeature = requiredQualityFeature,
                    probe = { withSnapshots, features ->
                        val probedUseCases = when {
                            withSnapshots -> useCasesList
                            else -> useCasesList - snapshotUseCase
                        }

                        cameraInfo.isSessionConfigSupported(
                            SessionConfig(
                                useCases = probedUseCases,
                                requiredFeatureGroup = features,
                            )
                        )
                    },
                )
            }

            val support = snapshotProbeCache[probeKey]
            if (support is InVideoSnapshotSupport.Unsupported) {
                Log.i(TAG, "${support.reason}; disabling snapshots while recording")
                useCasesList.remove(snapshotUseCase)
                imageCapture = null
            }
        }

        try {
            val sessionConfig = SessionConfig(
                useCases = useCasesList,
                preferredFeatureGroup = plan.preferredFeatures
            )

            if (plan.preferredFeatures.isNotEmpty()) {
                // What was asked for -- including which camera it was asked of -- is captured here
                // rather than read back from a field in the listener: it is called asynchronously,
                // so a field could already describe a later bind by then, and the event would name
                // settings (or dedup against a camera) that this result never involved.
                val requested = plan.preferredFeatures.toList()
                val boundLensFacing = lensFacing
                sessionConfig.setFeatureSelectionListener(
                    mainExecutor
                ) { selected ->
                    sessionEvents.tryEmit(
                        CameraSessionEvent.FeaturesSelected(
                            boundLensFacing = boundLensFacing,
                            requested = requested,
                            qualityFeature = requiredQualityFeature,
                            selected = selected,
                        ),
                    )
                }
            }

            camera = provider.bindToLifecycle(
                target.lifecycleOwner,
                cameraSelector,
                sessionConfig
            )
        } catch (exception: RuntimeException) {
            // A vendor extension can still fail to initialize at bind time even though the
            // pre-flight probe in isExtensionUsable() passed. When one was applied, record the
            // failure so the mode stops being offered, rather than letting the exception kill the
            // process or -- equally bad -- retrying the same doomed bind on every resume.
            val key = appliedExtension
            if (key == null) {
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

            Log.e(TAG, "Extension mode $extMode failed to bind; disabling it", exception)
            extensionAvailabilityRepository.record(key, usable = false)

            return BindOutcome.EXTENSION_UNUSABLE
        }

        return BindOutcome.BOUND
    }

    override fun unbind() {
        cameraProvider?.unbindAll()
    }

    // Maps the user-chosen video quality to the equivalent groupable feature, for use when a
    // feature group is passed to SessionConfig (see bind). VideoQuality.HIGHEST has no
    // groupable equivalent and is resolved to the highest quality the current camera supports,
    // mirroring what QualitySelector.from(Quality.HIGHEST) would have selected.
    private fun videoQualityAsGroupableFeature(videoQuality: VideoQuality): GroupableFeature? {
        val quality = when (videoQuality) {
            VideoQuality.HIGHEST -> highestSupportedVideoQuality()
            else -> videoQuality
        } ?: return null

        val feature = videoQualityFeatureMapper.map(quality)

        if (feature == null) {
            // Not fatal: bind() then requests no quality feature at all and the quality is
            // left to Recorder's default selector. Worth a log because it means the user's
            // explicit choice is silently not being asked for.
            Log.w(TAG, "No groupable feature equivalent for video quality $quality")
        }

        return feature
    }

    private fun highestSupportedVideoQuality(): VideoQuality? {
        val cameraInfo = try {
            cameraProvider?.getCameraInfo(cameraSelector)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unable to resolve camera info for quality lookup", exception)
            null
        }

        return cameraInfo?.let {
            Recorder.getVideoCapabilities(it)
                .getSupportedQualities(DynamicRange.SDR)
                .firstNotNullOfOrNull { quality -> cameraXConstantsMapper.map(quality) }
        }
    }

    private fun displayRotation(): Int {
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
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
    override fun probeUnknownExtensions(onRestart: () -> Unit, onSettled: () -> Unit) {
        val pending = unprobedExtensions()
        if (pending.isEmpty()) {
            onSettled()
            return
        }

        if (extensionProbesInFlight) return
        val provider = cameraProvider ?: return
        val em = extensionsManager ?: return
        extensionProbesInFlight = true

        thread {
            val verdicts = HashMap<ExtensionKey, Boolean?>()
            for (key in pending) {
                verdicts[key] = probeExtension(
                    provider = provider,
                    em = em,
                    selector = selectorFor(key.lensFacing),
                    extensionMode = key.extensionMode
                )
            }

            mainExecutor.execute {
                extensionProbesInFlight = false
                if (!isActive) return@execute

                if (!snapshotProbeCache.isProbedThrough(provider)) {
                    // The camera stack was reinitialized while probing: these verdicts describe
                    // vendor state that no longer exists (the same reasoning as the cache clear
                    // in onCameraProviderReady). Any refresh that ran for the new provider found
                    // this round still in flight and skipped scheduling, so start over for it.
                    onRestart()
                    return@execute
                }

                extensionAvailabilityRepository.recordProbeRound(verdicts)
                onSettled()
            }
        }
    }

    private fun unprobedExtensions(): List<ExtensionKey> {
        if (extensionsManager == null || cameraProvider == null) return emptyList()

        return extensionAvailabilityRepository.unprobed()
    }

    override fun supportedVideoQualities(): List<VideoQuality> {
        val cameraInfo = camera?.cameraInfo ?: return emptyList()

        return Recorder.getVideoCapabilities(cameraInfo)
            .getSupportedQualities(DynamicRange.SDR)
            .mapNotNull { cameraXConstantsMapper.map(it) }
    }

    // Whether the EIS toggle has anything to act on. Stabilization is only ever requested through
    // the feature group (see bind), which in turn needs the platform to be able to verify
    // feature combinations, so a camera that can stabilize is not on its own enough: where the
    // group cannot be used nothing applies EIS, and offering the toggle there is offering a
    // control that does nothing.
    override fun canApplyVideoStabilization(): Boolean {
        return canVerifyFeatureCombinations() && isVideoStabilizationSupported()
    }

    private fun canVerifyFeatureCombinations(): Boolean {
        val cameraInfo = try {
            cameraProvider?.getCameraInfo(cameraSelector)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unable to resolve camera info for feature combination gate", exception)
            null
        }

        return when (cameraInfo) {
            null -> false
            else -> featureCombinationSupport.canVerify(cameraInfo)
        }
    }

    private fun isVideoStabilizationSupported(): Boolean {
        // The toggle asks for both kinds of stabilization (see the preferred feature group in
        // bind), preferring the preview kind and falling back to the recording-only kind,
        // so it is meaningful whenever either one is available. Testing only the recorder
        // capability both hid the toggle on cameras that can stabilize the preview but not the
        // recording, and offered it on cameras where only the recorder can stabilize - where
        // the bind used to ask exclusively for preview stabilization, leaving the toggle with
        // nothing to do.
        return isPreviewStabilizationSupported() || isRecorderStabilizationSupported()
    }

    private fun isPreviewStabilizationSupported(): Boolean {
        return Preview.getPreviewCapabilities(getCurrentCameraInfo()).isStabilizationSupported
    }

    private fun isRecorderStabilizationSupported(): Boolean {
        return Recorder.getVideoCapabilities(getCurrentCameraInfo()).isStabilizationSupported
    }

    private fun getCurrentCameraInfo(): CameraInfo {
        val provider = requireNotNull(cameraProvider) {
            "Camera provider is not ready yet"
        }

        return provider.getCameraInfo(cameraSelector)
    }

    override fun setFlashMode(flashMode: FlashMode) {
        imageCapture?.flashMode = cameraXConstantsMapper.map(flashMode)
    }

    override fun toggleTorchState() {
        isTorchOn = !isTorchOn
    }

    override fun setZoomRatio(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    override fun setLinearZoom(linearZoom: Float) {
        camera?.cameraControl?.setLinearZoom(linearZoom)
    }

    override fun setExposureCompensationIndex(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    override fun startFocusAndMetering(x: Float, y: Float, autoCancelSeconds: Long) {
        val meteringPointFactory = previewTarget?.meteringPointFactory ?: return
        val point = meteringPointFactory.createPoint(x, y)
        val builder = FocusMeteringAction.Builder(point)

        when (autoCancelSeconds) {
            0L -> builder.disableAutoCancel()
            else -> builder.setAutoCancelDuration(autoCancelSeconds, TimeUnit.SECONDS)
        }

        camera?.cameraControl?.startFocusAndMetering(builder.build())
    }

    override fun cancelFocusAndMetering() {
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    // Every bind hands out a fresh LiveData in extension modes, and the old observer would
    // otherwise stay attached: after a handful of mode switches a single zoom step redrew the
    // thumb once per bind that had ever happened.
    override fun reattachZoomState() {
        zoomStateSource?.removeObserver(zoomStateObserver)
        zoomStateSource = null
        zoomState = null
        // Reading the new one is what costs ~100 ms behind an extension, and nothing before the
        // next message needs it: the bar below draws a freshly bound camera's 1.0x either way, and
        // every other reader is a gesture.
        handler.removeCallbacks(attachZoomState)
        handler.post(attachZoomState)
    }

    override fun setBarcodeFormats(barcodeFormats: Set<BarcodeFormat>) {
        qrAnalyzer?.setBarcodeFormats(barcodeFormats)
    }

    private fun focusOnQrScanArea() {
        val lifecycle = previewTarget?.lifecycleOwner?.lifecycle ?: return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        val point = SurfaceOrientedMeteringPointFactory(1f, 1f)
            .createPoint(0.5f, 0.5f, QR_SCAN_AREA_RATIO)

        camera?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(point).disableAutoCancel().build()
        )

        handler.postDelayed(qrAutofocus, QR_AUTOFOCUS_INTERVAL_MILLIS)
    }

    private fun selectorFor(lensFacing: LensFacing): CameraSelector {
        return when (lensFacing) {
            LensFacing.FRONT -> FRONT_CAMERA_SELECTOR
            LensFacing.BACK -> REAR_CAMERA_SELECTOR
        }
    }

    companion object {
        private const val TAG = "CameraSession"

        private const val EVENT_BUFFER_CAPACITY = 64
        private const val QR_AUTOFOCUS_INTERVAL_MILLIS = 2000L

        private val DEFAULT_LENS_FACING = LensFacing.BACK

        private val FRONT_CAMERA_SELECTOR: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        private val REAR_CAMERA_SELECTOR: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
    }
}
