package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderModeStripState
import javax.inject.Inject

interface ViewfinderModeSwitchDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    fun onAdjacentModeSwiped(offset: Int)
    fun onStripReleased(mode: CameraMode)

    fun followCamera()
    fun cancelSwitch()

    fun onPreviewStreaming(streaming: Boolean)
}

internal class ViewfinderModeSwitchDelegateImpl @Inject constructor() :
    ViewfinderModeSwitchDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder
    }

    override fun onAdjacentModeSwiped(offset: Int) {
        adjacentMode(offset)?.let(::onStripReleased)
    }

    override fun onStripReleased(mode: CameraMode) {
        val state = stateHolder.state.value

        // The strip is untouchable during a recording but not while its start sound still plays,
        // and rebinding the camera there starts the queued recording on a dead recorder. The touch
        // may already have dragged the strip, so put it back on the mode the camera is really in.
        if (state.recording.isActive()) {
            moveStrip(pendingMode = null)
            return
        }

        moveStrip(pendingMode = mode)

        // Blurred while the strip is still gliding, not once the camera has gone: the rebind holds
        // the main thread for half a second, so a frame left to the stream state would only
        // reach the screen after the wait it is there to explain. Guarded on the mode really
        // changing, since nothing would rebind to take it back down again.
        if (mode != state.mode) {
            stateHolder.update {
                it.copy(preview = it.preview.copy(isLastFrameShown = true))
            }
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
                modeStrip = state.modeStrip.copy(pendingMode = null),
                preview = state.preview.copy(isLastFrameShown = isLastFrameShown),
            )
        }
    }

    override fun onPreviewStreaming(streaming: Boolean) {
        stateHolder.update { state ->
            state.copy(
                preview = state.preview.copy(
                    isStreaming = streaming,
                    isLastFrameShown = !streaming,
                ),
            )
        }
    }

    private fun adjacentMode(offset: Int): CameraMode? {
        val state = stateHolder.state.value
        val modes = state.session.availableModes.toList()
        val index = modes.indexOf(state.modeStripTarget()).takeIf { it >= 0 } ?: return null

        return modes.getOrNull(index + offset)
    }

    private fun moveStrip(pendingMode: CameraMode?) {
        stateHolder.update { state ->
            state.copy(
                modeStrip = ViewfinderModeStripState(
                    pendingMode = pendingMode,
                    selectionId = state.modeStrip.selectionId + 1,
                ),
            )
        }
    }
}
