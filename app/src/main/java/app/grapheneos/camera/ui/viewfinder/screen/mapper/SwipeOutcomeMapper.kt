package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeOutcome
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
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
        if (state.capture.isSelfTimerRunning) return null

        return when (direction) {
            SwipeDirection.DOWN -> openSettingsPanel(state)
            SwipeDirection.UP -> closeSettingsPanel(state)
            SwipeDirection.LEFT -> selectAdjacentMode(state = state, offset = 1)
            SwipeDirection.RIGHT -> selectAdjacentMode(state = state, offset = -1)
        }
    }

    private fun openSettingsPanel(state: ViewfinderState): SwipeOutcome? {
        return when {
            state.isSettingsPanelOpen || state.recording.isActive() -> null
            !state.isQrMode() -> SwipeOutcome.OpenSettingsPanel
            !state.settings.scanAllCodes -> SwipeOutcome.ShowQrFormats
            else -> null
        }
    }

    private fun closeSettingsPanel(state: ViewfinderState): SwipeOutcome? {
        return when {
            state.recording.isActive() -> null
            else -> SwipeOutcome.CloseSettingsPanel
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
