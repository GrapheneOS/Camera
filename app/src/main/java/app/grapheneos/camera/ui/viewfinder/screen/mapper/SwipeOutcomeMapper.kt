package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.input.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import javax.inject.Inject

interface SwipeOutcomeMapper {

    fun map(
        direction: SwipeDirection,
        state: ViewfinderState,
    ): SwipeOutcome?
}

internal class SwipeOutcomeMapperImpl @Inject constructor() : SwipeOutcomeMapper {

    override fun map(
        direction: SwipeDirection,
        state: ViewfinderState,
    ): SwipeOutcome? {
        if (state.capture.isSelfTimerRunning()) return null

        return when (direction) {
            SwipeDirection.UP -> showQrFormats(state)
            SwipeDirection.DOWN -> null
            SwipeDirection.LEFT -> selectAdjacentMode(state = state, offset = 1)
            SwipeDirection.RIGHT -> selectAdjacentMode(state = state, offset = -1)
        }
    }

    private fun showQrFormats(state: ViewfinderState): SwipeOutcome? {
        return when {
            state.isQrMode() && !state.settings.scanAllCodes -> SwipeOutcome.ShowQrFormats
            else -> null
        }
    }

    private fun selectAdjacentMode(
        state: ViewfinderState,
        offset: Int,
    ): SwipeOutcome? {
        return when {
            state.isSettingsPanelOpen -> null
            state.recording.isActive() || !state.showsCameraModeTabs -> null
            else -> SwipeOutcome.SelectAdjacentMode(offset = offset)
        }
    }
}
