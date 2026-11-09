package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.data.camera.model.CameraZoomEvent
import app.grapheneos.camera.data.camera.session.ZoomControl
import app.grapheneos.camera.data.core.model.LensFacing
import app.grapheneos.camera.data.settings.model.VolumeKeyAction
import app.grapheneos.camera.domain.camera.mapper.ZoomPresetsMapper
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.model.input.ZoomDirection
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.ZoomState
import javax.inject.Inject
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

internal interface ZoomDelegate {

    fun bind(stateHolder: StateHolder<PreviewOverlaysState>)

    fun zoomAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): ZoomState

    fun onExpanded()

    fun onRatioChanged(ratio: Float)

    fun onPresetSelected(ratio: Float)

    fun onControlPressChanged(isPressed: Boolean)

    fun onPreviewPinchStarted()

    fun onPreviewPinchScaleChanged(scaleFactor: Float)

    fun onPreviewPinchFinished()

    fun onZoomKeyPressed(direction: ZoomDirection)

    fun onVolumeKeyPressed(direction: ZoomDirection)

    fun onVolumeKeyReleased()

    fun onScreenPaused()

    suspend fun trackZoom()
}

internal class ZoomDelegateImpl @Inject constructor(
    private val zoomControl: ZoomControl,
    private val zoomPresetsMapper: ZoomPresetsMapper,
) : ZoomDelegate {

    private lateinit var stateHolder: StateHolder<PreviewOverlaysState>

    private val state: PreviewOverlaysState
        get() = stateHolder.state.value

    private var lastPresetId = 0L

    override fun bind(stateHolder: StateHolder<PreviewOverlaysState>) {
        this.stateHolder = stateHolder
    }

    override fun zoomAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): ZoomState {
        val cameraChanged = previous.previewContent?.mode != current.previewContent?.mode ||
            previous.previewContent?.lensFacing != current.previewContent?.lensFacing
        return when {
            !current.isZoomVisible() -> current.zoom.idle()
            cameraChanged -> current.zoom.copy(pendingPreset = null)
            else -> current.zoom
        }
    }

    override fun onExpanded() {
        if (!state.isZoomVisible()) return

        updateZoom { zoom ->
            zoom.copy(
                isExpanded = true,
                interactionId = zoom.interactionId + 1,
            )
        }
    }

    override fun onRatioChanged(ratio: Float) {
        setRatio(
            ratio = ratio,
            expands = false,
        )
    }

    override fun onPresetSelected(ratio: Float) {
        val requested = acceptRatio(ratio) ?: return

        zoomControl.stopZoomAnimation()
        rememberForSelectedLens(requested)

        lastPresetId += 1
        updateZoom { zoom ->
            zoom.copy(
                pendingPreset = ZoomState.Preset(
                    ratio = requested,
                    requestId = lastPresetId,
                ),
                interactionId = zoom.interactionId + 1,
            )
        }
    }

    override fun onControlPressChanged(isPressed: Boolean) {
        val unchanged = isPressed == state.zoom.isControlPressed
        if (unchanged || (isPressed && !state.isZoomVisible())) return

        if (isPressed) {
            zoomControl.stopZoomAnimation()
        }

        updateZoom { zoom ->
            zoom.copy(
                isControlPressed = isPressed,
                pendingPreset = when {
                    isPressed -> null
                    else -> zoom.pendingPreset
                },
                interactionId = zoom.interactionId + 1,
            )
        }
    }

    override fun onPreviewPinchStarted() {
        if (!state.isZoomVisible()) return

        zoomControl.stopZoomAnimation()

        updateZoom { zoom ->
            zoom.copy(
                isExpanded = true,
                isPreviewPinching = true,
                pendingPreset = null,
                interactionId = zoom.interactionId + 1,
            )
        }
    }

    override fun onPreviewPinchScaleChanged(scaleFactor: Float) {
        val ratio = state.zoom.value?.zoomRatio ?: return
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return

        setRatio(
            ratio = ratio * scaleFactor.pow(PINCH_ZOOM_EXPONENT),
            expands = true,
        )
    }

    override fun onPreviewPinchFinished() {
        updateZoom { zoom -> zoom.copy(isPreviewPinching = false) }
    }

    override fun onZoomKeyPressed(direction: ZoomDirection) {
        val current = state.zoom.value?.zoomRatio ?: return
        val stepped = when (direction) {
            ZoomDirection.IN -> current + ZOOM_KEY_STEP
            ZoomDirection.OUT -> current - ZOOM_KEY_STEP
        }
        val ratio = when {
            // The wide-angle camera hands over to the main one at 1×.
            current < 1f && stepped > 1f -> 1f
            else -> stepped
        }

        setRatio(
            ratio = ratio,
            expands = true,
        )
    }

    override fun onVolumeKeyPressed(direction: ZoomDirection) {
        val zooms = state.settings.volumeKeyAction == VolumeKeyAction.ZOOM &&
            state.isZoomVisible()

        if (zooms) {
            updateZoom { zoom -> zoom.copy(keyStepDirection = direction) }
        }
    }

    override fun onVolumeKeyReleased() {
        updateZoom { zoom -> zoom.copy(keyStepDirection = null) }
    }

    override fun onScreenPaused() {
        updateZoom { zoom -> zoom.idle() }
    }

    override suspend fun trackZoom() {
        coroutineScope {
            launch { trackCameraZoom() }
            launch { trackPresets() }
            launch { trackKeySteps() }
            trackPanel()
        }
    }

    private suspend fun trackCameraZoom() {
        merge(
            zoomControl.events.map { event -> event is CameraZoomEvent.Loaded },
            stateHolder.state
                .map { state -> state.previewContent }
                .distinctUntilChanged { previous, current ->
                    previous?.mode == current?.mode &&
                        previous?.lensFacing == current?.lensFacing
                }
                .map { true },
        ).collect { isNewCamera ->
            if (isSelectedCameraAttached()) {
                refreshZoom(isNewCamera = isNewCamera)
            }
        }
    }

    private suspend fun trackPresets() {
        stateHolder.state
            .map { state ->
                state.zoom.pendingPreset?.takeIf {
                    state.isZoomVisible() && isSelectedCameraAttached()
                }
            }
            .distinctUntilChanged()
            .collectLatest { preset ->
                if (preset != null) {
                    try {
                        zoomControl.animateZoomRatio(preset.ratio)
                    } finally {
                        updateZoom { zoom ->
                            when (zoom.pendingPreset) {
                                preset -> zoom.copy(
                                    value = zoomControl.zoom ?: zoom.value,
                                    pendingPreset = null,
                                )
                                else -> zoom
                            }
                        }
                    }
                }
            }
    }

    private suspend fun trackKeySteps() {
        stateHolder.state
            .map { state -> state.zoom.keyStepDirection }
            .distinctUntilChanged()
            .collectLatest { direction ->
                if (direction != null) {
                    while (stepToNextStop(direction)) {
                        delay(KEY_STEP_INTERVAL)
                    }
                }
            }
    }

    private suspend fun trackPanel() {
        stateHolder.state
            .map { state ->
                state.zoom.takeIf { zoom ->
                    state.isZoomVisible() && zoom.isExpanded && zoom.pendingPreset == null &&
                        !zoom.isControlPressed && !zoom.isPreviewPinching &&
                        zoom.keyStepDirection == null
                }
            }
            .distinctUntilChanged()
            .collectLatest { zoom ->
                if (zoom != null) {
                    delay(COLLAPSE_DELAY)
                    updateZoom { it.copy(isExpanded = false) }
                }
            }
    }

    private fun refreshZoom(isNewCamera: Boolean) {
        val zoom = zoomControl.zoom ?: return

        stateHolder.update { state ->
            val updated = state.copy(
                zoom = state.zoom.copy(
                    value = zoom,
                    pendingPreset = when {
                        isNewCamera -> null
                        else -> state.zoom.pendingPreset
                    },
                ),
            )
            updated.copy(
                zoom = zoomAfterChange(
                    previous = state,
                    current = updated,
                ),
            )
        }
    }

    private fun setRatio(
        ratio: Float,
        expands: Boolean,
    ) {
        val requested = acceptRatio(ratio) ?: return

        updateZoom { zoom ->
            zoom.copy(
                value = zoom.value?.copy(zoomRatio = requested),
                isExpanded = zoom.isExpanded || expands,
                pendingPreset = null,
                interactionId = zoom.interactionId + 1,
            )
        }

        when {
            isSelectedCameraAttached() -> zoomControl.setZoomRatio(requested)
            else -> rememberForSelectedLens(requested)
        }
    }

    private fun stepToNextStop(direction: ZoomDirection): Boolean {
        val zoom = state.zoom.value
        val lensFacing = state.previewContent?.lensFacing
        val stop = when {
            zoom == null || lensFacing == null || !state.isZoomVisible() -> null
            else -> nextStop(
                zoom = zoom,
                lensFacing = lensFacing,
                direction = direction,
            )
        }

        if (stop != null) {
            setRatio(
                ratio = stop,
                expands = true,
            )
        }

        return stop != null
    }

    private fun nextStop(
        zoom: CameraZoom,
        lensFacing: LensFacing,
        direction: ZoomDirection,
    ): Float? {
        val presets = zoomPresetsMapper.map(
            zoom = zoom,
            lensFacing = lensFacing,
        )
        val stops = presets + zoom.minZoomRatio + zoom.maxZoomRatio

        return when (direction) {
            ZoomDirection.IN -> stops.filter { stop -> stop > zoom.zoomRatio }.minOrNull()
            ZoomDirection.OUT -> stops.filter { stop -> stop < zoom.zoomRatio }.maxOrNull()
        }
    }

    private fun acceptRatio(ratio: Float): Float? {
        val zoom = state.zoom.value

        return when {
            zoom == null || !state.isZoomVisible() || !ratio.isFinite() -> null
            else -> ratio.coerceIn(
                minimumValue = zoom.minZoomRatio,
                maximumValue = zoom.maxZoomRatio,
            )
        }
    }

    private fun rememberForSelectedLens(ratio: Float) {
        state.previewContent?.lensFacing?.let { selectedLens ->
            zoomControl.rememberZoomRatio(
                zoomRatio = ratio,
                lensFacing = selectedLens,
            )
        }
    }

    private fun isSelectedCameraAttached(): Boolean {
        return zoomControl.zoom != null &&
            zoomControl.lensFacing == state.previewContent?.lensFacing
    }

    private fun updateZoom(transform: (ZoomState) -> ZoomState) {
        stateHolder.update { state -> state.copy(zoom = transform(state.zoom)) }
    }

    private companion object {
        private const val PINCH_ZOOM_EXPONENT = 2
        private const val ZOOM_KEY_STEP = 1f
        private val KEY_STEP_INTERVAL = 500.milliseconds
        private val COLLAPSE_DELAY = 2.seconds
    }
}
