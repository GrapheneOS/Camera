package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.ExposureControl
import app.grapheneos.camera.data.camera.session.ZoomControl
import app.grapheneos.camera.data.sound.model.CameraSound
import app.grapheneos.camera.domain.sound.usecase.PlayCameraSound
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.NextZoomStopMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.input.ZoomDirection
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface ViewfinderCameraControlsDelegate {

    val zoomChanges: Flow<Unit>

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    suspend fun trackZoom()

    fun stepZoom(step: Float)
    fun startZoomStepping(direction: ZoomDirection)
    fun stopZoomStepping()
    fun scaleZoom(scaleFactor: Float)
    fun setLinearZoom(linearZoom: Float)

    fun setExposureCompensation(compensationIndex: Int)

    fun focusAt(x: Float, y: Float)
    fun cancelFocus()
}

internal class ViewfinderCameraControlsDelegateImpl @Inject constructor(
    private val session: CameraSession,
    private val zoomControl: ZoomControl,
    private val exposureControl: ExposureControl,
    private val playCameraSound: PlayCameraSound,
    private val nextZoomStopMapper: NextZoomStopMapper,
) : ViewfinderCameraControlsDelegate {

    private lateinit var scope: CoroutineScope

    private lateinit var stateHolder: ViewfinderStateHolder

    private var zoomStepping: Job? = null

    private val _zoomChanges = Channel<Unit>(capacity = Channel.BUFFERED)
    override val zoomChanges: Flow<Unit> = _zoomChanges.receiveAsFlow()

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        this.scope = scope
        this.stateHolder = stateHolder
    }

    override suspend fun trackZoom() {
        zoomControl.events.collect { event ->
            refreshZoom()

            when (event) {
                is CameraZoomEvent.Loaded -> Unit
                is CameraZoomEvent.Changed -> _zoomChanges.trySend(Unit)
            }
        }
    }

    override fun stepZoom(step: Float) {
        val state = stateHolder.state.value
        if (state.isQrMode()) return

        val zoom = zoomControl.zoom ?: return
        val requested = zoom.zoomRatio + step

        val zoomRatio = when {
            requested > zoom.maxZoomRatio -> zoom.maxZoomRatio
            requested < zoom.minZoomRatio -> zoom.minZoomRatio
            // smoothly transition between wide angle camera to primary one
            zoom.zoomRatio < 1f && requested > 1f -> 1f
            else -> requested
        }

        zoomControl.setZoomRatio(zoomRatio)
    }

    override fun startZoomStepping(direction: ZoomDirection) {
        stopZoomStepping()

        zoomStepping = scope.launch {
            while (stepToNextZoomStop(direction)) {
                delay(ZOOM_STOP_INTERVAL)
            }
        }
    }

    override fun stopZoomStepping() {
        zoomStepping?.cancel()
    }

    override fun scaleZoom(scaleFactor: Float) {
        val zoomRatio = zoomControl.zoom?.let { it.zoomRatio * scaleFactor } ?: 1f

        zoomControl.setZoomRatio(zoomRatio)
    }

    override fun setLinearZoom(linearZoom: Float) {
        zoomControl.setLinearZoom(linearZoom)
    }

    override fun setExposureCompensation(compensationIndex: Int) {
        exposureControl.setCompensationIndex(compensationIndex)

        stateHolder.update {
            val exposure = it.session.exposure?.copy(compensationIndex = compensationIndex)

            it.copy(session = it.session.copy(exposure = exposure))
        }
    }

    override fun focusAt(
        x: Float,
        y: Float,
    ) {
        val state = stateHolder.state.value

        session.startFocusAndMetering(
            x = x,
            y = y,
            autoCancelSeconds = state.settings.focusTimeoutSeconds,
        )

        if (!state.isVideoMode()) {
            scope.launch {
                playCameraSound(CameraSound.FOCUS_START)
            }
        }
    }

    override fun cancelFocus() {
        if (stateHolder.state.value.isQrMode()) return

        // cancel any manual focus
        // CameraX will start the continuous autofocus (if supported) automatically
        session.cancelFocusAndMetering()
    }

    private fun refreshZoom() {
        stateHolder.update {
            it.copy(session = it.session.copy(zoom = zoomControl.zoom))
        }
    }

    private fun stepToNextZoomStop(direction: ZoomDirection): Boolean {
        val zoom = zoomControl.zoom.takeUnless { stateHolder.state.value.isQrMode() }
        val stop = zoom?.let { nextZoomStopMapper.map(zoom = it, direction = direction) }

        stop?.let(zoomControl::setZoomRatio)
        return stop != null
    }

    private companion object {
        private val ZOOM_STOP_INTERVAL = 500.milliseconds
    }
}
