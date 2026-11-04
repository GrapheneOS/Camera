package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import javax.inject.Inject

internal interface PreviewOverlaysUiStateMapper {

    fun map(state: PreviewOverlaysState): PreviewOverlaysUiState

    fun mapLevel(state: PreviewOverlaysState): LevelUiState
}

internal class PreviewOverlaysUiStateMapperImpl @Inject constructor() :
    PreviewOverlaysUiStateMapper {

    override fun map(state: PreviewOverlaysState): PreviewOverlaysUiState {
        val pattern = when (state.gridType) {
            GridType.THREE_BY_THREE -> CompositionGridPattern.Thirds
            GridType.FOUR_BY_FOUR -> CompositionGridPattern.Quarters
            GridType.GOLDEN_RATIO -> CompositionGridPattern.GoldenRatio
            GridType.NONE -> null
        }

        return PreviewOverlaysUiState(
            gridPattern = pattern ?: CompositionGridPattern.Thirds,
            isGridVisible = state.showsGrid(),
            selfTimerRemaining = state.visibleSelfTimerRemaining(),
        )
    }

    override fun mapLevel(state: PreviewOverlaysState): LevelUiState {
        val level = state.level
        val pose = level.pose ?: return LevelUiState()
        val rotationDegrees = level.orientation?.let { orientation ->
            val rotationDifference = state.displayRotation.degrees - orientation.degrees
            val normalizedRotation = rotationDifference.mod(FULL_TURN_DEGREES)
            normalizedRotation.toFloat()
        } ?: 0f

        return LevelUiState(
            visible = state.showsLevelOverlay(),
            mode = when (pose) {
                LevelPose.FacingUp -> LevelIndicatorMode.Crosshairs
                LevelPose.FacingDown -> LevelIndicatorMode.Crosshairs
                LevelPose.Horizon -> LevelIndicatorMode.Horizon
            },
            rollDegrees = level.tiltDegrees,
            pitchDegrees = 0f - level.horizonDegrees,
            rotationDegrees = rotationDegrees,
        )
    }

    private companion object {
        private const val FULL_TURN_DEGREES = 360
    }
}
