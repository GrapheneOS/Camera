package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
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
    fun announceBind(
        aspectRatio: AspectRatio,
        isInPhotoMode: Boolean,
        currentMode: () -> CameraMode,
    )

    fun toggleLensFacing(extensionMode: ExtensionMode?): Boolean
    fun applyFlashMode(value: FlashMode)
    fun toggleTorch()
    fun onZoomStateChanged()
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
        chrome.hideExposurePanel()

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

    override fun announceBind(
        aspectRatio: AspectRatio,
        isInPhotoMode: Boolean,
        currentMode: () -> CameraMode,
    ) {
        loadTabs(currentMode)

        session.reattachZoomState()

        chrome.updateZoomThumb()
        emitEffect(Effect.HideZoomPanel)

        session.camera?.cameraInfo?.exposureState?.let { chrome.applyExposureState(it) }

        emitEffect(Effect.ResetTorchToggle)

        session.camera?.cameraInfo?.let { chrome.onPreviewBound(aspectRatio, it) }

        chrome.updateGyroscopeIndicator(isInPhotoMode)
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
        stateHolder.update { it.copy(flashMode = value) }
    }

    override fun toggleTorch() {
        session.toggleTorchState()

        stateHolder.update { it.copy(session = it.session.copy(isTorchOn = session.isTorchOn)) }
    }

    override fun onZoomStateChanged() {
        chrome.updateZoomThumb()
        emitEffect(Effect.ShowZoomPanel)
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
        val sessionState = ViewfinderSessionState(
            lensFacing = session.lensFacing,
            canTakePicture = session.imageCapture != null,
            isFlashAvailable = session.isFlashAvailable,
            canApplyVideoStabilization = session.canApplyVideoStabilization(),
        )

        stateHolder.update { it.copy(session = sessionState) }
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

    private fun loadTabs(currentMode: () -> CameraMode) {
        if (!entryPoint.showsCameraModeTabs) {
            return
        }

        session.probeUnknownExtensions(
            onRestart = { loadTabs(currentMode) },
            onSettled = { buildTabs(currentMode()) },
        )
    }

    private fun buildTabs(currentMode: CameraMode) {
        chrome.setCameraModeTabs(
            modes = resolveAvailableModes(
                allowsQrScanning = entryPoint.allowsQrScanning,
                extensionsAvailable = session.extensionsAvailable,
            ),
            currentMode = currentMode,
        )
    }

    private class Attachment(
        val environment: CameraSessionEnvironment,
        val chrome: ViewfinderChrome,
        val session: CameraSession,
        val emitEffect: (Effect) -> Unit,
    )
}
