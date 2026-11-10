package app.grapheneos.camera.ui.previewoverlays.screen.delegate

import app.grapheneos.camera.data.camera.session.ExposureControl
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction.ExposureAction
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.ExposureState
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewControl
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal interface ExposureDelegate {

    fun bind(stateHolder: StateHolder<PreviewOverlaysState>)

    fun exposureAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): ExposureState

    fun onAction(action: ExposureAction)

    fun onScreenPaused()

    suspend fun trackExposure()
}

internal class ExposureDelegateImpl @Inject constructor(
    private val exposureControl: ExposureControl,
) : ExposureDelegate {

    private lateinit var stateHolder: StateHolder<PreviewOverlaysState>

    private val state: PreviewOverlaysState
        get() = stateHolder.state.value

    override fun bind(stateHolder: StateHolder<PreviewOverlaysState>) {
        this.stateHolder = stateHolder
    }

    override fun exposureAfterChange(
        previous: PreviewOverlaysState,
        current: PreviewOverlaysState,
    ): ExposureState {
        val content = current.previewContent
        val cameraChanged = previous.previewContent?.mode != content?.mode ||
            previous.previewContent?.lensFacing != content?.lensFacing
        val isLivePreview = content?.isPreviewStreaming == true && !content.isTransitionFrameVisible
        return when {
            cameraChanged || !isLivePreview -> current.exposure.reset()
            !current.isPreviewInputAvailable() -> current.exposure.idle()
            else -> current.exposure
        }
    }

    override fun onAction(action: ExposureAction) {
        when (action) {
            is ExposureAction.ValueChanged -> onValueChanged(action.value)
            is ExposureAction.ResetClicked -> onResetClicked()
            is ExposureAction.PressChanged -> onPressChanged(action.isPressed)
        }
    }

    override fun onScreenPaused() {
        hidePanel(ExposureState())
    }

    override suspend fun trackExposure() {
        coroutineScope {
            launch { trackCameraExposure() }
            launch { trackFocusRequests() }
            trackPanelTimeout()
        }
    }

    private fun onValueChanged(value: Float) {
        val exposure = state.exposure.value ?: return
        if (!state.isExposureVisible() || !value.isFinite()) return

        val index = (value / exposure.compensationStep).roundToInt()
            .coerceIn(exposure.compensationRange)
        setCompensationIndex(
            index = index,
            isAdjusted = state.exposure.isAdjusted || index != exposure.compensationIndex,
        )
    }

    private fun onResetClicked() {
        if (!state.isExposureVisible()) return

        setCompensationIndex(
            index = 0,
            isAdjusted = false,
        )
    }

    private fun onPressChanged(isPressed: Boolean) {
        if (isPressed == state.exposure.isPressed) return
        if (isPressed && !state.isExposureVisible()) return

        updateExposure { exposure ->
            exposure.copy(
                isPressed = isPressed,
                interactionId = exposure.interactionId + 1,
            )
        }
    }

    private suspend fun trackCameraExposure() {
        exposureControl.exposure.collect { value ->
            when (value) {
                null -> hidePanel(state.exposure.reset())
                else -> updateExposure { exposure ->
                    exposure.copy(
                        value = value,
                        isAdjusted = exposure.isAdjusted || value.isCompensated(),
                    )
                }
            }
        }
    }

    private suspend fun trackFocusRequests() {
        stateHolder.state
            .map { state -> state.focus.selection?.requestId }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { showPanel() }
    }

    private suspend fun trackPanelTimeout() {
        stateHolder.state
            .map { state ->
                state.exposure.takeIf { exposure ->
                    state.isExposureVisible() && !exposure.isPressed
                }
            }
            .distinctUntilChanged()
            .collectLatest { exposure ->
                if (exposure != null) {
                    delay(PANEL_VISIBILITY_DURATION)
                    hidePanel(state.exposure.idle())
                }
            }
    }

    private fun showPanel() {
        val value = exposureControl.exposure.value
        if (value == null || !value.isAdjustable() || !state.isPreviewInputAvailable()) return

        stateHolder.update { state ->
            state.copy(
                activeControl = PreviewControl.Exposure,
                exposure = state.exposure.copy(
                    value = value,
                    isPressed = false,
                    isAdjusted = state.exposure.isAdjusted || value.isCompensated(),
                    interactionId = state.exposure.interactionId + 1,
                ),
            )
        }
    }

    private fun hidePanel(exposure: ExposureState) {
        stateHolder.update { state ->
            state.copy(
                activeControl = when (state.activeControl) {
                    PreviewControl.Exposure -> PreviewControl.Zoom
                    else -> state.activeControl
                },
                exposure = exposure,
            )
        }
    }

    private fun setCompensationIndex(
        index: Int,
        isAdjusted: Boolean,
    ) {
        exposureControl.setCompensationIndex(index)

        val value = exposureControl.exposure.value ?: return
        val isApplied = value.compensationIndex == index
        updateExposure { exposure ->
            exposure.copy(
                value = value,
                isAdjusted = when {
                    isApplied -> isAdjusted
                    else -> exposure.isAdjusted
                },
                interactionId = exposure.interactionId + 1,
            )
        }
    }

    private fun updateExposure(transform: (ExposureState) -> ExposureState) {
        stateHolder.update { state -> state.copy(exposure = transform(state.exposure)) }
    }

    private companion object {
        private val PANEL_VISIBILITY_DURATION = 2.seconds
    }
}
