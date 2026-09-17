package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.ExtensionMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface ViewfinderCameraDelegate {
    val lensFacing: LensFacing
    val isProviderReady: Boolean
    val sessionEvents: Flow<CameraSessionEvent>

    fun bind(stateHolder: ViewfinderStateHolder)

    fun attach(
        environment: CameraSessionEnvironment,
        chrome: ViewfinderChrome,
        session: CameraSession,
        emitEffect: (Effect) -> Unit,
    )
    fun detach()

    fun initialize(forced: Boolean, extensionMode: ExtensionMode?)
    fun beginBind(forced: Boolean): Boolean
    fun selectLens(isQrMode: Boolean, extensionMode: ExtensionMode?): ViewfinderBindTarget?
    fun bindCamera(settings: CameraBindSettings): BindOutcome
    fun announceBind()

    fun toggleLensFacing(extensionMode: ExtensionMode?): Boolean
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
    fun refreshQrHints()
    fun cancelFocusTimer()
    fun shouldAskForLocationPermission(): Boolean
}

internal class ViewfinderCameraDelegateImpl @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val resolveAvailableModes: ResolveAvailableModes,
) : ViewfinderCameraDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

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

    private val chrome: ViewfinderChrome
        get() {
            return attached.chrome
        }

    private val session: CameraSession
        get() {
            return attached.session
        }

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

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun attach(
        environment: CameraSessionEnvironment,
        chrome: ViewfinderChrome,
        session: CameraSession,
        emitEffect: (Effect) -> Unit,
    ) {
        attachment = Attachment(
            environment = environment,
            chrome = chrome,
            session = session,
            emitEffect = emitEffect,
        )

        refreshSessionState()
    }

    override fun detach() {
        attachment = null
        stateHolder.update { it.copy(session = ViewfinderSessionState()) }
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
        if ((!forced && session.camera != null) || session.cameraProvider == null) return false

        // Cancel any pending capture requests
        chrome.cancelPendingCapture()
        emitEffect(Effect.HideExposurePanel)

        return true
    }

    override fun selectLens(
        isQrMode: Boolean,
        extensionMode: ExtensionMode?,
    ): ViewfinderBindTarget? {
        val rotation = environment.displayRotation

        if (!environment.isSessionActive) return null

        // Silent: a lens the user picks is refused, with a message, in toggleLensFacing().
        session.lensFacing = session.lensFacing.supportedOrOpposite {
            session.isLensFacingSupported(
                lensFacing = it,
                extensionMode = extensionMode,
            )
        }

        session.selectLensFacing(session.lensFacing)

        // To use the last frame instead of showing a blank screen when
        // the camera that is being currently used gets unbind
        chrome.updateLastFrame()

        val qrLensFacing = when {
            isQrMode -> {
                chrome.startFocusTimer()
                qrLensFacing(extensionMode)
            }

            else -> null
        }

        return ViewfinderBindTarget(
            rotation = rotation,
            qrLensFacing = qrLensFacing,
        )
    }

    override fun bindCamera(settings: CameraBindSettings): BindOutcome {
        chrome.forceUpdateOrientationSensor()

        val outcome = session.bind(settings)

        refreshSessionState()

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

        emitEffect(Effect.HideZoomPanel)
    }

    override fun toggleLensFacing(extensionMode: ExtensionMode?): Boolean {
        val toggled = session.lensFacing.opposite()
        val isSupported = session.isLensFacingSupported(
            lensFacing = toggled,
            extensionMode = extensionMode,
        )

        if (isSupported) {
            session.lensFacing = toggled
            return true
        }

        val message = when (toggled) {
            LensFacing.BACK -> R.string.rear_camera_unavailable
            LensFacing.FRONT -> R.string.front_camera_unavailable
        }

        emitEffect(Effect.ShowMessage(message))

        return false
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

        emitEffect(Effect.ShowZoomPanel)
    }

    override fun refreshVideoQualities() {
        val videoQualities = session.supportedVideoQualities()

        stateHolder.update {
            it.copy(session = it.session.copy(videoQualities = videoQualities))
        }
    }

    override fun refreshQrHints() {
        session.refreshQrHints()
    }

    override fun cancelFocusTimer() {
        chrome.cancelFocusTimer()
    }

    override fun shouldAskForLocationPermission(): Boolean {
        return environment.shouldAskForLocationPermission()
    }

    private fun emitEffect(effect: Effect) {
        attached.emitEffect(effect)
    }

    private fun refreshSessionState() {
        stateHolder.update {
            it.copy(
                session = it.session.copy(
                    lensFacing = session.lensFacing,
                    canTakePicture = session.imageCapture != null,
                    isFlashAvailable = session.isFlashAvailable,
                    canApplyVideoStabilization = session.canApplyVideoStabilization(),
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

        if (isRearLensSupported) {
            return LensFacing.BACK
        }

        emitEffect(Effect.ShowMessage(R.string.qr_rear_camera_unavailable))

        return LensFacing.FRONT
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

    private class Attachment(
        val environment: CameraSessionEnvironment,
        val chrome: ViewfinderChrome,
        val session: CameraSession,
        val emitEffect: (Effect) -> Unit,
    )
}
