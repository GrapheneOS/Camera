package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.mapper.SwipeOutcomeMapper
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import javax.inject.Inject

interface ViewfinderModeSwitchDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun onPreviewSwiped(direction: SwipeDirection)
    fun onStripDragged()
    fun onStripTouchCancelled()
    fun onStripReleased(mode: CameraMode)

    fun followCamera()
    fun cancelSwitch()

    fun onPreviewStreaming(streaming: Boolean)
}

internal class ViewfinderModeSwitchDelegateImpl @Inject constructor(
    private val swipeOutcomeMapper: SwipeOutcomeMapper,
) : ViewfinderModeSwitchDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private val state: ViewfinderState
        get() = stateHolder.state.value

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun onPreviewSwiped(direction: SwipeDirection) {
        val outcome = swipeOutcomeMapper.map(
            direction = direction,
            state = state,
        )

        when (outcome) {
            null -> Unit

            is SwipeOutcome.ShowQrFormats -> {
                stateHolder.postEffect(Effect.ShowQrFormats)
            }

            is SwipeOutcome.SelectAdjacentMode -> {
                val cameraMode = adjacentMode(outcome.offset) ?: return
                onStripReleased(cameraMode)
            }
        }
    }

    override fun onStripDragged() {
        stateHolder.update { state ->
            state.copy(modeStrip = state.modeStrip.copy(isDragged = true))
        }
    }

    override fun onStripTouchCancelled() {
        stateHolder.update { state ->
            state.copy(modeStrip = state.modeStrip.copy(isDragged = false))
        }
    }

    override fun onStripReleased(mode: CameraMode) {
        // The strip is untouchable during a recording but not while its start sound still plays,
        // and rebinding the camera there starts the queued recording on a dead recorder. The touch
        // may already have dragged the strip, so put it back on the mode the camera is really in.
        if (state.recording.isActive()) {
            moveStrip(pendingMode = null)
            return
        }

        val isSwitchPending = mode != state.mode

        // Blurred while the strip is still gliding, not once the camera has gone: the rebind holds
        // the main thread for half a second, so a frame left to the stream state would only
        // reach the screen after the wait it is there to explain. Guarded on the mode really
        // changing, since nothing would rebind to take it back down again.
        stateHolder.update {
            it.copy(
                modeStrip = it.modeStrip
                    .movedTo(pendingMode = mode)
                    .copy(isSwitchPending = isSwitchPending),
                preview = it.preview.copy(
                    isLastFrameShown = it.preview.isLastFrameShown || isSwitchPending,
                ),
            )
        }
    }

    override fun followCamera() {
        moveStrip(pendingMode = null)
    }

    override fun cancelSwitch() {
        stateHolder.update { state ->
            // A frame raised for a switch the strip has just taken back, over a camera that never
            // stopped streaming: nothing is left to rebind and take it down.
            val isLastFrameShown = state.preview.isLastFrameShown && !state.preview.isStreaming

            state.copy(
                modeStrip = state.modeStrip.copy(
                    pendingMode = null,
                    isSwitchPending = false,
                ),
                preview = state.preview.copy(isLastFrameShown = isLastFrameShown),
            )
        }
    }

    override fun onPreviewStreaming(streaming: Boolean) {
        stateHolder.update { state ->
            state.copy(
                modeStrip = state.modeStrip.copy(
                    isSwitchPending = state.modeStrip.isSwitchPending && !streaming,
                ),
                preview = state.preview.copy(
                    isStreaming = streaming,
                    isLastFrameShown = !streaming,
                ),
            )
        }
    }

    private fun adjacentMode(offset: Int): CameraMode? {
        val modes = state.session.availableModes.toList()
        val index = modes.indexOf(state.modeStripTarget()).takeIf { it >= 0 } ?: return null

        return modes.getOrNull(index + offset)
    }

    private fun moveStrip(pendingMode: CameraMode?) {
        stateHolder.update { state ->
            state.copy(modeStrip = state.modeStrip.movedTo(pendingMode))
        }
    }
}
