package app.grapheneos.camera.ui.viewfinder.screen.delegate

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.BindOutcome
import app.grapheneos.camera.data.camera.model.CameraBindSettings
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.camera.model.CameraSessionEvent
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.TorchControl
import app.grapheneos.camera.data.core.model.ExtensionMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.domain.camera.usecase.ResolveAvailableModes
import app.grapheneos.camera.domain.camera.usecase.ResolveDroppedVideoQuality
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderHost
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderBindTarget
import app.grapheneos.camera.ui.viewfinder.screen.model.delegate.ViewfinderCameraEvent
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderCameraDelegate {

    val cameraEvents: Flow<ViewfinderCameraEvent>

    val lensFacing: LensFacing
    val isProviderReady: Boolean
    val isCameraReady: Boolean
    val canRecord: Boolean

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackSession()

    fun onScreenCreated(host: ViewfinderHost)
    fun onScreenDestroyed()

    fun initialize(forced: Boolean, extensionMode: ExtensionMode?)
    fun canBeginBind(forced: Boolean): Boolean
    fun selectLens(isQrMode: Boolean, extensionMode: ExtensionMode?): ViewfinderBindTarget?
    fun bindCamera(settings: CameraBindSettings): BindOutcome
    fun announceBind()
    fun unbindCamera()

    fun switchLens(extensionMode: ExtensionMode?): Boolean
    fun setPreviewRotation(rotation: Int)

    fun applyFlashMode(value: FlashMode)
    fun applyVideoLight(enabled: Boolean)
    fun toggleTorch()

    fun dismissQrResult()

    fun capabilities(): CameraCapabilities
    fun currentPreviewFrame(): Bitmap?
}

internal class ViewfinderCameraDelegateImpl @Inject constructor(
    private val session: CameraSession,
    private val torch: TorchControl,
    private val entryPoint: CameraEntryPoint,
    private val resolveAvailableModes: ResolveAvailableModes,
    private val resolveDroppedVideoQuality: ResolveDroppedVideoQuality,
) : ViewfinderCameraDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var host: ViewfinderHost? = null

    private val _cameraEvents = Channel<ViewfinderCameraEvent>(capacity = Channel.BUFFERED)
    override val cameraEvents: Flow<ViewfinderCameraEvent> = _cameraEvents.receiveAsFlow()

    override val lensFacing: LensFacing
        get() {
            return session.lensFacing
        }

    override val isProviderReady: Boolean
        get() {
            return session.cameraProvider != null
        }

    override val isCameraReady: Boolean
        get() {
            return session.camera != null
        }

    override val canRecord: Boolean
        get() {
            return session.camera != null && session.videoCapture != null
        }

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override suspend fun trackSession() {
        coroutineScope {
            launch {
                session.events.collect { event ->
                    onSessionEvent(event)
                }
            }

            launch {
                stateHolder.state
                    .map { state -> state.barcodeFormats() }
                    .distinctUntilChanged()
                    .collect { barcodeFormats -> session.setBarcodeFormats(barcodeFormats) }
            }

            launch {
                stateHolder.state
                    .map { state -> state.modeSettings.flashMode }
                    .distinctUntilChanged()
                    .collect(::applyFlashMode)
            }

            stateHolder.state
                .mapNotNull { state -> state.deviceOrientation }
                .distinctUntilChanged()
                .collect { orientation -> session.setCaptureOrientation(orientation) }
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

    override fun canBeginBind(forced: Boolean): Boolean {
        return when {
            host == null -> false
            session.cameraProvider == null -> false
            else -> forced || session.camera == null
        }
    }

    override fun selectLens(
        isQrMode: Boolean,
        extensionMode: ExtensionMode?,
    ): ViewfinderBindTarget? {
        val host = host?.takeIf { session.isActive } ?: return null

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
    }

    override fun unbindCamera() {
        session.unbind()
    }

    override fun switchLens(extensionMode: ExtensionMode?): Boolean {
        val lensFacing = session.lensFacing.opposite()
        val isSupported = session.isLensFacingSupported(
            lensFacing = lensFacing,
            extensionMode = extensionMode,
        )

        when {
            isSupported -> session.lensFacing = lensFacing
            else -> postEffect(Effect.ShowMessage(lensUnavailableMessage(lensFacing)))
        }

        return isSupported
    }

    override fun setPreviewRotation(rotation: Int) {
        session.setPreviewRotation(rotation)
    }

    override fun applyFlashMode(value: FlashMode) {
        session.setFlashMode(value)
        stateHolder.update {
            it.copy(flashMode = value)
        }
    }

    override fun applyVideoLight(enabled: Boolean) {
        val state = stateHolder.state.value
        if (state.isQrMode()) return

        torch.enable(state.isVideoMode() && enabled)
    }

    override fun toggleTorch() {
        torch.enable(!torch.isOn)

        stateHolder.update {
            it.copy(session = it.session.copy(isTorchOn = torch.isOn))
        }
    }

    override fun dismissQrResult() {
        stateHolder.update { it.copy(session = it.session.copy(isQrResultShown = false)) }
    }

    override fun capabilities(): CameraCapabilities {
        return session.capabilities()
    }

    override fun currentPreviewFrame(): Bitmap? {
        return host?.previewFrames?.currentFrame()
    }

    private fun onSessionEvent(event: CameraSessionEvent) {
        when (event) {
            is CameraSessionEvent.FeaturesSelected -> onFeaturesSelected(event)
            is CameraSessionEvent.QrCodeScanned -> showQrResult(event.text)

            is CameraSessionEvent.ProviderReady -> {
                _cameraEvents.trySend(ViewfinderCameraEvent.ProviderReady(forced = event.forced))
            }

            is CameraSessionEvent.CameraProviderUnavailable -> {
                postEffect(Effect.ShowMessage(R.string.camera_provider_init_failure))
            }

            is CameraSessionEvent.ExtensionsUnavailable -> {
                postEffect(Effect.ShowMessage(R.string.extensions_manager_init_failure))
            }
        }
    }

    private fun onFeaturesSelected(event: CameraSessionEvent.FeaturesSelected) {
        // The full request-vs-result picture (including which stabilization feature, if any,
        // survived) is only ever logged, never shown: the lead wants EIS left silently in its
        // known state -- 4K keeps priority and stabilization is given up without a notice.
        Log.i(TAG, "Requested ${event.requested} but got ${event.selected}")

        val droppedQuality = resolveDroppedVideoQuality(
            lensFacing = event.boundLensFacing,
            requestedQualityFeature = event.qualityFeature,
            selected = event.selected,
        ) ?: return

        postEffect(Effect.ShowVideoQualityUnsupported(droppedQuality))
    }

    private fun showQrResult(text: String) {
        if (stateHolder.state.value.session.isQrResultShown) return

        session.unbind()

        stateHolder.update { it.copy(session = it.session.copy(isQrResultShown = true)) }

        postEffect(Effect.ShowQrResult(text))
    }

    private fun refreshSessionState() {
        stateHolder.update {
            it.copy(
                session = it.session.copy(
                    lensFacing = session.lensFacing,
                    canTakePicture = session.imageCapture != null,
                    isTorchOn = false,
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

    @StringRes
    private fun lensUnavailableMessage(lensFacing: LensFacing): Int {
        return when (lensFacing) {
            LensFacing.BACK -> R.string.rear_camera_unavailable
            LensFacing.FRONT -> R.string.front_camera_unavailable
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

    private fun postEffect(effect: Effect) {
        stateHolder.postEffect(effect)
    }

    private companion object {
        private const val TAG = "ViewfinderCamera"
    }
}
