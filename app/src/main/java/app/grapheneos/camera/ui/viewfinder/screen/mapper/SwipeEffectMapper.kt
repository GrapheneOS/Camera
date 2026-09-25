package app.grapheneos.camera.ui.viewfinder.screen.mapper

import app.grapheneos.camera.ui.viewfinder.screen.model.SwipeDirection
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import javax.inject.Inject

interface SwipeEffectMapper {

    fun map(
        direction: SwipeDirection,
        state: ViewfinderState,
    ): Effect?
}

internal class SwipeEffectMapperImpl @Inject constructor() : SwipeEffectMapper {

    override fun map(
        direction: SwipeDirection,
        state: ViewfinderState,
    ): Effect? {
        if (state.capture.isSelfTimerRunning) return null

        return when (direction) {
            SwipeDirection.DOWN -> openSettingsSheet(state)
            SwipeDirection.UP -> closeSettingsSheet(state)
            SwipeDirection.LEFT -> selectAdjacentMode(state = state, offset = 1)
            SwipeDirection.RIGHT -> selectAdjacentMode(state = state, offset = -1)
        }
    }

    private fun openSettingsSheet(state: ViewfinderState): Effect? {
        return when {
            !state.isQrMode() -> Effect.Settings.OpenSheet
            !state.settings.scanAllCodes -> Effect.Settings.ShowQrFormats
            else -> null
        }
    }

    private fun closeSettingsSheet(state: ViewfinderState): Effect? {
        return when {
            state.recording.isActive() -> null
            else -> Effect.Settings.CloseSheet
        }
    }

    private fun selectAdjacentMode(
        state: ViewfinderState,
        offset: Int,
    ): Effect? {
        return when {
            state.recording.isActive() || !state.showsCameraModeTabs -> null
            else -> Effect.ModeTab.SelectAdjacent(offset = offset)
        }
    }
}
