package app.grapheneos.camera.data.camera.session

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.annotation.VisibleForTesting
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.ZoomState
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import app.grapheneos.camera.analyzer.QRAnalyzer
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.ExtensionKey
import app.grapheneos.camera.data.camera.repository.CameraProviderSource
import app.grapheneos.camera.data.camera.repository.ExtensionAvailabilityRepository
import app.grapheneos.camera.domain.camera.mapper.VideoQualityFeatureMapper
import app.grapheneos.camera.domain.camera.model.CameraBindRequest
import app.grapheneos.camera.domain.camera.model.FeatureGroupRequest
import app.grapheneos.camera.domain.camera.model.ImageCaptureMode
import app.grapheneos.camera.domain.camera.model.InVideoSnapshotSupport
import app.grapheneos.camera.domain.camera.usecase.BuildCameraSessionPlan
import app.grapheneos.camera.domain.camera.usecase.ResolveInVideoSnapshotSupport
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executors
import kotlin.concurrent.thread

@SuppressLint("UnsafeOptInUsageError")
class CameraSession @AssistedInject constructor(
    @Assisted private val environment: CameraSessionEnvironment,
    private val cameraProviderSource: CameraProviderSource,
    private val extensionAvailabilityRepository: ExtensionAvailabilityRepository,
    private val featureCombinationSupport: FeatureCombinationSupport,
    private val buildCameraSessionPlan: BuildCameraSessionPlan,
    private val videoQualityFeatureMapper: VideoQualityFeatureMapper,
    private val resolveInVideoSnapshotSupport: ResolveInVideoSnapshotSupport,
) {

    var listener: Listener? = null

    var camera: Camera? = null

    var imageCapture: ImageCapture? = null

    var preview: Preview? = null

    var videoCapture: VideoCapture<Recorder>? = null

    var iAnalyzer: ImageAnalysis? = null

    var lensFacing = DEFAULT_LENS_FACING

    private var cameraSelector: CameraSelector = CameraSelector.Builder()
        .requireLensFacing(DEFAULT_LENS_FACING)
        .build()

    // Asking CameraInfo for the zoom state is cheap for a plain camera but costs ~100 ms once an
    // extension is bound, because CameraX then queries the extension's zoom range through
    // CameraExtensionCharacteristics, which enumerates every vendor key. Read this snapshot instead
    // of the camera on any path that runs more than once per bind. It is null from the moment a
    // bind starts until attachZoomState has run, which is where that query was moved to.
    var zoomState: ZoomState? = null
        private set

    private var zoomStateSource: LiveData<ZoomState>? = null

    private val zoomStateObserver = Observer<ZoomState> {
        zoomState = it
        if (it.linearZoom != 0f || it.zoomRatio != 1f) {
            listener?.onZoomStateChanged()
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val attachZoomState = Runnable {
        if (!environment.isSessionActive) return@Runnable

        zoomStateSource = camera?.cameraInfo?.zoomState?.also {
            it.observe(environment.sessionLifecycleOwner, zoomStateObserver)
        }

        zoomState = zoomStateSource?.value
    }

    var cameraProvider: ProcessCameraProvider? = null

    private var extensionsManager: ExtensionsManager? = null

    private var extensionProbesInFlight = false

    private var qrAnalyzer: QRAnalyzer? = null

    private val cameraExecutor by lazy {
        Executors.newSingleThreadExecutor()
    }

    val extensionsAvailable: Boolean
        get() {
            return extensionsManager != null && cameraProvider != null
        }

    val isFlashAvailable: Boolean
        get() {
            return camera?.cameraInfo?.hasFlashUnit() ?: false
        }

    val isZslSupported: Boolean
        get() {
            return camera?.cameraInfo?.isZslSupported ?: false
        }

    var isTorchOn: Boolean = false
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

    fun refreshQrHints() {
        qrAnalyzer?.refreshHints()
    }

    fun selectLensFacing(lensFacing: Int) {
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    // Every bind hands out a fresh LiveData in extension modes, and the old observer would
    // otherwise stay attached: after a handful of mode switches a single zoom step redrew the
    // thumb once per bind that had ever happened.
    fun reattachZoomState() {
        zoomStateSource?.removeObserver(zoomStateObserver)
        zoomStateSource = null
        zoomState = null
        // Reading the new one is what costs ~100 ms behind an extension, and nothing before the
        // next message needs it: the bar below draws a freshly bound camera's 1.0x either way, and
        // every other reader is a gesture.
        handler.removeCallbacks(attachZoomState)
        handler.post(attachZoomState)
    }

    // Whether the EIS toggle has anything to act on. Stabilization is only ever requested through
    // the feature group (see startCamera), which in turn needs the platform to be able to verify
    // feature combinations, so a camera that can stabilize is not on its own enough: where the
    // group cannot be used nothing applies EIS, and offering the toggle there is offering a
    // control that does nothing.
    fun canApplyVideoStabilization(): Boolean {
        return canVerifyFeatureCombinations() && isVideoStabilizationSupported()
    }

    private fun isVideoStabilizationSupported(): Boolean {
        // The toggle asks for both kinds of stabilization (see the preferred feature group in
        // startCamera), preferring the preview kind and falling back to the recording-only kind,
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

    fun toggleTorchState() {
        isTorchOn = !isTorchOn
    }

    private fun getCurrentCameraInfo(): CameraInfo {
        val provider = requireNotNull(cameraProvider) {
            "Camera provider is not ready yet"
        }

        return provider.getCameraInfo(cameraSelector)
    }

    fun initialize(forced: Boolean, extensionMode: Int) {
        if (cameraProvider != null) {
            listener?.onProviderReady(forced)
            return
        }

        cameraProviderSource.acquireProvider(environment.sessionContext) { provider ->
            when (provider) {
                null -> listener?.onCameraProviderUnavailable()
                else -> onCameraProviderReady(provider, forced, extensionMode)
            }
        }
    }

    private fun onCameraProviderReady(
        provider: ProcessCameraProvider,
        forced: Boolean,
        extensionMode: Int,
    ) {
        if (provider !== probedCameraProvider) {
            // A different provider instance means the camera stack was reinitialized:
            // extension verdicts probed through the previous instance (including bind-time
            // blacklists, see startCamera) describe vendor state that no longer exists.
            extensionAvailabilityRepository.clear()
            snapshotSupport.clear()
            probedCameraProvider = provider
        }
        cameraProvider = provider

        // Manually switch to the other lens facing (if the default lens facing isn't
        // supported for the current device)
        if (!isLensFacingSupported(lensFacing, extensionMode)) {
            lensFacing = when (lensFacing) {
                CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
                else -> CameraSelector.LENS_FACING_BACK
            }
        }

        cameraProviderSource.acquireExtensionsManager(
            environment.sessionContext,
            provider,
        ) { manager ->
            when (manager) {
                null -> listener?.onExtensionsUnavailable()
                else -> extensionsManager = manager
            }

            listener?.onProviderReady(forced)
        }
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
    // probeExtension() itself also runs on the probe thread that loadTabs() spawns, but its
    // results come back through the main executor.
    //
    // true/false is a verdict that is safe to cache; null is a failure that may be transient and
    // must not be (see ExtensionAvailabilityRepository). The provider and manager are parameters
    // rather than the fields because this also runs off the main thread, where the fields could
    // be swapped out mid-probe.

    // Refreshing the tabs must not run extension probes on the calling (main) thread: the
    // first refresh after process start needs one vendor-extender init round trip over
    // binder per advertised mode per lens, which measures at over half a second of blocked
    // main thread -- more than a hundred dropped frames -- during startup on a Pixel 7 Pro.
    // The probes run on their own short-lived thread instead (not cameraExecutor, which the
    // QR analyzer may be draining) and the tabs are built once every verdict is in, so the
    // tab bar still appears exactly once, fully formed, at the same time it used to; until
    // then swipes and taps resolve to no tab and the app simply stays in the current mode.
    // Later refreshes find the cache warm and rebuild synchronously, exactly as before.
    fun probeUnknownExtensions(onRestart: () -> Unit, onSettled: () -> Unit) {
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

            environment.sessionMainExecutor.execute {
                extensionProbesInFlight = false
                if (!environment.isSessionActive) return@execute

                if (probedCameraProvider !== provider) {
                    // The camera stack was reinitialized while probing: these verdicts describe
                    // vendor state that no longer exists (the same reasoning as the cache clear
                    // in initialize). Any refresh that ran for the new provider found this round
                    // still in flight and skipped scheduling, so start over for it.
                    onRestart()
                    return@execute
                }

                extensionAvailabilityRepository.recordProbeRound(verdicts)
                onSettled()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun probeExtension(
        provider: ProcessCameraProvider,
        em: ExtensionsManager,
        selector: CameraSelector,
        extensionMode: Int,
    ): Boolean? {
        // What the vendor advertises is as static as the vendor init verdict below, so a
        // negative answer is cached the same way -- notably, isExtensionAvailable() is *not*
        // the cheap in-process lookup it appears to be: on Android 17 every call costs a round
        // trip to the vendor's extensions proxy service, which is exactly the kind of work this
        // probe exists to keep off the main thread (see loadTabs). It sits inside the try below so
        // that a transient failure of that round trip is treated like any other -- returning null
        // to retry later -- rather than propagating out: on the background probe thread an escaping
        // exception would strand the round with extensionProbesInFlight still set, blocking every
        // later tab refresh.
        return try {
            when {
                !em.isExtensionAvailable(selector, extensionMode) -> false
                else -> {
                    provider.getCameraInfo(
                        em.getExtensionEnabledCameraSelector(selector, extensionMode)
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

    private fun isExtensionUsable(
        selector: CameraSelector,
        lensFacing: Int,
        extensionMode: Int,
        probeOnMiss: Boolean = true,
    ): Boolean {
        if (extensionMode == ExtensionMode.NONE) return true

        val em = extensionsManager ?: return false
        val provider = cameraProvider ?: return false

        val key = ExtensionKey(
            lensFacing = lensFacing,
            extensionMode = extensionMode,
        )
        extensionAvailabilityRepository.verdict(key)?.let { return it }

        if (!probeOnMiss) return false

        // A background probe round (loadTabs) may already be asking the vendor about this very
        // key. Probing inline here would run a second synchronous vendor round trip on the main
        // thread and race that round's verdict write. While a round is in flight, treat the miss
        // as "unusable for now"; the round fills the cache and the next rebind/tab refresh picks
        // up the real verdict. The inline probe stays for the not-in-flight cold case, where it is
        // the only path to a verdict.
        if (extensionProbesInFlight) return false

        val verdict = probeExtension(provider, em, selector, extensionMode) ?: return false
        extensionAvailabilityRepository.record(key, usable = verdict)
        return verdict
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

    // Maps the user-chosen video quality to the equivalent groupable feature, for use when a
    // feature group is passed to SessionConfig (see startCamera). Quality.HIGHEST has no
    // groupable equivalent and is resolved to the highest quality the current camera supports,
    // mirroring what QualitySelector.from(Quality.HIGHEST) would have selected.
    private fun videoQualityAsGroupableFeature(videoQuality: Quality): GroupableFeature? {
        val quality = when (videoQuality) {
            Quality.HIGHEST -> highestSupportedVideoQuality()
            else -> videoQuality
        } ?: return null

        val feature = videoQualityFeatureMapper.map(quality)

        if (feature == null) {
            // Not fatal: startCamera() then requests no quality feature at all and the quality is
            // left to Recorder's default selector. Worth a log because it means the user's
            // explicit choice is silently not being asked for.
            Log.w(TAG, "No groupable feature equivalent for video quality $quality")
        }

        return feature
    }

    private fun highestSupportedVideoQuality(): Quality? {
        val cameraInfo = try {
            cameraProvider?.getCameraInfo(cameraSelector)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Unable to resolve camera info for quality lookup", exception)
            null
        }

        return cameraInfo?.let {
            Recorder.getVideoCapabilities(it)
                .getSupportedQualities(DynamicRange.SDR)
                .firstOrNull()
        }
    }

    // What was asked for -- including which camera it was asked of -- is passed in rather than
    // read back from a field: the callback is delivered asynchronously, so a field could already
    // describe a later bind by the time this runs, and the message would then name settings (or
    // dedup against a camera) that this result never involved.

    fun isLensFacingSupported(lensFacing: Int, extensionMode: Int): Boolean {
        var tCameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        if (extensionMode != ExtensionMode.NONE) {
            extensionsManager?.let { em ->
                if (!isExtensionUsable(tCameraSelector, lensFacing, extensionMode)) {
                    return false
                }

                try {
                    tCameraSelector = em.getExtensionEnabledCameraSelector(
                        tCameraSelector,
                        extensionMode,
                    )
                } catch (_: IllegalArgumentException) {
                    return false
                }
            }
        }

        return cameraProvider?.hasCamera(tCameraSelector) ?: false
    }

    @SuppressLint("RestrictedApi")
    fun bind(settings: CameraBindSettings): BindOutcome {
        val provider = requireNotNull(cameraProvider) {
            "Camera provider is not ready yet"
        }

        // Unbind/close all other camera(s) [if any]
        provider.unbindAll()

        val extMode = settings.mode.extensionMode
        var appliedExtension: ExtensionKey? = null
        if (extMode != ExtensionMode.NONE) {
            val em = extensionsManager
            if (em != null && isExtensionUsable(cameraSelector, lensFacing, extMode)) {
                appliedExtension = ExtensionKey(
                    lensFacing = lensFacing,
                    extensionMode = extMode,
                )
                cameraSelector = em.getExtensionEnabledCameraSelector(cameraSelector, extMode)
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
            imageCaptureTargetRotation = imageCapture?.targetRotation ?: settings.rotation,
            previewTargetRotation = preview?.targetRotation ?: settings.rotation,
            flashMode = settings.flashMode,
            photoQuality = settings.photoQuality,
            waitForFocusLock = settings.waitForFocusLock,
            enableZsl = settings.enableZsl,
            selectHighestResolution = settings.selectHighestResolution,
            videoQuality = settings.videoQuality,
            mirrorVideoOnFrontCamera = settings.mirrorVideoOnFrontCamera,
            featureGroup = featureGroup,
        )

        val plan = buildCameraSessionPlan(bindRequest)

        if (settings.isQrMode) {
            val analyzer = environment.createQrAnalyzer()
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
            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(
                    requireNotNull(settings.qrLensFacing) {
                        "QR mode needs a lens facing"
                    }
                )
                .build()

            useCasesList.add(mIAnalyzer)
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
            it.surfaceProvider = environment.previewSurfaceProvider
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
        // answer it, so the verdict is cached (snapshotSupport) and every entry into video mode
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

            if (!snapshotSupport.containsKey(probeKey)) {
                snapshotProbeCount++

                val cameraInfo = try {
                    provider.getCameraInfo(cameraSelector)
                } catch (exception: IllegalArgumentException) {
                    Log.e(TAG, "Failed to query camera info", exception)
                    return BindOutcome.FAILED
                }

                snapshotSupport[probeKey] = resolveInVideoSnapshotSupport(
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

            val support = snapshotSupport[probeKey]
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
                val requested = plan.preferredFeatures.toList()
                val boundLensFacing = lensFacing
                sessionConfig.setFeatureSelectionListener(
                    environment.sessionMainExecutor
                ) { selected ->
                    listener?.onFeaturesSelected(
                        boundLensFacing = boundLensFacing,
                        requested = requested,
                        qualityFeature = requiredQualityFeature,
                        selected = selected,
                    )
                }
            }

            camera = provider.bindToLifecycle(
                environment.sessionLifecycleOwner,
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

    private fun unprobedExtensions(): List<ExtensionKey> {
        if (extensionsManager == null || cameraProvider == null) return emptyList()

        return extensionAvailabilityRepository.unprobed()
    }

    private fun selectorFor(lensFacing: Int): CameraSelector {
        return when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> FRONT_CAMERA_SELECTOR
            else -> REAR_CAMERA_SELECTOR
        }
    }

    interface Listener {

        fun onZoomStateChanged()

        fun onCameraProviderUnavailable()

        fun onExtensionsUnavailable()

        fun onProviderReady(forced: Boolean)

        fun onFeaturesSelected(
            boundLensFacing: Int,
            requested: List<GroupableFeature>,
            qualityFeature: GroupableFeature?,
            selected: Set<GroupableFeature>,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(environment: CameraSessionEnvironment): CameraSession
    }

    private data class SnapshotProbeKey(
        val lensFacing: Int,
        val videoQuality: Quality,
        val usesFeatureGroup: Boolean,
        val captureMode: ImageCaptureMode,
        val selectHighestResolution: Boolean,
    )

    companion object {
        private const val TAG = "CameraSession"

        const val DEFAULT_LENS_FACING = CameraSelector.LENS_FACING_BACK

        private val FRONT_CAMERA_SELECTOR: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        private val REAR_CAMERA_SELECTOR: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Every setting that reaches one of the three probed SessionConfigs has to appear in the
        // key. A setting added to the ImageCapture, Recorder or Preview builder without being added
        // here would be answered from a verdict that predates it, which either takes in-video
        // snapshots away for no reason or keeps them on a camera that cannot bind them.
        private val snapshotSupport = HashMap<SnapshotProbeKey, InVideoSnapshotSupport>()

        // The provider the verdicts above were probed through. A different instance means the
        // camera stack was reinitialized and none of them describe it any more.
        private var probedCameraProvider: ProcessCameraProvider? = null

        // A cache hit and a repeated probe reach the same verdict, so this is the only thing that
        // tells them apart from the outside.
        @VisibleForTesting
        var snapshotProbeCount = 0
            private set

        @VisibleForTesting
        fun clearSnapshotProbeCache() {
            snapshotSupport.clear()
            snapshotProbeCount = 0
        }
    }
}
