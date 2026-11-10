package app.grapheneos.camera.ui.previewoverlays.screen.mapper

import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.domain.camera.mapper.ZoomPresetsMapper
import app.grapheneos.camera.ui.components.compositiongrid.model.CompositionGridPattern
import app.grapheneos.camera.ui.components.focusindicator.model.FocusIndicatorAppearance
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPhase
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.FocusPress
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.LevelPose
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewControl
import app.grapheneos.camera.ui.previewoverlays.screen.model.state.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.FocusUiModel
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewControlUiModel
import app.grapheneos.camera.ui.previewoverlays.screen.model.ui.PreviewOverlaysUiState
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList

internal interface PreviewOverlaysUiStateMapper {

    fun map(state: PreviewOverlaysState): PreviewOverlaysUiState

    fun mapLevel(state: PreviewOverlaysState): LevelUiState
}

internal class PreviewOverlaysUiStateMapperImpl @Inject constructor(
    private val zoomPresetsMapper: ZoomPresetsMapper,
) : PreviewOverlaysUiStateMapper {

    override fun map(state: PreviewOverlaysState): PreviewOverlaysUiState {
        return PreviewOverlaysUiState(
            focus = focus(state),
            control = control(state),
            gridPattern = gridPattern(state),
            selfTimerRemaining = state.visibleSelfTimerRemaining(),
        )
    }

    override fun mapLevel(state: PreviewOverlaysState): LevelUiState {
        val level = state.level
        val pose = level.pose ?: return LevelUiState()

        return LevelUiState(
            visible = state.isLevelVisible(),
            mode = when (pose) {
                LevelPose.FacingUp -> LevelIndicatorMode.Crosshairs
                LevelPose.FacingDown -> LevelIndicatorMode.Crosshairs
                LevelPose.Horizon -> LevelIndicatorMode.Horizon
            },
            rollDegrees = level.tiltDegrees,
            pitchDegrees = 0f - level.horizonDegrees,
            rotationDegrees = levelRotationDegrees(state),
        )
    }

    private fun gridPattern(state: PreviewOverlaysState): CompositionGridPattern? {
        if (!state.isGridVisible()) return null

        return when (state.settings.gridType) {
            GridType.THREE_BY_THREE -> CompositionGridPattern.Thirds
            GridType.FOUR_BY_FOUR -> CompositionGridPattern.Quarters
            GridType.GOLDEN_RATIO -> CompositionGridPattern.GoldenRatio
            GridType.NONE -> null
        }
    }

    private fun levelRotationDegrees(state: PreviewOverlaysState): Float {
        val orientation = state.level.orientation ?: return 0f
        val difference = state.displayRotation.degrees - orientation.degrees

        return difference.mod(FULL_TURN_DEGREES).toFloat()
    }

    private fun focus(state: PreviewOverlaysState): FocusUiModel? {
        val press = state.focus.press as? FocusPress.NewPoint
        val selection = state.focus.selection
        return when {
            press != null -> FocusUiModel(
                requestId = press.requestId,
                x = press.x,
                y = press.y,
                appearance = FocusIndicatorAppearance.Focusing,
                visible = state.isPreviewInputAvailable(),
            )
            selection != null -> FocusUiModel(
                requestId = selection.requestId,
                x = selection.x,
                y = selection.y,
                appearance = when (selection.phase) {
                    FocusPhase.Focusing -> FocusIndicatorAppearance.Focusing
                    FocusPhase.Focused -> FocusIndicatorAppearance.Focused
                    FocusPhase.Locked -> FocusIndicatorAppearance.Locked
                },
                visible = selection.visible && state.isPreviewInputAvailable(),
            )
            else -> null
        }
    }

    private fun control(state: PreviewOverlaysState): PreviewControlUiModel? {
        return when (state.visibleControl()) {
            PreviewControl.Zoom -> zoom(state)
            PreviewControl.Exposure -> exposure(state)
            null -> null
        }
    }

    private fun zoom(state: PreviewOverlaysState): PreviewControlUiModel.Zoom? {
        val content = state.previewContent
        val zoom = state.zoom.value
        if (content == null || zoom == null) return null

        val range = zoom.minZoomRatio..zoom.maxZoomRatio
        val presets = zoomPresetsMapper.map(
            zoom = zoom,
            lensFacing = content.lensFacing,
        )

        return PreviewControlUiModel.Zoom(
            ratio = zoom.zoomRatio.coerceIn(range),
            range = range,
            presets = presets.toImmutableList(),
            isCurrentValueShown = state.zoom.pendingPreset == null,
            isExpanded = state.zoom.isExpanded,
            isPinching = state.zoom.isPreviewPinching,
        )
    }

    private fun exposure(state: PreviewOverlaysState): PreviewControlUiModel.Exposure? {
        val exposure = state.exposure.value ?: return null

        val step = exposure.compensationStep
        val range = exposure.compensationRange

        return PreviewControlUiModel.Exposure(
            value = exposure.compensationIndex.coerceIn(range) * step,
            range = range.first * step..range.last * step,
            steps = range.last - range.first - 1,
            majorTickInterval = (1f / step).roundToInt().coerceAtLeast(1),
            isResetVisible = state.exposure.isAdjusted,
        )
    }

    private companion object {
        private const val FULL_TURN_DEGREES = 360
    }
}
