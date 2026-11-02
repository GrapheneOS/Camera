package app.grapheneos.camera.data.camera.session

import androidx.camera.core.ZoomState
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import javax.inject.Inject

interface ZoomStateTracker {

    /** Read instead of CameraInfo, which costs ~100 ms behind an extension. Null until [attach]. */
    val zoomState: ZoomState?

    fun attach(
        source: LiveData<ZoomState>,
        lifecycleOwner: LifecycleOwner,
        onEvent: (CameraZoomEvent) -> Unit,
    )

    fun detach()
}

internal class ZoomStateTrackerImpl @Inject constructor() : ZoomStateTracker {

    override var zoomState: ZoomState? = null
        private set

    private var source: LiveData<ZoomState>? = null

    private var isLoaded = false

    private var onEvent: (CameraZoomEvent) -> Unit = {}

    private val observer = Observer<ZoomState> {
        val event = when {
            !isLoaded -> {
                CameraZoomEvent.Loaded
            }

            it.zoomRatio != zoomState?.zoomRatio || it.linearZoom != zoomState?.linearZoom -> {
                CameraZoomEvent.Changed
            }

            else -> null
        }

        zoomState = it
        isLoaded = true

        event?.let(onEvent)
    }

    override fun attach(
        source: LiveData<ZoomState>,
        lifecycleOwner: LifecycleOwner,
        onEvent: (CameraZoomEvent) -> Unit,
    ) {
        this.onEvent = onEvent
        this.source = source
        source.observe(lifecycleOwner, observer)

        zoomState = source.value
    }

    override fun detach() {
        source?.removeObserver(observer)
        source = null
        zoomState = null
        isLoaded = false
    }
}
