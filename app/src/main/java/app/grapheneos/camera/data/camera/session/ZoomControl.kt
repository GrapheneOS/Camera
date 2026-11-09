package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ZoomState
import androidx.lifecycle.asFlow
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.di.core.CameraSessionScope
import app.grapheneos.camera.di.core.MainLooperDispatcher
import javax.inject.Inject
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface ZoomControl {

    val events: Flow<CameraZoomEvent>

    /** Null until the zoom state of the attached camera has been read; see [attach]. */
    val zoom: CameraZoom?

    val lensFacing: LensFacing?

    /**
     * The ratio last requested for a lens is applied again to the next camera attached for it,
     * within that camera's range. A camera attached with [remembersZoom] off neither gets that
     * ratio nor replaces it.
     */
    fun attach(
        camera: Camera,
        lensFacing: LensFacing,
        remembersZoom: Boolean,
    )

    fun detach()

    fun setZoomRatio(zoomRatio: Float)

    /** For a lens whose camera is not attached yet; see [attach]. */
    fun rememberZoomRatio(
        zoomRatio: Float,
        lensFacing: LensFacing,
    )

    suspend fun animateZoomRatio(zoomRatio: Float)

    fun stopZoomAnimation()
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

    override var zoom: CameraZoom? = null
        private set

    override var lensFacing: LensFacing? = null
        private set

    private val rememberedRatios = mutableMapOf<LensFacing, Float>()
    private var remembersZoom = false
    private var control: CameraControl? = null
    private var trackJob: Job? = null
    private var zoomAnimation: Job? = null

    // Reading the zoom state costs ~100 ms behind an extension, so it waits for the next main
    // thread message instead of running inside the bind; zoom stays null until then.
    override fun attach(
        camera: Camera,
        lensFacing: LensFacing,
        remembersZoom: Boolean,
    ) {
        detach()
        this.lensFacing = lensFacing
        this.remembersZoom = remembersZoom
        control = camera.cameraControl
        trackJob = scope.launch(mainLooperDispatcher) {
            camera.cameraInfo.zoomState.asFlow().collect { state ->
                onZoomState(state)
            }
        }
    }

    override fun detach() {
        stopZoomAnimation()
        trackJob?.cancel()
        trackJob = null
        control = null
        zoom = null
        lensFacing = null
        remembersZoom = false
    }

    override fun setZoomRatio(zoomRatio: Float) {
        stopZoomAnimation()
        rememberForAttachedLens(zoomRatio)
        control?.setZoomRatio(zoomRatio)
    }

    override fun rememberZoomRatio(
        zoomRatio: Float,
        lensFacing: LensFacing,
    ) {
        if (zoomRatio.isFinite() && zoomRatio > 0f) {
            rememberedRatios[lensFacing] = zoomRatio
        }
    }

    override suspend fun animateZoomRatio(zoomRatio: Float) {
        stopZoomAnimation()

        val animation = scope.launch(mainLooperDispatcher) { animateTo(zoomRatio) }
        zoomAnimation = animation
        try {
            animation.join()
        } finally {
            animation.cancel()
        }
    }

    override fun stopZoomAnimation() {
        zoomAnimation?.cancel()
        zoomAnimation = null
    }

    private suspend fun animateTo(zoomRatio: Float) {
        val state = zoom ?: return
        val cameraControl = control ?: return
        val target = zoomRatio.coerceIn(
            minimumValue = state.minZoomRatio,
            maximumValue = state.maxZoomRatio,
        )

        rememberForAttachedLens(target)
        interpolateZoom(
            cameraControl = cameraControl,
            start = state.zoomRatio,
            target = target,
        )

        if (zoom?.zoomRatio != target) {
            withTimeoutOrNull(ZOOM_CONFIRMATION_TIMEOUT) {
                events.first { zoom?.zoomRatio == target }
            }
        }
    }

    private suspend fun interpolateZoom(
        cameraControl: CameraControl,
        start: Float,
        target: Float,
    ) {
        if (start == target) return

        var elapsed = Duration.ZERO
        while (elapsed < ZOOM_ANIMATION_DURATION) {
            val next = minOf(elapsed + ZOOM_FRAME_INTERVAL, ZOOM_ANIMATION_DURATION)
            delay(next - elapsed)
            val fraction = (next / ZOOM_ANIMATION_DURATION).toFloat()
            val ratio = when (next) {
                ZOOM_ANIMATION_DURATION -> target
                else -> start * (target / start).pow(fraction)
            }
            cameraControl.setZoomRatio(ratio)
            elapsed = next
        }
    }

    private fun onZoomState(state: ZoomState) {
        val previous = zoom
        val reported = cameraXStateMapper.map(state)
        val current = when (previous) {
            null -> restoreRememberedRatio(reported)
            else -> reported
        }
        zoom = current

        val event = when {
            previous == null -> CameraZoomEvent.Loaded
            current.zoomRatio != previous.zoomRatio ||
                current.linearZoom != previous.linearZoom -> CameraZoomEvent.Changed
            else -> null
        }
        event?.let(_events::tryEmit)
    }

    private fun rememberForAttachedLens(zoomRatio: Float) {
        val attachedLens = lensFacing

        if (attachedLens != null && remembersZoom) {
            rememberZoomRatio(
                zoomRatio = zoomRatio,
                lensFacing = attachedLens,
            )
        }
    }

    private fun restoreRememberedRatio(zoom: CameraZoom): CameraZoom {
        val remembered = rememberedRatios[lensFacing]
        if (remembered == null || !remembersZoom || !zoom.isAdjustable()) return zoom

        val ratio = remembered.coerceIn(
            minimumValue = zoom.minZoomRatio,
            maximumValue = zoom.maxZoomRatio,
        )
        if (ratio != zoom.zoomRatio) {
            control?.setZoomRatio(ratio)
        }

        return zoom.copy(zoomRatio = ratio)
    }

    private companion object {
        private val ZOOM_ANIMATION_DURATION = 200.milliseconds
        private val ZOOM_FRAME_INTERVAL = 16.milliseconds
        private val ZOOM_CONFIRMATION_TIMEOUT = 1.seconds
    }
}
