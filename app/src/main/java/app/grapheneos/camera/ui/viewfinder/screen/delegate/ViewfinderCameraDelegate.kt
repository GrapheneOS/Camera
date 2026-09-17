package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.ExtensionMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface ViewfinderCameraDelegate {
    val lensFacing: LensFacing
    val isProviderReady: Boolean
    val sessionEvents: Flow<CameraSessionEvent>

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenDestroyed()

    fun initialize(forced: Boolean, extensionMode: ExtensionMode?)
    fun beginBind(forced: Boolean): Boolean
    fun selectLens(isQrMode: Boolean, extensionMode: ExtensionMode?): ViewfinderBindTarget?
    fun bindCamera(settings: CameraBindSettings): BindOutcome
    fun announceBind()

    fun switchLensFacing(lensFacing: LensFacing, extensionMode: ExtensionMode?): Boolean
    fun applyFlashMode(value: FlashMode)
    fun toggleTorch()
    fun stepZoom(step: Float)
    fun scaleZoom(scaleFactor: Float)
    fun setLinearZoom(linearZoom: Float)
    fun setExposureCompensation(compensationIndex: Int)
    fun focusAt(x: Float, y: Float, autoCancelSeconds: Long)
    fun cancelFocus()
    fun onZoomStateChanged()
    fun refreshVideoQualities()
    fun showQrResult(): Boolean
    fun dismissQrResult()
}

internal class ViewfinderCameraDelegateImpl @Inject constructor(
    private val session: CameraSession,
    private val entryPoint: CameraEntryPoint,
    private val resolveAvailableModes: ResolveAvailableModes,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderCameraDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var host: ViewfinderHost? = null

    override val lensFacing: LensFacing
        get() {
            return session.lensFacing
        }

    override val isProviderReady: Boolean
        get() {
            return session.cameraProvider != null
        }

    override val sessionEvents: Flow<CameraSessionEvent>
        get() {
            return session.events
        }

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder

        scope.launch(mainDispatcher) {
            stateHolder.state
                .map { it.barcodeFormats() }
                .distinctUntilChanged()
                .collect { session.setBarcodeFormats(it) }
        }
    }

    override fun onScreenCreated(host: ViewfinderHost) {
        this.host = host
        session.setPreviewTarget(host.previewTarget)
    }

    override fun onScreenDestroyed() {
        host = null
        session.setPreviewTarget(null)

        stateHolder.update {
            it.copy(session = it.session.copy(isQrResultShown = false))
        }
    }

    override fun initialize(
        forced: Boolean,
        extensionMode: ExtensionMode?,
    ) {
        session.initialize(
            forced = forced,
            extensionMode = extensionMode,
        )
    }

    override fun beginBind(forced: Boolean): Boolean {
        val host = host ?: return false
        if ((!forced && session.camera != null) || session.cameraProvider == null) return false

        host.chrome.cancelPendingCapture()

        return true
    }

    override fun selectLens(
        isQrMode: Boolean,
        extensionMode: ExtensionMode?,
    ): ViewfinderBindTarget? {
        val host = host ?: return null
        if (!session.isActive) return null

        // Silent: ViewfinderViewModel.switchLens() refuses a lens the user picks, with a message.
        session.lensFacing = session.lensFacing.supportedOrOpposite {
            session.isLensFacingSupported(
                lensFacing = it,
                extensionMode = extensionMode,
            )
        }

        session.selectLensFacing(session.lensFacing)

        host.previewFrames.holdCurrentFrame()

        val qrLensFacing = when {
            isQrMode -> qrLensFacing(extensionMode)
            else -> null
        }

        return ViewfinderBindTarget(qrLensFacing = qrLensFacing)
    }

    override fun bindCamera(settings: CameraBindSettings): BindOutcome {
        host?.chrome?.forceUpdateOrientationSensor()

        val outcome = session.bind(settings)

        refreshSessionState(isVideoMode = settings.isVideoMode)

        return outcome
    }

    override fun announceBind() {
        loadTabs()

        session.reattachZoomState()

        stateHolder.update {
            it.copy(
                session = it.session.copy(
                    zoom = session.zoom,
                    exposure = session.exposure,
                ),
            )
        }
    }

    override fun switchLensFacing(
        lensFacing: LensFacing,
        extensionMode: ExtensionMode?,
    ): Boolean {
        val isSupported = session.isLensFacingSupported(
            lensFacing = lensFacing,
            extensionMode = extensionMode,
        )

        if (isSupported) {
            session.lensFacing = lensFacing
        }

        return isSupported
    }

    override fun applyFlashMode(value: FlashMode) {
        session.setFlashMode(value)
        stateHolder.update {
            it.copy(flashMode = value)
        }
    }

    override fun toggleTorch() {
        session.toggleTorchState()

        stateHolder.update {
            it.copy(session = it.session.copy(isTorchOn = session.isTorchOn))
        }
    }

    override fun stepZoom(step: Float) {
        val zoom = session.zoom ?: return
        val requested = zoom.zoomRatio + step

        val zoomRatio = when {
            requested > zoom.maxZoomRatio -> zoom.maxZoomRatio
            requested < zoom.minZoomRatio -> zoom.minZoomRatio
            // smoothly transition between wide angle camera to primary one
            zoom.zoomRatio < 1f && requested > 1f -> 1f
            else -> requested
        }

        session.setZoomRatio(zoomRatio)
    }

    override fun scaleZoom(scaleFactor: Float) {
        val zoomRatio = session.zoom?.let { it.zoomRatio * scaleFactor } ?: 1f

        session.setZoomRatio(zoomRatio)
    }

    override fun setLinearZoom(linearZoom: Float) {
        session.setLinearZoom(linearZoom)
    }

    override fun setExposureCompensation(compensationIndex: Int) {
        session.setExposureCompensationIndex(compensationIndex)

        stateHolder.update {
            val exposure = it.session.exposure?.copy(compensationIndex = compensationIndex)

            it.copy(session = it.session.copy(exposure = exposure))
        }
    }

    override fun focusAt(
        x: Float,
        y: Float,
        autoCancelSeconds: Long,
    ) {
        session.startFocusAndMetering(
            x = x,
            y = y,
            autoCancelSeconds = autoCancelSeconds,
        )
    }

    override fun cancelFocus() {
        session.cancelFocusAndMetering()
    }

    override fun onZoomStateChanged() {
        stateHolder.update {
            it.copy(session = it.session.copy(zoom = session.zoom))
        }
    }

    override fun refreshVideoQualities() {
        val videoQualities = session.supportedVideoQualities()

        stateHolder.update {
            it.copy(session = it.session.copy(videoQualities = videoQualities))
        }
    }

    override fun showQrResult(): Boolean {
        if (stateHolder.state.value.session.isQrResultShown) return false

        session.unbind()

        stateHolder.update { it.copy(session = it.session.copy(isQrResultShown = true)) }

        return true
    }

    override fun dismissQrResult() {
        stateHolder.update { it.copy(session = it.session.copy(isQrResultShown = false)) }
    }

    private fun refreshSessionState(isVideoMode: Boolean) {
        stateHolder.update {
            it.copy(
                session = it.session.copy(
                    lensFacing = session.lensFacing,
                    canTakePicture = session.imageCapture != null,
                    isFlashAvailable = session.isFlashAvailable,
                    canApplyVideoStabilization = isVideoMode &&
                        session.canApplyVideoStabilization(),
                    isTorchOn = false,
                    isZslSupported = session.isZslSupported,
                    sensorOrientationDegrees = session.sensorOrientationDegrees,
                ),
            )
        }
    }

    private fun qrLensFacing(extensionMode: ExtensionMode?): LensFacing {
        val isRearLensSupported = session.isLensFacingSupported(
            lensFacing = LensFacing.BACK,
            extensionMode = extensionMode,
        )

        return when {
            isRearLensSupported -> LensFacing.BACK
            else -> LensFacing.FRONT
        }
    }

    private fun loadTabs() {
        if (!entryPoint.showsCameraModeTabs) {
            return
        }

        session.probeUnknownExtensions(
            onRestart = { loadTabs() },
            onSettled = { buildTabs() },
        )
    }

    private fun buildTabs() {
        val availableModes = resolveAvailableModes(
            allowsQrScanning = entryPoint.allowsQrScanning,
            extensionsAvailable = session.extensionsAvailable,
        )

        stateHolder.update {
            it.copy(session = it.session.copy(availableModes = availableModes))
        }
    }
}
