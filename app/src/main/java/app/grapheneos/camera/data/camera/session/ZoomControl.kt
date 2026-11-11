package app.grapheneos.camera.data.camera.session

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ZoomState
import androidx.lifecycle.asFlow
import app.grapheneos.camera.data.camera.mapper.CameraXStateMapper
import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.session.ZoomControl.Restoration
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
     * within that camera's range. [restoration] controls when to apply it. [Restoration.DISABLED]
     * keeps the saved ratio untouched for a later binding.
     */
    fun attach(
        camera: Camera,
        lensFacing: LensFacing,
        restoration: Restoration,
    )

    fun detach()

    /**
     * Applied within the camera's range once [zoom] has been read, when the camera attached is the
     * one for [lensFacing]. Otherwise it waits for that camera; see [attach].
     */
    fun setZoomRatio(
        zoomRatio: Float,
        lensFacing: LensFacing,
    )

    /** For a ratio that [animateZoomRatio] is about to bring the camera to. */
    fun rememberZoomRatio(
        zoomRatio: Float,
        lensFacing: LensFacing,
    )

    suspend fun animateZoomRatio(zoomRatio: Float)

    fun stopZoomAnimation()

    enum class Restoration {
        BEFORE_PREVIEW,
        WHEN_ZOOM_LOADS,
        DISABLED,
    }
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
    private var pendingRatio: Float? = null
    private var control: CameraControl? = null
    private var trackJob: Job? = null
    private var zoomAnimation: Job? = null

    override fun attach(
        camera: Camera,
        lensFacing: LensFacing,
        restoration: Restoration,
    ) {
        detach()
        this.lensFacing = lensFacing
        remembersZoom = restoration != Restoration.DISABLED
        control = camera.cameraControl

        if (restoration == Restoration.BEFORE_PREVIEW) {
            restoreBeforePreview(
                camera = camera,
                lensFacing = lensFacing,
            )
        }

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
        pendingRatio = null
        control = null
        zoom = null
        lensFacing = null
        remembersZoom = false
    }

    override fun setZoomRatio(
        zoomRatio: Float,
        lensFacing: LensFacing,
    ) {
        if (!zoomRatio.isFinite() || zoomRatio <= 0f) return

        val isAttached = lensFacing == this.lensFacing

        if (!isAttached || remembersZoom) {
            rememberedRatios[lensFacing] = zoomRatio
        }

        if (!isAttached) return

        stopZoomAnimation()
        val current = zoom
        when {
            current == null && !remembersZoom -> pendingRatio = zoomRatio
            current?.isAdjustable() == true -> control?.setZoomRatio(current.clampRatio(zoomRatio))
        }
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
        if (!zoomRatio.isFinite() || zoomRatio <= 0f) return

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
        val target = state.clampRatio(zoomRatio)

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
            null -> applyInitialRatio(
                zoom = reported,
                targetRatio = when {
                    remembersZoom -> rememberedRatios[lensFacing]
                    else -> pendingRatio
                },
            )
            else -> reported
        }
        pendingRatio = null
        zoom = current

        val event = when {
            previous == null -> CameraZoomEvent.Loaded
            current != previous -> CameraZoomEvent.Changed
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

    private fun restoreBeforePreview(
        camera: Camera,
        lensFacing: LensFacing,
    ) {
        val ratio = rememberedRatios[lensFacing] ?: return
        val state = camera.cameraInfo.zoomState.value ?: return

        applyInitialRatio(
            zoom = cameraXStateMapper.map(state),
            targetRatio = ratio,
        )
    }

    private fun applyInitialRatio(
        zoom: CameraZoom,
        targetRatio: Float?,
    ): CameraZoom {
        if (targetRatio == null || !zoom.isAdjustable()) return zoom

        val target = zoom.clampRatio(targetRatio)
        if (target != zoom.zoomRatio) {
            control?.setZoomRatio(target)
        }

        return zoom.copy(zoomRatio = target)
    }

    private fun CameraZoom.clampRatio(zoomRatio: Float): Float {
        return zoomRatio.coerceIn(
            minimumValue = minZoomRatio,
            maximumValue = maxZoomRatio,
        )
    }

    private companion object {
        private val ZOOM_ANIMATION_DURATION = 200.milliseconds
        private val ZOOM_FRAME_INTERVAL = 16.milliseconds
        private val ZOOM_CONFIRMATION_TIMEOUT = 1.seconds
    }
}
