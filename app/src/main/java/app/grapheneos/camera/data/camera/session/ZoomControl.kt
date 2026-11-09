package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ZoomState
import androidx.lifecycle.asFlow
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.di.core.CameraSessionScope
import app.grapheneos.camera.di.core.MainLooperDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

interface ZoomControl {

    val events: Flow<CameraZoomEvent>

    /** Null until the zoom state of the attached camera has been read; see [attach]. */
    val zoom: CameraZoom?

    fun attach(camera: Camera)

    fun detach()

    fun setZoomRatio(zoomRatio: Float)

    fun setLinearZoom(linearZoom: Float)
}

internal class ZoomControlImpl @Inject constructor(
    private val cameraXStateMapper: CameraXStateMapper,
    @CameraSessionScope private val scope: CoroutineScope,
    @MainLooperDispatcher private val mainLooperDispatcher: CoroutineDispatcher,
) : ZoomControl {

    private val _events = MutableSharedFlow<CameraZoomEvent>(
        extraBufferCapacity = CAMERA_EVENT_BUFFER_CAPACITY,
    )
    override val events: Flow<CameraZoomEvent> = _events.asSharedFlow()

    override val zoom: CameraZoom?
        get() {
            return zoomState?.let { cameraXStateMapper.map(it) }
        }

    private var control: CameraControl? = null
    private var zoomState: ZoomState? = null
    private var trackJob: Job? = null

    // Every bind hands out a fresh LiveData in extension modes, and the old one would otherwise stay
    // tracked: after a handful of mode switches a single zoom step redrew the thumb once per bind
    // that had ever happened. Reading the new one costs ~100 ms behind an extension, and nothing
    // before the next message needs it: the bar is drawn once CameraZoomEvent.Loaded arrives, and
    // every other reader is a gesture.
    override fun attach(camera: Camera) {
        detach()
        control = camera.cameraControl
        trackJob = scope.launch(mainLooperDispatcher) {
            camera.cameraInfo.zoomState.asFlow().collect { state -> onZoomState(state) }
        }
    }

    override fun detach() {
        trackJob?.cancel()
        trackJob = null
        control = null
        zoomState = null
    }

    override fun setZoomRatio(zoomRatio: Float) {
        control?.setZoomRatio(zoomRatio)
    }

    override fun setLinearZoom(linearZoom: Float) {
        control?.setLinearZoom(linearZoom)
    }

    private fun onZoomState(state: ZoomState) {
        val previous = zoomState
        zoomState = state

        val event = when {
            previous == null -> CameraZoomEvent.Loaded
            state.zoomRatio != previous.zoomRatio ||
                state.linearZoom != previous.linearZoom -> CameraZoomEvent.Changed
            else -> null
        }
        event?.let(_events::tryEmit)
    }
}
