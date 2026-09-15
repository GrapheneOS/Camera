package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.core.CameraSelector
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.CameraSessionEnvironment
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.domain.camera.model.CameraEntryPoint
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderChrome
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderEffects
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderSessionState
import javax.inject.Inject

interface ViewfinderCameraDelegate {
    val sessionState: ViewfinderSessionState
    val lensFacing: Int
    val flashMode: Int

    fun bind(stateHolder: ViewfinderStateHolder)

    fun attach(
        environment: CameraSessionEnvironment,
        effects: ViewfinderEffects,
        chrome: ViewfinderChrome,
        session: CameraSession,
        listener: CameraSession.Listener,
        emitEffect: (Effect) -> Unit,
    )

    fun detach()

    fun initialize(forced: Boolean, extensionMode: Int)
    fun beginBind(forced: Boolean): Boolean
    fun selectLens(isQrMode: Boolean, extensionMode: Int): ViewfinderBindTarget?
    fun bind(settings: CameraBindSettings): BindOutcome
    fun announceBind(aspectRatio: Int, isInPhotoMode: Boolean, currentMode: () -> CameraMode)

    fun toggleLensFacing(extensionMode: Int): Boolean
    fun applyFlashMode(value: Int)
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

    override val sessionState: ViewfinderSessionState
        get() {
            return stateHolder.state.value.session
        }

    override val lensFacing: Int
        get() {
            return session.lensFacing
        }

    override val flashMode: Int
        get() {
            return stateHolder.state.value.flashMode
        }

    override fun bind(stateHolder: ViewfinderStateHolder) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder
    }

    override fun attach(
        environment: CameraSessionEnvironment,
        effects: ViewfinderEffects,
        chrome: ViewfinderChrome,
        session: CameraSession,
        listener: CameraSession.Listener,
        emitEffect: (Effect) -> Unit,
    ) {
        attachment = Attachment(
            environment = environment,
            effects = effects,
            chrome = chrome,
            session = session,
            emitEffect = emitEffect,
        )

        session.listener = listener

        refreshSessionState()
    }

    override fun detach() {
        attachment?.session?.listener = null
        attachment = null
        stateHolder.update { it.copy(session = ViewfinderSessionState()) }
    }

    override fun initialize(
        forced: Boolean,
        extensionMode: Int,
    ) {
        session.initialize(
            forced = forced,
            extensionMode = extensionMode,
        )
    }

    override fun beginBind(forced: Boolean): Boolean {
        if ((!forced && session.camera != null) || session.cameraProvider == null) return false

        // Cancel any pending capture requests
        effects.cancelPendingCapture()
        effects.hideExposurePanel()

        return true
    }

    override fun selectLens(isQrMode: Boolean, extensionMode: Int): ViewfinderBindTarget? {
        val rotation = environment.displayRotation

        if (!environment.isSessionActive) return null

        // Test whether the current lens facing is supported by the current device
        // If not then silently switch to the other lens facing
        // (Snackbar/popup message can be shown before startCamera is called
        // in specific cases of explicitly switching to another side or if
        // the camera is expected)
        val isCurrentLensSupported = session.isLensFacingSupported(
            lensFacing = session.lensFacing,
            extensionMode = extensionMode,
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
            isQrMode -> {
                effects.startFocusTimer()
                qrLensFacing(extensionMode)
            }

            else -> null
        }

        return ViewfinderBindTarget(
            rotation = rotation,
            qrLensFacing = qrLensFacing,
        )
    }

    override fun bind(settings: CameraBindSettings): BindOutcome {
        effects.forceUpdateOrientationSensor()

        val outcome = session.bind(settings)

        refreshSessionState()

        return outcome
    }

    override fun announceBind(
        aspectRatio: Int,
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

    override fun toggleLensFacing(extensionMode: Int): Boolean {
        // Manually switch to the opposite lens facing
        session.lensFacing = when (session.lensFacing) {
            CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
            else -> CameraSelector.LENS_FACING_BACK
        }

        val isNewLensSupported = session.isLensFacingSupported(
            lensFacing = session.lensFacing,
            extensionMode = extensionMode,
        )

        if (isNewLensSupported) return true

        // Else revert back to the old facing (while displaying an error message
        // to the user)
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

        return false
    }

    override fun applyFlashMode(value: Int) {
        session.imageCapture?.flashMode = value
        stateHolder.update { it.copy(flashMode = value) }
    }

    override fun onZoomStateChanged() {
        chrome.updateZoomThumb()
        emitEffect(Effect.ShowZoomPanel)
    }

    override fun refreshQrHints() {
        session.refreshQrHints()
    }

    override fun cancelFocusTimer() {
        effects.cancelFocusTimer()
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

    private fun qrLensFacing(extensionMode: Int): Int {
        val isRearLensSupported = session.isLensFacingSupported(
            lensFacing = CameraSelector.LENS_FACING_BACK,
            extensionMode = extensionMode,
        )

        if (isRearLensSupported) {
            return CameraSelector.LENS_FACING_BACK
        }

        emitEffect(Effect.ShowMessage(R.string.qr_rear_camera_unavailable))

        return CameraSelector.LENS_FACING_FRONT
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
        val effects: ViewfinderEffects,
        val chrome: ViewfinderChrome,
        val session: CameraSession,
        val emitEffect: (Effect) -> Unit,
    )
}
